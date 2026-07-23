package com.law.todo.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.law.todo.application.view.TodoConfigurationJourneyView.JourneyIssue;
import com.law.todo.application.view.TodoConfigurationJourneyView.JourneyStep;
import com.law.todo.application.view.TodoConfigurationViews.TemplateConfigurationDetail;
import com.law.todo.definition.model.TodoDefinitionDocument;
import com.law.todo.routing.RoutingGraphValidator;

/** Deterministic, read-only health projection for the editable configuration snapshot. */
@Component
public class TodoConfigurationJourneyEvaluator
{
    private final TodoConfigurationResourceCatalogService resources;
    private final TodoTemplateService templates;

    /** Compatibility constructor for focused callers that do not have catalog access. */
    public TodoConfigurationJourneyEvaluator()
    {this(null,null);}

    @Autowired
    public TodoConfigurationJourneyEvaluator(TodoConfigurationResourceCatalogService resources,TodoTemplateService templates)
    {this.resources=resources;this.templates=templates;}

    public Evaluation evaluate(TemplateConfigurationDetail detail,TodoDefinitionDocument definition)
    {
        List<JourneyStep> steps=new ArrayList<>();List<JourneyIssue> issues=new ArrayList<>();
        steps.add(evaluateEvent(detail,definition,issues));
        steps.add(evaluateTrigger(definition,issues));
        steps.add(evaluateOwner(definition,issues));
        steps.add(evaluateDod(definition,issues));
        steps.add(evaluateSla(definition,issues));
        steps.add(evaluateRouting(definition,issues));
        steps.add(evaluateSimulation(detail,definition,issues));
        return new Evaluation(steps,issues);
    }

    private JourneyStep evaluateEvent(TemplateConfigurationDetail detail,TodoDefinitionDocument definition,List<JourneyIssue> issues)
    {
        String event=definition==null||definition.event()==null?null:definition.event().eventType();
        Map<String,Object> value=eventValue(definition);
        if(blank(event))return step("EVENT","Event",List.of(),false,false,value);
        List<JourneyIssue> local=new ArrayList<>();
        if(definition.event().payloadVersion()<=0)
            local.add(blocker("TODO_JOURNEY_EVENT_VERSION_REQUIRED","EVENT","event.payloadVersion","Select an active event version","Select an active event version"));
        else if(resources!=null&&!hasUsableSchema(detail,event))
            local.add(blocker("TODO_JOURNEY_EVENT_SCHEMA_REQUIRED","EVENT","event","The selected event has no usable business fields","Maintain an active event schema"));
        append(issues,local);return step("EVENT","Event",local,true,local.isEmpty(),value);
    }

    private JourneyStep evaluateTrigger(TodoDefinitionDocument definition,List<JourneyIssue> issues)
    {
        Map<String,Object> value=fieldValue("condition",definition==null||definition.event()==null?Map.of():definition.event().condition());
        if(definition==null||definition.event()==null||blank(definition.event().eventType()))
            return step("TRIGGER","Trigger",List.of(),false,false,value);
        List<JourneyIssue> local=new ArrayList<>();
        if(definition.event().condition().isEmpty())
            local.add(warning("TODO_JOURNEY_TRIGGER_RECOMMENDATION","TRIGGER","event.condition",
                    "This todo will start for every matching event","Add a business condition if this should be more selective"));
        append(issues,local);return step("TRIGGER","Trigger",local,true,true,value);
    }

    private JourneyStep evaluateOwner(TodoDefinitionDocument definition,List<JourneyIssue> issues)
    {
        Map<String,Object> owner=config(definition==null?null:definition.owner());List<JourneyIssue> local=new ArrayList<>();
        boolean configured=ownerRule(owner)||ownerRule(map(owner.get("fallback")));
        if(!configured)
            local.add(blocker("TODO_JOURNEY_OWNER_FALLBACK_REQUIRED","OWNER","owner.config",
                    "The owner cannot be resolved and has no fallback","Select an owner or configure a fallback"));
        append(issues,local);return step("OWNER","Owner",local,configured,configured,fieldValue("config",owner));
    }

