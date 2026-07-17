package com.law.todo.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson2.JSON;
import com.law.todo.application.command.TodoDefinitionCommands.SimulateDefinitionCommand;
import com.law.todo.application.command.TodoDefinitionCommands.VirtualTaskCompletionSample;
import com.law.todo.application.view.TodoSimulationView;
import com.law.todo.application.view.TodoSimulationView.AutoActionTrace;
import com.law.todo.application.view.TodoSimulationView.FormTrace;
import com.law.todo.application.view.TodoSimulationView.HandlerTrace;
import com.law.todo.application.view.TodoSimulationView.OwnerTrace;
import com.law.todo.application.view.TodoSimulationView.RouteTrace;
import com.law.todo.application.view.TodoSimulationView.SimulationIssue;
import com.law.todo.application.view.TodoSimulationView.SlaTrace;
import com.law.todo.application.view.TodoSimulationView.TriggerTrace;
import com.law.todo.assignment.OwnerResolutionContext;
import com.law.todo.assignment.OwnerResolutionResult;
import com.law.todo.definition.codec.TodoDefinitionCodec;
import com.law.todo.definition.catalog.TodoDecisionService;
import com.law.todo.definition.catalog.TodoEventCatalogService;
import com.law.todo.definition.compiler.DefinitionValidationReport;
import com.law.todo.definition.compiler.TodoDefinitionCompiler;
import com.law.todo.definition.compiler.TodoDefinitionCompiler.CompilationContext;
import com.law.todo.definition.compiler.TodoDefinitionCompiler.TemplateVersion;
import com.law.todo.definition.model.TodoDefinitionDocument;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.service.WorkingTimeCalculator;
import com.law.todo.domain.service.WorkingTimeCalculator.WorkCalendar;
import com.law.todo.expression.ConditionEvaluator;
import com.law.todo.expression.ConditionValidator;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.spi.TodoCompletionHandler;

/** Pure read-only dry run over a compiled immutable definition snapshot. */
@Service
public class TodoDefinitionSimulationService
{
    private final TodoMapper mapper;
    private final TodoAssignmentResolver owners;
    private final List<TodoCompletionHandler> handlers;
    private final TodoDefinitionCompiler compiler;
    private final TodoDefinitionCodec codec=new TodoDefinitionCodec();
    private final ConditionValidator conditions=new ConditionValidator();
    private final ConditionEvaluator evaluator=new ConditionEvaluator();

    public TodoDefinitionSimulationService(TodoMapper mapper,TodoAssignmentResolver owners)
    {this(mapper,owners,List.of(),compiler(mapper));}

    @Autowired
    public TodoDefinitionSimulationService(TodoMapper mapper,TodoAssignmentResolver owners,List<TodoCompletionHandler> handlers)
    {this(mapper,owners,handlers,compiler(mapper));}

    TodoDefinitionSimulationService(TodoMapper mapper,TodoAssignmentResolver owners,
            List<TodoCompletionHandler> handlers,TodoDefinitionCompiler compiler)
    {this.mapper=mapper;this.owners=owners;this.handlers=handlers==null?List.of():List.copyOf(handlers);this.compiler=compiler;}

