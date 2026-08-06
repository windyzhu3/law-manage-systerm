package com.law.todo.application;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.view.TodoConfigurationJourneyView;
import com.law.todo.application.view.TodoConfigurationJourneyView.CurrentResources;
import com.law.todo.application.view.TodoConfigurationJourneyView.JourneyIssue;
import com.law.todo.application.view.TodoConfigurationJourneyView.JourneyPermissions;
import com.law.todo.application.view.TodoConfigurationJourneyView.JourneyStep;
import com.law.todo.application.view.TodoConfigurationJourneyView.TemplateSummary;
import com.law.todo.application.view.TodoConfigurationJourneyView.TemplateWorkbenchItem;
import com.law.todo.application.view.TodoConfigurationJourneyView.TemplateWorkbenchPage;
import com.law.todo.application.view.TodoConfigurationViews.TemplateConfigurationDetail;
import com.law.todo.application.view.TodoConfigurationViews.TemplateVersionDetail;
import com.law.todo.application.view.TodoConfigurationViews.JourneyImpact;
import com.law.todo.application.view.TodoConfigurationViews.RoutingTargetCatalogEntry;
import com.law.todo.application.view.TodoSimulationReadinessView;
import com.law.todo.definition.codec.TodoDefinitionCodec;
import com.law.todo.definition.model.TodoDefinitionDocument;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoConfigurationMapper;

/** Read-only aggregate for the configuration journey and its batch workbench. */
@Service
public class TodoConfigurationJourneyService
{
    private static final int STEP_COUNT=7;
    private static final int MAX_BATCH_ROWS=5000;
    private final TodoConfigurationQueryService query;
    private final TodoDefinitionCodec codec;
    private final TodoConfigurationMapper mapper;
    private final TodoConfigurationResourceCatalogService resourceCatalog;
    private final TodoConfigurationJourneyEvaluator evaluator;
    private final TodoEmployeeTodoPreviewProjector preview;
    private final TodoTemplateService templates;
    private final TodoEventResourceService eventResources;
    private final TodoBusinessOutcomeCatalogService outcomes;
    private final TodoSimulationReadinessService readiness;
    private final TodoJourneyDependencyService dependencies=new TodoJourneyDependencyService();

    @Autowired
    public TodoConfigurationJourneyService(TodoConfigurationQueryService query,TodoConfigurationMapper mapper,
            TodoConfigurationResourceCatalogService resourceCatalog,TodoConfigurationJourneyEvaluator evaluator,
            TodoEmployeeTodoPreviewProjector preview,TodoTemplateService templates,
            TodoEventResourceService eventResources,TodoBusinessOutcomeCatalogService outcomes,
            TodoSimulationReadinessService readiness)
    {this(query,new TodoDefinitionCodec(),mapper,resourceCatalog,evaluator,preview,templates,eventResources,outcomes,readiness);}

    TodoConfigurationJourneyService(TodoConfigurationQueryService query,TodoDefinitionCodec codec,TodoConfigurationMapper mapper,
            TodoConfigurationResourceCatalogService resourceCatalog,TodoConfigurationJourneyEvaluator evaluator,
            TodoEmployeeTodoPreviewProjector preview,TodoTemplateService templates,
            TodoEventResourceService eventResources)
    {this(query,codec,mapper,resourceCatalog,evaluator,preview,templates,eventResources,null,null);}

    TodoConfigurationJourneyService(TodoConfigurationQueryService query,TodoDefinitionCodec codec,TodoConfigurationMapper mapper,
            TodoConfigurationResourceCatalogService resourceCatalog,TodoConfigurationJourneyEvaluator evaluator,
            TodoEmployeeTodoPreviewProjector preview,TodoTemplateService templates,
            TodoEventResourceService eventResources,TodoBusinessOutcomeCatalogService outcomes)
    {this(query,codec,mapper,resourceCatalog,evaluator,preview,templates,eventResources,outcomes,null);}

