package com.law.todo.schedule;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson2.JSON;
import com.law.todo.application.TodoAutoActionService;
import com.law.todo.application.TodoRoutingService;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;

@Service
public class TodoScheduleService
{
    public static final String DEFAULT_TIMEZONE="Asia/Shanghai";
    public static final String RESOLVED_POLICY="RESOLVED_POLICY";
    public static final String LEGACY_POLICY="LEGACY_PRE_0_20_49";
    public static final Duration CLAIM_LEASE=Duration.ofMinutes(5);

    private final TodoMapper mapper;
    private final TodoRoutingService routing;

    public TodoScheduleService(TodoMapper mapper,TodoRoutingService routing)
    {
        this.mapper=mapper;this.routing=routing;
    }

    /**
     * Resolves a scheduled completion identity from immutable Todo context. User payload must never
     * choose an occurrence because that would allow one Todo to target another schedule.
     */
    @Transactional(readOnly=true)
    public Long requireOccurrenceIdForTodo(String occurrenceKey,Long todoId)
    {
        if(occurrenceKey==null||occurrenceKey.isBlank()||todoId==null)
            throw new TodoException("TODO_SCHEDULE_SOURCE_TODO_INVALID",
                    "Scheduled Todo identity is incomplete");
        Map<String,Object> identity=mapper.selectScheduleOccurrenceIdentityByKey(occurrenceKey.trim());
        if(identity==null)
            throw new TodoException("TODO_SCHEDULE_OCCURRENCE_NOT_FOUND",
                    "Schedule occurrence does not exist");
        Long occurrenceId=requiredLong(identity,"occurrenceId","occurrence_id");
        Map<String,Object> occurrence=mapper.selectScheduleOccurrenceById(occurrenceId);
        if(occurrence==null||!occurrenceKey.trim().equals(text(occurrence,"occurrenceKey","occurrence_key"))
                ||!todoId.equals(longValue(occurrence,"todoId","todo_id")))
            throw new TodoException("TODO_SCHEDULE_SOURCE_TODO_INVALID",
                    "Schedule occurrence is not linked to this Todo");
        return occurrenceId;
    }

    /**
     * The input timestamp is local to {@code timezone}. Absolute LocalDateTime values are calculated
     * once and persisted, so a later policy edit cannot move an existing plan.
     */
    @Transactional
    public long createPlan(CreateSchedulePlanCommand command)
    {
        validate(command);
        String timezone=command.timezone()==null||command.timezone().isBlank()
                ?DEFAULT_TIMEZONE:command.timezone().trim();
        try { ZoneId.of(timezone); }
        catch (RuntimeException invalid)
        {
            throw new TodoException("TODO_SCHEDULE_TIMEZONE_INVALID","Unknown schedule timezone");
        }
        validateTemplate(command.templateVersionId(),command.businessType(),command.purpose());
        List<TodoScheduleWindow> windows=command.windows()==null||command.windows().isEmpty()
                ?defaultWindows(command.firstContactCompletedAt())
                :configuredWindows(command.firstContactCompletedAt(),command.windows());
        Map<String,Object> existing=mapper.selectSchedulePlanByIdempotencyKey(command.idempotencyKey().trim());
        if(existing!=null&&!existing.isEmpty())
            return requireMatchingPlanForUpdate(command,timezone,windows);
        Map<String,Object> plan=new HashMap<>();
        plan.put("previousTodoId",command.previousTodoId());
        plan.put("templateVersionId",command.templateVersionId());
        plan.put("businessType",command.businessType().trim());
        plan.put("businessId",command.businessId());
        plan.put("schedulePurpose",command.purpose().name());
        plan.put("idempotencyKey",command.idempotencyKey().trim());
        plan.put("timezone",timezone);
        plan.put("ruleVersionId",command.ruleVersionId());
        plan.put("assignmentPolicyId",command.assignmentPolicyId());
        plan.put("assignmentPolicyVersion",command.assignmentPolicyVersion());
        plan.put("assignmentPolicySnapshotSource",RESOLVED_POLICY);
        plan.put("firstContactAt",command.firstContactCompletedAt());
        plan.put("status","ACTIVE");
        try
        {
            mapper.insertSchedulePlan(plan);
        }
        catch(DuplicateKeyException duplicate)
        {
            Map<String,Object> concurrent=mapper.selectSchedulePlanByIdempotencyKeyForUpdate(
                    command.idempotencyKey().trim());
            if(concurrent!=null&&!concurrent.isEmpty())
                return requireMatchingPlan(command,timezone,windows,concurrent);
            throw duplicate;
        }
        Long planId=longValue(plan,"planId","plan_id");
        if(planId==null)
            throw new TodoException("TODO_SCHEDULE_PLAN_PERSIST_FAILED","Schedule plan identity was not generated");
        for(TodoScheduleWindow window:windows)
        {
            Map<String,Object> row=new HashMap<>();
            row.put("planId",planId);row.put("windowCode",window.windowCode());
            row.put("windowOrder",window.windowOrder());row.put("dayOffset",window.dayOffset());
            row.put("startTime",window.startTime());row.put("endTime",window.endTime());
            row.put("startAt",window.startAt());row.put("materializeAt",window.startAt());
            row.put("dueAt",window.dueAt());row.put("maxAttempts",window.maxAttempts());
            row.put("occurrenceNo",window.occurrenceNo());row.put("status","PENDING");
            mapper.insertScheduleWindow(row);
        }
        return planId;
    }