    @Transactional(readOnly=true)
    public TodoSimulationView simulate(long versionId,SimulateDefinitionCommand command)
    {
        Map<String,Object> row=requireVersion(versionId);
        List<SimulationIssue> issues=new ArrayList<>();
        String status=text(value(row,"status","status"));
        if(!Set.of("DRAFT","BLOCKED","PUBLISHED","RETIRED").contains(status))
        {
            issues.add(new SimulationIssue("TODO_SIMULATION_VERSION_STATUS_INELIGIBLE","version.status","ERROR",
                    "Only DRAFT, BLOCKED, PUBLISHED or RETIRED definition versions can be simulated"));
            return halted(versionId,null,null,command,issues);
        }
        String compiled=text(value(row,"compiled_json","compiledJson"));
        String expectedHash=text(value(row,"definition_hash","definitionHash"));
        if(compiled==null||compiled.isBlank()||expectedHash==null||expectedHash.isBlank())
        {
            issues.add(new SimulationIssue("TODO_DEFINITION_NOT_COMPILED","version.compiledJson","ERROR","Run preflight before simulation"));
            return halted(versionId,expectedHash,null,command,issues);
        }
        TodoDefinitionDocument definition;
        try{definition=codec.read(compiled);}catch(RuntimeException invalid)
        {
            issues.add(new SimulationIssue("TODO_COMPILED_DEFINITION_INVALID","version.compiledJson","ERROR",safeMessage(invalid)));
            return halted(versionId,expectedHash,null,command,issues);
        }
        String canonical=codec.canonicalJson(definition),actualHash=sha256(canonical);
        if(!expectedHash.equals(actualHash))
        {
            issues.add(new SimulationIssue("TODO_DEFINITION_HASH_MISMATCH","version.definitionHash","ERROR","Compiled definition hash does not match its immutable snapshot"));
            return halted(versionId,actualHash,definition,command,issues);
        }

        try
        {
            DefinitionValidationReport report=compiler.compile(definition,new CompilationContext(versionId,
                    "DRAFT".equals(status)||"BLOCKED".equals(status),id->templateVersion(id,versionId,status)));
            report.errors().forEach(issue->issues.add(new SimulationIssue(issue.code(),issue.path(),"ERROR",issue.message())));
            report.warnings().forEach(issue->issues.add(new SimulationIssue(issue.code(),issue.path(),"WARNING",issue.message())));
        }
        catch(RuntimeException invalid)
        {
            issues.add(new SimulationIssue("TODO_SIMULATION_DEFINITION_VALIDATION_FAILED","definition","ERROR",safeMessage(invalid)));
        }
        if(issues.stream().anyMatch(issue->isHalting(issue,status)))
            return halted(versionId,actualHash,definition,command,issues);

        TriggerTrace trigger=trigger(definition,command,issues);
        boolean matched="MATCHED".equals(trigger.status());
        OwnerTrace owner=matched?owner(definition,command,issues):new OwnerTrace("SKIPPED",null,List.of(),List.of(),false,List.of("trigger:"+trigger.status().toLowerCase()));
        SlaTrace sla=matched?sla(definition,command,issues):new SlaTrace("SKIPPED",null,command.effectiveAt(),null,null,null,null,List.of("trigger:"+trigger.status().toLowerCase()));
        List<RouteTrace> routes=matched?routes(definition,command,issues):List.of();
        List<AutoActionTrace> actions=matched?autoActions(definition,command.payload(),sla,issues):List.of();
        if(!matched)issues.add(new SimulationIssue("UNKNOWN".equals(trigger.status())?"TODO_SIMULATION_TRIGGER_UNKNOWN":"TODO_SIMULATION_TRIGGER_NOT_MATCHED","event.condition","UNKNOWN".equals(trigger.status())?"WARNING":"INFO","No todo, route or automatic action would be created for this sample event"));
        List<HandlerTrace> handlerTraces=handlerTraces(issues);
        issues.sort(Comparator.comparing(SimulationIssue::code).thenComparing(SimulationIssue::path).thenComparing(SimulationIssue::message));
        return new TodoSimulationView(versionId,actualHash,trigger,owner,sla,
                new FormTrace(definition.ui().config(),definition.dod().config()),routes,actions,handlerTraces,issues);
    }

    private TemplateVersion templateVersion(long id,long currentId,String currentStatus)
    {
        Map<String,Object> target=mapper.selectTemplateVersionById(id);
        if(target==null||target.isEmpty())return null;
        String status=text(value(target,"status","status"));
        if(id==currentId&&"RETIRED".equals(currentStatus))status="PUBLISHED";
        if(id==currentId&&"BLOCKED".equals(currentStatus))status="DRAFT";
        return new TemplateVersion(id,status);
    }

    private boolean isHalting(SimulationIssue issue,String versionStatus)
    {
        return "ERROR".equals(issue.severity())
                && !("BLOCKED".equals(versionStatus)&&"TODO_DECISION_UNRESOLVED".equals(issue.code()));
    }

