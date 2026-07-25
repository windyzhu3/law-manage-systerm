package com.law.todo.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson2.JSON;
import com.law.todo.application.command.TodoActionCommands.ActionCommand;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.definition.codec.TodoDefinitionCodec;
import com.law.todo.definition.model.TodoDefinitionDocument;
import com.law.todo.definition.model.TodoDefinitionDocument.DodRule;
import com.law.todo.definition.model.TodoDefinitionDocument.UiSchema;
import com.law.todo.domain.TodoAccessPolicy;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.TodoStatus;
import com.law.todo.domain.TodoStatusTransitions;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.spi.TodoCompletionHandler;
import com.law.todo.spi.TodoCompletionHandler.CompletionResult;

@Service
public class TodoCommandService
{
    private final TodoMapper mapper;
    private final TodoAccessPolicy access;
    private final TodoDodService dod;
    private final List<TodoCompletionHandler> completionHandlers;
    private final TodoRoutingService routing;

    public TodoCommandService(TodoMapper mapper,TodoAccessPolicy access)
    {
        this(mapper,access,new TodoDodService(List.of()),List.of(),null);
    }

    @Autowired
    public TodoCommandService(TodoMapper mapper,TodoAccessPolicy access,TodoDodService dod,
            List<TodoCompletionHandler> completionHandlers,TodoRoutingService routing)
    {
        this.mapper=mapper;this.access=access;this.dod=dod;
        this.completionHandlers=completionHandlers==null?List.of():completionHandlers;this.routing=routing;
    }

    @Transactional public TodoInstance claim(Long id,ActionCommand command,Actor actor)
    {
        requireHumanActionId(command);if(repeated(id,command))return mapper.selectById(id);TodoInstance todo=require(id);
        if(!access.canClaim(todo,actor.userId(),actor.deptId()))deny();validateAction(todo,command,"CLAIM",actor);
        return transition(todo,TodoStatus.CLAIMED,actor.userId(),"CLAIM",command,actor);
    }
    @Transactional public TodoInstance start(Long id,ActionCommand command,Actor actor)
    {
        requireHumanActionId(command);if(repeated(id,command))return mapper.selectById(id);TodoInstance todo=requireOwner(id,actor);
        validateAction(todo,command,"START",actor);return transition(todo,TodoStatus.IN_PROGRESS,null,"START",command,actor);
    }
    @Transactional public TodoInstance submit(Long id,ActionCommand command,Actor actor)
    {
        requireHumanActionId(command);if(repeated(id,command))return mapper.selectById(id);TodoInstance todo=requireOwner(id,actor);
        validateAction(todo,command,"SUBMIT",actor);return transition(todo,TodoStatus.SUBMITTED,null,"SUBMIT",command,actor);
    }
    @Transactional public TodoInstance complete(Long id,ActionCommand command,Actor actor)
    {
        requireHumanActionId(command);if(repeated(id,command))return mapper.selectById(id);
        return complete(requireOwner(id,actor),command,actor,"COMPLETE");
    }
    @Transactional public TodoInstance returnTodo(Long id,ActionCommand command,Actor actor)
    {
        requireHumanActionId(command);if(repeated(id,command))return mapper.selectById(id);TodoInstance todo=require(id);
        if(!access.canReview(todo,actor.userId()))deny();validateAction(todo,command,"RETURN",actor);
        return transition(todo,TodoStatus.RETURNED,null,"RETURN",command,actor);
    }
    @Transactional public TodoInstance transfer(Long id,ActionCommand command,Actor actor)
    {
        requireHumanActionId(command);if(repeated(id,command))return mapper.selectById(id);TodoInstance todo=requireOwner(id,actor);
        TodoStatus current=TodoStatus.fromCode(todo.getStatus());if(current.isTerminal())terminal("transferred");
        validateAction(todo,command,"TRANSFER",actor);Long target=positiveOwner(command.payload().get("targetOwnerId"));
        return transition(todo,current,target,"TRANSFER",command,actor);
    }
    @Transactional public TodoInstance cancel(Long id,ActionCommand command,Actor actor)
    {
        requireHumanActionId(command);if(repeated(id,command))return mapper.selectById(id);TodoInstance todo=requireOwner(id,actor);
        validateAction(todo,command,"CANCEL",actor);return transition(todo,TodoStatus.CANCELLED,null,"CANCEL",command,actor);
    }

