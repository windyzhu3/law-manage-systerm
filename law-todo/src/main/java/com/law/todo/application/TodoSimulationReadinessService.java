package com.law.todo.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.law.todo.application.TodoSimulationEvidenceService.PublicationGate;
import com.law.todo.application.TodoSimulationEvidenceService.SimulationGateBlocker;
import com.law.todo.application.view.TodoConfigurationJourneyView.JourneyIssue;
import com.law.todo.application.view.TodoSimulationReadinessView;
import com.law.todo.application.view.TodoSimulationScenarioViews.SimulationScenario;
import com.law.todo.definition.compiler.DefinitionValidationReport;
import com.law.todo.definition.compiler.DefinitionValidationReport.ValidationIssue;
import com.law.todo.mapper.TodoConfigurationMapper;

@Service
public class TodoSimulationReadinessService
{
    public static final String FULL_SIMULATION="FULL_SIMULATION";
    public static final int FULL_SIMULATION_VERSION=1;

    private final TodoSimulationScenarioCatalog scenarios;
    private final TodoSimulationEvidenceService evidence;
    private final TodoConfigurationMapper mapper;

    public TodoSimulationReadinessService(TodoSimulationScenarioCatalog scenarios,
            TodoSimulationEvidenceService evidence)
    {
        this(scenarios,evidence,null);
    }

    @Autowired
    public TodoSimulationReadinessService(TodoSimulationScenarioCatalog scenarios,
            TodoSimulationEvidenceService evidence,TodoConfigurationMapper mapper)
    {
        this.scenarios=scenarios;
        this.evidence=evidence;
        this.mapper=mapper;
    }

    @Transactional(readOnly=true)
    public TodoSimulationReadinessView readiness(long templateId,long versionId,
            String definitionHash,String templateCode,String businessType)
    {
        List<SimulationScenario> catalog=scenarios.scenarios(templateCode,businessType);
        List<SimulationScenario> required=catalog.stream()
                .filter(SimulationScenario::requiredForPublish)
                .filter(scenario->"ACTIVE".equals(scenario.status()))
                .toList();
        PublicationGate scenarioGate=evidence.gate(
                templateId,versionId,definitionHash,required);
        boolean fullReady=evidence.hasPassingEvidence(templateId,versionId,
                definitionHash,FULL_SIMULATION,FULL_SIMULATION_VERSION);

        List<JourneyIssue> issues=new ArrayList<>();
        if(!scenarioGate.publicationReady())
        {
            Map<String,String> names=new LinkedHashMap<>();
            required.forEach(scenario->names.put(scenario.scenarioCode(),scenario.scenarioName()));
            String scenarioNames=scenarioGate.blockers().stream()
                    .map(SimulationGateBlocker::scenarioCode)
                    .map(code->names.getOrDefault(code,code))
                    .collect(Collectors.joining("、"));
            issues.add(new JourneyIssue(
                    "TODO_REQUIRED_SIMULATION_SCENARIOS_INCOMPLETE",
                    "BLOCKER",
                    "SIMULATION_PUBLISH",
                    "simulation.scenarios",
                    "还有必测场景未通过："+scenarioNames,
                    "验证未通过场景","SIMULATION"));
        }
        if(!fullReady)
        {
            issues.add(new JourneyIssue(
                    "TODO_FULL_SIMULATION_REQUIRED",
                    "BLOCKER",
                    "SIMULATION_PUBLISH",
                    "simulation.full",
                    "完整试运行尚未通过",
                    "运行完整试运行","SIMULATION"));
        }

        int passed=Math.max(0,required.size()-scenarioGate.blockers().size());
        boolean publicationReady=scenarioGate.publicationReady()&&fullReady;
        return new TodoSimulationReadinessView(templateId,versionId,definitionHash,
                required.size(),passed,scenarioGate.blockers(),fullReady,
                publicationReady,issues);
    }

    @Transactional(readOnly=true)
    public DefinitionValidationReport applyPreflightGate(long versionId,
            DefinitionValidationReport report)
    {
        if(report==null||mapper==null)return report;
        Map<String,Object> identity=mapper.selectTemplateIdentityByVersionId(versionId);
        if(identity==null||identity.isEmpty())return report;
        long templateId=Long.parseLong(String.valueOf(value(identity,"template_id","templateId")));
        String templateCode=String.valueOf(value(identity,"template_code","templateCode"));
        String businessType=String.valueOf(value(identity,"business_type","businessType"));
        TodoSimulationReadinessView state=readiness(templateId,versionId,
                report.definitionHash(),templateCode,businessType);

        Map<String,ValidationIssue> errors=new LinkedHashMap<>();
        report.errors().forEach(issue->errors.put(issue.code()+'\u0000'+issue.path(),issue));
        state.issues().forEach(issue->errors.putIfAbsent(
                issue.code()+'\u0000'+issue.fieldPath(),
                new ValidationIssue(issue.code(),issue.fieldPath(),issue.message())));
        return new DefinitionValidationReport(List.copyOf(errors.values()),report.warnings(),
                report.compiledJson(),report.definitionHash());
    }

