package com.law.todo.application;

import java.lang.reflect.Array;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.law.todo.application.TodoBusinessPayloadHydrationService.ExecutionHydration;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoConfigurationCommands.ConfigurationSimulationCommand;
import com.law.todo.application.command.TodoConfigurationCommands.JourneySimulationCommand;
import com.law.todo.application.view.TodoConfigurationJourneyView;
import com.law.todo.application.view.TodoConfigurationJourneyView.JourneyIssue;
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
import com.law.todo.spi.TodoBusinessPayloadAccess.PayloadFieldSource;
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
        ConfigurationSimulationResult simulation=simulations.simulate(engineCommand,actor);
        PayloadHydration publicPayload=execution.publicView();
        Redactor redactor=new Redactor(execution.payload(),publicPayload.fields());
        TodoSimulationView engine=redactor.engine(simulation.simulation());
        boolean successful=successful(engine,command.expectedDefinitionHash());
        List<JourneyIssue> issues=issues(journey.issues(),engine,successful,redactor);
        HydratedPayload payload=new HydratedPayload(redactor.map(publicPayload.payload()),
                redactor.fields(publicPayload.fields()),
                publicPayload.coveragePercent());
        return new TodoJourneySimulationResult(payload,engine,trace(engine,journey,redactor),
                journey.employeePreview(),issues,issues.stream().noneMatch(issue->"BLOCKER".equals(issue.severity())));
    }

    private boolean successful(TodoSimulationView engine,String expectedHash)
    {
        return engine!=null&&expectedHash.equals(engine.definitionHash())&&"MATCHED".equals(engine.trigger().status())
                &&engine.issues().stream().noneMatch(issue->"ERROR".equals(issue.severity()));
    }

    private List<JourneyIssue> issues(List<JourneyIssue> journeyIssues,TodoSimulationView engine,
            boolean successful,Redactor redactor)
    {
        List<JourneyIssue> result=new ArrayList<>();
        for(JourneyIssue issue:journeyIssues)
            if(!successful||!"TODO_JOURNEY_SIMULATION_REQUIRED".equals(issue.code()))result.add(issue);
        for(SimulationIssue issue:engine.issues())
        {
            String severity="ERROR".equals(issue.severity())?"BLOCKER":
                    "WARNING".equals(issue.severity())?"WARNING":null;
            if(severity!=null)result.add(new JourneyIssue(issue.code(),severity,step(issue.path()),issue.path(),
                    redactor.text(issue.message()),"Review the highlighted simulation trace"));
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

    private List<TraceSection> trace(TodoSimulationView engine,TodoConfigurationJourneyView journey,Redactor redactor)
    {
        List<TraceSection> trace=new ArrayList<>();
        trace.add(new TraceSection("EVENT","Event",engine.trigger().status(),
                engine.trigger().eventType(),List.of(
                detail("Event",engine.trigger().eventType(),"DEFINITION",engine.trigger().status()),
                detail("Payload version",String.valueOf(engine.trigger().payloadVersion()),"EVENT_SCHEMA",
                        engine.trigger().status()))));
        trace.add(new TraceSection("OWNER","Owner",engine.owner().status(),
                engine.owner().ownerId()==null?"No single owner resolved":"Owner resolved",
                List.of(detail("Owner",text(engine.owner().ownerId()),"OWNER_RULE",engine.owner().status()),
                        detail("Fallback",String.valueOf(engine.owner().fallbackUsed()),"OWNER_RULE",
                                engine.owner().status()))));
        trace.add(new TraceSection("DOD","Definition of done","EVALUATED","Completion requirements evaluated",
                mapDetails(engine.form().dod(),"DOD_RULE",redactor)));
        trace.add(new TraceSection("SLA","Service level agreement",engine.sla().status(),
                engine.sla().dueAt()==null?"No due time calculated":"Due time calculated",
                List.of(detail("Calendar",engine.sla().calendarCode(),"SLA_RULE",engine.sla().status()),
                        detail("Due at",text(engine.sla().dueAt()),"SLA_CALCULATION",engine.sla().status()))));
        trace.add(new TraceSection("ROUTING","Routing",routeStatus(engine.routes()),
                engine.routes().isEmpty()?"No follow-up route selected":engine.routes().size()+" route steps evaluated",
                engine.routes().stream().map(route->detail(route.nodeKey(),
                        route.nodeType()+" / "+route.status(),"ROUTING_RULE",route.status())).toList()));
        trace.add(new TraceSection("TODO_PREVIEW","Todo preview","READY",
                journey.employeePreview().title(),List.of(
                detail("Assignee",journey.employeePreview().assigneeSummary(),"PREVIEW","READY"),
                detail("Due",journey.employeePreview().dueSummary(),"PREVIEW","READY"))));
        return List.copyOf(trace);
    }

    private List<TraceDetail> mapDetails(Map<String,Object> values,String source,Redactor redactor)
    {
        List<TraceDetail> result=new ArrayList<>();
        values.forEach((key,value)->result.add(detail(key,redactor.text(String.valueOf(value)),source,"EVALUATED")));
        return List.copyOf(result);
    }

    private String routeStatus(List<RouteTrace> routes)
    {return routes.stream().anyMatch(route->route.status().contains("INVALID")||route.status().contains("UNKNOWN"))?"WARNING":"EVALUATED";}
    private TraceDetail detail(String label,String value,String source,String status)
    {return new TraceDetail(label,value,source,status);}
    private String text(Object value){return value==null?null:String.valueOf(value);}

    private static final class Redactor
    {
        private static final String REDACTED="[REDACTED]";
        private static final Pattern PERSONAL=Pattern.compile("(?i)(?:\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b|(?<!\\d)(?:\\+?\\d[ -]?){8,18}(?!\\d)|\\b\\d{17}[0-9X]\\b)");
        private final Set<String> sensitiveValues=new LinkedHashSet<>();

        private Redactor(Map<String,Object> raw,List<PayloadFieldSource> fields)
        {
            collect(raw);
            for(PayloadFieldSource field:fields)
            {
                if(!field.sensitive())continue;
                Object value=valueAt(raw,field.path());
                if(value!=null&&!String.valueOf(value).isBlank())sensitiveValues.add(String.valueOf(value));
            }
        }

        private void collect(Map<String,Object> values)
        {
            values.forEach((key,value)->
            {
                if(sensitiveKey(key)&&value!=null)sensitiveValues.add(String.valueOf(value));
                if(value instanceof Map<?,?> nested)
                {
                    Map<String,Object> child=new LinkedHashMap<>();
                    nested.forEach((nestedKey,nestedValue)->child.put(String.valueOf(nestedKey),nestedValue));
                    collect(child);
                }
                else if(value instanceof String text&&PERSONAL.matcher(text).find())sensitiveValues.add(text);
            });
        }

        private List<PayloadFieldSource> fields(List<PayloadFieldSource> values)
        {
            return values.stream().map(field->
            {
                boolean sensitive=field.sensitive()||sensitiveKey(field.path())
                        ||field.value()!=null&&sensitiveValues.contains(String.valueOf(field.value()));
                return sensitive&&!field.missing()?new PayloadFieldSource(field.path(),REDACTED,field.source(),
                        field.required(),false,null,true):field;
            }).toList();
        }

        private TodoSimulationView engine(TodoSimulationView value)
        {
            TriggerTrace trigger=new TriggerTrace(value.trigger().status(),value.trigger().eventType(),
                    value.trigger().payloadVersion(),texts(value.trigger().trace()));
            OwnerTrace owner=new OwnerTrace(value.owner().status(),value.owner().ownerId(),value.owner().candidates(),
                    value.owner().ccUsers(),value.owner().fallbackUsed(),texts(value.owner().trace()));
            SlaTrace sla=new SlaTrace(value.sla().status(),value.sla().calendarCode(),value.sla().startAt(),
                    value.sla().dueAt(),value.sla().remind80At(),value.sla().overdue100At(),
                    value.sla().escalate150At(),texts(value.sla().trace()));
            List<RouteTrace> routes=value.routes().stream().map(route->new RouteTrace(route.order(),route.nodeKey(),
                    route.nodeType(),route.status(),route.branchKey(),route.occurrence(),route.templateVersionId(),
                    route.effectiveAt(),texts(route.trace()))).toList();
            List<AutoActionTrace> actions=value.autoActions().stream().map(action->new AutoActionTrace(action.ruleKey(),
                    action.actionType(),action.triggerAt(),action.scheduledAt(),action.status(),text(action.reason()))).toList();
            List<HandlerTrace> handlers=value.handlers().stream().map(handler->new HandlerTrace(handler.code(),
                    handler.status(),handler.simulatable(),text(handler.reason()))).toList();
            List<SimulationIssue> issues=value.issues().stream().map(issue->new SimulationIssue(issue.code(),issue.path(),
                    issue.severity(),text(issue.message()))).toList();
            return new TodoSimulationView(value.versionId(),value.definitionHash(),trigger,owner,sla,
                    new FormTrace(map(value.form().ui()),map(value.form().dod())),routes,actions,handlers,issues);
        }

        private Map<String,Object> map(Map<String,Object> values)
        {
            Map<String,Object> result=new LinkedHashMap<>();
            values.forEach((key,value)->result.put(key,sensitiveKey(key)?REDACTED:value(value)));
            return result;
        }

        private Object value(Object value)
        {
            if(value instanceof Map<?,?> source)
            {
                Map<String,Object> nested=new LinkedHashMap<>();
                source.forEach((key,item)->nested.put(String.valueOf(key),
                        sensitiveKey(String.valueOf(key))?REDACTED:value(item)));
                return nested;
            }
            if(value instanceof Iterable<?> source)
            {
                List<Object> result=new ArrayList<>();source.forEach(item->result.add(value(item)));return result;
            }
            if(value!=null&&value.getClass().isArray())
            {
                List<Object> result=new ArrayList<>();
                for(int index=0;index<Array.getLength(value);index++)result.add(value(Array.get(value,index)));
                return result;
            }
            return value instanceof String string?text(string):value;
        }

        private List<String> texts(List<String> values){return values.stream().map(this::text).toList();}
        private String text(String value)
        {
            if(value==null)return null;
            String result=value;
            for(String sensitive:sensitiveValues)result=result.replace(sensitive,REDACTED);
            return PERSONAL.matcher(result).find()?REDACTED:result;
        }
        private boolean sensitiveKey(String value)
        {
            String key=value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");
            return key.contains("secret")||key.contains("token")||key.contains("password")||key.contains("phone")
                    ||key.contains("mobile")||key.contains("email")||key.contains("idcard")
                    ||key.contains("identity")||key.contains("passport")||key.contains("fileurl");
        }
        private static Object valueAt(Map<String,Object> values,String path)
        {
            Object cursor=values;
            for(String segment:path.split("\\."))
            {
                if(!(cursor instanceof Map<?,?> map))return null;
                cursor=map.get(segment);
            }
            return cursor;
        }
    }
}