    @Transactional public TodoInstance autoComplete(Long id,ActionCommand command,Actor actor)
    {
        requireServiceActor(actor);fenceAutoExecution(id,command,"COMPLETE_DEFAULT");if(repeatedAuto(id,command,"COMPLETE_DEFAULT"))return mapper.selectById(id);
        return complete(require(id),command,actor,"COMPLETE_DEFAULT");
    }
    @Transactional public TodoInstance autoReturn(Long id,ActionCommand command,Actor actor)
    {
        requireServiceActor(actor);fenceAutoExecution(id,command,"RETURN_DEFAULT");if(repeatedAuto(id,command,"RETURN_DEFAULT"))return mapper.selectById(id);TodoInstance todo=require(id);
        validateAction(todo,command,"RETURN",actor);return transition(todo,TodoStatus.RETURNED,null,"RETURN_DEFAULT",command,actor);
    }
    @Transactional public TodoInstance autoEscalate(Long id,ActionCommand command,Actor actor)
    {
        requireServiceActor(actor);fenceAutoExecution(id,command,"ESCALATE");if(repeatedAuto(id,command,"ESCALATE"))return mapper.selectById(id);TodoInstance todo=require(id);
        TodoStatus status=TodoStatus.fromCode(todo.getStatus());if(status.isTerminal())terminal("escalated");
        validateAction(todo,command,"ESCALATE",actor);return transition(todo,status,null,"ESCALATE",command,actor);
    }
    @Transactional public TodoInstance autoTransfer(Long id,ActionCommand command,Actor actor)
    {
        requireServiceActor(actor);fenceAutoExecution(id,command,"TRANSFER");if(repeatedAuto(id,command,"TRANSFER"))return mapper.selectById(id);TodoInstance todo=require(id);
        TodoStatus status=TodoStatus.fromCode(todo.getStatus());if(status.isTerminal())terminal("transferred");
        validateAction(todo,command,"TRANSFER",actor);return transition(todo,status,positiveOwner(command.payload().get("targetOwnerId")),"TRANSFER",command,actor);
    }
    @Transactional public TodoInstance autoReturnPool(Long id,ActionCommand command,Actor actor)
    {
        requireServiceActor(actor);fenceAutoExecution(id,command,"RETURN_POOL");if(repeatedAuto(id,command,"RETURN_POOL"))return mapper.selectById(id);TodoInstance todo=require(id);
        TodoStatus status=TodoStatus.fromCode(todo.getStatus());if(status.isTerminal())terminal("returned to pool");
        validateAction(todo,command,"RETURN_POOL",actor);
        if(mapper.returnToPoolConditionally(id,status.code(),actor.userName())<=0)concurrent();
        writeAction(todo,command,actor,"RETURN_POOL",status.code(),TodoStatus.CREATED.code());
        todo.setStatus(TodoStatus.CREATED.code());todo.setOwnerId(null);todo.setOwnerDeptId(null);return todo;
    }

    private TodoInstance complete(TodoInstance todo,ActionCommand command,Actor actor,String actionType)
    {
        validateAction(todo,command,"COMPLETE",actor);
        boolean controlledAutomatic="COMPLETE_DEFAULT".equals(actionType)
                &&actor==TodoAutoActionService.SERVICE_ACTOR;
        CompletionContext context=controlledAutomatic
                ?CompletionContext.controlledAutomatic(todo,command.payload(),actor.userId(),actor.userName())
                :CompletionContext.human(todo,command.payload(),actor.userId(),actor.userName());
        List<TodoCompletionHandler> supported=completionHandlers.stream()
                .filter(handler->handler.supports(todo)).toList();
        if(supported.size()>1)
            throw new TodoException("TODO_COMPLETION_HANDLER_AMBIGUOUS",
                    "More than one completion handler supports this Todo");
        CompletionResult result=supported.isEmpty()
                ?CompletionResult.completeTodo(command.payload())
                :supported.get(0).handle(context);
        if(result==null)
            throw new TodoException("TODO_COMPLETION_RESULT_REQUIRED",
                    "Completion handler did not return an authoritative result");
        if(!result.completeTodo())
        {
            String status=TodoStatus.fromCode(todo.getStatus()).code();
            writeAction(todo,command,actor,"COMPLETE_RETAINED",status,status);
            return todo;
        }
        TodoInstance completed=transition(todo,TodoStatus.COMPLETED,null,actionType,command,actor);
        if(routing!=null)routing.advance(completed,result.routingPayload());
        return completed;
    }

