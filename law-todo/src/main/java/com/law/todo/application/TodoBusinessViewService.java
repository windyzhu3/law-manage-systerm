package com.law.todo.application;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.view.TodoBusinessSummary;
import com.law.todo.application.view.TodoChainView;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.TodoAccessPolicy;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.spi.TodoBusinessAccessChecker;
import org.springframework.stereotype.Service;

@Service
public class TodoBusinessViewService
{
    private final TodoMapper mapper;
    private final List<TodoBusinessAccessChecker> accessCheckers;
    private final TodoAccessPolicy todoAccess;

    public TodoBusinessViewService(TodoMapper mapper,List<TodoBusinessAccessChecker> accessCheckers,TodoAccessPolicy todoAccess)
    {
        this.mapper=mapper;this.accessCheckers=accessCheckers;this.todoAccess=todoAccess;
    }

    public TodoBusinessSummary summary(String businessType,Long businessId,Actor actor)
    {
        Map<String,Object> query=authorizedQuery(businessType,businessId,actor);
        Map<String,Object> row=mapper.selectBusinessTodoSummary(query);
        if(row==null) row=Collections.emptyMap();
        List<Long> owners=mapper.selectBusinessTodoOwners(query);
        Map<String,Object> recent=mapper.selectBusinessRecentAction(query);
        return new TodoBusinessSummary(businessType,businessId,number(row,"active_count","activeCount"),
            number(row,"overdue_count","overdueCount"),date(row,"nearest_due_at","nearestDueAt"),
            owners==null?List.of():owners,recent==null?Map.of():recent);
    }

    public List<Map<String,Object>> businessTodos(String businessType,Long businessId,Actor actor)
    {
        List<Map<String,Object>> rows=mapper.selectBusinessTodos(authorizedQuery(businessType,businessId,actor));
        if(rows==null||rows.isEmpty())return List.of();
        List<TodoInstance> todos=rows.stream().map(this::todo).toList();
        Map<Long,List<String>> actions=allowedActions(todos,actor);
        return rows.stream().map(row->withAllowedActions(row,actions)).toList();
    }

    public TodoChainView chain(Long rootTodoId,Actor actor)
    {
        Map<String,Object> root=mapper.selectRootTodo(rootTodoId);
        if(root==null) throw new TodoException("TODO_NOT_FOUND","待办链不存在");
        String type=String.valueOf(value(root,"business_type","businessType"));
        Long id=Long.valueOf(String.valueOf(value(root,"business_id","businessId")));
        Map<String,Object> query=authorizedQuery(type,id,actor);query.put("rootTodoId",rootTodoId);
        List<Map<String,Object>> nodes=mapper.selectTodoChain(query);
        return new TodoChainView(rootTodoId,nodes==null?List.of():nodes);
    }

    private Map<String,Object> authorizedQuery(String type,Long id,Actor actor)
    {
        if(type==null||id==null||actor==null) throw new TodoException("TODO_INVALID_ARGUMENT","业务对象和操作人不能为空");
        TodoBusinessAccessChecker checker=accessCheckers.stream().filter(item->item.supports(type)).findFirst()
            .orElseThrow(()->new TodoException("TODO_BUSINESS_TYPE_UNSUPPORTED","不支持的业务对象类型"));
        if(!checker.canView(type,id,actor.userId(),actor.deptId())) throw new TodoException("TODO_ACCESS_DENIED","无权查看业务对象待办");
        Map<String,Object> query=new HashMap<>();query.put("businessType",type);query.put("businessId",id);
        query.put("currentUserId",actor.userId());query.put("currentDeptId",actor.deptId());return query;
    }

    private Map<String,Object> withAllowedActions(Map<String,Object> row,
            Map<Long,List<String>> actions)
    {
        Map<String,Object> view=new HashMap<>(row);
        Long todoId=longValue(value(row,"todo_id","todoId"));
        view.put("allowedActions",todoId==null?List.of():
                actions.getOrDefault(todoId,List.of()));
        return view;
    }

    private TodoInstance todo(Map<String,Object> row)
    {
        TodoInstance todo=new TodoInstance();
        todo.setTodoId(longValue(value(row,"todo_id","todoId")));
        todo.setStatus(String.valueOf(value(row,"status","status")));
        todo.setOwnerId(longValue(value(row,"owner_id","ownerId")));
        todo.setOwnerDeptId(longValue(value(row,"owner_dept_id","ownerDeptId")));
        return todo;
    }