    public List<TodoScheduleWindow> defaultWindows(LocalDateTime firstContactCompletedAt)
    {
        if(firstContactCompletedAt==null)
            throw new TodoException("TODO_SCHEDULE_PLAN_INVALID","First-contact completion time is required");
        List<TodoScheduleWindow> windows=new ArrayList<>();
        windows.add(TodoScheduleWindow.t0(firstContactCompletedAt));
        int order=1;
        for(int day=1;day<=2;day++)
        {
            windows.add(TodoScheduleWindow.fixed("T"+day+"_AM",order++,day,
                    LocalTime.of(9,0),LocalTime.of(11,0),firstContactCompletedAt.toLocalDate()));
            windows.add(TodoScheduleWindow.fixed("T"+day+"_NOON",order++,day,
                    LocalTime.of(12,0),LocalTime.of(14,0),firstContactCompletedAt.toLocalDate()));
            windows.add(TodoScheduleWindow.fixed("T"+day+"_PM",order++,day,
                    LocalTime.of(15,0),LocalTime.of(18,0),firstContactCompletedAt.toLocalDate()));
        }
        return List.copyOf(windows);
    }

    public List<TodoScheduleWindow> configuredWindows(LocalDateTime firstContactCompletedAt,
            List<ScheduleWindowRule> rules)
    {
        if(firstContactCompletedAt==null)
            throw new TodoException("TODO_SCHEDULE_RULE_INVALID","Configured schedule windows are required");
        validateScheduleWindowRules(rules);
        List<TodoScheduleWindow> windows=new ArrayList<>();
        for(ScheduleWindowRule rule:rules)
        {
            LocalDateTime startAt;
            LocalDateTime dueAt;
            LocalTime startTime;
            LocalTime endTime;
            if(rule.dayOffset()==0)
            {
                if(rule.startOffsetMinutes()==null||rule.startOffsetMinutes()<0
                        ||rule.durationMinutes()==null||rule.durationMinutes()<=0)
                    throw new TodoException("TODO_SCHEDULE_RULE_INVALID",
                            "Same-day schedule window requires positive relative timing");
                startAt=firstContactCompletedAt.plusMinutes(rule.startOffsetMinutes());
                dueAt=startAt.plusMinutes(rule.durationMinutes());
                startTime=startAt.toLocalTime();
                endTime=dueAt.toLocalTime();
            }
            else
            {
                if(rule.startTime()==null||rule.endTime()==null||!rule.endTime().isAfter(rule.startTime()))
                    throw new TodoException("TODO_SCHEDULE_RULE_INVALID",
                            "Future schedule window requires a valid local-time period");
                startTime=rule.startTime();endTime=rule.endTime();
                var date=firstContactCompletedAt.toLocalDate().plusDays(rule.dayOffset());
                startAt=LocalDateTime.of(date,startTime);dueAt=LocalDateTime.of(date,endTime);
            }
            windows.add(new TodoScheduleWindow(rule.windowCode().trim(),rule.windowOrder(),rule.dayOffset(),
                    startTime,endTime,startAt,dueAt,rule.maxAttempts(),rule.occurrenceNo()));
        }
        windows.sort(java.util.Comparator.comparingInt(TodoScheduleWindow::windowOrder));
        return List.copyOf(windows);
    }

    /**
     * Parses and validates the canonical definition schedule using the same invariants as runtime
     * materialization. Missing presentation-only order and occurrence fields are deterministic:
     * list order and occurrence one respectively.
     */
    public static List<ScheduleWindowRule> requireValidWindowConfiguration(Object configured)
    {
        if(!(configured instanceof Collection<?> values)||values.isEmpty())
            throw invalidScheduleRule("Configured schedule windows are required");
        List<ScheduleWindowRule> rules=new ArrayList<>();
        int implicitOrder=0;
        for(Object raw:values)
        {
            if(!(raw instanceof Map<?,?> window))
                throw invalidScheduleRule("Configured schedule window must be an object");
            String code=requiredText(window,"windowCode");
            int order=optionalInteger(window,"windowOrder",implicitOrder++);
            int dayOffset=requiredInteger(window,"dayOffset");
            int attempts=requiredInteger(window,"maxAttempts");
            int occurrence=optionalInteger(window,"occurrenceNo",1);
            Integer startOffset=optionalInteger(window,"startOffsetMinutes");
            Integer duration=optionalInteger(window,"durationMinutes");
            LocalTime start=parseTime(window,"startTime");
            LocalTime end=parseTime(window,"endTime");
            rules.add(new ScheduleWindowRule(code,order,dayOffset,start,end,startOffset,duration,
                    attempts,occurrence));
        }
        validateScheduleWindowRules(rules);
        return List.copyOf(rules);
    }

    /**
     * Resolves the one canonical timing mode accepted by every definition entry point. A schedule
     * is never allowed to fall back to scalar timing: once the schedule property is present its
     * shape and windows must be valid, and it cannot coexist with a configured scalar duration.
     */
    public static SlaTimingConfiguration requireValidSlaTimingConfiguration(Map<?,?> configured)
    {
        if(configured==null)
            throw new TodoException("TODO_SLA_MINUTES_INVALID","SLA minutes must be positive");
        boolean schedulePresent=configured.containsKey("schedule");
        boolean scalarConfigured=configured.get("minutes")!=null
                ||configured.get("durationValue")!=null||configured.get("durationUnit")!=null;
        if(schedulePresent)
        {
            if(scalarConfigured)
                throw invalidScheduleRule("Scheduled and scalar SLA timing cannot be combined");
            Object rawSchedule=configured.get("schedule");
            if(!(rawSchedule instanceof Map<?,?> schedule)||!schedule.containsKey("windows"))
                throw invalidScheduleRule("Configured schedule must contain windows");
            return new SlaTimingConfiguration(SlaTimingMode.WINDOWS,null,
                    requireValidWindowConfiguration(schedule.get("windows")));
        }
        Long minutes=positiveLong(configured.get("minutes"));
        if(minutes==null)
            minutes=durationMinutes(configured.get("durationValue"),configured.get("durationUnit"));
        if(minutes==null)
            throw new TodoException("TODO_SLA_MINUTES_INVALID","SLA minutes must be positive");
        return new SlaTimingConfiguration(SlaTimingMode.SCALAR,minutes,List.of());
    }

