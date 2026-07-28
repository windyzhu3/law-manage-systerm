package com.law.todo.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.law.todo.application.TodoConfigurationResourceCatalogService.FieldResource;
import com.law.todo.application.TodoFieldDisplayResolutionService.FieldValue;
import com.law.todo.application.TodoTemplateFieldUsageService.FieldUsage;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoConfigurationCommands.JourneyPayloadCommand;
import com.law.todo.application.view.TodoJourneyPayloadView;
import com.law.todo.application.view.TodoJourneyPayloadView.BusinessObjectSummary;
import com.law.todo.application.view.TodoJourneyPayloadView.JourneyIssue;
import com.law.todo.application.view.TodoJourneyPayloadView.PayloadField;
import com.law.todo.domain.TodoException;
import com.law.todo.spi.TodoBusinessPayloadAccess.PayloadFieldSource;
import com.law.todo.spi.TodoBusinessPayloadAccess.PayloadHydration;
import com.law.todo.spi.TodoFieldReferenceDirectory.DisplayReference;

/** Prepares a scoped, labelled and creation-focused payload for the simulation workbench. */
@Service
public class TodoJourneyPayloadPreparationService
{
    private final TodoConfigurationJourneyService journeys;private final TodoBusinessPayloadHydrationService payloads;
    private final TodoConfigurationResourceCatalogService resources;private final TodoTemplateFieldUsageService usages;
    private final TodoFieldDisplayResolutionService displays;

    public TodoJourneyPayloadPreparationService(TodoConfigurationJourneyService journeys,
            TodoBusinessPayloadHydrationService payloads,TodoConfigurationResourceCatalogService resources,
            TodoTemplateFieldUsageService usages,TodoFieldDisplayResolutionService displays)
    {this.journeys=journeys;this.payloads=payloads;this.resources=resources;this.usages=usages;this.displays=displays;}

    @Transactional(readOnly=true)
    public TodoJourneyPayloadView prepare(JourneyPayloadCommand command,Actor actor)
    {
        var journey=journeys.load(command.templateId(),actor);var template=journey.template();
        if(template.versionId()!=command.versionId())
            throw new TodoException("TODO_SIMULATION_VERSION_STALE","Template version changed; reload before simulation");
        if(!command.expectedDefinitionHash().equals(template.definitionHash()))
            throw new TodoException("TODO_SIMULATION_DEFINITION_STALE","Template definition changed; reload before simulation");
        String definitionJson=journeys.canonicalDefinition(command.templateId(),actor);
        if(definitionJson==null||definitionJson.isBlank())
            throw new TodoException("TODO_TEMPLATE_JSON_INVALID","Template definition is unavailable");

        PayloadHydration hydration=payloads.hydrate(command.eventType(),command.payloadVersion(),command.businessType(),
                command.businessId(),actor,command.manualOverrides());
        List<FieldResource> descriptors=resources.fields(command.businessType(),command.eventType());
        Map<String,FieldResource> descriptorByPath=new LinkedHashMap<>();
        descriptors.forEach(field->descriptorByPath.put(field.code(),field));
        Map<String,PayloadFieldSource> sourceByPath=new LinkedHashMap<>();
        hydration.fields().forEach(field->sourceByPath.put(field.path(),field));
        Map<String,List<FieldUsage>> usageMap=usages.usages(definitionJson);

        List<FieldValue> semanticValues=new ArrayList<>();
        descriptors.forEach(field->
        {
            PayloadFieldSource source=sourceByPath.get(field.code());
            if(source!=null&&!source.missing()&&!"PLAIN_VALUE".equals(field.semanticType()))
                semanticValues.add(new FieldValue(field,source.value()));
        });
        Map<String,DisplayReference> resolved=displays.resolve(semanticValues,actor);

        Set<String> eventPaths=new LinkedHashSet<>();
        descriptors.stream().filter(field->field.sourceEvents().contains(command.eventType()))
                .forEach(field->eventPaths.add(field.code()));
        Set<String> completionPaths=paths(usageMap,"COMPLETION_INPUT");
        Set<String> routingPaths=paths(usageMap,"ROUTING_INPUT");routingPaths.removeAll(completionPaths);
        Set<String> creationPaths=paths(usageMap,"OWNER_INPUT");creationPaths.removeAll(eventPaths);
        Set<String> advancedPaths=new LinkedHashSet<>();
        advancedPaths.addAll(paths(usageMap,"TRIGGER_INPUT"));advancedPaths.addAll(paths(usageMap,"SLA_INPUT"));
        advancedPaths.removeAll(eventPaths);advancedPaths.removeAll(creationPaths);advancedPaths.removeAll(completionPaths);
        advancedPaths.removeAll(routingPaths);

        List<PayloadField> event=build(eventPaths,descriptorByPath,sourceByPath,usageMap,resolved,true);
        List<PayloadField> creation=build(creationPaths,descriptorByPath,sourceByPath,usageMap,resolved,true);
        List<PayloadField> completion=build(completionPaths,descriptorByPath,sourceByPath,usageMap,resolved,false);
        List<PayloadField> routing=build(routingPaths,descriptorByPath,sourceByPath,usageMap,resolved,false);
        List<PayloadField> advanced=build(advancedPaths,descriptorByPath,sourceByPath,usageMap,resolved,false);

        List<JourneyIssue> blockers=new ArrayList<>();
        List<PayloadField> creationFields=new ArrayList<>(event);creationFields.addAll(creation);
        creationFields.stream().filter(PayloadField::required).filter(PayloadField::missing)
                .forEach(field->blockers.add(new JourneyIssue("TODO_CREATION_FIELD_REQUIRED",field.path(),
                        field.label()+"为创建待办必填项")));
        long required=creationFields.stream().filter(PayloadField::required).count();
        long present=creationFields.stream().filter(PayloadField::required).filter(field->!field.missing()).count();
        int coverage=required==0?100:(int)Math.round(present*100.0/required);
        BusinessObjectSummary business=new BusinessObjectSummary(command.businessType(),command.businessId(),
                text(hydration.payload().get("businessNo")),text(hydration.payload().get("businessName")),hydration.sample());
        return new TodoJourneyPayloadView(business,hydration.payload(),event,creation,completion,routing,advanced,coverage,blockers);
    }

