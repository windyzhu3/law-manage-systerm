package com.ruoyi.system.service.event;

import java.util.Map;
import org.springframework.stereotype.Component;
import com.law.business.security.BusinessActor;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.spi.TodoCompletionHandler;
import com.ruoyi.system.service.casecenter.CaseCreationService;

@Component
public class CaseCreationTodoHandler implements TodoCompletionHandler
{
    private final CaseCreationService service;public CaseCreationTodoHandler(CaseCreationService service){this.service=service;}
    @Override public boolean supports(TodoInstance todo){return "CASE_CREATE_CHECK".equals(todo.getTemplateCode());}
    @Override public void complete(TodoInstance todo,Map<String,Object> payload,Long userId,String userName){service.createFromContract(todo.getBusinessId(),new BusinessActor(userId,userName,userName,todo.getOwnerDeptId(),userId==1));}
}