    private TodoInstance transition(TodoInstance todo,TodoStatus target,Long owner,String action,ActionCommand command,Actor actor)
    {
        TodoStatus from=TodoStatus.fromCode(todo.getStatus());TodoStatusTransitions.requireAllowed(from,target);
        if(mapper.updateStatusConditionally(todo.getTodoId(),from.code(),target.code(),owner,actor.userName())<=0)concurrent();
        writeAction(todo,command,actor,action,from.code(),target.code());todo.setStatus(target.code());if(owner!=null)todo.setOwnerId(owner);return todo;
    }

    private void writeAction(TodoInstance todo,ActionCommand command,Actor actor,String action,String from,String to)
    {
        Map<String,Object> log=new HashMap<>();log.put("todoId",todo.getTodoId());log.put("actionId",command.actionId());
        log.put("actionType",action);log.put("actionSource",actor==TodoAutoActionService.SERVICE_ACTOR?"SYSTEM":"HUMAN");
        log.put("fromStatus",from);log.put("toStatus",to);log.put("operatorId",actor.userId());log.put("operatorName",actor.userName());
        log.put("opinion",command.opinion());log.put("payloadJson",JSON.toJSONString(command.payload()));
        if(mapper.insertActionIfAbsent(log)<=0&&mapper.selectActionById(command.actionId())==null)
            throw new TodoException("TODO_ACTION_LOG_FAILED","Todo action audit failed");
    }

    private boolean repeated(Long todoId,ActionCommand command)
    {
        Map<String,Object> action=mapper.selectActionById(command.actionId());if(action==null||action.isEmpty())return false;
        Object recorded=value(action,"todo_id","todoId");if(recorded==null||!todoId.equals(Long.valueOf(String.valueOf(recorded))))
            throw new TodoException("TODO_ACTION_ID_CONFLICT","Action id is already used by another todo");return true;
    }

    private boolean repeatedAuto(Long todoId,ActionCommand command,String expectedType)
    {
        requireAutoActionId(command);Map<String,Object> action=mapper.selectActionById(command.actionId());if(action==null||action.isEmpty())return false;
        Object recorded=value(action,"todo_id","todoId");String type=text(value(action,"action_type","actionType"));
        String source=text(value(action,"action_source","actionSource"));String operator=text(value(action,"operator_id","operatorId"));
        String name=text(value(action,"operator_name","operatorName"));
        if(recorded==null||!todoId.equals(Long.valueOf(String.valueOf(recorded)))||!expectedType.equals(type)||!"SYSTEM".equals(source)
                ||!String.valueOf(TodoAutoActionService.SERVICE_ACTOR.userId()).equals(operator)||!TodoAutoActionService.SERVICE_ACTOR.userName().equals(name))
            throw new TodoException("TODO_AUTO_ACTION_REPLAY_CONFLICT","Reserved auto action id belongs to another action or source");
        return true;
    }

    private void fenceAutoExecution(Long todoId,ActionCommand command,String expectedType)
    {
        requireAutoActionId(command);Map<String,Object> execution=mapper.selectAutoActionExecutionForUpdate(command.actionId());
        String key=execution==null?null:text(value(execution,"execution_key","executionKey"));Object recorded=execution==null?null:value(execution,"todo_id","todoId");String type=execution==null?null:text(value(execution,"action_type","actionType"));String status=execution==null?null:text(value(execution,"status","status"));
        if(execution==null||!command.actionId().equals(key)||recorded==null||!todoId.equals(Long.valueOf(String.valueOf(recorded)))||!expectedType.equals(type)||!"CLAIMED".equals(status))
            throw new TodoException("TODO_AUTO_ACTION_FENCE_REJECTED","Controlled auto action requires its matching CLAIMED execution lock");
    }

