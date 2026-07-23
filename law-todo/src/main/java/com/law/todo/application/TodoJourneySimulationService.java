package com.law.todo.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.law.todo.application.TodoBusinessPayloadHydrationService.ExecutionHydration;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoConfigurationCommands.ConfigurationSimulationCommand;
import com.law.todo.application.command.TodoConfigurationCommands.JourneySimulationCommand;
import com.law.todo.application.view.TodoConfigurationJourneyView;
import com.law.todo.application.view.TodoConfigurationJourneyView.EmployeeTodoPreview;
import com.law.todo.application.view.TodoConfigurationJourneyView.JourneyIssue;
import com.law.todo.application.view.TodoConfigurationJourneyView.PreviewField;
import com.law.todo.application.view.TodoConfigurationJourneyView.PreviewMaterial;
import com.law.todo.application.view.TodoConfigurationViews.ConfigurationSimulationResult;
import com.law.todo.application.view.TodoJourneySimulationResult;
import com.law.todo.application.view.TodoJourneySimulationResult.HydratedPayload;
import com.law.todo.application.view.TodoJourneySimulationResult.TraceDetail;
import com.law.todo.application.view.TodoJourneySimulationResult.TraceSection;
import com.law.todo.application.view.TodoSimulationView;
import com.law.todo.application.view.TodoSimulationView.AutoActionTrace;
import com.law.todo.application.view.TodoSimulationView.FormTrace;
import com.law.todo.application.view.TodoSimulationView.HandlerTrace;
import com.law.todo.application.view.TodoSimulationView.OwnerTrace;
import com.law.todo.application.view.TodoSimulationView.RouteTrace;
import com.law.todo.application.view.TodoSimulationView.SimulationIssue;
import com.law.todo.application.view.TodoSimulationView.SlaTrace;
import com.law.todo.application.view.TodoSimulationView.TriggerTrace;
import com.law.todo.domain.TodoException;
import com.law.todo.spi.TodoBusinessPayloadAccess.PayloadHydration;

/**
 * Page boundary for explainable simulation. Raw hydrated values are confined to the engine command;
 * every value returned to the caller is derived from the redacted hydration view.
 */
@Service
public class TodoJourneySimulationService
{
    private final TodoBusinessPayloadHydrationService hydration;
    private final TodoConfigurationSimulationService simulations;
    private final TodoConfigurationJourneyService journeys;

    public TodoJourneySimulationService(TodoBusinessPayloadHydrationService hydration,
            TodoConfigurationSimulationService simulations,TodoConfigurationJourneyService journeys)
    {this.hydration=hydration;this.simulations=simulations;this.journeys=journeys;}

    @Transactional(readOnly=true)
    public TodoJourneySimulationResult simulate(JourneySimulationCommand command,Actor actor)
    {
        if(command.businessId()==0)
            throw new TodoException("TODO_SIMULATION_BUSINESS_ID_INVALID","Simulation business ID must not be zero");
        TodoConfigurationJourneyView journey=journeys.load(command.templateId(),actor);
        if(journey.template().versionId()!=command.versionId()
                ||!command.expectedDefinitionHash().equals(journey.template().definitionHash()))
            throw new TodoException("TODO_TEMPLATE_PREFLIGHT_STALE",
                    "Definition or bound rules changed after preflight; run preflight again");

        ExecutionHydration execution=hydration.hydrateForExecution(command.eventType(),command.payloadVersion(),
                command.businessType(),command.businessId(),actor,command.manualOverrides());
        ConfigurationSimulationCommand engineCommand=new ConfigurationSimulationCommand(
                "journey-"+UUID.randomUUID(),command.versionId(),command.eventType(),command.payloadVersion(),
                command.businessType(),command.businessId(),execution.payload(),command.effectiveAt(),
                command.taskCompletions(),command.expectedDefinitionHash());
        PayloadHydration publicPayload=execution.publicView();
        TodoSensitiveDataPolicy policy=TodoSensitiveDataPolicy.from(execution.payload(),publicPayload.fields());
        ConfigurationSimulationResult simulation=simulations.simulate(engineCommand,actor,policy);
        TodoSimulationView engine=sanitizeEngine(simulation.simulation(),policy);
        EmployeeTodoPreview preview=sanitizePreview(journey.employeePreview(),policy);
        boolean successful=successful(engine,command.expectedDefinitionHash());
        List<JourneyIssue> issues=issues(journey.issues(),engine,successful,policy);
        HydratedPayload payload=new HydratedPayload(policy.redactMap(publicPayload.payload()),
                policy.redactFields(publicPayload.fields()),
                publicPayload.coveragePercent());
        return new TodoJourneySimulationResult(payload,engine,trace(engine,preview,policy),
                preview,issues,issues.stream().noneMatch(issue->"BLOCKER".equals(issue.severity())));
    }

