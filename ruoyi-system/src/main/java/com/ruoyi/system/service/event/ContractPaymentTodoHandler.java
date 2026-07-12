package com.ruoyi.system.service.event;

import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.law.business.security.BusinessActor;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.spi.TodoCompletionHandler;
import com.ruoyi.system.service.contract.ContractPaymentService;

@Component
public class ContractPaymentTodoHandler implements TodoCompletionHandler
{
    private final ContractPaymentService service;private final TodoMapper todos;public ContractPaymentTodoHandler(ContractPaymentService service,TodoMapper todos){this.service=service;this.todos=todos;}
    @Override public boolean supports(TodoInstance todo){return "PAYMENT_CONFIRM".equals(todo.getTemplateCode());}
    @Override public void complete(TodoInstance todo,Map<String,Object> p,Long userId,String userName){Long plan=number(p.get("planId"));service.confirm(plan,text(p.get("receivedAmount")),text(p.get("remark")),text(p.get("paymentMethod")),text(p.get("voucherUrl")),actor(todo,userId,userName));relation(todo,plan);}
    private void relation(TodoInstance t,Long plan){Map<String,Object> row=new HashMap<>();row.put("todoId",t.getTodoId());row.put("businessType","FEE_PLAN");row.put("businessId",plan);row.put("relationType","SECONDARY");todos.insertRelation(row);}
    private BusinessActor actor(TodoInstance t,Long id,String name){return new BusinessActor(id,name,name,t.getOwnerDeptId(),id==1);}private Long number(Object v){return Long.valueOf(String.valueOf(v));}private String text(Object v){return v==null?null:String.valueOf(v);}
}
