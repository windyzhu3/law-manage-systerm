package com.law.todo.application;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoConfigurationCommands.ScenarioSimulationCommand;
import com.law.todo.application.view.TodoSimulationScenarioViews.SimulationEvidenceSummary;
import com.law.todo.application.view.TodoSimulationScenarioViews.SimulationScenario;
import com.law.todo.definition.compiler.DefinitionValidationReport;
import com.law.todo.definition.compiler.DefinitionValidationReport.ValidationIssue;
import com.law.todo.mapper.TodoConfigurationMapper;

@Service
public class TodoSimulationEvidenceService
{
    private final TodoConfigurationMapper mapper;
    private final TodoSimulationScenarioCatalog scenarios;

    public TodoSimulationEvidenceService(TodoConfigurationMapper mapper)
    {this(mapper,new TodoSimulationScenarioCatalog(mapper));}

    @Autowired
    public TodoSimulationEvidenceService(TodoConfigurationMapper mapper,TodoSimulationScenarioCatalog scenarios)
    {this.mapper=mapper;this.scenarios=scenarios;}

    @Transactional
    public SimulationEvidenceSummary record(long templateId,SimulationScenario scenario,
            ScenarioSimulationCommand command,String actualNextTemplateCode,boolean passed,
            List<String> traceCodes,Actor actor)
    {
        String inputHash=inputHash(templateId,scenario,command);
        LocalDateTime executed=LocalDateTime.now();
        Map<String,Object> summary=new TreeMap<>();
        summary.put("actualNextTemplateCode",actualNextTemplateCode);
        summary.put("expectedNextTemplateCode",scenario.expectedNextTemplateCode());
        summary.put("passed",passed);summary.put("traceCodes",traceCodes==null?List.of():traceCodes);
        Map<String,Object> row=new HashMap<>();
        row.put("templateId",templateId);row.put("versionId",command.versionId());
        row.put("definitionHash",command.definitionHash());row.put("scenarioCode",scenario.scenarioCode());
        row.put("scenarioVersion",scenario.scenarioVersion());row.put("resultStatus",passed?"PASSED":"FAILED");
        row.put("inputHash",inputHash);
        row.put("traceSummaryJson",JSON.toJSONString(summary,JSONWriter.Feature.SortMapEntriesByKeys));
        row.put("executedBy",actor.userId());row.put("executedTime",executed);
        row.put("expireTime",null);mapper.insertSimulationEvidence(row);
        return new SimulationEvidenceSummary(scenario.scenarioCode(),scenario.scenarioVersion(),
                command.definitionHash(),passed?"PASSED":"FAILED",inputHash,executed,null);
    }

    @Transactional(readOnly=true)
    public PublicationGate gate(long templateId,long versionId,String definitionHash,List<SimulationScenario> scenarios)
    {
        List<SimulationGateBlocker> blockers=new ArrayList<>();
        for(SimulationScenario scenario:scenarios==null?List.<SimulationScenario>of():scenarios)
        {
            if(!scenario.requiredForPublish()||!"ACTIVE".equals(scenario.status()))continue;
            Map<String,Object> query=new HashMap<>();query.put("templateId",templateId);query.put("versionId",versionId);
            query.put("definitionHash",definitionHash);query.put("scenarioCode",scenario.scenarioCode());
            query.put("scenarioVersion",scenario.scenarioVersion());
            if(mapper.selectPassingSimulationEvidence(query)==null)
            {
                Map<String,Object> latest=mapper.selectLatestSimulationEvidence(query);
                blockers.add(new SimulationGateBlocker(scenario.scenarioCode(),
                        evidenceReason(latest,definitionHash)));
            }
        }
        return new PublicationGate(blockers.isEmpty(),
                blockers.stream().map(SimulationGateBlocker::scenarioCode).toList(),blockers);
    }