    private TodoSimulationView halted(long versionId,String hash,TodoDefinitionDocument definition,
            SimulateDefinitionCommand command,List<SimulationIssue> issues)
    {
        sort(issues);
        String eventType=definition==null||definition.event()==null?null:definition.event().eventType();
        int payloadVersion=definition==null||definition.event()==null?0:definition.event().payloadVersion();
        FormTrace form=definition==null?new FormTrace(Map.of(),Map.of()):new FormTrace(
                definition.ui()==null?Map.of():definition.ui().config(),definition.dod()==null?Map.of():definition.dod().config());
        return new TodoSimulationView(versionId,hash,new TriggerTrace("SKIPPED",eventType,payloadVersion,List.of("definition:ineligible")),
                new OwnerTrace("SKIPPED",null,List.of(),List.of(),false,List.of("definition:ineligible")),
                new SlaTrace("SKIPPED",null,command.effectiveAt(),null,null,null,null,List.of("definition:ineligible")),
                form,List.of(),List.of(),List.of(),issues);
    }

    private TriggerTrace trigger(TodoDefinitionDocument definition,SimulateDefinitionCommand command,List<SimulationIssue> issues)
    {
        var event=definition.event();List<String> trace=new ArrayList<>();
        if(event.condition().isEmpty())return new TriggerTrace("MATCHED",event.eventType(),event.payloadVersion(),List.of("condition:empty:true"));
        Map<String,Object> catalog=mapper.selectEventCatalog(event.eventType(),event.payloadVersion());
        String schema=catalog==null?null:text(value(catalog,"payload_schema_json","payloadSchemaJson"));
        var validation=conditions.validate(event.condition(),schema,true);
        if(!validation.valid())
        {
            validation.issues().forEach(issue->issues.add(new SimulationIssue(issue.code(),issue.path(),"ERROR",issue.message())));
            return new TriggerTrace("UNKNOWN",event.eventType(),event.payloadVersion(),List.of("condition:invalid"));
        }
        boolean matched=evaluator.evaluate(validation.expression(),command.payload());trace.add("condition:"+(matched?"matched":"not-matched"));
        return new TriggerTrace(matched?"MATCHED":"NOT_MATCHED",event.eventType(),event.payloadVersion(),trace);
    }

    private OwnerTrace owner(TodoDefinitionDocument definition,SimulateDefinitionCommand command,List<SimulationIssue> issues)
    {
        try
        {
            OwnerResolutionResult result=owners.resolveForSimulation(definition.owner(),new OwnerResolutionContext(command.payload(),command.businessType(),command.businessId(),command.effectiveAt()));
            boolean resolved=result.ownerId()!=null||!result.candidateUserIds().isEmpty();
            if(!resolved)issues.add(new SimulationIssue("TODO_SIMULATION_OWNER_UNRESOLVED","owner","WARNING","Sample context cannot resolve an owner or candidate pool"));
            return new OwnerTrace(resolved?"RESOLVED":"UNRESOLVED",result.ownerId(),result.candidateUserIds(),result.ccUserIds(),result.fallbackUsed(),result.trace());
        }
        catch(RuntimeException failure)
        {
            issues.add(new SimulationIssue("TODO_SIMULATION_OWNER_CONTEXT_UNKNOWN","owner","WARNING",safeMessage(failure)));
            return new OwnerTrace("UNKNOWN",null,List.of(),List.of(),false,List.of("owner:error"));
        }
    }

