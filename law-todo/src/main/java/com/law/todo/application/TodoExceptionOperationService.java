package com.law.todo.application;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.alibaba.fastjson2.JSON;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoOperationCommands.BatchTransferCommand;
import com.law.todo.application.command.TodoOperationCommands.ForceCommand;
import com.law.todo.application.command.TodoOperationCommands.SlaWaiverCommand;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.TodoExceptionPolicy;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.spi.TodoCompletionHandler;
import com.law.todo.spi.TodoCompletionHandler.CompletionContext;

@Service
public class TodoExceptionOperationService
{
    private final TodoMapper mapper;private final TodoDodService dod;private final List<TodoCompletionHandler> handlers;private final TodoExceptionPolicy policy=new TodoExceptionPolicy();
    public TodoExceptionOperationService(TodoMapper mapper,TodoDodService dod,List<TodoCompletionHandler> handlers){this.mapper=mapper;this.dod=dod;this.handlers=handlers==null?List.of():handlers;}

    @Transactional public TodoInstance forceComplete(Long id,ForceCommand command,Actor actor)
    {
        if(repeated(id,command.actionId()))return mapper.selectById(id);
        TodoInstance todo=prepare(id,command.actionId());dod.validateBusiness(todo,command.payload());
        writeException(todo,command.actionId(),"FORCE_COMPLETE",command.reason(),command.payload(),actor);
        terminal(todo,"COMPLETED",actor);
        CompletionContext context=new CompletionContext(todo,command.payload(),
                actor.userId(),actor.userName(),false);
        for(TodoCompletionHandler handler:handlers)if(handler.supports(todo))handler.complete(context);
        return todo;
    }
    @Transactional public TodoInstance forceCancel(Long id,ForceCommand command,Actor actor)
    {
        if(repeated(id,command.actionId()))return mapper.selectById(id);
        TodoInstance todo=prepare(id,command.actionId());writeException(todo,command.actionId(),"FORCE_CANCEL",command.reason(),command.payload(),actor);terminal(todo,"CANCELLED",actor);return todo;
    }
    @Transactional public int batchTransfer(BatchTransferCommand command,Actor actor)
    {
        Long dept=mapper.selectUserDeptId(command.targetOwnerId());if(dept==null)throw new TodoException("TODO_TRANSFER_OWNER_INVALID","目标负责人不存在或已停用");int changed=0;
        for(Long id:command.todoIds()){String action=command.actionId()+":"+id;if(repeated(id,action)){changed++;continue;}TodoInstance todo=prepare(id,action);writeException(todo,action,"BATCH_TRANSFER",command.reason(),Map.of("targetOwnerId",command.targetOwnerId()),actor);if(mapper.transferOwnerConditionally(id,todo.getStatus(),command.targetOwnerId(),dept,actor.userName())<=0)concurrent();changed++;}return changed;
    }
    @Transactional public boolean waiveSla(Long id,SlaWaiverCommand command,Actor actor)
    {
        Map<String,Object> previous=mapper.selectSlaWaiverByActionId(command.actionId());if(previous!=null&&!previous.isEmpty()){requireSameTodo(id,previous);return true;}
        TodoInstance todo=prepare(id,command.actionId());Map<String,Object> record=mapper.selectSlaRecord(id);LocalDateTime original=date(value(record,"due_at","dueAt"));policy.requireExtension(original,command.newDueAt());
        Map<String,Object> waiver=base(todo,command.actionId(),"SLA_WAIVER",command.reason(),actor);waiver.put("originalDueAt",original);waiver.put("newDueAt",command.newDueAt());
        if(mapper.insertSlaWaiverIfAbsent(waiver)<=0&&mapper.selectSlaWaiverByActionId(command.actionId())==null)throw new TodoException("TODO_ACTION_LOG_FAILED","SLA 豁免记录失败");
        if(mapper.extendSlaConditionally(id,original,command.newDueAt())<=0)concurrent();return true;
    }
    @Transactional public TodoInstance regenerate(Long id,ForceCommand command,Actor actor)
    {
        Map<String,Object> prior=mapper.selectExceptionLogByActionId(command.actionId());
        if(prior!=null&&!prior.isEmpty()){Object recorded=value(prior,"todo_id","todoId");if(!id.equals(Long.valueOf(String.valueOf(recorded))))throw new TodoException("TODO_ACTION_ID_CONFLICT","动作幂等键已被其他待办使用");TodoInstance existing=mapper.selectByTriggerKey("REGENERATE:"+command.actionId());if(existing!=null)return existing;}
        TodoInstance source=mapper.selectById(id);if(source==null)throw new TodoException("TODO_NOT_FOUND","待办不存在");
        Map<String,Object> insert=new HashMap<>();insert.put("sourceTodoId",id);insert.put("actionId",command.actionId());insert.put("operator",actor.userName());
        if(mapper.insertRegeneratedTodo(insert)<=0)throw new TodoException("TODO_REGENERATE_FAILED","重新生成待办失败");
        writeException(source,command.actionId(),"REGENERATE",command.reason(),Map.of("newTodoId",insert.get("newTodoId")),actor);
        return mapper.selectById(Long.valueOf(String.valueOf(insert.get("newTodoId"))));
    }
    public List<Map<String,Object>> dashboard(){return mapper.selectOperationsDashboard();}

