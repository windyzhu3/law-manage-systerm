package com.law.todo.schedule;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public static final Duration CLAIM_LEASE=Duration.ofMinutes(5);

    private final TodoMapper mapper;
    private final TodoRoutingService routing;

    public TodoScheduleService(TodoMapper mapper,TodoRoutingService routing)
    {
        this.mapper=mapper;this.routing=routing;
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
        Map<String,Object> plan=new HashMap<>();
        plan.put("previousTodoId",command.previousTodoId());
        plan.put("templateVersionId",command.templateVersionId());
        plan.put("businessType",command.businessType().trim());
        plan.put("businessId",command.businessId());
        plan.put("timezone",timezone);
        plan.put("ruleVersionId",command.ruleVersionId());
        plan.put("firstContactAt",command.firstContactCompletedAt());
        plan.put("status","ACTIVE");
        mapper.insertSchedulePlan(plan);
        Long planId=longValue(plan,"planId","plan_id");
        if(planId==null)
            throw new TodoException("TODO_SCHEDULE_PLAN_PERSIST_FAILED","Schedule plan identity was not generated");
        for(TodoScheduleWindow window:defaultWindows(command.firstContactCompletedAt()))
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
    public void completeOccurrence(Long occurrenceId,String result,LocalDateTime completedAt)
    {
        if(occurrenceId==null||occurrenceId<=0||result==null||result.isBlank()||completedAt==null)
            throw new TodoException("TODO_SCHEDULE_RESULT_INVALID","Schedule occurrence result is incomplete");
        Map<String,Object> occurrence=mapper.selectScheduleOccurrenceById(occurrenceId);
        if(occurrence==null)
            throw new TodoException("TODO_SCHEDULE_OCCURRENCE_NOT_FOUND","Schedule occurrence does not exist");
        Long planId=requiredLong(occurrence,"planId","plan_id");
        if("CONNECTED".equals(result))
        {
            if(mapper.selectSchedulePlanForUpdate(planId)==null)
                throw new TodoException("TODO_SCHEDULE_PLAN_NOT_FOUND","Schedule plan does not exist");
        }
        int accepted=mapper.recordScheduleOccurrenceResult(occurrenceId,result,completedAt);
        if(accepted!=1)
        {
            Map<String,Object> current=mapper.selectScheduleOccurrenceById(occurrenceId);
            if(current==null||!"COMPLETED".equals(text(current,"status","status"))
                    ||!result.equals(text(current,"resultCode","result_code")))
                throw new TodoException("TODO_SCHEDULE_RESULT_NOT_ACCEPTED",
                        "Schedule occurrence is not materialized or already has another result");
            occurrence=current;
        }
        if("CONNECTED".equals(result))
            terminateLockedPlan(planId,occurrenceId,"CONTACTED",completedAt);
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
                ||command.firstContactCompletedAt()==null)
            throw new TodoException("TODO_SCHEDULE_PLAN_INVALID","Schedule plan identity is incomplete");
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

    public record CreateSchedulePlanCommand(
            Long previousTodoId,
            Long templateVersionId,
            String businessType,
            Long businessId,
            LocalDateTime firstContactCompletedAt,
            String timezone,
            Long ruleVersionId) { }
}