    @Transactional(readOnly=true)
    public Map<Long,TodoSimulationReadinessView> readinessBatch(List<BatchRequest> requests)
    {
        List<BatchRequest> source=requests==null?List.of():requests;
        if(source.isEmpty())return Map.of();
        List<Long> versionIds=source.stream()
                .filter(request->!immutable(request.publishStatus()))
                .map(BatchRequest::versionId).distinct().toList();
        Map<Long,Map<String,Object>> rows=new LinkedHashMap<>();
        if(mapper!=null&&!versionIds.isEmpty())
        {
            List<Map<String,Object>> loaded=mapper.selectSimulationReadinessBatch(versionIds);
            for(Map<String,Object> row:loaded==null?List.<Map<String,Object>>of():loaded)
                rows.put(longValue(value(row,"version_id","versionId")),row);
        }

        Map<Long,TodoSimulationReadinessView> result=new LinkedHashMap<>();
        for(BatchRequest request:source)
        {
            if(immutable(request.publishStatus()))
            {
                result.put(request.versionId(),new TodoSimulationReadinessView(
                        request.templateId(),request.versionId(),request.definitionHash(),
                        0,0,List.of(),true,true,List.of()));
                continue;
            }
            result.put(request.versionId(),batchProjection(request,rows.get(request.versionId())));
        }
        return Map.copyOf(result);
    }

    private TodoSimulationReadinessView batchProjection(BatchRequest request,Map<String,Object> row)
    {
        if(row==null||request.definitionHash()==null||request.definitionHash().isBlank()
                ||!request.definitionHash().equals(
                stringValue(value(row,"definition_hash","definitionHash"))))
            return projection(request,0,0,List.of(),List.of(),false);
        int required=intValue(value(row,"required_scenario_count","requiredScenarioCount"));
        int passed=intValue(value(row,"passed_scenario_count","passedScenarioCount"));
        List<String> codes=split(value(row,"blocking_scenario_codes","blockingScenarioCodes"));
        List<String> names=split(value(row,"blocking_scenario_names","blockingScenarioNames"));
        List<SimulationGateBlocker> blockers=codes.stream()
                .map(code->new SimulationGateBlocker(code,"MISSING")).toList();
        return projection(request,required,passed,blockers,names,
                booleanValue(value(row,"full_simulation_passed","fullSimulationPassed")));
    }

    private TodoSimulationReadinessView projection(BatchRequest request,int required,int passed,
            List<SimulationGateBlocker> blockers,List<String> blockerNames,boolean fullReady)
    {
        List<JourneyIssue> issues=new ArrayList<>();
        if(required>passed||!blockers.isEmpty())
        {
            String names=blockerNames.isEmpty()?blockers.stream()
                    .map(SimulationGateBlocker::scenarioCode).collect(Collectors.joining("、")):
                    String.join("、",blockerNames);
            issues.add(new JourneyIssue("TODO_REQUIRED_SIMULATION_SCENARIOS_INCOMPLETE",
                    "BLOCKER","SIMULATION_PUBLISH","simulation.scenarios",
                    "还有必测场景未通过："+names,"验证未通过场景","SIMULATION"));
        }
        if(!fullReady)
            issues.add(new JourneyIssue("TODO_FULL_SIMULATION_REQUIRED","BLOCKER",
                    "SIMULATION_PUBLISH","simulation.full",
                    "完整试运行尚未通过","运行完整试运行","SIMULATION"));
        boolean ready=required==passed&&blockers.isEmpty()&&fullReady;
        return new TodoSimulationReadinessView(request.templateId(),request.versionId(),
                request.definitionHash(),required,passed,blockers,fullReady,ready,issues);
    }

    private boolean immutable(String status)
    {
        return "PUBLISHED".equals(status)||"RETIRED".equals(status);
    }

    private List<String> split(Object value)
    {
        String text=stringValue(value);
        return text==null||text.isBlank()?List.of():
                java.util.Arrays.stream(text.split("\\|"))
                        .map(String::trim).filter(item->!item.isEmpty()).toList();
    }

    private int intValue(Object value)
    {
        return value==null?0:Integer.parseInt(String.valueOf(value));
    }

    private long longValue(Object value)
    {
        return Long.parseLong(String.valueOf(value));
    }

    private boolean booleanValue(Object value)
    {
        if(value instanceof Boolean result)return result;
        return value!=null&&("1".equals(String.valueOf(value))
                ||"true".equalsIgnoreCase(String.valueOf(value)));
    }

    private String stringValue(Object value)
    {
        return value==null?null:String.valueOf(value);
    }

    private Object value(Map<String,Object> row,String snake,String camel)
    {
        return row.containsKey(snake)?row.get(snake):row.get(camel);
    }

    public record BatchRequest(long templateId,long versionId,String definitionHash,
            String publishStatus) { }
}