    private JourneyStep evaluateDod(TodoDefinitionDocument definition,List<JourneyIssue> issues)
    {
        Map<String,Object> dod=config(definition==null?null:definition.dod());List<JourneyIssue> local=new ArrayList<>();
        boolean configured=!strings(dod.get("requiredFields")).isEmpty()||!strings(dod.get("requiredAttachments")).isEmpty();
        if(!dod.isEmpty()&&!configured)
            local.add(warning("TODO_JOURNEY_DOD_RECOMMENDATION","DOD","dod.config",
                    "No required completion evidence is configured","Add the fields or materials employees must provide"));
        append(issues,local);return step("DOD","Definition of done",local,configured,configured,fieldValue("config",dod));
    }

    private JourneyStep evaluateSla(TodoDefinitionDocument definition,List<JourneyIssue> issues)
    {
        Map<String,Object> sla=config(definition==null?null:definition.sla());
        if(sla.isEmpty())return step("SLA","Service level agreement",List.of(),false,false,fieldValue("config",sla));
        List<JourneyIssue> local=new ArrayList<>();String calendar=text(sla.get("calendarCode"));
        if(blank(calendar)||templates!=null&&!calendarAvailable(calendar))
            local.add(blocker("TODO_JOURNEY_CALENDAR_REQUIRED","SLA","sla.calendarCode",
                    "The service-level calendar is unavailable","Choose an active working calendar"));
        if(!positive(sla.get("minutes"))&&!positive(sla.get("durationValue")))
            local.add(blocker("TODO_JOURNEY_SLA_DURATION_REQUIRED","SLA","sla",
                    "A positive service-level duration is required","Set a duration for this todo"));
        append(issues,local);return step("SLA","Service level agreement",local,true,local.isEmpty(),fieldValue("config",sla));
    }

    private JourneyStep evaluateRouting(TodoDefinitionDocument definition,List<JourneyIssue> issues)
    {
        Map<String,Object> routing=config(definition==null?null:definition.routing());
        if(routing.isEmpty())return step("ROUTING","Routing",List.of(),true,true,fieldValue("config",routing));
        List<JourneyIssue> local=new ArrayList<>();
        if(definition.routing()==null||!new RoutingGraphValidator(new com.law.todo.expression.ConditionValidator()).validate(definition.routing()).isEmpty())
            local.add(blocker("TODO_JOURNEY_ROUTING_INVALID","ROUTING","routing",
                    "The routing path is incomplete or invalid","Repair the routing path"));
        append(issues,local);return step("ROUTING","Routing",local,true,local.isEmpty(),fieldValue("config",routing));
    }

    private JourneyStep evaluateSimulation(TemplateConfigurationDetail detail,TodoDefinitionDocument definition,List<JourneyIssue> issues)
    {
        Map<String,Object> ui=config(definition==null?null:definition.ui());List<JourneyIssue> local=new ArrayList<>();
        String status=text(ui.containsKey("simulationStatus")?ui.get("simulationStatus"):ui.get("lastSimulationStatus"));
        boolean success=Boolean.TRUE.equals(ui.get("simulationSuccessful"))||"SUCCESS".equalsIgnoreCase(status)
                ||"SUCCEEDED".equalsIgnoreCase(status)||"PASSED".equalsIgnoreCase(status);
        String simulatedHash=text(ui.get("simulationDefinitionHash"));String currentHash=detail==null||detail.editableVersion()==null?null:detail.editableVersion().definitionHash();
        success=success&&!blank(simulatedHash)&&simulatedHash.equals(currentHash);
        if(!success)local.add(blocker("TODO_JOURNEY_SIMULATION_REQUIRED","SIMULATION_PUBLISH","ui.simulationStatus",
                "A successful simulation of this editable definition is required","Run a successful simulation before publishing"));
        append(issues,local);return step("SIMULATION_PUBLISH","Simulation and publish",local,success,success,fieldValue("config",ui));
    }

