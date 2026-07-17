package com.law.todo.application.view;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record TodoSimulationView(
        long versionId,
        String definitionHash,
        TriggerTrace trigger,
        OwnerTrace owner,
        SlaTrace sla,
        FormTrace form,
        List<RouteTrace> routes,
        List<AutoActionTrace> autoActions,
        List<HandlerTrace> handlers,
        List<SimulationIssue> issues)
{
    public TodoSimulationView
    {
        routes=copy(routes);autoActions=copy(autoActions);handlers=copy(handlers);issues=copy(issues);
    }
    public record TriggerTrace(String status,String eventType,int payloadVersion,List<String> trace)
    { public TriggerTrace { trace=copy(trace); } }
    public record OwnerTrace(String status,Long ownerId,List<Long> candidates,List<Long> ccUsers,
            boolean fallbackUsed,List<String> trace)
    { public OwnerTrace { candidates=copy(candidates);ccUsers=copy(ccUsers);trace=copy(trace); } }
    public record SlaTrace(String status,String calendarCode,LocalDateTime startAt,LocalDateTime dueAt,
            LocalDateTime remind80At,LocalDateTime overdue100At,LocalDateTime escalate150At,List<String> trace)
    { public SlaTrace { trace=copy(trace); } }
    public record FormTrace(Map<String,Object> ui,Map<String,Object> dod)
    { public FormTrace { ui=immutable(ui);dod=immutable(dod); } }
    public record RouteTrace(int order,String nodeKey,String nodeType,String status,String branchKey,
            Integer occurrence,Long templateVersionId,LocalDateTime effectiveAt,List<String> trace)
    { public RouteTrace { trace=copy(trace); } }
    public record AutoActionTrace(String ruleKey,String actionType,String triggerAt,LocalDateTime scheduledAt,String status,String reason) { }
    public record HandlerTrace(String code,String status,boolean simulatable,String reason) { }
    public record SimulationIssue(String code,String path,String severity,String message) { }
    private static <T> List<T> copy(List<T> values){return values==null?List.of():List.copyOf(values);}
    private static Map<String,Object> immutable(Map<String,Object> value){return value==null?Map.of():java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(value));}
}