    private boolean successful(TodoSimulationView engine,String expectedHash)
    {
        return engine!=null&&expectedHash.equals(engine.definitionHash())&&"MATCHED".equals(engine.trigger().status())
                &&engine.issues().stream().noneMatch(issue->"ERROR".equals(issue.severity()));
    }

    private List<JourneyIssue> issues(List<JourneyIssue> journeyIssues,TodoSimulationView engine,
            boolean successful,TodoSensitiveDataPolicy policy)
    {
        List<JourneyIssue> result=new ArrayList<>();
        for(JourneyIssue issue:journeyIssues)
            if(!successful||!"TODO_JOURNEY_SIMULATION_REQUIRED".equals(issue.code()))
                result.add(new JourneyIssue(issue.code(),issue.severity(),issue.stepCode(),issue.fieldPath(),
                        policy.redactText(issue.message()),policy.redactText(issue.repairAction())));
        for(SimulationIssue issue:engine.issues())
        {
            String severity="ERROR".equals(issue.severity())?"BLOCKER":
                    "WARNING".equals(issue.severity())?"WARNING":null;
            if(severity!=null)result.add(new JourneyIssue(issue.code(),severity,step(issue.path()),issue.path(),
                    policy.redactText(issue.message()),"Review the highlighted simulation trace"));
        }
        if(!successful&&result.stream().noneMatch(issue->"TODO_JOURNEY_SIMULATION_REQUIRED".equals(issue.code())))
            result.add(new JourneyIssue("TODO_JOURNEY_SIMULATION_REQUIRED","BLOCKER","SIMULATION_PUBLISH",
                    "simulation","A successful simulation of this editable definition is required",
                    "Run a successful simulation before publishing"));
        return List.copyOf(result);
    }

    private String step(String path)
    {
        if(path==null)return "SIMULATION_PUBLISH";
        if(path.startsWith("event"))return "EVENT";
        if(path.startsWith("owner"))return "OWNER";
        if(path.startsWith("sla"))return "SLA";
        if(path.startsWith("routing")||path.startsWith("taskCompletions"))return "ROUTING";
        if(path.startsWith("dod"))return "DOD";
        return "SIMULATION_PUBLISH";
    }

    private List<TraceSection> trace(TodoSimulationView engine,EmployeeTodoPreview preview,
            TodoSensitiveDataPolicy policy)
    {
        List<TraceSection> trace=new ArrayList<>();
        trace.add(new TraceSection("EVENT","Event",engine.trigger().status(),
                engine.trigger().eventType(),List.of(
                detail("Event",engine.trigger().eventType(),"DEFINITION",engine.trigger().status(),policy),
                detail("Payload version",String.valueOf(engine.trigger().payloadVersion()),"EVENT_SCHEMA",
                        engine.trigger().status(),policy))));
        trace.add(new TraceSection("OWNER","Owner",engine.owner().status(),
                engine.owner().ownerId()==null?"No single owner resolved":"Owner resolved",
                List.of(detail("Owner",text(engine.owner().ownerId()),"OWNER_RULE",engine.owner().status(),policy),
                        detail("Fallback",String.valueOf(engine.owner().fallbackUsed()),"OWNER_RULE",
                                engine.owner().status(),policy))));
        trace.add(new TraceSection("DOD","Definition of done","EVALUATED","Completion requirements evaluated",
                mapDetails(engine.form().dod(),"DOD_RULE",policy)));
        trace.add(new TraceSection("SLA","Service level agreement",engine.sla().status(),
                engine.sla().dueAt()==null?"No due time calculated":"Due time calculated",
                List.of(detail("Calendar",engine.sla().calendarCode(),"SLA_RULE",engine.sla().status(),policy),
                        detail("Due at",text(engine.sla().dueAt()),"SLA_CALCULATION",engine.sla().status(),policy))));
        trace.add(new TraceSection("ROUTING","Routing",routeStatus(engine.routes()),
                engine.routes().isEmpty()?"No follow-up route selected":engine.routes().size()+" route steps evaluated",
                engine.routes().stream().map(route->detail(route.nodeKey(),
                        route.nodeType()+" / "+route.status(),"ROUTING_RULE",route.status(),policy)).toList()));
        trace.add(new TraceSection("TODO_PREVIEW","Todo preview","READY",
                preview.title(),List.of(
                detail("Assignee",preview.assigneeSummary(),"PREVIEW","READY",policy),
                detail("Due",preview.dueSummary(),"PREVIEW","READY",policy))));
        return List.copyOf(trace);
    }