    @Transactional(readOnly=true)
    public DefinitionValidationReport applyPreflightGate(long versionId,DefinitionValidationReport report)
    {
        Map<String,Object> identity=mapper.selectTemplateIdentityByVersionId(versionId);
        if(identity==null||identity.isEmpty())return report;
        long templateId=Long.parseLong(String.valueOf(value(identity,"template_id","templateId")));
        String templateCode=String.valueOf(value(identity,"template_code","templateCode"));
        String businessType=String.valueOf(value(identity,"business_type","businessType"));
        List<SimulationScenario> required=scenarios.scenarios(templateCode,businessType);
        if(required.stream().noneMatch(SimulationScenario::requiredForPublish))return report;
        PublicationGate gate=gate(templateId,versionId,report.definitionHash(),required);
        if(gate.publicationReady())return report;
        List<ValidationIssue> errors=new ArrayList<>(report.errors());
        Map<String,String> names=new HashMap<>();
        required.forEach(scenario->names.put(scenario.scenarioCode(),scenario.scenarioName()));
        String details=gate.blockers().stream().map(blocker->
                names.getOrDefault(blocker.scenarioCode(),blocker.scenarioCode())
                        +"（"+reasonLabel(blocker.reason())+"）").collect(java.util.stream.Collectors.joining("、"));
        errors.add(new ValidationIssue("TODO_REQUIRED_SIMULATION_SCENARIOS_INCOMPLETE","simulation.scenarios",
                "以下必测场景尚未通过："+details));
        return new DefinitionValidationReport(errors,report.warnings(),report.compiledJson(),report.definitionHash());
    }

    String inputHash(long templateId,SimulationScenario scenario,ScenarioSimulationCommand command)
    {
        Map<String,Object> canonical=new TreeMap<>();
        canonical.put("templateId",templateId);canonical.put("versionId",command.versionId());
        canonical.put("definitionHash",command.definitionHash());canonical.put("scenarioCode",scenario.scenarioCode());
        canonical.put("scenarioVersion",scenario.scenarioVersion());canonical.put("businessType",command.businessType());
        canonical.put("businessId",command.businessId());canonical.put("manualOverrides",command.manualOverrides());
        canonical.put("effectiveAt",command.effectiveAt().toString());
        return TodoDefinitionSimulationService.sha256(
                JSON.toJSONString(canonical,JSONWriter.Feature.SortMapEntriesByKeys));
    }

    private Object value(Map<String,Object> row,String snake,String camel)
    {return row.containsKey(snake)?row.get(snake):row.get(camel);}

    private String evidenceReason(Map<String,Object> latest,String definitionHash)
    {
        if(latest==null||latest.isEmpty())return "MISSING";
        Object hash=value(latest,"definition_hash","definitionHash");
        if(hash==null||!definitionHash.equals(String.valueOf(hash)))return "DEFINITION_CHANGED";
        Object status=value(latest,"result_status","resultStatus");
        if(status==null||!"PASSED".equals(String.valueOf(status)))return "LAST_RUN_FAILED";
        Object expires=value(latest,"expire_time","expireTime");
        if(expires instanceof LocalDateTime time&&!time.isAfter(LocalDateTime.now()))return "EVIDENCE_EXPIRED";
        return "EVIDENCE_UNAVAILABLE";
    }

    private String reasonLabel(String reason)
    {
        return switch(reason)
        {
            case "DEFINITION_CHANGED" -> "配置已变更，请重新验证";
            case "LAST_RUN_FAILED" -> "最近一次验证未通过";
            case "EVIDENCE_EXPIRED" -> "验证结果已过期，请重新验证";
            case "EVIDENCE_UNAVAILABLE" -> "验证结果不可用，请重新验证";
            default -> "尚未验证";
        };
    }

    public record SimulationGateBlocker(String scenarioCode,String reason) { }

    public record PublicationGate(boolean publicationReady,List<String> blockingScenarioCodes,
            List<SimulationGateBlocker> blockers)
    {
        public PublicationGate
        {
            blockingScenarioCodes=blockingScenarioCodes==null?List.of():List.copyOf(blockingScenarioCodes);
            blockers=blockers==null?List.of():List.copyOf(blockers);
        }
        public PublicationGate(boolean publicationReady,List<String> blockingScenarioCodes)
        {this(publicationReady,blockingScenarioCodes,blockingScenarioCodes==null?List.of():
                blockingScenarioCodes.stream().map(code->new SimulationGateBlocker(code,"MISSING")).toList());}
    }
}