    TodoConfigurationJourneyService(TodoConfigurationQueryService query,TodoDefinitionCodec codec,TodoConfigurationMapper mapper,
            TodoConfigurationResourceCatalogService resourceCatalog,TodoConfigurationJourneyEvaluator evaluator,
            TodoEmployeeTodoPreviewProjector preview,TodoTemplateService templates,
            TodoEventResourceService eventResources,TodoBusinessOutcomeCatalogService outcomes,
            TodoSimulationReadinessService readiness)
    {
        this.query=query;this.codec=codec;this.mapper=mapper;this.resourceCatalog=resourceCatalog;
        this.evaluator=evaluator;this.preview=preview;this.templates=templates;this.eventResources=eventResources;
        this.outcomes=outcomes;this.readiness=readiness;
    }

    public TodoConfigurationJourneyView load(long templateId,Actor actor)
    {
        TemplateConfigurationDetail detail=query.template(templateId);
        TemplateVersionDetail version=Objects.requireNonNull(detail.editableVersion(),"editableVersion");
        TodoDefinitionDocument definition=codec.read(version.definitionJson());
        TodoSimulationReadinessView state=readiness==null?null:readiness.readiness(
                templateId,version.versionId(),version.definitionHash(),
                detail.templateCode(),detail.businessType());
        TodoConfigurationJourneyEvaluator.Evaluation evaluation=evaluator.evaluate(detail,definition,state);
        return new TodoConfigurationJourneyView(summary(detail,version),evaluation.steps(),resources(detail,definition),
                preview.project(detail,definition),evaluation.issues(),permissions(actor));
    }

    /** Internal canonical source; deliberately excluded from the HTTP journey view. */
    public String canonicalDefinition(long templateId,Actor actor)
    {
        TemplateConfigurationDetail detail=query.template(templateId);
        return Objects.requireNonNull(detail.editableVersion(),"editableVersion").definitionJson();
    }

    public JourneyImpact impact(String beforeDefinitionJson,String afterDefinitionJson)
    {
        return dependencies.analyze(codec.read(beforeDefinitionJson),codec.read(afterDefinitionJson));
    }

    public TemplateWorkbenchPage workbench(Map<String,Object> query,Actor actor)
    {
        Map<String,Object> normalized=normalize(query);
        int offset=pagination(normalized.get("offset"),0,"offset");
        int limit=pagination(normalized.get("limit"),20,"limit");
        if(offset<0||limit<1||limit>200)
            throw new TodoException("TODO_CONFIGURATION_QUERY_INVALID","Workbench pagination is out of range");
        String issueType=text(normalized.get("issueType"));
        Predicate<TemplateWorkbenchItem> issueFilter=issueFilter(issueType);
        // Issue health is derived from the definition, so evaluate one bounded SQL batch before
        // applying the issue filter and page. Rejecting overflow preserves exact totals.
        Map<String,Object> batch=new LinkedHashMap<>(normalized);
        batch.remove("issueType");batch.put("offset",0);batch.put("limit",MAX_BATCH_ROWS+1);
        List<Map<String,Object>> source=mapper.selectTemplateJourneySummaries(batch);
        source=source==null?List.of():source;
        if(source.size()>MAX_BATCH_ROWS)
            throw new TodoException("TODO_CONFIGURATION_WORKBENCH_LIMIT_EXCEEDED",
                    "Workbench non-issue filters exceed the bounded evaluation limit");
        Map<Long,TodoSimulationReadinessView> readinessByVersion=readiness==null?Map.of():
                readiness.readinessBatch(source.stream().map(this::readinessRequest).toList());
        List<TemplateWorkbenchItem> evaluated=source.stream()
                .map(row->workbenchItem(row,readinessByVersion.get(versionId(row)))).toList();
        int blockerTemplates=(int)evaluated.stream().filter(item->health(item).equals("BLOCKER")).count();
        int warningTemplates=(int)evaluated.stream().filter(item->health(item).equals("WARNING")).count();
        int readyTemplates=evaluated.size()-blockerTemplates-warningTemplates;
        List<TemplateWorkbenchItem> filtered=evaluated.stream().filter(issueFilter).toList();
        int from=Math.min(offset,filtered.size());int to=Math.min(from+limit,filtered.size());
        return new TemplateWorkbenchPage(filtered.subList(from,to),filtered.size(),
                blockerTemplates,warningTemplates,readyTemplates);
    }