    private SlaTrace sla(TodoDefinitionDocument definition,SimulateDefinitionCommand command,List<SimulationIssue> issues)
    {
        Map<String,Object> rule=definition.sla().config();String calendarCode=text(rule.get("calendarCode"));
        if(rule.isEmpty()||(calendarCode==null&&rule.get("minutes")==null))
            return new SlaTrace("NOT_CONFIGURED",null,command.effectiveAt(),null,null,null,null,List.of("sla:not-configured"));
        Map<String,Object> row=calendarCode==null?null:mapper.selectCalendarByCode(calendarCode);
        if(row==null)
        {
            issues.add(new SimulationIssue("TODO_SIMULATION_CALENDAR_UNKNOWN","sla.calendarCode","ERROR","Configured calendar is unavailable"));
            return new SlaTrace("UNKNOWN",calendarCode,command.effectiveAt(),null,null,null,null,List.of("calendar:not-found"));
        }
        Long minutes=positiveLong(rule.get("minutes"));
        if(minutes==null)
        {
            issues.add(new SimulationIssue("TODO_SIMULATION_SLA_DURATION_UNKNOWN","sla.minutes","ERROR","A positive SLA duration is required"));
            return new SlaTrace("UNKNOWN",calendarCode,command.effectiveAt(),null,null,null,null,List.of("duration:invalid"));
        }
        try
        {
            WorkCalendar calendar=calendar(row);WorkingTimeCalculator calculator=new WorkingTimeCalculator();
            LocalDateTime due=calculator.addWorkingMinutes(command.effectiveAt(),minutes,calendar);
            LocalDateTime p80=calculator.addWorkingMinutes(command.effectiveAt(),ceiling(minutes,80),calendar);
            LocalDateTime p150=calculator.addWorkingMinutes(command.effectiveAt(),ceiling(minutes,150),calendar);
            return new SlaTrace("PLANNED",calendarCode,command.effectiveAt(),due,p80,due,p150,List.of("duration:working-minutes:"+minutes));
        }
        catch(RuntimeException invalid)
        {
            issues.add(new SimulationIssue("TODO_SIMULATION_CALENDAR_INVALID","sla.calendarCode","ERROR",safeMessage(invalid)));
            return new SlaTrace("UNKNOWN",calendarCode,command.effectiveAt(),null,null,null,null,List.of("calendar:invalid"));
        }
    }

    private List<RouteTrace> routes(TodoDefinitionDocument definition,SimulateDefinitionCommand command,List<SimulationIssue> issues)
    {
        Map<String,Object> graph=definition.routing().config();
        Map<String,Map<String,Object>> nodes=new LinkedHashMap<>();for(Map<String,Object> node:objectList(graph.get("nodes")))nodes.put(text(node.get("key")),node);
        if(nodes.isEmpty())
        {
            Long legacy=longValue(graph.get("templateVersionId"));
            return legacy==null?List.of():List.of(new RouteTrace(1,"legacy-next","TASK","PENDING_COMPLETION",null,0,legacy,
                    command.effectiveAt(),List.of("legacy:single-next","task:would-create","task:pending-completion")));
        }
        Map<String,List<Map<String,Object>>> outgoing=new HashMap<>();for(Map<String,Object> edge:objectList(graph.get("edges")))outgoing.computeIfAbsent(text(edge.get("from")),ignored->new ArrayList<>()).add(edge);
        outgoing.values().forEach(edges->edges.sort(Comparator.comparingInt((Map<String,Object> edge)->integer(edge.get("priority"),0)).reversed().thenComparing(edge->text(edge.get("key")))));
        RouteSimulationContext context=new RouteSimulationContext(command.payload(),command.effectiveAt(),
                validatedSamples(command.taskCompletions(),nodes,command.effectiveAt(),issues));
        List<RouteTrace> traces=new ArrayList<>();walk(text(graph.get("start")),null,0,nodes,outgoing,context,traces,issues,new HashSet<>(),new HashMap<>(),0);
        return List.copyOf(traces);
    }