    private static Long positiveLong(Object raw)
    {
        if(raw==null)return null;
        try
        {
            long value=raw instanceof Number number?number.longValue():Long.parseLong(String.valueOf(raw));
            return value>0?value:null;
        }
        catch(NumberFormatException invalid){return null;}
    }

    private static Long durationMinutes(Object rawValue,Object rawUnit)
    {
        Long value=positiveLong(rawValue);
        if(value==null||rawUnit==null)return null;
        return switch(String.valueOf(rawUnit).trim().toUpperCase(java.util.Locale.ROOT))
        {
            case "MINUTE" -> value;
            case "HOUR" -> Math.multiplyExact(value,60L);
            case "DAY" -> Math.multiplyExact(value,24L*60L);
            default -> null;
        };
    }

    private static void validateScheduleWindowRules(List<ScheduleWindowRule> rules)
    {
        if(rules==null||rules.isEmpty())
            throw invalidScheduleRule("Configured schedule windows are required");
        HashSet<String> codes=new HashSet<>();
        HashSet<Integer> orders=new HashSet<>();
        for(ScheduleWindowRule rule:rules)
        {
            if(rule==null||rule.windowCode()==null||rule.windowCode().isBlank()
                    ||rule.windowOrder()<0||rule.dayOffset()<0||rule.maxAttempts()<=0
                    ||rule.occurrenceNo()<=0||!codes.add(rule.windowCode().trim())
                    ||!orders.add(rule.windowOrder()))
                throw invalidScheduleRule("Configured schedule window identity is invalid");
            if(rule.dayOffset()==0)
            {
                if(rule.startOffsetMinutes()==null||rule.startOffsetMinutes()<0
                        ||rule.durationMinutes()==null||rule.durationMinutes()<=0
                        ||rule.startTime()!=null||rule.endTime()!=null)
                    throw invalidScheduleRule("Same-day schedule window requires a non-negative offset and positive duration");
            }
            else if(rule.startTime()==null||rule.endTime()==null
                    ||!rule.endTime().isAfter(rule.startTime())
                    ||rule.startOffsetMinutes()!=null||rule.durationMinutes()!=null)
                throw invalidScheduleRule("Future schedule window requires a valid local-time period");
        }
    }

    private static String requiredText(Map<?,?> value,String key)
    {
        Object raw=value.get(key);
        if(raw==null||String.valueOf(raw).isBlank())throw invalidScheduleRule(key+" is required");
        return String.valueOf(raw).trim();
    }

    private static int requiredInteger(Map<?,?> value,String key)
    {
        Integer result=optionalInteger(value,key);
        if(result==null)throw invalidScheduleRule(key+" is required");
        return result;
    }

    private static int optionalInteger(Map<?,?> value,String key,int fallback)
    {
        Integer result=optionalInteger(value,key);
        return result==null?fallback:result;
    }

    private static Integer optionalInteger(Map<?,?> value,String key)
    {
        Object raw=value.get(key);
        if(raw==null)return null;
        try
        {
            if(raw instanceof Number number)
            {
                double decimal=number.doubleValue();
                int result=number.intValue();
                if(decimal!=result)throw new NumberFormatException();
                return result;
            }
            return Integer.valueOf(String.valueOf(raw));
        }
        catch(NumberFormatException invalid){throw invalidScheduleRule(key+" must be an integer");}
    }

    private static LocalTime parseTime(Map<?,?> value,String key)
    {
        Object raw=value.get(key);
        if(raw==null||String.valueOf(raw).isBlank())return null;
        try{return LocalTime.parse(String.valueOf(raw));}
        catch(RuntimeException invalid){throw invalidScheduleRule(key+" must be a local time");}
    }

    private static TodoException invalidScheduleRule(String message)
    {return new TodoException("TODO_SCHEDULE_RULE_INVALID",message);}

