package com.law.todo.application;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.view.TodoConfigurationJourneyView;
import com.law.todo.application.view.TodoConfigurationJourneyView.CurrentResources;
import com.law.todo.application.view.TodoConfigurationJourneyView.JourneyPermissions;
import com.law.todo.application.view.TodoConfigurationJourneyView.TemplateSummary;
import com.law.todo.application.view.TodoConfigurationJourneyView.TemplateWorkbenchItem;
import com.law.todo.application.view.TodoConfigurationJourneyView.TemplateWorkbenchPage;
import com.law.todo.application.view.TodoConfigurationViews.TemplateConfigurationDetail;
import com.law.todo.application.view.TodoConfigurationViews.TemplateVersionDetail;
import com.law.todo.definition.codec.TodoDefinitionCodec;
import com.law.todo.definition.model.TodoDefinitionDocument;
import com.law.todo.mapper.TodoConfigurationMapper;

/** Read-only aggregate for the configuration journey and its batch workbench. */
@Service
public class TodoConfigurationJourneyService
{
    private static final int STEP_COUNT=7;
    private final TodoConfigurationQueryService query;
    private final TodoDefinitionCodec codec;
    private final TodoConfigurationMapper mapper;
    private final TodoConfigurationResourceCatalogService resourceCatalog;
    private final TodoConfigurationJourneyEvaluator evaluator;
    private final TodoEmployeeTodoPreviewProjector preview;

    @Autowired
    public TodoConfigurationJourneyService(TodoConfigurationQueryService query,TodoConfigurationMapper mapper,
            TodoConfigurationResourceCatalogService resourceCatalog,TodoConfigurationJourneyEvaluator evaluator,
            TodoEmployeeTodoPreviewProjector preview)
    {this(query,new TodoDefinitionCodec(),mapper,resourceCatalog,evaluator,preview);}

    TodoConfigurationJourneyService(TodoConfigurationQueryService query,TodoDefinitionCodec codec,TodoConfigurationMapper mapper,
            TodoConfigurationResourceCatalogService resourceCatalog,TodoConfigurationJourneyEvaluator evaluator,
            TodoEmployeeTodoPreviewProjector preview)
    {
        this.query=query;this.codec=codec;this.mapper=mapper;this.resourceCatalog=resourceCatalog;
        this.evaluator=evaluator;this.preview=preview;
    }

    public TodoConfigurationJourneyView load(long templateId,Actor actor)
    {
        TemplateConfigurationDetail detail=query.template(templateId);
        TemplateVersionDetail version=Objects.requireNonNull(detail.editableVersion(),"editableVersion");
        TodoDefinitionDocument definition=codec.read(version.definitionJson());
        TodoConfigurationJourneyEvaluator.Evaluation evaluation=evaluator.evaluate(detail,definition);
        return new TodoConfigurationJourneyView(summary(detail,version),evaluation.steps(),resources(detail),
                preview.project(detail,definition),evaluation.issues(),permissions(actor));
    }

    public TemplateWorkbenchPage workbench(Map<String,Object> query,Actor actor)
    {
        Map<String,Object> normalized=normalize(query);
        List<TemplateWorkbenchItem> rows=mapper.selectTemplateJourneySummaries(normalized).stream()
                .map(this::workbenchItem).toList();
        return new TemplateWorkbenchPage(rows,mapper.countTemplateJourneySummaries(normalized),
                (int)rows.stream().filter(item->item.blockerCount()>0).count(),
                (int)rows.stream().filter(item->item.warningCount()>0).count());
    }

    private TemplateSummary summary(TemplateConfigurationDetail detail,TemplateVersionDetail version)
    {
        return new TemplateSummary(detail.templateId(),version.versionId(),number(version.versionNo()),number(detail.version()),
                detail.templateName(),detail.businessType(),businessStage(version.definitionJson()),version.status(),
                version.definitionHash(),version.definitionJson());
    }

    private CurrentResources resources(TemplateConfigurationDetail detail)
    {
        String businessType=detail.businessType();
        return new CurrentResources(List.of(),resourceCatalog.fields(businessType),query.ownerCatalog(),
                resourceCatalog.materials(businessType),resourceCatalog.validators(businessType),resourceCatalog.recipes(businessType),
                List.of(),List.of());
    }

    private TemplateWorkbenchItem workbenchItem(Map<String,Object> row)
    {
        codec.read(text(row,"definition_json","definitionJson"));
        String publishStatus=text(row,"publish_status","publishStatus");
        boolean published="PUBLISHED".equals(publishStatus);
        return new TemplateWorkbenchItem(requiredId(row),text(row,"template_name","templateName"),
                text(row,"business_type","businessType"),text(row,"business_stage","businessStage"),
                published?"PUBLISHED":"IN_PROGRESS",0,STEP_COUNT,0,0,text(row,"last_editor","lastEditor"),
                time(row,"update_time","updateTime"),published?"VIEW_PUBLISHED":"CONTINUE_CONFIGURATION");
    }

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
    private int number(Integer value){return value==null?0:value;}
    private String text(Map<String,Object> row,String snake,String camel)
    {Object value=value(row,snake,camel);return value==null?null:String.valueOf(value);}
    private Object value(Map<String,Object> row,String snake,String camel)
    {return row.containsKey(snake)?row.get(snake):row.get(camel);}
    private LocalDateTime time(Map<String,Object> row,String snake,String camel)
    {Object value=value(row,snake,camel);if(value instanceof LocalDateTime time)return time;if(value instanceof Timestamp timestamp)return timestamp.toLocalDateTime();return null;}
}
