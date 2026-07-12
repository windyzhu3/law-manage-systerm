package com.ruoyi.system.service.event;

import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.law.business.security.BusinessActor;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.spi.TodoCompletionHandler;
import com.ruoyi.system.service.casecenter.CaseConfirmationService;

@Component
public class CaseAcceptanceTodoHandler implements TodoCompletionHandler
{
    private final CaseConfirmationService service;public CaseAcceptanceTodoHandler(CaseConfirmationService service){this.service=service;}
    @Override public boolean supports(TodoInstance todo){return "CASE_ACCEPT".equals(todo.getTemplateCode());}
    @Override public void complete(TodoInstance todo,Map<String,Object> p,Long id,String name){Map<String,Object> command=new HashMap<>();command.put("confirmId",p.get("confirmId"));boolean accepted=Boolean.parseBoolean(String.valueOf(p.get("accepted")));command.put("confirmResult",accepted?"accepted":"rejected");command.put("opinion",p.get("reason"));service.handle(command,new BusinessActor(id,name,name,todo.getOwnerDeptId(),id==1));}
}
