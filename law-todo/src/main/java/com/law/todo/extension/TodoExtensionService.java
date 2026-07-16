package com.law.todo.extension;

import static com.law.todo.extension.TodoExtensionCommands.ExtensionStatus.APPROVED;
import static com.law.todo.extension.TodoExtensionCommands.ExtensionStatus.PENDING;
import static com.law.todo.extension.TodoExtensionCommands.ExtensionStatus.REJECTED;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.alibaba.fastjson2.JSON;
import com.law.todo.application.TodoSlaService;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.service.WorkingTimeCalculator;
import com.law.todo.domain.service.WorkingTimeCalculator.WorkCalendar;
import com.law.todo.extension.TodoExtensionCommands.DecisionCommand;
import com.law.todo.extension.TodoExtensionCommands.DurationPolicy;
import com.law.todo.extension.TodoExtensionCommands.DurationUnit;
import com.law.todo.extension.TodoExtensionCommands.ExtensionStatus;
import com.law.todo.extension.TodoExtensionCommands.ExtensionView;
import com.law.todo.extension.TodoExtensionCommands.PendingSlaMode;
import com.law.todo.extension.TodoExtensionCommands.RequestCommand;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.notification.StationNotificationAdapter;
import com.law.todo.notification.TodoNotificationPort;
import com.law.todo.notification.TodoNotificationPort.NotificationCommand;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TodoExtensionService
{
    private final TodoMapper mapper;
    private final TodoNotificationPort notifications;

    public TodoExtensionService(TodoMapper mapper){this(mapper,new StationNotificationAdapter(mapper));}
    @Autowired public TodoExtensionService(TodoMapper mapper,TodoNotificationPort notifications)
    {this.mapper=mapper;this.notifications=notifications;}

    @Transactional public ExtensionView request(Long todoId,RequestCommand command,Actor actor)
    {
        requireAction(command==null?null:command.actionId());requireActor(actor);requireProofIds(command.proofFileObjectIds());
        Map<String,Object> claimed=claim(command.actionId(),"REQUEST",todoId,null,actor);if(present(claimed))return replayRequest(todoId,claimed);
        Map<String,Object> context=mapper.selectExtensionContext(todoId);requireContext(todoId,context,actor);
        Policy policy=policy(context);LocalDateTime current=date(value(context,"due_at","dueAt"));
        requireLater(current,command.requestedDueAt());
        if(policy.proofRequired()&&command.proofFileObjectIds().isEmpty())fail("TODO_EXTENSION_PROOF_REQUIRED","Proof material is required");
        WorkCalendar calendar=calendar(context);
        LocalDateTime maximum=new TodoSlaService(mapper,null).addDuration(current,
            new DurationPolicy(policy.versionId(),policy.maxValue(),policy.maxUnit()),calendar);
        if(command.requestedDueAt().isAfter(maximum))fail("TODO_EXTENSION_DURATION_EXCEEDED","Requested due time exceeds the configured maximum");
        if(mapper.countApprovedExtensions(todoId,policy.versionId())>=policy.maxCount())
            fail("TODO_EXTENSION_COUNT_EXCEEDED","Configured extension count has been exhausted");
        Map<String,Object> row=new HashMap<>();row.put("todoId",todoId);row.put("requestActionId",command.actionId());
        row.put("originalDueAt",current);row.put("requestedDueAt",command.requestedDueAt());row.put("reason",command.reason());
        row.put("proofFileIdsJson",JSON.toJSONString(command.proofFileObjectIds()));row.put("requesterId",actor.userId());
        row.put("requesterName",actor.userName());row.put("requesterDeptId",actor.deptId());row.put("policyVersionId",policy.versionId());
        row.put("pendingSlaMode",policy.pendingMode().name());
        try
        {
            if(mapper.insertExtensionRequest(row)<=0)pendingConflict(todoId);
        }
        catch(DuplicateKeyException ex){pendingConflict(todoId);}
        Long extensionId=number(row.get("extensionId"));Map<String,Object> saved=mapper.selectExtensionById(extensionId);
        if(!present(saved))fail("TODO_EXTENSION_WRITE_FAILED","Extension request was not persisted");
        completeAction(command.actionId(),extensionId,PENDING.name());
        notifications.send(new NotificationCommand("extension:"+extensionId+":requested",todoId,actor.userId(),
            "EXTENSION_REQUESTED","Extension requested","Extension request is pending approval"));
        return view(saved);
    }

    @Transactional public ExtensionView approve(Long extensionId,DecisionCommand command,Actor actor)
    {return decide(extensionId,command,actor,APPROVED);}

    @Transactional public ExtensionView reject(Long extensionId,DecisionCommand command,Actor actor)
    {return decide(extensionId,command,actor,REJECTED);}

    private ExtensionView decide(Long extensionId,DecisionCommand command,Actor actor,ExtensionStatus target)
    {
        requireAction(command==null?null:command.actionId());requireActor(actor);
        String actionType=target==APPROVED?"APPROVE":"REJECT";
        Map<String,Object> current=mapper.selectExtensionById(extensionId);if(!present(current))fail("TODO_EXTENSION_NOT_FOUND","Extension request does not exist");
        Long todoId=number(value(current,"todo_id","todoId"));Map<String,Object> claimed=claim(command.actionId(),actionType,todoId,extensionId,actor);
        if(present(claimed))return replayDecision(extensionId,actionType,claimed);
        if(status(current)!=PENDING)fail("TODO_EXTENSION_ALREADY_DECIDED","Extension request has already been decided");
        if(target==APPROVED)validateApprovalStillAllowed(current);
        Map<String,Object> decision=new HashMap<>();decision.put("extensionId",extensionId);decision.put("fromStatus",PENDING.name());
        decision.put("toStatus",target.name());decision.put("decisionActionId",command.actionId());decision.put("decisionReason",command.reason());
        decision.put("decisionActorId",actor.userId());decision.put("decisionActorName",actor.userName());decision.put("decisionActorDeptId",actor.deptId());
        if(target==APPROVED)decision.put("approvedDueAt",date(value(current,"requested_due_at","requestedDueAt")));
        if(mapper.decideExtensionConditionally(decision)<=0)
        {
            Map<String,Object> winner=mapper.selectExtensionById(extensionId);
            if(present(winner)&&status(winner)!=PENDING)fail("TODO_EXTENSION_ALREADY_DECIDED","Extension request has already been decided");
            fail("TODO_CONCURRENT_MODIFICATION","Extension request changed concurrently");
        }
        if(target==APPROVED)applyApproved(current);
        Map<String,Object> saved=mapper.selectExtensionById(extensionId);if(!present(saved))fail("TODO_EXTENSION_NOT_FOUND","Extension request does not exist");
        completeAction(command.actionId(),extensionId,target.name());
        Long recipient=number(value(saved,"requester_id","requesterId"));
        if(recipient!=null)notifications.send(new NotificationCommand("extension:"+extensionId+":"+target.name().toLowerCase(),todoId,recipient,
            "EXTENSION_"+target.name(),"Extension "+target.name().toLowerCase(),target==APPROVED?"Due date updated":"Due date unchanged"));
        return view(saved);
    }

    private void validateApprovalStillAllowed(Map<String,Object> extension)
    {
        LocalDateTime original=date(value(extension,"original_due_at","originalDueAt"));
        LocalDateTime effective=date(value(extension,"effective_due_at","effectiveDueAt"));if(effective==null)effective=original;
        LocalDateTime requested=date(value(extension,"requested_due_at","requestedDueAt"));requireLater(effective,requested);
        Policy policy=policy(extension);WorkCalendar calendar=calendar(extension);
        LocalDateTime maximum=new TodoSlaService(mapper,null).addDuration(effective,new DurationPolicy(policy.versionId(),policy.maxValue(),policy.maxUnit()),calendar);
        if(requested.isAfter(maximum))fail("TODO_EXTENSION_DURATION_EXCEEDED","Requested due time exceeds the configured maximum");
    }

    private void applyApproved(Map<String,Object> extension)
    {
        LocalDateTime start=date(value(extension,"start_at","startAt"));LocalDateTime original=date(value(extension,"original_due_at","originalDueAt"));
        LocalDateTime effective=date(value(extension,"effective_due_at","effectiveDueAt"));if(effective==null)effective=original;
        LocalDateTime requested=date(value(extension,"requested_due_at","requestedDueAt"));WorkCalendar calendar=calendar(extension);
        TodoSlaService.ThresholdPlan plan=new TodoSlaService(mapper,null).planThresholds(start,requested,calendar);
        Map<String,Object> update=new HashMap<>();update.put("todoId",number(value(extension,"todo_id","todoId")));
        update.put("expectedDueAt",effective);update.put("approvedDueAt",requested);
        update.put("remind80DueAt",fired(extension,"remind80")?null:plan.remind80DueAt());
        update.put("overdue100DueAt",fired(extension,"overdue100")?null:plan.overdue100DueAt());
        update.put("escalate150DueAt",fired(extension,"escalate150")?null:plan.escalate150DueAt());
        if(mapper.applyApprovedExtension(update)<=0)fail("TODO_CONCURRENT_MODIFICATION","SLA changed concurrently");
    }

    private boolean fired(Map<String,Object> row,String prefix){return value(row,prefix+"_at",prefix+"At")!=null;}
    private void requireContext(Long todoId,Map<String,Object> context,Actor actor)
    {
        if(!present(context))fail("TODO_NOT_FOUND","Todo or governed SLA policy does not exist");
        String todoStatus=String.valueOf(value(context,"todo_status","todoStatus"));
        if("COMPLETED".equals(todoStatus)||"CANCELLED".equals(todoStatus))fail("TODO_TERMINAL","Terminal Todo cannot be extended");
        Long owner=number(value(context,"owner_id","ownerId"));if(owner==null||!owner.equals(actor.userId()))fail("TODO_ACCESS_DENIED","Only the current owner can request an extension");
        if(number(value(context,"todo_id","todoId"))!=null&&!todoId.equals(number(value(context,"todo_id","todoId"))))fail("TODO_ACTION_ID_CONFLICT","Todo mismatch");
    }

    private Policy policy(Map<String,Object> row)
    {
        Long version=number(value(row,"policy_version_id","policyVersionId"));
        if(version==null||version<=0)fail("TODO_EXTENSION_POLICY_MISSING","A versioned extension policy is required");
        int count=integer(value(row,"max_extension_count","maxExtensionCount"));long max=longValue(value(row,"max_extension_value","maxExtensionValue"));
        if(count<0||max<=0)fail("TODO_EXTENSION_POLICY_INVALID","Extension policy limits are invalid");
        DurationUnit unit;try{unit=DurationUnit.valueOf(String.valueOf(value(row,"max_extension_unit","maxExtensionUnit")));}
        catch(Exception ex){throw new TodoException("TODO_EXTENSION_POLICY_INVALID","Extension duration unit is invalid");}
        PendingSlaMode pending;try{pending=PendingSlaMode.valueOf(String.valueOf(value(row,"pending_sla_mode","pendingSlaMode")));}
        catch(Exception ex){throw new TodoException("TODO_EXTENSION_POLICY_INVALID","Pending SLA mode must be CONTINUE");}
        return new Policy(version,count,max,unit,booleanValue(value(row,"proof_required","proofRequired")),pending);
    }

    private WorkCalendar calendar(Map<String,Object> row)
    {
        Set<DayOfWeek> days=EnumSet.noneOf(DayOfWeek.class);Object rawDays=value(row,"work_days","workDays");
        if(rawDays==null)fail("TODO_EXTENSION_CALENDAR_MISSING","Working calendar is required");
        for(String day:String.valueOf(rawDays).split(","))days.add(DayOfWeek.of(Integer.parseInt(day.trim())));
        Map<LocalDate,Boolean> exceptions=new HashMap<>();Object raw=value(row,"exception_json","exceptionJson");
        if(raw!=null&&!String.valueOf(raw).isBlank()&&!"null".equals(String.valueOf(raw)))
            for(Map.Entry<String,Object> item:JSON.parseObject(String.valueOf(raw)).entrySet())exceptions.put(LocalDate.parse(item.getKey()),Boolean.valueOf(String.valueOf(item.getValue())));
        return new WorkCalendar(days,time(value(row,"work_start","workStart")),time(value(row,"work_end","workEnd")),exceptions);
    }

    private ExtensionView view(Map<String,Object> row)
    {
        List<Long> proof=new ArrayList<>();Object raw=value(row,"proof_file_ids_json","proofFileIdsJson");
        if(raw!=null&&!String.valueOf(raw).isBlank())for(Object id:JSON.parseArray(String.valueOf(raw)))proof.add(Long.valueOf(String.valueOf(id)));
        Object pending=value(row,"pending_sla_mode","pendingSlaMode");
        return new ExtensionView(number(value(row,"extension_id","extensionId")),number(value(row,"todo_id","todoId")),status(row),
            date(value(row,"original_due_at","originalDueAt")),date(value(row,"requested_due_at","requestedDueAt")),date(value(row,"approved_due_at","approvedDueAt")),
            List.copyOf(proof),number(value(row,"requester_id","requesterId")),number(value(row,"decision_actor_id","decisionActorId")),
            string(value(row,"decision_reason","decisionReason")),number(value(row,"policy_version_id","policyVersionId")),
            pending==null?PendingSlaMode.CONTINUE:PendingSlaMode.valueOf(String.valueOf(pending)));
    }

    private Map<String,Object> claim(String actionId,String actionType,Long todoId,Long extensionId,Actor actor)
    {
        Map<String,Object> row=new HashMap<>();row.put("actionId",actionId);row.put("actionType",actionType);row.put("todoId",todoId);row.put("extensionId",extensionId);
        row.put("actorId",actor.userId());row.put("actorName",actor.userName());row.put("actorDeptId",actor.deptId());
        try{if(mapper.insertExtensionActionIfAbsent(row)>0)return null;}catch(DuplicateKeyException ignored){ }
        Map<String,Object> winner=mapper.selectExtensionActionForUpdate(actionId);if(!present(winner))fail("TODO_EXTENSION_ACTION_CONFLICT","Extension action could not be claimed");return winner;
    }
    private ExtensionView replayRequest(Long todoId,Map<String,Object> action)
    {
        if(!"REQUEST".equals(string(value(action,"action_type","actionType")))||!todoId.equals(number(value(action,"todo_id","todoId")))||!"APPLIED".equals(string(value(action,"action_status","actionStatus"))))
            fail("TODO_EXTENSION_ACTION_CONFLICT","actionId belongs to a different extension action");
        Map<String,Object> saved=mapper.selectExtensionById(number(value(action,"extension_id","extensionId")));if(!present(saved))fail("TODO_EXTENSION_ACTION_CONFLICT","Recorded extension result is missing");return view(saved);
    }
    private ExtensionView replayDecision(Long extensionId,String actionType,Map<String,Object> action)
    {
        if(!actionType.equals(string(value(action,"action_type","actionType")))||!extensionId.equals(number(value(action,"extension_id","extensionId")))||!"APPLIED".equals(string(value(action,"action_status","actionStatus"))))
            fail("TODO_EXTENSION_ACTION_CONFLICT","actionId belongs to a different extension decision");
        String expectedStatus="APPROVE".equals(actionType)?APPROVED.name():REJECTED.name();
        Map<String,Object> saved=mapper.selectExtensionByIdForUpdate(extensionId);if(!expectedStatus.equals(string(value(action,"result_status","resultStatus")))||!present(saved)||!expectedStatus.equals(status(saved).name()))fail("TODO_EXTENSION_ACTION_CONFLICT","Recorded decision result does not match");return view(saved);
    }
    private void completeAction(String actionId,Long extensionId,String resultStatus){Map<String,Object> row=new HashMap<>();row.put("actionId",actionId);row.put("extensionId",extensionId);row.put("resultStatus",resultStatus);if(mapper.completeExtensionAction(row)<=0)fail("TODO_EXTENSION_ACTION_CONFLICT","Extension action result could not be recorded");}
    private void pendingConflict(Long todoId){Map<String,Object> pending=mapper.selectPendingExtensionByTodoId(todoId);if(present(pending))fail("TODO_EXTENSION_PENDING_EXISTS","A pending extension request already exists");fail("TODO_EXTENSION_ACTION_CONFLICT","Extension request collided with another action");}
    private void requireProofIds(List<Long> ids){if(ids!=null&&ids.stream().anyMatch(id->id==null||id<=0))fail("TODO_EXTENSION_PROOF_INVALID","Proof fileObjectIds must be positive");}
    private void requireLater(LocalDateTime current,LocalDateTime requested){if(current==null||requested==null||!requested.isAfter(current))fail("TODO_EXTENSION_DUE_INVALID","Requested due time must be later than the current effective due time");}
    private void requireAction(String value){if(value==null||value.isBlank()||value.length()>64)fail("TODO_ACTION_ID_INVALID","actionId is required and limited to 64 characters");}
    private void requireActor(Actor actor){if(actor==null||actor.userId()==null||actor.userName()==null||actor.userName().isBlank())fail("TODO_ACTOR_INVALID","Actor is required");}
    private ExtensionStatus status(Map<String,Object> row){return ExtensionStatus.valueOf(String.valueOf(value(row,"status","status")));}
    private boolean present(Map<String,Object> row){return row!=null&&!row.isEmpty();}
    private Object value(Map<String,Object> row,String snake,String camel){if(row==null)return null;return row.containsKey(snake)?row.get(snake):row.get(camel);}
    private String string(Object value){return value==null?null:String.valueOf(value);}
    private Long number(Object value){return value==null?null:Long.valueOf(String.valueOf(value));}
    private int integer(Object value){return value==null?0:Integer.parseInt(String.valueOf(value));}
    private long longValue(Object value){return value==null?0:Long.parseLong(String.valueOf(value));}
    private boolean booleanValue(Object value){return value!=null&&Boolean.parseBoolean(String.valueOf(value));}
    private LocalDateTime date(Object value){return value instanceof LocalDateTime time?time:value==null?null:LocalDateTime.parse(String.valueOf(value).replace(' ','T'));}
    private LocalTime time(Object value){String text=String.valueOf(value);return LocalTime.parse(text.length()>=8?text.substring(0,8):text);}
    private void fail(String code,String message){throw new TodoException(code,message);}
    private record Policy(long versionId,int maxCount,long maxValue,DurationUnit maxUnit,boolean proofRequired,PendingSlaMode pendingMode) { }
}