    private TodoInstance require(Long id){TodoInstance todo=mapper.selectById(id);if(todo==null)throw new TodoException("TODO_NOT_FOUND","Todo does not exist");return todo;}
    private TodoInstance requireOwner(Long id,Actor actor){TodoInstance todo=require(id);if(!access.canOperate(todo,actor.userId()))deny();return todo;}
    private void deny(){throw new TodoException("TODO_ACCESS_DENIED","No permission to operate this todo");}
    private void concurrent(){throw new TodoException("TODO_CONCURRENT_MODIFICATION","Todo state changed concurrently");}
    private void terminal(String action){throw new TodoException("TODO_TERMINAL","Terminal todo cannot be "+action);}
    private Long positiveOwner(Object value){if(value==null)throw new TodoException("TODO_TRANSFER_OWNER_REQUIRED","targetOwnerId is required");try{Long owner=Long.valueOf(String.valueOf(value));if(owner<=0)throw new NumberFormatException();return owner;}catch(NumberFormatException invalid){throw new TodoException("TODO_TRANSFER_OWNER_INVALID","targetOwnerId must be a positive integer");}}
    private void requireServiceActor(Actor actor){if(actor!=TodoAutoActionService.SERVICE_ACTOR)throw new TodoException("TODO_AUTO_ACTION_ACTOR_REQUIRED","Controlled auto actions require the service actor");}
    private void requireHumanActionId(ActionCommand command){if(command!=null&&command.actionId()!=null&&command.actionId().regionMatches(true,0,"AUTO:",0,5))throw new TodoException("TODO_ACTION_ID_RESERVED","AUTO: action ids are reserved for controlled service actions");}
    private void requireAutoActionId(ActionCommand command){if(command==null||command.actionId()==null||!command.actionId().startsWith("AUTO:"))throw new TodoException("TODO_AUTO_ACTION_ID_REQUIRED","Controlled auto actions require a reserved AUTO: action id");}

    private void validateAction(TodoInstance todo,ActionCommand command,String action,Actor actor)
    {
        Map<String,Object> version=mapper.selectTemplateVersionById(todo.getTemplateVersionId());
        String compiled=version==null?null:text(value(version,"compiled_json","compiledJson"));
        if(compiled==null||compiled.isBlank())compiled=version==null?null:text(value(version,"definition_json","definitionJson"));
        TodoDefinitionDocument definition;
        if(compiled!=null&&!compiled.isBlank())definition=new TodoDefinitionCodec().read(compiled);
        else
        {
            String json=todo.getDodSnapshotJson();if((json==null||json.isBlank())&&version!=null)json=text(value(version,"dod_rule_json","dodRuleJson"));
            if(json==null||json.isBlank())return;
            definition=new TodoDefinitionDocument(1,todo.getTemplateCode(),null,null,new DodRule(JSON.parseObject(json)),null,new UiSchema(Map.of()),null,List.of(),List.of(),List.of());
        }
        if(todo.getDodSnapshotJson()!=null&&!todo.getDodSnapshotJson().isBlank())
            definition=new TodoDefinitionDocument(definition.schemaVersion(),definition.templateCode(),definition.event(),definition.owner(),new DodRule(JSON.parseObject(todo.getDodSnapshotJson())),definition.sla(),definition.ui(),definition.routing(),definition.autoActions(),definition.decisionRefs(),definition.acceptanceRefs());
        dod.validate(todo,definition,action,command.fields(),command.fileObjectIds(),actor);
        if("COMPLETE".equals(action))
        {
            List<String> legacyTypes=JSON.parseObject(JSON.toJSONString(definition.dod().config())).getList("requiredAttachments",String.class);
            if(legacyTypes!=null&&!legacyTypes.isEmpty())dod.validate(todo,List.of(),legacyTypes,command.fields(),mapper.selectAttachmentTypes(todo.getTodoId()));
        }
    }
    private Object value(Map<String,Object> map,String snake,String camel){return map.containsKey(snake)?map.get(snake):map.get(camel);}
    private String text(Object value){return value==null?null:String.valueOf(value);}
}