    private List<TraceDetail> mapDetails(Map<String,Object> values,String source,TodoSensitiveDataPolicy policy)
    {
        List<TraceDetail> result=new ArrayList<>();
        values.forEach((key,value)->result.add(detail(key,String.valueOf(value),source,"EVALUATED",policy)));
        return List.copyOf(result);
    }

    private String routeStatus(List<RouteTrace> routes)
    {return routes.stream().anyMatch(route->route.status().contains("INVALID")||route.status().contains("UNKNOWN"))?"WARNING":"EVALUATED";}
    private TraceDetail detail(String label,String value,String source,String status,TodoSensitiveDataPolicy policy)
    {return new TraceDetail(policy.redactText(label),policy.redactText(value),source,status);}
    private String text(Object value){return value==null?null:String.valueOf(value);}

    private TodoSimulationView sanitizeEngine(TodoSimulationView value,TodoSensitiveDataPolicy policy)
    {
        TriggerTrace trigger=new TriggerTrace(policy.redactText(value.trigger().status()),
                policy.redactText(value.trigger().eventType()),
                value.trigger().payloadVersion(),texts(value.trigger().trace(),policy));
        OwnerTrace owner=new OwnerTrace(policy.redactText(value.owner().status()),
                policy.protects(value.owner().ownerId())?null:value.owner().ownerId(),
                value.owner().candidates().stream().filter(candidate->!policy.protects(candidate)).toList(),
                value.owner().ccUsers().stream().filter(candidate->!policy.protects(candidate)).toList(),
                value.owner().fallbackUsed(),texts(value.owner().trace(),policy));
        SlaTrace sla=new SlaTrace(policy.redactText(value.sla().status()),
                policy.redactText(value.sla().calendarCode()),value.sla().startAt(),
                value.sla().dueAt(),value.sla().remind80At(),value.sla().overdue100At(),
                value.sla().escalate150At(),texts(value.sla().trace(),policy));
        List<RouteTrace> routes=value.routes().stream().map(route->new RouteTrace(route.order(),
                policy.redactText(route.nodeKey()),policy.redactText(route.nodeType()),
                policy.redactText(route.status()),policy.redactText(route.branchKey()),route.occurrence(),
                policy.protects(route.templateVersionId())?null:route.templateVersionId(),
                route.effectiveAt(),texts(route.trace(),policy))).toList();
        List<AutoActionTrace> actions=value.autoActions().stream().map(action->new AutoActionTrace(
                policy.redactText(action.ruleKey()),policy.redactText(action.actionType()),
                policy.redactText(action.triggerAt()),action.scheduledAt(),policy.redactText(action.status()),
                policy.redactText(action.reason()))).toList();
        List<HandlerTrace> handlers=value.handlers().stream().map(handler->new HandlerTrace(
                policy.redactText(handler.code()),policy.redactText(handler.status()),handler.simulatable(),
                policy.redactText(handler.reason()))).toList();
        List<SimulationIssue> issues=value.issues().stream().map(issue->new SimulationIssue(
                policy.redactText(issue.code()),policy.redactText(issue.path()),policy.redactText(issue.severity()),
                policy.redactText(issue.message()))).toList();
        return new TodoSimulationView(value.versionId(),value.definitionHash(),trigger,owner,sla,
                new FormTrace(policy.redactMap(value.form().ui()),policy.redactMap(value.form().dod())),
                routes,actions,handlers,issues);
    }

    private EmployeeTodoPreview sanitizePreview(EmployeeTodoPreview preview,TodoSensitiveDataPolicy policy)
    {
        List<PreviewField> fields=preview.fields().stream().map(field->new PreviewField(
                policy.redactText(field.code()),policy.redactText(field.label()),policy.redactText(field.type()),
                field.required())).toList();
        List<PreviewMaterial> materials=preview.materials().stream().map(material->new PreviewMaterial(
                policy.redactText(material.code()),policy.redactText(material.label()),material.required())).toList();
        return new EmployeeTodoPreview(policy.redactText(preview.title()),policy.redactText(preview.assigneeSummary()),
                fields,materials,texts(preview.completionInstructions(),policy),policy.redactText(preview.dueSummary()));
    }

    private List<String> texts(List<String> values,TodoSensitiveDataPolicy policy)
    {return values.stream().map(policy::redactText).toList();}
}
