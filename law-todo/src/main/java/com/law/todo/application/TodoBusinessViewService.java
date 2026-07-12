package com.law.todo.application;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.view.TodoBusinessSummary;
import com.law.todo.application.view.TodoChainView;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.spi.TodoBusinessAccessChecker;
import org.springframework.stereotype.Service;

@Service
public class TodoBusinessViewService
{
    private final TodoMapper mapper;
    private final List<TodoBusinessAccessChecker> accessCheckers;

    public TodoBusinessViewService(TodoMapper mapper,List<TodoBusinessAccessChecker> accessCheckers)
    {
        this.mapper=mapper;this.accessCheckers=accessCheckers;
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
        return mapper.selectBusinessTodos(authorizedQuery(businessType,businessId,actor));
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

    private static Object value(Map<String,Object> row,String snake,String camel){return row.containsKey(snake)?row.get(snake):row.get(camel);}
    private static long number(Map<String,Object> row,String snake,String camel){Object value=value(row,snake,camel);return value==null?0:Long.parseLong(String.valueOf(value));}
    private static LocalDateTime date(Map<String,Object> row,String snake,String camel){Object value=value(row,snake,camel);return value instanceof LocalDateTime time?time:null;}
}
