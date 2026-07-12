package com.ruoyi.system.service.event;

import java.time.LocalDate;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.law.business.contract.dto.ContractTodoSignCommand;
import com.law.business.security.BusinessActor;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.spi.TodoCompletionHandler;
import com.ruoyi.system.service.contract.ContractLifecycleService;

@Component
public class ContractSignTodoHandler implements TodoCompletionHandler
{
    private final ContractLifecycleService service;public ContractSignTodoHandler(ContractLifecycleService service){this.service=service;}
    @Override public boolean supports(TodoInstance todo){return "CONTRACT_SIGN".equals(todo.getTemplateCode());}
    @Override public void complete(TodoInstance todo,Map<String,Object> payload,Long operatorId,String operatorName){ContractTodoSignCommand command=new ContractTodoSignCommand(todo.getBusinessId(),text(payload.get("signStatus")),text(payload.get("signMethod")),LocalDate.parse(text(payload.get("signDate"))),text(payload.get("signFileUrl")));service.sign(command,new BusinessActor(operatorId,operatorName,operatorName,todo.getOwnerDeptId(),operatorId==1));}
    private String text(Object value){return value==null?null:String.valueOf(value);}
}