    /**
     * Conditionally claims each due window. The occurrence key is stable across a failed retry and
     * the routing boundary independently protects Todo creation with its database unique key.
     */
    /*
     * Do not wrap the scan in one transaction: createScheduledNext owns the atomic Todo graph
     * transaction. A failure can therefore roll that graph back before these durable claim rows are
     * returned to RETRY/PENDING.
     */
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public int materializeDue(LocalDateTime now,int limit)
    {
        if(now==null||limit<=0) return 0;
        LocalDateTime staleBefore=now.minus(CLAIM_LEASE);
        int created=0;
        for(Map<String,Object> window:mapper.selectDueScheduleWindows(now,staleBefore,limit))
        {
            long windowId=requiredLong(window,"windowId","window_id");
            long planId=requiredLong(window,"planId","plan_id");
            int windowVersion=intValue(window,"version");
            if(mapper.claimScheduleWindow(windowId,windowVersion,now,staleBefore)!=1) continue;
            int claimedWindowVersion=windowVersion+1;
            String code=text(window,"windowCode","window_code");
            int occurrenceNo=intValue(window,"occurrenceNo","occurrence_no");
            String occurrenceKey=planId+":"+code+":"+occurrenceNo;
            Map<String,Object> occurrence=new HashMap<>();
            occurrence.put("planId",planId);occurrence.put("windowId",windowId);
            occurrence.put("windowCode",code);occurrence.put("occurrenceNo",occurrenceNo);
            occurrence.put("occurrenceKey",occurrenceKey);occurrence.put("dueAt",dateTime(window,"dueAt","due_at"));
            occurrence.put("status","RETRY");
            int inserted=mapper.insertScheduleOccurrenceIfAbsent(occurrence);
            Map<String,Object> persisted=inserted==1?occurrence:mapper.selectScheduleOccurrenceByKey(occurrenceKey);
            if(persisted==null)
            {
                mapper.retryScheduleWindow(windowId,claimedWindowVersion,"TODO_SCHEDULE_OCCURRENCE_MISSING",now);
                continue;
            }
            long occurrenceId=requiredLong(persisted,"occurrenceId","occurrence_id");
            int occurrenceVersion=intValue(persisted,"version");
            if(mapper.claimScheduleOccurrence(occurrenceId,occurrenceVersion,now,staleBefore)!=1)
            {
                if("MATERIALIZED".equals(text(persisted,"status","status")))
                    mapper.completeScheduleWindow(windowId,claimedWindowVersion,now);
                else mapper.retryScheduleWindow(windowId,claimedWindowVersion,"TODO_SCHEDULE_OCCURRENCE_BUSY",now);
                continue;
            }
            try
            {
                Long previousTodoId=requiredLong(window,"previousTodoId","previous_todo_id");
                TodoInstance previous=mapper.selectById(previousTodoId);
                if(previous==null)
                    throw new TodoException("TODO_SCHEDULE_PREVIOUS_NOT_FOUND","Previous Todo no longer exists");
                Long templateVersionId=requiredLong(window,"templateVersionId","template_version_id");
                LocalDateTime dueAt=dateTime(window,"dueAt","due_at");
                TodoInstance todo=routing.createScheduledNext(previous,templateVersionId,occurrenceKey,dueAt);
                if(todo==null||todo.getTodoId()==null)
                    throw new TodoException("TODO_SCHEDULE_TODO_ID_MISSING","Scheduled Todo identity was not generated");
                if(mapper.completeScheduleOccurrence(occurrenceId,todo.getTodoId(),now)!=1)
                    throw new TodoException("TODO_SCHEDULE_OCCURRENCE_CHANGED","Schedule occurrence changed concurrently");
                mapper.completeScheduleWindow(windowId,claimedWindowVersion,now);
                mapper.updateSchedulePlanWindow(planId,code,now);
                created++;
            }
            catch(RuntimeException failure)
            {
                String errorCode=errorCode(failure);
                mapper.retryScheduleOccurrence(occurrenceId,occurrenceVersion+1,errorCode,
                        truncate(failure.getMessage(),1000),now);
                mapper.retryScheduleWindow(windowId,claimedWindowVersion,errorCode,now);
            }
        }
        return created;
    }

    @Transactional
    public ScheduleOccurrenceContext lockOccurrenceContext(Long occurrenceId)
    {
        if(occurrenceId==null||occurrenceId<=0)
            throw new TodoException("TODO_SCHEDULE_RESULT_INVALID","Schedule occurrence identity is incomplete");
        Map<String,Object> identity=mapper.selectScheduleOccurrenceIdentity(occurrenceId);
        if(identity==null)
            throw new TodoException("TODO_SCHEDULE_OCCURRENCE_NOT_FOUND","Schedule occurrence does not exist");
        Long planId=requiredLong(identity,"planId","plan_id");
        Map<String,Object> plan=mapper.selectSchedulePlanForUpdate(planId);
        if(plan==null)
            throw new TodoException("TODO_SCHEDULE_PLAN_NOT_FOUND","Schedule plan does not exist");
        Map<String,Object> occurrence=mapper.selectScheduleOccurrenceContextForUpdate(occurrenceId);
        if(occurrence==null)
            throw new TodoException("TODO_SCHEDULE_OCCURRENCE_NOT_FOUND","Schedule occurrence does not exist");
        ScheduleOccurrenceContext context=context(occurrence);
        if(!planId.equals(context.planId())
                ||requiredLong(identity,"occurrenceId","occurrence_id")!=context.occurrenceId()
                ||requiredLong(identity,"windowId","window_id")!=context.windowId()
                ||!planId.equals(longValue(plan,"planId","plan_id"))
                ||!java.util.Objects.equals(longValue(plan,"assignmentPolicyId",
                        "assignment_policy_id"),context.assignmentPolicyId())
                ||!java.util.Objects.equals(integerValue(plan,"assignmentPolicyVersion",
                        "assignment_policy_version"),context.assignmentPolicyVersion())
                ||!java.util.Objects.equals(text(plan,"assignmentPolicySnapshotSource",
                        "assignment_policy_snapshot_source"),
                        context.assignmentPolicySnapshotSource())
                ||!java.util.Objects.equals(text(plan,"status","status"),context.planStatus()))
            throw new TodoException("TODO_SCHEDULE_OCCURRENCE_CHANGED",
                    "Schedule occurrence changed while acquiring its plan-first fence");
        return context;
    }

    @Transactional
    public ScheduleCompletion completeOccurrence(Long occurrenceId,String result,LocalDateTime completedAt)
    {
        return completeLockedOccurrence(lockOccurrenceContext(occurrenceId),result,completedAt);
    }