    public Map<Long,List<String>> allowedActions(List<TodoInstance> todos,Actor actor)
    {
        if(todos==null||todos.isEmpty()||actor==null)return Map.of();
        List<TodoInstance> actionable=todos.stream()
                .filter(todo->todo!=null&&todo.getTodoId()!=null)
                .toList();
        if(actionable.isEmpty())return Map.of();
        List<Long> todoIds=actionable.stream().map(TodoInstance::getTodoId)
                .distinct().toList();
        List<Map<String,Object>> rows=mapper.selectAllowedActionFacts(
                todoIds,actor.userId(),actor.deptId());
        Map<Long,AccessFacts> facts=new HashMap<>();
        if(rows!=null)
        {
            for(Map<String,Object> row:rows)
            {
                Long todoId=longValue(value(row,"todo_id","todoId"));
                if(todoId!=null)facts.put(todoId,new AccessFacts(
                        truth(value(row,"can_claim","canClaim")),
                        truth(value(row,"can_review","canReview"))));
            }
        }
        Map<Long,List<String>> result=new LinkedHashMap<>();
        for(TodoInstance todo:actionable)
        {
            AccessFacts access=facts.getOrDefault(todo.getTodoId(),
                    AccessFacts.NONE);
            result.put(todo.getTodoId(),allowedActions(todo,actor,access));
        }
        return result;
    }

    public List<String> allowedActions(TodoInstance todo,Actor actor)
    {
        if(todo==null||todo.getTodoId()==null||actor==null)return List.of();
        String status=todo.getStatus();List<String> actions=new java.util.ArrayList<>();
        boolean owner=todoAccess.canOperate(todo,actor.userId());
        if("CREATED".equals(status)&&todoAccess.canClaim(todo,actor.userId(),actor.deptId()))actions.add("claim");
        if(("CLAIMED".equals(status)||"RETURNED".equals(status))&&owner)actions.add("start");
        if("IN_PROGRESS".equals(status)&&owner)actions.add("submit");
        if("SUBMITTED".equals(status)&&owner)actions.add("complete");
        if("SUBMITTED".equals(status)&&todoAccess.canReview(todo,actor.userId()))actions.add("return");
        if(!"COMPLETED".equals(status)&&!"CANCELLED".equals(status)&&owner){actions.add("transfer");actions.add("cancel");}
        return actions;
    }

    private List<String> allowedActions(TodoInstance todo,Actor actor,
            AccessFacts access)
    {
        String status=todo.getStatus();
        List<String> actions=new java.util.ArrayList<>();
        boolean owner=todo.getOwnerId()!=null
                && todo.getOwnerId().equals(actor.userId());
        boolean canClaim=todo.getOwnerId()!=null?owner:access.canClaim();
        if("CREATED".equals(status)&&canClaim)actions.add("claim");
        if(("CLAIMED".equals(status)||"RETURNED".equals(status))&&owner)
            actions.add("start");
        if("IN_PROGRESS".equals(status)&&owner)actions.add("submit");
        if("SUBMITTED".equals(status)&&owner)actions.add("complete");
        if("SUBMITTED".equals(status)&&access.canReview())actions.add("return");
        if(!"COMPLETED".equals(status)&&!"CANCELLED".equals(status)&&owner)
        {
            actions.add("transfer");
            actions.add("cancel");
        }
        return actions;
    }

    private static Object value(Map<String,Object> row,String snake,String camel){return row.containsKey(snake)?row.get(snake):row.get(camel);}
    private static Long longValue(Object value){return value==null?null:Long.valueOf(String.valueOf(value));}
    private static boolean truth(Object value)
    {
        if(value instanceof Boolean bool)return bool;
        if(value instanceof Number number)return number.intValue()>0;
        return value!=null&&("1".equals(String.valueOf(value))
                ||"true".equalsIgnoreCase(String.valueOf(value)));
    }
    private static long number(Map<String,Object> row,String snake,String camel){Object value=value(row,snake,camel);return value==null?0:Long.parseLong(String.valueOf(value));}
    private static LocalDateTime date(Map<String,Object> row,String snake,String camel){Object value=value(row,snake,camel);return value instanceof LocalDateTime time?time:null;}

    private record AccessFacts(boolean canClaim,boolean canReview)
    {
        private static final AccessFacts NONE=new AccessFacts(false,false);
    }
}