    private Set<String> paths(Map<String,List<FieldUsage>> usages,String stage)
    {
        Set<String> result=new LinkedHashSet<>();
        usages.forEach((path,values)->{if(values.stream().anyMatch(value->stage.equals(value.stage())))result.add(path);});
        return result;
    }

    private List<PayloadField> build(Set<String> paths,Map<String,FieldResource> descriptors,
            Map<String,PayloadFieldSource> sources,Map<String,List<FieldUsage>> usages,
            Map<String,DisplayReference> resolved,boolean creationStage)
    {
        List<PayloadField> result=new ArrayList<>();
        for(String path:paths)
        {
            FieldResource descriptor=descriptors.get(path);
            if(descriptor==null)descriptor=new FieldResource(path,path,"string",false,List.of(),List.of());
            PayloadFieldSource source=sources.get(path);Object raw=source==null?null:source.value();
            boolean missing=source==null||source.missing();List<FieldUsage> fieldUsages=usages.getOrDefault(path,List.of());
            boolean required=descriptor.required()||fieldUsages.stream().anyMatch(value->value.required()&&!value.conditional());
            DisplayReference display=resolved.get(path);
            String displayValue=missing?null:display==null?String.valueOf(raw):display.displayValue();
            String issueCode=required&&missing&&creationStage?"TODO_CREATION_FIELD_REQUIRED":null;
            String issueMessage=issueCode==null?null:descriptor.name()+"为创建待办必填项";
            result.add(new PayloadField(path,descriptor.name(),raw,displayValue,descriptor.semanticType(),
                    descriptor.optionSource(),descriptor.dictType(),source==null?"MISSING":source.source(),
                    required,missing,source!=null&&source.sensitive(),true,
                    display==null?Map.of():display.meta(),fieldUsages,issueCode,issueMessage));
        }
        return List.copyOf(result);
    }

    private String text(Object value){return value==null?null:String.valueOf(value);}
}