    @Transactional
    public ScheduleCompletion completeOccurrence(ScheduleOccurrenceContext context,String result,
            LocalDateTime completedAt)
    {
        if(context==null||result==null||result.isBlank()||completedAt==null)
            throw new TodoException("TODO_SCHEDULE_RESULT_INVALID","Schedule occurrence result is incomplete");
        ScheduleOccurrenceContext authoritative=lockOccurrenceContext(context.occurrenceId());
        if(!authoritative.equals(context))
            throw new TodoException("TODO_SCHEDULE_RESULT_NOT_ACCEPTED",
                    "Schedule occurrence context changed before completion");
        return completeLockedOccurrence(authoritative,result,completedAt);
    }

    /**
     * Completes an unsuccessful occurrence only after its authoritative attempt limit is reached.
     * The schedule, never the client, derives whether a later configured window exists.
     */
    @Transactional
    public ScheduleCompletion completeAttemptLimit(ScheduleOccurrenceContext context,
            LocalDateTime completedAt)
    {
        if(context==null||completedAt==null)
            throw new TodoException("TODO_SCHEDULE_RESULT_INVALID","Schedule occurrence result is incomplete");
        ScheduleOccurrenceContext authoritative=lockOccurrenceContext(context.occurrenceId());
        if(!authoritative.equals(context))
            throw new TodoException("TODO_SCHEDULE_RESULT_NOT_ACCEPTED",
                    "Schedule occurrence context changed before completion");
        Map<String,Object> next=mapper.selectNextScheduleWindow(authoritative.planId(),
                authoritative.windowId());
        return completeLockedOccurrence(authoritative,next==null?"EXHAUSTED":"NEXT_WINDOW",completedAt);
    }

    private ScheduleCompletion completeLockedOccurrence(ScheduleOccurrenceContext context,String result,
            LocalDateTime completedAt)
    {
        if(!"ACTIVE".equals(context.planStatus())
                && !("COMPLETED".equals(context.status())&&result.equals(context.resultCode())))
            throw new TodoException("TODO_SCHEDULE_RESULT_NOT_ACCEPTED","Schedule plan is not active");
        int accepted=mapper.recordScheduleOccurrenceResult(context.occurrenceId(),result,completedAt);
        boolean replayed=accepted!=1;
        if(accepted!=1)
        {
            ScheduleOccurrenceContext current=lockOccurrenceContext(context.occurrenceId());
            if(!"COMPLETED".equals(current.status())||!result.equals(current.resultCode()))
                throw new TodoException("TODO_SCHEDULE_RESULT_NOT_ACCEPTED",
                        "Schedule occurrence is not materialized or already has another result");
            context=current;
        }
        if("CONNECTED".equals(result))
            terminateLockedPlan(context.planId(),context.occurrenceId(),"CONTACTED",completedAt);
        Map<String,Object> next=null;
        if("NEXT_WINDOW".equals(result))
        {
            next=mapper.selectNextScheduleWindow(context.planId(),context.windowId());
            if(next==null)
                throw new TodoException("TODO_SCHEDULE_NEXT_WINDOW_NOT_FOUND",
                        "Retry result requires a later configured window");
        }
        return new ScheduleCompletion(context.planId(),context.windowCode(),context.occurrenceNo(),
                next==null?null:text(next,"windowCode","window_code"),
                next==null?null:dateTime(next,"startAt","start_at"),replayed,
                context.businessType(),context.businessId(),context.todoId(),context.timezone(),
                context.templateVersionId(),context.ruleVersionId(),context.assignmentPolicyId(),
                context.assignmentPolicyVersion(),context.assignmentPolicySnapshotSource(),
                context.maxAttempts());
    }

    @Transactional
    public void cancelPlan(Long planId,String reason,LocalDateTime cancelledAt)
    {
        if(planId==null||planId<=0||reason==null||reason.isBlank()||cancelledAt==null)
            throw new TodoException("TODO_SCHEDULE_CANCEL_INVALID","Schedule cancellation is incomplete");
        if(mapper.selectSchedulePlanForUpdate(planId)==null)
            throw new TodoException("TODO_SCHEDULE_PLAN_NOT_FOUND","Schedule plan does not exist");
        terminateLockedPlan(planId,null,reason,cancelledAt);
    }

    private void terminateLockedPlan(Long planId,Long exceptOccurrenceId,String reason,
            LocalDateTime cancelledAt)
    {
        mapper.completeSchedulePlan(planId,reason,cancelledAt);
        cancelActiveLinkedTodos(planId,exceptOccurrenceId,reason);
        mapper.cancelFutureScheduleWindows(planId,exceptOccurrenceId,reason,cancelledAt);
        mapper.cancelFutureScheduleOccurrences(planId,exceptOccurrenceId,reason,cancelledAt);
    }