    private Map<String,VirtualTaskCompletionSample> validatedSamples(List<VirtualTaskCompletionSample> samples,
            Map<String,Map<String,Object>> nodes,LocalDateTime effectiveAt,List<SimulationIssue> issues)
    {
        Map<String,Integer> counts=new HashMap<>();
        for(VirtualTaskCompletionSample sample:samples)if(sample!=null&&sample.nodeKey()!=null)counts.merge(sample.nodeKey(),1,Integer::sum);
        Map<String,VirtualTaskCompletionSample> result=new LinkedHashMap<>();Set<String> duplicateIssues=new HashSet<>();
        for(VirtualTaskCompletionSample sample:samples)
        {
            if(sample==null||sample.nodeKey()==null||sample.nodeKey().isBlank())
            {
                issues.add(new SimulationIssue("TODO_SIMULATION_TASK_SAMPLE_INVALID","taskCompletions","ERROR","TASK completion sample requires a node key, payload and completion time"));continue;
            }
            String key=sample.nodeKey(),path="taskCompletions."+key;
            if(counts.getOrDefault(key,0)>1)
            {
                if(duplicateIssues.add(key))
                    issues.add(new SimulationIssue("TODO_SIMULATION_TASK_SAMPLE_DUPLICATE",path,"ERROR","TASK completion samples must have unique node keys"));
                continue;
            }
            Map<String,Object> node=nodes.get(key);
            if(node==null){issues.add(new SimulationIssue("TODO_SIMULATION_TASK_SAMPLE_NODE_UNKNOWN",path,"ERROR","TASK completion sample references an unknown route node"));continue;}
            if(!"TASK".equals(text(node.get("type")))){issues.add(new SimulationIssue("TODO_SIMULATION_TASK_SAMPLE_NODE_NOT_TASK",path,"ERROR","Completion samples can reference TASK nodes only"));continue;}
            if(sample.payload()==null||sample.completedAt()==null){issues.add(new SimulationIssue("TODO_SIMULATION_TASK_SAMPLE_INVALID",path,"ERROR","TASK completion sample requires a payload and completion time"));continue;}
            if(sample.completedAt().isBefore(effectiveAt)){issues.add(new SimulationIssue("TODO_SIMULATION_TASK_SAMPLE_TIME_INVALID",path+".completedAt","ERROR","TASK completion time cannot precede the sample event effective time"));continue;}
            result.put(key,sample);
        }
        return Map.copyOf(result);
    }

