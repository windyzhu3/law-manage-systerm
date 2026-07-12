package com.ruoyi.system.service.event;

import java.util.Map;
import org.springframework.stereotype.Component;
import com.law.business.security.BusinessActor;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.spi.TodoCompletionHandler;
import com.ruoyi.system.service.contract.ContractLifecycleService;

@Component
public class ContractReviewTodoHandler implements TodoCompletionHandler
{
    private final ContractLifecycleService service;public ContractReviewTodoHandler(ContractLifecycleService service){this.service=service;}
    @Override public boolean supports(TodoInstance todo){return "CONTRACT_REVIEW".equals(todo.getTemplateCode());}
    @Override public void complete(TodoInstance todo,Map<String,Object> payload,Long operatorId,String operatorName){service.approve(todo.getBusinessId(),text(payload.get("action")),text(payload.get("opinion")),new BusinessActor(operatorId,operatorName,operatorName,todo.getOwnerDeptId(),operatorId==1));}
    private String text(Object value){return value==null?null:String.valueOf(value);}
}
