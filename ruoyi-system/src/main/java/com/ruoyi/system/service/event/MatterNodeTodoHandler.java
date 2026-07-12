package com.ruoyi.system.service.event;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.law.business.security.BusinessActor;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.spi.TodoCompletionHandler;
import com.ruoyi.system.service.matter.MatterNodeService;

@Component
public class MatterNodeTodoHandler implements TodoCompletionHandler
{
    private final MatterNodeService service;private final TodoMapper todos;public MatterNodeTodoHandler(MatterNodeService service,TodoMapper todos){this.service=service;this.todos=todos;}
    @Override public boolean supports(TodoInstance todo){return "MATTER_NODE_HANDLE".equals(todo.getTemplateCode());}
    @SuppressWarnings("unchecked") @Override public void complete(TodoInstance todo,Map<String,Object> p,Long id,String name){Long node=Long.valueOf(String.valueOf(p.get("nodeId")));service.completeFromTodo(node,p.get("actualDate"),(Map<String,Object>)p.getOrDefault("dynamicFields",Map.of()),(List<Map<String,Object>>)p.getOrDefault("materials",List.of()),new BusinessActor(id,name,name,todo.getOwnerDeptId(),id==1));relation(todo,node,"MATTER_NODE");}
    private void relation(TodoInstance t,Long id,String type){Map<String,Object> r=new java.util.HashMap<>();r.put("todoId",t.getTodoId());r.put("businessType",type);r.put("businessId",id);r.put("relationType","SECONDARY");todos.insertRelation(r);}
}
