package com.ruoyi.system.service.event;

import java.util.Map;
import org.springframework.stereotype.Component;
import com.law.business.security.BusinessActor;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.spi.TodoCompletionHandler;
import com.ruoyi.system.service.matter.MatterExpenseService;

@Component
public class MatterExpenseTodoHandler implements TodoCompletionHandler
{
    private final MatterExpenseService service;private final TodoMapper todos;public MatterExpenseTodoHandler(MatterExpenseService service,TodoMapper todos){this.service=service;this.todos=todos;}
    @Override public boolean supports(TodoInstance todo){return "MATTER_EXPENSE_REVIEW".equals(todo.getTemplateCode());}
    @Override public void complete(TodoInstance todo,Map<String,Object> p,Long id,String name){Long expense=Long.valueOf(String.valueOf(p.get("expenseId")));service.reviewFromTodo(expense,text(p.get("result")),text(p.get("voucherUrl")),text(p.get("remark")),new BusinessActor(id,name,name,todo.getOwnerDeptId(),id==1));Map<String,Object> r=new java.util.HashMap<>();r.put("todoId",todo.getTodoId());r.put("businessType","MATTER_EXPENSE");r.put("businessId",expense);r.put("relationType","SECONDARY");todos.insertRelation(r);}private String text(Object v){return v==null?null:String.valueOf(v);}
}