    private TemplateSummary summary(TemplateConfigurationDetail detail,TemplateVersionDetail version)
    {
        return new TemplateSummary(detail.templateId(),version.versionId(),number(version.versionNo()),number(detail.version()),
                detail.templateCode(),detail.templateName(),detail.businessType(),businessStage(version.definitionJson()),version.status(),
                version.definitionHash());
    }

    private CurrentResources resources(TemplateConfigurationDetail detail,TodoDefinitionDocument definition)
    {
        String businessType=detail.businessType();
        var fields=resourceCatalog.fields(businessType);
        var events=eventResources.list(Map.of("businessObjectType",businessType,"offset",0,"limit",500)).rows();
        return new CurrentResources(events,fields,query.ownerCatalog(),
                resourceCatalog.materials(businessType),resourceCatalog.validators(businessType),resourceCatalog.recipes(businessType),
                templates.listTemplateCalendarCatalog(),routingTargets(businessType),
                outcomes==null?TodoBusinessOutcomeCatalogService.BusinessOutcomeSet.empty():
                        outcomes.resolve(detail.templateCode(),businessType,
                                detail.editableVersion()==null?null:detail.editableVersion().versionId(),definition));
    }

    private List<RoutingTargetCatalogEntry> routingTargets(String businessType)
    {
        List<Map<String,Object>> rows=mapper.selectPublishedRoutingTargetCatalog(businessType);
        if(rows==null)return List.of();
        return rows.stream()
                .filter(row->businessType.equals(text(row,"business_type","businessType")))
                .filter(row->"PUBLISHED".equals(text(row,"status","status")))
                .map(row->new RoutingTargetCatalogEntry(
                        longNumber(value(row,"template_id","templateId")),text(row,"template_code","templateCode"),
                        text(row,"template_name","templateName"),text(row,"business_type","businessType"),
                        longNumber(value(row,"version_id","versionId")),
                        integer(value(row,"version_no","versionNo"),0),text(row,"status","status")))
                .toList();
    }

    private TemplateWorkbenchItem workbenchItem(Map<String,Object> row,TodoSimulationReadinessView readiness)
    {
        String definitionJson=text(row,"definition_json","definitionJson");
        TodoDefinitionDocument definition=codec.read(definitionJson);
        TemplateConfigurationDetail detail=workbenchDetail(row,definitionJson);
        TodoConfigurationJourneyEvaluator.Evaluation evaluation=evaluator.evaluatePure(detail,definition,readiness);
        List<JourneyIssue> issues=mergeIssues(evaluation.issues(),
                persistedIssues(text(row,"validation_report_json","validationReportJson")).stream()
                        .filter(issue->!simulationIssue(issue.code())).toList());
        int blockers=(int)issues.stream().filter(issue->"BLOCKER".equals(issue.severity())).count();
        int warnings=(int)issues.stream().filter(issue->"WARNING".equals(issue.severity())).count();
        int completed=(int)evaluation.steps().stream().filter(this::completed).count();
        JourneyStep nextStep=evaluation.steps().stream().filter(step->!completed(step)).findFirst().orElse(null);
        String publishStatus=text(row,"publish_status","publishStatus");
        boolean published="PUBLISHED".equals(publishStatus);
        String templateStatus=text(row,"template_status","templateStatus");
        String replacementCode=text(row,"replacement_template_code","replacementTemplateCode");
        String runtimeState="0".equals(templateStatus)?"ACTIVE":replacementCode!=null?"REPLACED":"INACTIVE";
        String primaryAction="REPLACED".equals(runtimeState)?"OPEN_REPLACEMENT":
                published?"VIEW_PUBLISHED":"CONTINUE_CONFIGURATION";
        String journeyState=published?"PUBLISHED":blockers>0?"BLOCKED":warnings>0?"WARNING":
                completed==STEP_COUNT?"READY":"IN_PROGRESS";
        return new TemplateWorkbenchItem(requiredId(row),text(row,"template_code","templateCode"),
                text(row,"template_name","templateName"),
                text(row,"business_type","businessType"),text(row,"business_stage","businessStage"),
                journeyState,completed,STEP_COUNT,blockers,warnings,text(row,"last_editor","lastEditor"),
                time(row,"update_time","updateTime"),primaryAction,
                nextStep==null?null:nextStep.code(),nextStep==null?null:nextStep.title(),templateStatus,
                integer(value(row,"lock_version","lockVersion"),0),runtimeState,
                longNumber(value(row,"replacement_template_id","replacementTemplateId")),replacementCode,
                text(row,"replacement_template_name","replacementTemplateName"));
    }