    private TodoInstance prepare(Long id,String actionId){Map<String,Object> prior=mapper.selectExceptionLogByActionId(actionId);if(prior!=null&&!prior.isEmpty()){Object recorded=value(prior,"todo_id","todoId");if(recorded!=null&&!id.equals(Long.valueOf(String.valueOf(recorded))))throw new TodoException("TODO_ACTION_ID_CONFLICT","动作幂等键已被其他待办使用");return mapper.selectById(id);}TodoInstance todo=mapper.selectById(id);policy.requireMutable(todo);return todo;}
    private boolean repeated(Long id,String actionId){Map<String,Object> prior=mapper.selectExceptionLogByActionId(actionId);if(prior==null||prior.isEmpty())return false;requireSameTodo(id,prior);return true;}
    private void requireSameTodo(Long id,Map<String,Object> prior){Object recorded=value(prior,"todo_id","todoId");if(recorded==null||!id.equals(Long.valueOf(String.valueOf(recorded))))throw new TodoException("TODO_ACTION_ID_CONFLICT","动作幂等键已被其他待办使用");}
    private void terminal(TodoInstance todo,String target,Actor actor){if(mapper.forceTerminalConditionally(todo.getTodoId(),todo.getStatus(),target,actor.userName())<=0)concurrent();todo.setStatus(target);}
    private void writeException(TodoInstance todo,String action,String type,String reason,Map<String,Object> payload,Actor actor){Map<String,Object> row=base(todo,action,type,reason,actor);row.put("fromStatus",todo.getStatus());row.put("payloadJson",JSON.toJSONString(payload));if(mapper.insertExceptionLogIfAbsent(row)<=0&&mapper.selectExceptionLogByActionId(action)==null)throw new TodoException("TODO_ACTION_LOG_FAILED","异常操作留痕失败");}
    private Map<String,Object> base(TodoInstance todo,String action,String type,String reason,Actor actor){Map<String,Object> row=new HashMap<>();row.put("todoId",todo.getTodoId());row.put("actionId",action);row.put("operationType",type);row.put("reason",reason);row.put("operatorId",actor.userId());row.put("operatorName",actor.userName());row.put("operatorDeptId",actor.deptId());return row;}
    private Object value(Map<String,Object> map,String a,String b){if(map==null)return null;return map.containsKey(a)?map.get(a):map.get(b);}private LocalDateTime date(Object value){return value instanceof LocalDateTime time?time:value==null?null:LocalDateTime.parse(String.valueOf(value).replace(' ','T'));}
    private void concurrent(){throw new TodoException("TODO_CONCURRENT_MODIFICATION","待办已被其他操作修改，请刷新后重试");}
}