    private void walk(String key,String branch,int occurrence,Map<String,Map<String,Object>> nodes,
            Map<String,List<Map<String,Object>>> outgoing,RouteSimulationContext context,List<RouteTrace> traces,
            List<SimulationIssue> issues,Set<String> active,Map<String,JoinSimulationState> joins,int depth)
    {
        if(key==null||depth>nodes.size()*4+8){issues.add(new SimulationIssue("TODO_SIMULATION_ROUTE_LIMIT","routing","WARNING","Route trace reached its safe bound"));return;}
        String fence=key+"|"+branch+"|"+occurrence;if(!active.add(fence)){issues.add(new SimulationIssue("TODO_SIMULATION_ROUTE_CYCLE","routing.nodes."+key,"WARNING","Route requires runtime occurrence state"));return;}
        Map<String,Object> node=nodes.get(key);if(node==null){issues.add(new SimulationIssue("TODO_SIMULATION_ROUTE_NODE_UNKNOWN","routing.nodes."+key,"ERROR","Route node is missing"));active.remove(fence);return;}
        String type=text(node.get("type"));
        if("JOIN".equals(type))
        {
            String joinKey=key+"|"+occurrence;List<String> required=stringList(node.get("branches"));
            JoinSimulationState state=joins.computeIfAbsent(joinKey,ignored->new JoinSimulationState());
            boolean known=branch!=null&&required.contains(branch);if(known)state.arrive(branch,context);
            boolean ready="ANY".equals(text(node.get("joinMode")))?!state.arrivals.isEmpty():state.arrivals.keySet().containsAll(required);
            boolean advance=known&&ready&&!state.advanced;String status=!known?"UNKNOWN_BRANCH":advance?"ADVANCED":ready?"ALREADY_ADVANCED":"WAITING";
            if(advance){state.advanced=true;context=state.advancingContext(text(node.get("joinMode")));}
            traces.add(new RouteTrace(traces.size()+1,key,type,status,branch,occurrence,null,context.effectiveAt,
                    List.of("arrivals:"+String.join(",",new java.util.TreeSet<>(state.arrivals.keySet())))));
            if(!known)issues.add(new SimulationIssue("TODO_SIMULATION_JOIN_BRANCH_UNKNOWN","routing.nodes."+key,"WARNING","JOIN received a branch not declared by its contract"));
            if(!advance){active.remove(fence);return;}
        }
        else
        {
            if("TASK".equals(type))
            {
                VirtualTaskCompletionSample sample=context.samples.get(key);
                if(sample==null)
                {
                    traces.add(new RouteTrace(traces.size()+1,key,type,"PENDING_COMPLETION",branch,occurrence,
                            longValue(node.get("templateVersionId")),context.effectiveAt,
                            List.of("task:would-create","task:pending-completion")));
                    active.remove(fence);return;
                }
                context.merge(sample);
                traces.add(new RouteTrace(traces.size()+1,key,type,"VIRTUAL_COMPLETED",branch,occurrence,
                        longValue(node.get("templateVersionId")),context.effectiveAt,
                        List.of("task:would-create","task:virtual-completed","task:completed-at:"+sample.completedAt())));
            }
            else
                traces.add(new RouteTrace(traces.size()+1,key,type,"END".equals(type)?"ENDED":"VISITED",branch,occurrence,
                        longValue(node.get("templateVersionId")),context.effectiveAt,List.of("node:"+type.toLowerCase())));
        }
        List<Map<String,Object>> edges=outgoing.getOrDefault(key,List.of());
        if("END".equals(type)||edges.isEmpty()){active.remove(fence);return;}
        if("DECISION".equals(type))
        {
            Map<String,Object> selected=null,fallback=null;for(Map<String,Object> edge:edges){if(Boolean.TRUE.equals(edge.get("default")))fallback=edge;else if(matches(edge.get("condition"),context.payload,issues,"routing.edges."+text(edge.get("key")))){selected=edge;break;}}
            follow(selected==null?fallback:selected,branch,occurrence,nodes,outgoing,context,traces,issues,active,joins,depth);active.remove(fence);return;
        }
        if("FORK".equals(type))
        {
            RouteSimulationContext forkContext=context;
            List<Map<String,Object>> selected=edges.stream().filter(edge->edge.get("condition")==null
                    ||matches(edge.get("condition"),forkContext.payload,issues,"routing.edges."+text(edge.get("key")))).toList();
            selected=new ArrayList<>(selected);selected.sort(Comparator.comparing(
                    (Map<String,Object> edge)->forkContext.nextTaskCompletionAt(text(edge.get("to"))),Comparator.nullsLast(LocalDateTime::compareTo))
                    .thenComparing(edge->text(edge.get("key"))));
            for(Map<String,Object> edge:selected)follow(edge,text(edge.get("branchKey")),occurrence,nodes,outgoing,
                    forkContext.copy(),traces,issues,new HashSet<>(active),joins,depth);
            active.remove(fence);return;
        }
        if("LOOP".equals(type))
        {
            boolean end=node.get("endCondition")!=null&&matches(node.get("endCondition"),context.payload,issues,"routing.nodes."+key+".endCondition");Integer max=integer(node.get("maxOccurrences"),null);String wanted=end||(max!=null&&occurrence>=max)?"EXIT":"BODY";
            Map<String,Object> edge=edges.stream().filter(e->wanted.equals(text(e.get("branchKey")))).findFirst().orElse(null);follow(edge,branch,"BODY".equals(wanted)?occurrence+1:occurrence,nodes,outgoing,context,traces,issues,active,joins,depth);active.remove(fence);return;
        }
        follow(edges.get(0),branch,occurrence,nodes,outgoing,context,traces,issues,active,joins,depth);active.remove(fence);
    }

    private void follow(Map<String,Object> edge,String branch,int occurrence,Map<String,Map<String,Object>> nodes,
            Map<String,List<Map<String,Object>>> outgoing,RouteSimulationContext context,List<RouteTrace> traces,
            List<SimulationIssue> issues,Set<String> active,Map<String,JoinSimulationState> joins,int depth)
    {if(edge==null){issues.add(new SimulationIssue("TODO_SIMULATION_ROUTE_NO_MATCH","routing","WARNING","No deterministic outgoing route matched"));return;}walk(text(edge.get("to")),branch,occurrence,nodes,outgoing,context,traces,issues,active,joins,depth+1);}

