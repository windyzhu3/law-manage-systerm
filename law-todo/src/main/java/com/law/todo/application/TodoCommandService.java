package com.law.todo.application;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import com.alibaba.fastjson2.JSON;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.todo.application.command.TodoActionCommands.ActionCommand;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.domain.TodoAccessPolicy;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.TodoStatus;
import com.law.todo.domain.TodoStatusTransitions;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.spi.TodoCompletionHandler;
import com.law.todo.definition.codec.TodoDefinitionCodec;
import com.law.todo.definition.model.TodoDefinitionDocument;
import com.law.todo.definition.model.TodoDefinitionDocument.DodRule;
import com.law.todo.definition.model.TodoDefinitionDocument.UiSchema;

@Service
public class TodoCommandService
{
    private final TodoMapper mapper; private final TodoAccessPolicy access;private final TodoDodService dod;private final List<TodoCompletionHandler> completionHandlers;private final TodoRoutingService routing;
    public TodoCommandService(TodoMapper mapper,TodoAccessPolicy access){this(mapper,access,new TodoDodService(List.of()),List.of(),null);}
    @Autowired public TodoCommandService(TodoMapper mapper,TodoAccessPolicy access,TodoDodService dod,List<TodoCompletionHandler> completionHandlers,TodoRoutingService routing){this.mapper=mapper;this.access=access;this.dod=dod;this.completionHandlers=completionHandlers==null?List.of():completionHandlers;this.routing=routing;}

    @Transactional public TodoInstance claim(Long id,ActionCommand c,Actor a){if(repeated(id,c))return mapper.selectById(id);TodoInstance t=require(id);if(!access.canClaim(t,a.userId(),a.deptId()))deny();validateAction(t,c,"CLAIM");return transition(t,TodoStatus.CLAIMED,a.userId(),"CLAIM",c,a);}
    @Transactional public TodoInstance start(Long id,ActionCommand c,Actor a){if(repeated(id,c))return mapper.selectById(id);TodoInstance t=requireOwner(id,a);validateAction(t,c,"START");return transition(t,TodoStatus.IN_PROGRESS,null,"START",c,a);}
    @Transactional public TodoInstance submit(Long id,ActionCommand c,Actor a){if(repeated(id,c))return mapper.selectById(id);TodoInstance t=requireOwner(id,a);validateAction(t,c,"SUBMIT");return transition(t,TodoStatus.SUBMITTED,null,"SUBMIT",c,a);}
    @Transactional public TodoInstance complete(Long id,ActionCommand c,Actor a){if(repeated(id,c))return mapper.selectById(id);TodoInstance t=requireOwner(id,a);validateAction(t,c,"COMPLETE");TodoInstance completed=transition(t,TodoStatus.COMPLETED,null,"COMPLETE",c,a);for(TodoCompletionHandler h:completionHandlers)if(h.supports(completed))h.complete(completed,c.payload(),a.userId(),a.userName());if(routing!=null)routing.advance(completed,c.payload());return completed;}
    @Transactional public TodoInstance returnTodo(Long id,ActionCommand c,Actor a){if(repeated(id,c))return mapper.selectById(id);TodoInstance t=require(id);if(!access.canReview(t,a.userId()))deny();validateAction(t,c,"RETURN");return transition(t,TodoStatus.RETURNED,null,"RETURN",c,a);}
    @Transactional public TodoInstance transfer(Long id,ActionCommand c,Actor a){if(repeated(id,c))return mapper.selectById(id);TodoInstance t=requireOwner(id,a);TodoStatus current=TodoStatus.fromCode(t.getStatus());if(current.isTerminal())throw new TodoException("TODO_TERMINAL","终态待办不能转派");validateAction(t,c,"TRANSFER");Object targetValue=c.payload().get("targetOwnerId");if(targetValue==null)throw new TodoException("TODO_TRANSFER_OWNER_REQUIRED","新负责人不能为空");Long target=Long.valueOf(String.valueOf(targetValue));return transition(t,current,target,"TRANSFER",c,a);}
    @Transactional public TodoInstance cancel(Long id,ActionCommand c,Actor a){if(repeated(id,c))return mapper.selectById(id);TodoInstance t=requireOwner(id,a);validateAction(t,c,"CANCEL");return transition(t,TodoStatus.CANCELLED,null,"CANCEL",c,a);}

    private TodoInstance transition(TodoInstance t,TodoStatus target,Long owner,String action,ActionCommand c,Actor a)
    {
        TodoStatus from=TodoStatus.fromCode(t.getStatus());TodoStatusTransitions.requireAllowed(from,target);
        if(mapper.updateStatusConditionally(t.getTodoId(),from.code(),target.code(),owner,a.userName())<=0)throw new TodoException("TODO_CONCURRENT_MODIFICATION","待办状态已变化，请刷新后重试");
        Map<String,Object> log=new HashMap<>();log.put("todoId",t.getTodoId());log.put("actionId",c.actionId());log.put("actionType",action);log.put("fromStatus",from.code());log.put("toStatus",target.code());log.put("operatorId",a.userId());log.put("operatorName",a.userName());log.put("opinion",c.opinion());log.put("payloadJson",JSON.toJSONString(c.payload()));
        if(mapper.insertActionIfAbsent(log)<=0&&mapper.selectActionById(c.actionId())==null)throw new TodoException("TODO_ACTION_LOG_FAILED","待办动作记录失败");
        t.setStatus(target.code());if(owner!=null)t.setOwnerId(owner);return t;
    }
    private boolean repeated(Long todoId,ActionCommand c){Map<String,Object> action=mapper.selectActionById(c.actionId());if(action==null||action.isEmpty())return false;Object recorded=value(action,"todo_id","todoId");if(recorded==null||!todoId.equals(Long.valueOf(String.valueOf(recorded))))throw new TodoException("TODO_ACTION_ID_CONFLICT","动作幂等键已被其他待办使用");return true;}
    private TodoInstance require(Long id){TodoInstance t=mapper.selectById(id);if(t==null)throw new TodoException("TODO_NOT_FOUND","待办不存在");return t;}
    private TodoInstance requireOwner(Long id,Actor a){TodoInstance t=require(id);if(!access.canOperate(t,a.userId()))deny();return t;}
    private void deny(){throw new TodoException("TODO_ACCESS_DENIED","无权操作该待办");}
    private void validateAction(TodoInstance todo,ActionCommand command,String action)
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
        if(todo.getDodSnapshotJson()!=null&&!todo.getDodSnapshotJson().isBlank())definition=new TodoDefinitionDocument(definition.schemaVersion(),definition.templateCode(),definition.event(),definition.owner(),new DodRule(JSON.parseObject(todo.getDodSnapshotJson())),definition.sla(),definition.ui(),definition.routing(),definition.autoActions(),definition.decisionRefs(),definition.acceptanceRefs());
        dod.validate(todo,definition,action,command.fields(),command.fileObjectIds());
        if("COMPLETE".equals(action))
        {
            List<String> legacyTypes=JSON.parseObject(JSON.toJSONString(definition.dod().config())).getList("requiredAttachments",String.class);
            if(legacyTypes!=null&&!legacyTypes.isEmpty())dod.validate(todo,List.of(),legacyTypes,command.fields(),mapper.selectAttachmentTypes(todo.getTodoId()));
        }
    }
    private Object value(Map<String,Object> map,String a,String b){return map.containsKey(a)?map.get(a):map.get(b);}private String text(Object value){return value==null?null:String.valueOf(value);}
}
