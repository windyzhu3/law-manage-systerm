package com.ruoyi.system.service.event;

import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.law.business.security.BusinessActor;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.spi.TodoCompletionHandler;
import com.ruoyi.system.service.contract.ContractInvoiceService;

@Component
public class ContractInvoiceTodoHandler implements TodoCompletionHandler
{
    private final ContractInvoiceService service;private final TodoMapper todos;public ContractInvoiceTodoHandler(ContractInvoiceService service,TodoMapper todos){this.service=service;this.todos=todos;}
    @Override public boolean supports(TodoInstance todo){return "INVOICE_HANDLE".equals(todo.getTemplateCode());}
    @Override public void complete(TodoInstance todo,Map<String,Object> p,Long userId,String userName){Long plan=Long.valueOf(String.valueOf(p.get("planId")));String action=text(p.get("action"));String target="partial".equals(action)?"2":"1";service.invoice(plan,target,text(p.get("remark")),text(p.get("invoiceType")),text(p.get("invoiceNo")),text(p.get("invoiceFileUrl")),new BusinessActor(userId,userName,userName,todo.getOwnerDeptId(),userId==1));Map<String,Object> row=new HashMap<>();row.put("todoId",todo.getTodoId());row.put("businessType","FEE_PLAN");row.put("businessId",plan);row.put("relationType","SECONDARY");todos.insertRelation(row);}
    private String text(Object value){return value==null?null:String.valueOf(value);}
}