    private static final class RouteSimulationContext
    {
        private final Map<String,Object> payload;
        private final Map<String,VirtualTaskCompletionSample> samples;
        private LocalDateTime effectiveAt;
        private RouteSimulationContext(Map<String,Object> payload,LocalDateTime effectiveAt,
                Map<String,VirtualTaskCompletionSample> samples)
        {this.payload=new LinkedHashMap<>(payload);this.effectiveAt=effectiveAt;this.samples=samples;}
        private void merge(VirtualTaskCompletionSample sample)
        {payload.putAll(sample.payload());if(sample.completedAt().isAfter(effectiveAt))effectiveAt=sample.completedAt();}
        private RouteSimulationContext copy(){return new RouteSimulationContext(payload,effectiveAt,samples);}
        private LocalDateTime nextTaskCompletionAt(String nodeKey)
        {VirtualTaskCompletionSample sample=samples.get(nodeKey);return sample==null?null:sample.completedAt();}
    }

    private static final class JoinSimulationState
    {
        private final Map<String,RouteSimulationContext> arrivals=new LinkedHashMap<>();
        private boolean advanced;
        private void arrive(String branch,RouteSimulationContext context){arrivals.putIfAbsent(branch,context.copy());}
        private RouteSimulationContext advancingContext(String mode)
        {
            Comparator<RouteSimulationContext> order=Comparator.comparing(value->value.effectiveAt);
            return ("ANY".equals(mode)?arrivals.values().stream().min(order):arrivals.values().stream().max(order))
                    .orElseThrow().copy();
        }
    }

    private boolean matches(Object raw,Map<String,Object> payload,List<SimulationIssue> issues,String path)
    {
        if(!(raw instanceof Map<?,?> map))return false;Map<String,Object> condition=new LinkedHashMap<>();map.forEach((k,v)->condition.put(String.valueOf(k),v));
        try{return evaluator.evaluate(conditions.decodeCanonical(condition),payload);}
        catch(RuntimeException invalid){issues.add(new SimulationIssue("TODO_SIMULATION_ROUTE_CONTEXT_UNKNOWN",path,"WARNING",safeMessage(invalid)));return false;}
    }

    private List<AutoActionTrace> autoActions(TodoDefinitionDocument definition,Map<String,Object> payload,SlaTrace sla,List<SimulationIssue> issues)
    {
        List<AutoActionTrace> result=new ArrayList<>();
        for(var rule:definition.autoActions())
        {
            Map<String,Object> c=rule.config();String trigger=text(c.get("triggerAt"));String status="WOULD_SCHEDULE",reason="Controlled action is not executed during simulation";
            if(c.get("precondition")!=null)
            {
                try
                {
                    @SuppressWarnings("unchecked") Map<String,Object> precondition=(Map<String,Object>)c.get("precondition");
                    if(!evaluator.evaluate(conditions.decodeCanonical(precondition),payload)){status="SKIPPED_PRECONDITION";reason="Sample context does not satisfy the precondition";}
                }
                catch(RuntimeException unknown){status="UNKNOWN";reason=safeMessage(unknown);issues.add(new SimulationIssue("TODO_SIMULATION_AUTO_ACTION_CONTEXT_UNKNOWN","autoActions."+text(c.get("ruleKey"))+".precondition","WARNING",reason));}
            }
            LocalDateTime scheduledAt=scheduledAt(trigger,sla);
            if("WOULD_SCHEDULE".equals(status)&&scheduledAt==null)
            {
                status="UNSCHEDULABLE";reason="The governed SLA timestamp cannot be calculated";
                issues.add(new SimulationIssue("TODO_SIMULATION_AUTO_ACTION_UNSCHEDULABLE","autoActions."+text(c.get("ruleKey"))+".triggerAt","ERROR",reason));
            }
            result.add(new AutoActionTrace(text(c.get("ruleKey")),text(c.containsKey("actionType")?c.get("actionType"):c.get("action")),trigger,scheduledAt,status,reason));
        }
        result.sort(Comparator.comparing(AutoActionTrace::ruleKey,Comparator.nullsFirst(String::compareTo)));return List.copyOf(result);
    }
    private LocalDateTime scheduledAt(String trigger,SlaTrace sla){return switch(trigger==null?"":trigger){case "SLA_80"->sla.remind80At();case "SLA_150"->sla.escalate150At();case "DUE","SLA_100"->sla.dueAt();default->null;};}
    private List<HandlerTrace> handlerTraces(List<SimulationIssue> issues)
    {List<HandlerTrace> result=handlers.stream().map(handler->new HandlerTrace(handler.catalogCode(),handler.supportsSimulation()?"SIMULATABLE":"NOT_EXECUTED",handler.supportsSimulation(),handler.simulationDescription())).sorted(Comparator.comparing(HandlerTrace::code)).toList();for(HandlerTrace handler:result)if(!handler.simulatable())issues.add(new SimulationIssue("TODO_SIMULATION_HANDLER_NOT_EXECUTED","handlers."+handler.code(),"INFO",handler.reason()));return result;}

