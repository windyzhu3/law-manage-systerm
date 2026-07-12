package com.ruoyi.system.service.event;

import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.law.business.security.BusinessActor;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.spi.TodoCompletionHandler;
import com.ruoyi.system.service.casecenter.CaseAssignmentService;

@Component
public class CaseAssignmentTodoHandler implements TodoCompletionHandler
{
    private final CaseAssignmentService service;public CaseAssignmentTodoHandler(CaseAssignmentService service){this.service=service;}
    @Override public boolean supports(TodoInstance todo){return "CASE_ASSIGN".equals(todo.getTemplateCode())||"CASE_REASSIGN".equals(todo.getTemplateCode());}
    @Override public void complete(TodoInstance todo,Map<String,Object> p,Long id,String name){Map<String,Object> command=new HashMap<>(p);command.put("caseId",todo.getBusinessId());command.put("mainLawyerId",p.get("lawyerId"));command.putIfAbsent("assignMethod","manual");command.putIfAbsent("priority","medium");command.putIfAbsent("assignReason","normal");command.put("notifyFlag","Y");service.assign(command,new BusinessActor(id,name,name,todo.getOwnerDeptId(),id==1));}
}