    private void cancelActiveLinkedTodos(Long planId,Long exceptOccurrenceId,String reason)
    {
        for(Map<String,Object> linked:mapper.selectActiveLinkedScheduleTodosForUpdate(planId,exceptOccurrenceId))
        {
            Long todoId=requiredLong(linked,"todoId","todo_id");
            String fromStatus=text(linked,"status","status");
            if(fromStatus==null||mapper.updateStatusConditionally(todoId,fromStatus,"CANCELLED",null,
                    TodoAutoActionService.SERVICE_ACTOR.userName())!=1)
                throw new TodoException("TODO_SCHEDULE_TODO_CANCEL_CONFLICT",
                        "Linked scheduled Todo changed during plan cancellation");
            Map<String,Object> action=new HashMap<>();
            String actionId="SCHEDULE:CANCEL:"+planId+":"+todoId;
            action.put("todoId",todoId);action.put("actionId",actionId);
            action.put("actionType","CANCEL");action.put("actionSource","SYSTEM");
            action.put("fromStatus",fromStatus);action.put("toStatus","CANCELLED");
            action.put("operatorId",TodoAutoActionService.SERVICE_ACTOR.userId());
            action.put("operatorName",TodoAutoActionService.SERVICE_ACTOR.userName());
            action.put("opinion","MATERIALIZED_SCHEDULE_CANCELLED");
            action.put("payloadJson",JSON.toJSONString(Map.of("planId",planId,"reason",reason)));
            if(mapper.insertActionIfAbsent(action)<=0&&mapper.selectActionById(actionId)==null)
                throw new TodoException("TODO_ACTION_LOG_FAILED","Todo action audit failed");
        }
    }

    private void validate(CreateSchedulePlanCommand command)
    {
        if(command==null||command.previousTodoId()==null||command.previousTodoId()<=0
                ||command.templateVersionId()==null||command.templateVersionId()<=0
                ||command.businessType()==null||command.businessType().isBlank()
                ||command.businessId()==null||command.businessId()<=0
                ||command.ruleVersionId()==null||command.ruleVersionId()<=0
                ||command.assignmentPolicyId()==null||command.assignmentPolicyId()<=0
                ||command.assignmentPolicyVersion()==null||command.assignmentPolicyVersion()<0
                ||command.firstContactCompletedAt()==null||command.purpose()==null
                ||command.idempotencyKey()==null||command.idempotencyKey().isBlank()
                ||command.idempotencyKey().trim().length()>192)
            throw new TodoException("TODO_SCHEDULE_PLAN_INVALID","Schedule plan identity is incomplete");
    }

    private void validateTemplate(Long templateVersionId,String businessType,SchedulePurpose purpose)
    {
        Map<String,Object> template=mapper.selectTemplateVersionById(templateVersionId);
        if(template==null||!"PUBLISHED".equals(text(template,"status","status"))
                ||!purpose.requiredTemplateCode().equals(
                        text(template,"templateCode","template_code"))
                ||!businessType.trim().equals(text(template,"businessType","business_type")))
            throw new TodoException("TODO_SCHEDULE_TEMPLATE_INVALID",
                    "Schedule purpose requires its published template version for the same business type");
    }

    private long requireMatchingPlan(CreateSchedulePlanCommand command,String timezone,
            List<TodoScheduleWindow> windows,Map<String,Object> persisted)
    {
        try
        {
            Long planId=longValue(persisted,"planId","plan_id");
            boolean matches=planId!=null
                    &&Objects.equals(command.previousTodoId(),longValue(persisted,
                            "previousTodoId","previous_todo_id"))
                    &&Objects.equals(command.templateVersionId(),longValue(persisted,
                            "templateVersionId","template_version_id"))
                    &&command.businessType().trim().equals(text(persisted,
                            "businessType","business_type"))
                    &&Objects.equals(command.businessId(),longValue(persisted,
                            "businessId","business_id"))
                    &&command.purpose().name().equals(text(persisted,
                            "schedulePurpose","schedule_purpose"))
                    &&command.idempotencyKey().trim().equals(text(persisted,
                            "idempotencyKey","idempotency_key"))
                    &&timezone.equals(text(persisted,"timezone","timezone"))
                    &&Objects.equals(command.ruleVersionId(),longValue(persisted,
                            "ruleVersionId","rule_version_id"))
                    &&Objects.equals(command.assignmentPolicyId(),longValue(persisted,
                            "assignmentPolicyId","assignment_policy_id"))
                    &&Objects.equals(command.assignmentPolicyVersion(),integerValue(persisted,
                            "assignmentPolicyVersion","assignment_policy_version"))
                    &&RESOLVED_POLICY.equals(text(persisted,
                            "assignmentPolicySnapshotSource","assignment_policy_snapshot_source"))
                    &&Objects.equals(command.firstContactCompletedAt(),dateTimeValue(persisted,
                            "firstContactAt","first_contact_at"))
                    &&matchingWindows(windows,mapper.selectScheduleWindowsByPlanIdForUpdate(planId));
            if(matches)return planId;
        }
        catch(NumberFormatException|java.time.DateTimeException malformed) { }
        throw new TodoException("TODO_SCHEDULE_IDEMPOTENCY_CONFLICT",
                "Schedule idempotency key belongs to another immutable plan");
    }

    private long requireMatchingPlanForUpdate(CreateSchedulePlanCommand command,String timezone,
            List<TodoScheduleWindow> windows)
    {
        Map<String,Object> persisted=mapper.selectSchedulePlanByIdempotencyKeyForUpdate(
                command.idempotencyKey().trim());
        if(persisted==null||persisted.isEmpty())
            throw new TodoException("TODO_SCHEDULE_IDEMPOTENCY_CONFLICT",
                    "Schedule idempotency plan disappeared during replay verification");
        return requireMatchingPlan(command,timezone,windows,persisted);
    }