    private Map<String,Object> requireVersion(long id){Map<String,Object> row=mapper.selectTemplateVersionById(id);if(row==null||row.isEmpty())throw new TodoException("TODO_TEMPLATE_VERSION_NOT_FOUND","Template version not found");return row;}
    private static TodoDefinitionCompiler compiler(TodoMapper mapper){return new TodoDefinitionCompiler(new TodoDefinitionCodec(),new TodoEventCatalogService(mapper),new TodoDecisionService(mapper));}
    private void sort(List<SimulationIssue> issues){issues.sort(Comparator.comparing(SimulationIssue::code).thenComparing(SimulationIssue::path).thenComparing(SimulationIssue::message));}
    private WorkCalendar calendar(Map<String,Object> row){Set<DayOfWeek> days=EnumSet.noneOf(DayOfWeek.class);for(String day:text(value(row,"work_days","workDays")).split(","))days.add(DayOfWeek.of(Integer.parseInt(day.trim())));Map<LocalDate,Boolean> exceptions=new HashMap<>();String json=text(value(row,"exception_json","exceptionJson"));if(json!=null&&!json.isBlank())JSON.parseObject(json).forEach((k,v)->exceptions.put(LocalDate.parse(k),Boolean.valueOf(String.valueOf(v))));return new WorkCalendar(days,time(value(row,"work_start","workStart")),time(value(row,"work_end","workEnd")),exceptions);}
    private LocalTime time(Object value){String text=String.valueOf(value);return LocalTime.parse(text.length()>8?text.substring(0,8):text);}
    private long ceiling(long minutes,int percent){return (minutes*percent+99)/100;}
    private Long positiveLong(Object value){try{Long parsed=value==null?null:Long.valueOf(String.valueOf(value));return parsed!=null&&parsed>0?parsed:null;}catch(NumberFormatException invalid){return null;}}
    private Long longValue(Object value){try{return value==null?null:Long.valueOf(String.valueOf(value));}catch(NumberFormatException invalid){return null;}}
    private Integer integer(Object value,Integer fallback){try{return value==null?fallback:Integer.valueOf(String.valueOf(value));}catch(NumberFormatException invalid){return fallback;}}
    private List<String> stringList(Object value){return value instanceof List<?> list?list.stream().map(String::valueOf).toList():List.of();}
    @SuppressWarnings("unchecked") private List<Map<String,Object>> objectList(Object value){if(!(value instanceof List<?> list))return List.of();List<Map<String,Object>> result=new ArrayList<>();for(Object entry:list)if(entry instanceof Map<?,?> map){Map<String,Object> item=new LinkedHashMap<>();map.forEach((k,v)->item.put(String.valueOf(k),v));result.add(item);}return result;}
    private Object value(Map<String,Object> row,String snake,String camel){return row.containsKey(snake)?row.get(snake):row.get(camel);}
    private String text(Object value){return value==null?null:String.valueOf(value);}
    private String safeMessage(Throwable error){return error.getMessage()==null?error.getClass().getSimpleName():error.getMessage();}
    static String sha256(String value){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception impossible){throw new IllegalStateException(impossible);}}
}