    private boolean hasUsableSchema(TemplateConfigurationDetail detail,String event)
    {return resources.fields(detail==null?null:detail.businessType()).stream().anyMatch(field->field.sourceEvents().contains(event));}
    private boolean calendarAvailable(String code)
    {return templates.listTemplateCalendarCatalog().stream().anyMatch(row->code.equals(text(row.get("calendarCode"))));}
    private JourneyStep step(String code,String title,List<JourneyIssue> local,boolean started,boolean complete,Map<String,Object> value)
    {return new JourneyStep(code,title,JourneyState.from(local,started,complete).name(),local.size(),value);}
    private Map<String,Object> eventValue(TodoDefinitionDocument definition)
    {
        TodoDefinitionDocument.EventRule event=definition==null?null:definition.event();Map<String,Object> value=new LinkedHashMap<>();
        value.put("eventType",event==null?null:event.eventType());value.put("payloadVersion",event==null?0:event.payloadVersion());
        return Collections.unmodifiableMap(value);
    }
    private Map<String,Object> fieldValue(String name,Object value)
    {return Collections.singletonMap(name,value);}
    private void append(List<JourneyIssue> target,List<JourneyIssue> source)
    {source.stream().filter(issue->"BLOCKER".equals(issue.severity())).forEach(target::add);source.stream().filter(issue->"WARNING".equals(issue.severity())).forEach(target::add);}
    private JourneyIssue blocker(String code,String step,String path,String message,String repair)
    {return new JourneyIssue(code,"BLOCKER",step,path,message,repair);}
    private JourneyIssue warning(String code,String step,String path,String message,String repair)
    {return new JourneyIssue(code,"WARNING",step,path,message,repair);}
    private Map<String,Object> config(Object section)
    {if(section instanceof TodoDefinitionDocument.OwnerRule owner)return owner.config();if(section instanceof TodoDefinitionDocument.DodRule dod)return dod.config();if(section instanceof TodoDefinitionDocument.SlaRule sla)return sla.config();if(section instanceof TodoDefinitionDocument.UiSchema ui)return ui.config();if(section instanceof TodoDefinitionDocument.RoutingGraph routing)return routing.config();return Map.of();}
    private boolean ownerRule(Map<String,Object> value)
    {
        String type=text(value.get("type"));
        if(blank(type))return false;
        if("BUSINESS_OWNER".equals(type)||"SUPERVISOR".equals(type))return true;
        return !blank(text(value.get("value")))||!blank(text(value.get("operand")))||!blank(text(value.get("field")));
    }
    private Map<String,Object> map(Object value)
    {if(!(value instanceof Map<?,?> source))return Map.of();Map<String,Object> result=new java.util.LinkedHashMap<>();source.forEach((key,item)->result.put(String.valueOf(key),item));return result;}
    private List<String> strings(Object value)
    {if(!(value instanceof Collection<?> values))return List.of();return values.stream().filter(item->item!=null&&!String.valueOf(item).isBlank()).map(String::valueOf).toList();}
    private boolean positive(Object value){try{return value!=null&&Long.parseLong(String.valueOf(value))>0;}catch(NumberFormatException invalid){return false;}}
    private String text(Object value){return value==null?null:String.valueOf(value);}
    private boolean blank(String value){return value==null||value.isBlank();}

    public enum JourneyState
    {
        NOT_STARTED,IN_PROGRESS,COMPLETED,WARNING,BLOCKED;
        static JourneyState from(List<JourneyIssue> issues,boolean started,boolean complete)
        {
            if(issues.stream().anyMatch(issue->"BLOCKER".equals(issue.severity())))return BLOCKED;
            if(issues.stream().anyMatch(issue->"WARNING".equals(issue.severity())))return WARNING;
            if(complete)return COMPLETED;
            return started?IN_PROGRESS:NOT_STARTED;
        }
    }

    public record Evaluation(List<JourneyStep> steps,List<JourneyIssue> issues)
    {
        public Evaluation { steps=steps==null?List.of():List.copyOf(steps);issues=issues==null?List.of():List.copyOf(issues); }
        public JourneyStep step(String code)
        {return steps.stream().filter(step->step.code().equals(code)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown journey step: "+code));}
    }
}