    private boolean matchingWindows(List<TodoScheduleWindow> expected,List<Map<String,Object>> persisted)
    {
        if(persisted==null||expected.size()!=persisted.size())return false;
        for(int index=0;index<expected.size();index++)
        {
            TodoScheduleWindow window=expected.get(index);
            Map<String,Object> row=persisted.get(index);
            if(!window.windowCode().equals(text(row,"windowCode","window_code"))
                    ||window.windowOrder()!=intValue(row,"windowOrder","window_order")
                    ||window.dayOffset()!=intValue(row,"dayOffset","day_offset")
                    ||!Objects.equals(window.startTime(),timeValue(row,"startTime","start_time"))
                    ||!Objects.equals(window.endTime(),timeValue(row,"endTime","end_time"))
                    ||!Objects.equals(window.startAt(),dateTimeValue(row,
                            "materializeAt","materialize_at"))
                    ||!Objects.equals(window.dueAt(),dateTimeValue(row,"dueAt","due_at"))
                    ||window.maxAttempts()!=intValue(row,"maxAttempts","max_attempts")
                    ||window.occurrenceNo()!=intValue(row,"occurrenceNo","occurrence_no"))
                return false;
        }
        return true;
    }

    private ScheduleOccurrenceContext context(Map<String,Object> value)
    {
        ScheduleOccurrenceContext context=new ScheduleOccurrenceContext(
                requiredLong(value,"occurrenceId","occurrence_id"),
                requiredLong(value,"planId","plan_id"),requiredLong(value,"windowId","window_id"),
                text(value,"windowCode","window_code"),intValue(value,"occurrenceNo","occurrence_no"),
                longValue(value,"todoId","todo_id"),text(value,"businessType","business_type"),
                requiredLong(value,"businessId","business_id"),text(value,"timezone","timezone"),
                requiredLong(value,"templateVersionId","template_version_id"),
                longValue(value,"ruleVersionId","rule_version_id"),
                longValue(value,"assignmentPolicyId","assignment_policy_id"),
                integerValue(value,"assignmentPolicyVersion","assignment_policy_version"),
                text(value,"assignmentPolicySnapshotSource",
                        "assignment_policy_snapshot_source"),
                intValue(value,"maxAttempts","max_attempts"),
                text(value,"status","status"),text(value,"resultCode","result_code"),
                text(value,"planStatus","plan_status"));
        if(!context.hasValidAssignmentPolicySnapshot())
            throw new TodoException("TODO_SCHEDULE_POLICY_SNAPSHOT_INVALID",
                    "Schedule assignment-policy provenance is inconsistent");
        return context;
    }

    private String errorCode(RuntimeException failure)
    {
        return failure instanceof TodoException todo?todo.getBusinessCode():"TODO_SCHEDULE_TODO_CREATE_FAILED";
    }

    private String truncate(String value,int length)
    {
        return value==null?null:value.substring(0,Math.min(value.length(),length));
    }

    private long requiredLong(Map<String,Object> row,String camel,String snake)
    {
        Long value=longValue(row,camel,snake);
        if(value==null) throw new TodoException("TODO_SCHEDULE_ROW_INVALID","Schedule row is missing "+camel);
        return value;
    }

    private Long longValue(Map<String,Object> row,String camel,String snake)
    {
        Object value=row.containsKey(camel)?row.get(camel):row.get(snake);
        return value==null?null:value instanceof Number number?number.longValue():Long.valueOf(String.valueOf(value));
    }

    private Integer integerValue(Map<String,Object> row,String camel,String snake)
    {
        Object value=row.containsKey(camel)?row.get(camel):row.get(snake);
        return value==null?null:value instanceof Number number?number.intValue()
                :Integer.valueOf(String.valueOf(value));
    }

    private int intValue(Map<String,Object> row,String camel,String... snake)
    {
        Object value=row.get(camel);
        if(value==null&&snake.length>0)value=row.get(snake[0]);
        return value==null?0:value instanceof Number number?number.intValue():Integer.parseInt(String.valueOf(value));
    }

    private String text(Map<String,Object> row,String camel,String snake)
    {
        Object value=row.containsKey(camel)?row.get(camel):row.get(snake);
        return value==null?null:String.valueOf(value);
    }

    private LocalDateTime dateTime(Map<String,Object> row,String camel,String snake)
    {
        Object value=row.containsKey(camel)?row.get(camel):row.get(snake);
        if(value instanceof LocalDateTime dateTime)return dateTime;
        if(value!=null)return LocalDateTime.parse(String.valueOf(value).replace(' ','T'));
        throw new TodoException("TODO_SCHEDULE_ROW_INVALID","Schedule row is missing "+camel);
    }

    private LocalDateTime dateTimeValue(Map<String,Object> row,String camel,String snake)
    {
        Object value=row.containsKey(camel)?row.get(camel):row.get(snake);
        if(value==null)return null;
        if(value instanceof LocalDateTime dateTime)return dateTime;
        if(value instanceof java.sql.Timestamp timestamp)return timestamp.toLocalDateTime();
        return LocalDateTime.parse(String.valueOf(value).replace(' ','T'));
    }

    private LocalTime timeValue(Map<String,Object> row,String camel,String snake)
    {
        Object value=row.containsKey(camel)?row.get(camel):row.get(snake);
        if(value==null)return null;
        if(value instanceof LocalTime time)return time;
        if(value instanceof java.sql.Time time)return time.toLocalTime();
        return LocalTime.parse(String.valueOf(value));
    }

