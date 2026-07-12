package com.ruoyi.system.service.event;

import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.law.business.security.BusinessActor;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.spi.TodoCompletionHandler;
import com.ruoyi.system.service.casecenter.CaseTransferService;

@Component
public class CaseTransferReviewTodoHandler implements TodoCompletionHandler
{
    private final CaseTransferService service;public CaseTransferReviewTodoHandler(CaseTransferService service){this.service=service;}
    @Override public boolean supports(TodoInstance todo){return "CASE_TRANSFER_REVIEW".equals(todo.getTemplateCode());}
    @Override public void complete(TodoInstance todo,Map<String,Object> p,Long id,String name){Map<String,Object> command=new HashMap<>(p);service.approve(command,new BusinessActor(id,name,name,todo.getOwnerDeptId(),id==1));}
}