    private TemplateConfigurationDetail workbenchDetail(Map<String,Object> row,String definitionJson)
    {
        long templateId=requiredId(row);String publishStatus=text(row,"publish_status","publishStatus");
        Long versionId=longNumber(value(row,"version_id","versionId"));if(versionId==null)versionId=templateId;
        int versionNo=integer(value(row,"version_no","versionNo"),0);
        String versionStatus=text(row,"version_status","versionStatus");
        if(versionStatus==null)versionStatus="PUBLISHED".equals(publishStatus)?"PUBLISHED":"DRAFT";
        String definitionHash=text(row,"definition_hash","definitionHash");
        String validation=text(row,"validation_report_json","validationReportJson");
        TemplateVersionDetail version=new TemplateVersionDetail(versionId,versionNo,versionStatus,null,1,definitionJson,
                null,null,null,null,null,definitionHash,validation,null,null,null,null,null,
                time(row,"update_time","updateTime"));
        boolean published="PUBLISHED".equals(publishStatus);
        return new TemplateConfigurationDetail(templateId,text(row,"template_code","templateCode"),
                text(row,"template_name","templateName"),text(row,"business_type","businessType"),
                text(row,"template_status","templateStatus"),integer(value(row,"lock_version","lockVersion"),0),
                published?versionNo:0,published?null:versionId,published?null:versionStatus,
                published?versionId:null,published?versionNo:null,version,List.of());
    }

    private List<JourneyIssue> persistedIssues(String reportJson)
    {
        if(reportJson==null||reportJson.isBlank())return List.of();
        try
        {
            JSONObject report=JSON.parseObject(reportJson);if(report==null)return List.of();
            List<JourneyIssue> result=new ArrayList<>();
            appendPersisted(result,report.getJSONArray("errors"),"BLOCKER");
            appendPersisted(result,report.getJSONArray("warnings"),"WARNING");
            return List.copyOf(result);
        }
        catch(RuntimeException invalid)
        {
            return List.of(new JourneyIssue("TODO_JOURNEY_VALIDATION_REPORT_INVALID","BLOCKER",
                    "SIMULATION_PUBLISH","validationReport","The persisted validation report is invalid",
                    "Run validation again before publishing"));
        }
    }

    private void appendPersisted(List<JourneyIssue> target,JSONArray values,String severity)
    {
        if(values==null)return;
        for(int index=0;index<values.size();index++)
        {
            JSONObject value=values.getJSONObject(index);if(value==null)continue;
            String code=value.getString("code");String path=value.getString("path");
            if(code==null||code.isBlank())continue;
            String message=value.getString("message");
            target.add(new JourneyIssue(code,severity,stepCode(path),path,message,
                    message==null?"Review the validation issue":message));
        }
    }

    private List<JourneyIssue> mergeIssues(List<JourneyIssue> evaluated,List<JourneyIssue> persisted)
    {
        Map<String,JourneyIssue> merged=new LinkedHashMap<>();
        for(JourneyIssue issue:evaluated)merged.put(issueKey(issue),issue);
        for(JourneyIssue issue:persisted)
        {
            String key=issueKey(issue);JourneyIssue existing=merged.get(key);
            if(existing==null||severityRank(issue.severity())>severityRank(existing.severity()))merged.put(key,issue);
        }
        return List.copyOf(merged.values());
    }

    private boolean simulationIssue(String code)
    {
        return "TODO_JOURNEY_SIMULATION_REQUIRED".equals(code)
                ||"TODO_REQUIRED_SIMULATION_SCENARIOS_INCOMPLETE".equals(code)
                ||"TODO_FULL_SIMULATION_REQUIRED".equals(code)
                ||"TODO_FULL_SIMULATION_STALE".equals(code);
    }