    public record ScheduleOccurrenceContext(Long occurrenceId,Long planId,Long windowId,String windowCode,
            int occurrenceNo,Long todoId,String businessType,Long businessId,String timezone,
            Long templateVersionId,Long ruleVersionId,Long assignmentPolicyId,
            Integer assignmentPolicyVersion,String assignmentPolicySnapshotSource,int maxAttempts,
            String status,String resultCode,String planStatus)
    {
        public ScheduleOccurrenceContext(Long occurrenceId,Long planId,Long windowId,String windowCode,
                int occurrenceNo,Long todoId,String businessType,Long businessId,String timezone,
                Long templateVersionId,Long ruleVersionId,Long assignmentPolicyId,
                int assignmentPolicyVersion,int maxAttempts,String status,String resultCode,
                String planStatus)
        {
            this(occurrenceId,planId,windowId,windowCode,occurrenceNo,todoId,businessType,businessId,
                    timezone,templateVersionId,ruleVersionId,assignmentPolicyId,
                    assignmentPolicyVersion,RESOLVED_POLICY,maxAttempts,status,resultCode,planStatus);
        }

        public ScheduleOccurrenceContext(Long occurrenceId,Long planId,Long windowId,String windowCode,
                int occurrenceNo,Long todoId,String businessType,Long businessId,String timezone,
                Long templateVersionId,Long ruleVersionId,int maxAttempts,String status,String resultCode)
        {
            this(occurrenceId,planId,windowId,windowCode,occurrenceNo,todoId,businessType,businessId,
                    timezone,templateVersionId,ruleVersionId,1L,0,RESOLVED_POLICY,maxAttempts,
                    status,resultCode,"ACTIVE");
        }

        public boolean hasValidAssignmentPolicySnapshot()
        {
            return RESOLVED_POLICY.equals(assignmentPolicySnapshotSource)
                    ?assignmentPolicyId!=null&&assignmentPolicyId>0
                            &&assignmentPolicyVersion!=null&&assignmentPolicyVersion>=0
                    :LEGACY_POLICY.equals(assignmentPolicySnapshotSource)
                            &&assignmentPolicyId==null&&assignmentPolicyVersion==null;
        }
    }

    public record ScheduleCompletion(Long planId,String windowCode,int occurrenceNo,
            String nextWindowCode,LocalDateTime nextStartAt,boolean replayed,String businessType,
            Long businessId,Long todoId,String timezone,Long templateVersionId,Long ruleVersionId,
            Long assignmentPolicyId,Integer assignmentPolicyVersion,
            String assignmentPolicySnapshotSource,int maxAttempts)
    {
        public ScheduleCompletion(Long planId,String windowCode,int occurrenceNo,
                String nextWindowCode,LocalDateTime nextStartAt,boolean replayed,String businessType,
                Long businessId,Long todoId,String timezone,Long templateVersionId,Long ruleVersionId,
                Long assignmentPolicyId,int assignmentPolicyVersion,int maxAttempts)
        {
            this(planId,windowCode,occurrenceNo,nextWindowCode,nextStartAt,replayed,businessType,
                    businessId,todoId,timezone,templateVersionId,ruleVersionId,assignmentPolicyId,
                    assignmentPolicyVersion,RESOLVED_POLICY,maxAttempts);
        }

        public ScheduleCompletion(Long planId,String windowCode,int occurrenceNo,
                String nextWindowCode,LocalDateTime nextStartAt,boolean replayed)
        {
            this(planId,windowCode,occurrenceNo,nextWindowCode,nextStartAt,replayed,
                    null,null,null,null,null,null,null,null,null,0);
        }
    }

    public record ScheduleWindowRule(String windowCode,int windowOrder,int dayOffset,
            LocalTime startTime,LocalTime endTime,Integer startOffsetMinutes,Integer durationMinutes,
            int maxAttempts,int occurrenceNo) { }

    public enum SlaTimingMode { SCALAR,WINDOWS }

    public record SlaTimingConfiguration(SlaTimingMode mode,Long minutes,
            List<ScheduleWindowRule> windows)
    {
        public SlaTimingConfiguration
        {
            windows=windows==null?List.of():List.copyOf(windows);
        }
    }

    public enum SchedulePurpose
    {
        LEAD_RETRY("TD-003"),
        LEAD_PROGRESS_5D("TD-004");

        private final String requiredTemplateCode;

        SchedulePurpose(String requiredTemplateCode)
        {
            this.requiredTemplateCode=requiredTemplateCode;
        }

        public String requiredTemplateCode()
        {
            return requiredTemplateCode;
        }
    }

    public record CreateSchedulePlanCommand(
            Long previousTodoId,
            Long templateVersionId,
            String businessType,
            Long businessId,
            LocalDateTime firstContactCompletedAt,
            String timezone,
            Long ruleVersionId,
            Long assignmentPolicyId,
            Integer assignmentPolicyVersion,
            List<ScheduleWindowRule> windows,
            SchedulePurpose purpose,
            String idempotencyKey)
    {
        public CreateSchedulePlanCommand(Long previousTodoId,Long templateVersionId,String businessType,
                Long businessId,LocalDateTime firstContactCompletedAt,String timezone,Long ruleVersionId,
                Long assignmentPolicyId,Integer assignmentPolicyVersion,List<ScheduleWindowRule> windows)
        {
            this(previousTodoId,templateVersionId,businessType,businessId,firstContactCompletedAt,
                    timezone,ruleVersionId,assignmentPolicyId,assignmentPolicyVersion,windows,
                    SchedulePurpose.LEAD_RETRY,
                    "LEAD_RETRY:"+businessId+":"+previousTodoId);
        }

        public CreateSchedulePlanCommand(Long previousTodoId,Long templateVersionId,String businessType,
                Long businessId,LocalDateTime firstContactCompletedAt,String timezone,Long ruleVersionId,
                Long assignmentPolicyId,Integer assignmentPolicyVersion)
        {
            this(previousTodoId,templateVersionId,businessType,businessId,firstContactCompletedAt,
                    timezone,ruleVersionId,assignmentPolicyId,assignmentPolicyVersion,List.of());
        }
    }
}