    private TodoSimulationReadinessService.BatchRequest readinessRequest(Map<String,Object> row)
    {
        return new TodoSimulationReadinessService.BatchRequest(requiredId(row),versionId(row),
                text(row,"definition_hash","definitionHash"),
                text(row,"publish_status","publishStatus"));
    }

    private long versionId(Map<String,Object> row)
    {
        Long result=longNumber(value(row,"version_id","versionId"));
        return result==null?requiredId(row):result;
    }

    private boolean completed(JourneyStep step)
    {return "COMPLETED".equals(step.state())||"WARNING".equals(step.state());}

    private Predicate<TemplateWorkbenchItem> issueFilter(String issueType)
    {
        if(issueType==null||issueType.isBlank())return item->true;
        return switch(issueType)
        {
            case "BLOCKER" -> item->"BLOCKER".equals(health(item));
            case "WARNING" -> item->"WARNING".equals(health(item));
            case "READY" -> item->"READY".equals(health(item));
            default -> throw new TodoException("TODO_CONFIGURATION_QUERY_INVALID","Unsupported workbench issue filter");
        };
    }

    private String health(TemplateWorkbenchItem item)
    {return item.blockerCount()>0?"BLOCKER":item.warningCount()>0?"WARNING":"READY";}

    private String stepCode(String path)
    {
        if(path==null)return "SIMULATION_PUBLISH";
        if(path.startsWith("event.condition"))return "TRIGGER";
        if(path.startsWith("event"))return "EVENT";
        if(path.startsWith("owner"))return "OWNER";
        if(path.startsWith("dod"))return "DOD";
        if(path.startsWith("sla"))return "SLA";
        if(path.startsWith("routing"))return "ROUTING";
        return "SIMULATION_PUBLISH";
    }

    private String issueKey(JourneyIssue issue)
    {return String.valueOf(issue.code())+'\u0000'+String.valueOf(issue.fieldPath());}
    private int severityRank(String severity){return "BLOCKER".equals(severity)?2:"WARNING".equals(severity)?1:0;}

    private Map<String,Object> normalize(Map<String,Object> query)
    {
        Map<String,Object> normalized=new LinkedHashMap<>(query==null?Map.of():query);
        normalized.putIfAbsent("offset",0);normalized.putIfAbsent("limit",20);
        return normalized;
    }

    private String businessStage(String definitionJson)
    {
        TodoDefinitionDocument definition=codec.read(definitionJson);
        Object stage=definition.ui()==null?null:definition.ui().config().get("businessStage");
        return stage==null?null:String.valueOf(stage);
    }

    private JourneyPermissions permissions(Actor actor)
    {
        boolean allowed=actor!=null&&actor.userId()!=null;
        return new JourneyPermissions(allowed,allowed,allowed,allowed,allowed,allowed);
    }

    private long requiredId(Map<String,Object> row)
    {return Long.parseLong(String.valueOf(value(row,"template_id","templateId")));}
    private int pagination(Object value,int fallback,String field)
    {try{return value==null?fallback:Integer.parseInt(String.valueOf(value));}
        catch(NumberFormatException invalid){throw new TodoException("TODO_CONFIGURATION_QUERY_INVALID",field+" must be an integer");}}
    private int integer(Object value,int fallback)
    {return value==null?fallback:Integer.parseInt(String.valueOf(value));}
    private Long longNumber(Object value){return value==null?null:Long.valueOf(String.valueOf(value));}
    private String text(Object value){return value==null?null:String.valueOf(value);}
    private int number(Integer value){return value==null?0:value;}
    private String text(Map<String,Object> row,String snake,String camel)
    {Object value=value(row,snake,camel);return value==null?null:String.valueOf(value);}
    private Object value(Map<String,Object> row,String snake,String camel)
    {return row.containsKey(snake)?row.get(snake):row.get(camel);}
    private LocalDateTime time(Map<String,Object> row,String snake,String camel)
    {Object value=value(row,snake,camel);if(value instanceof LocalDateTime time)return time;if(value instanceof Timestamp timestamp)return timestamp.toLocalDateTime();return null;}
}
