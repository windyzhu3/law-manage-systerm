package com.ruoyi.system.service.event;

import java.util.Map;
import org.springframework.stereotype.Component;
import com.law.business.security.BusinessActor;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.spi.TodoCompletionHandler;
import com.ruoyi.system.service.casecenter.CaseAssignmentService;
import com.law.business.lawcase.dto.CaseAssignmentCommand;

@Component
public class CaseAssignmentTodoHandler implements TodoCompletionHandler
{
    private final CaseAssignmentService service;public CaseAssignmentTodoHandler(CaseAssignmentService service){this.service=service;}
    @Override public boolean supports(TodoInstance todo){return "CASE_ASSIGN".equals(todo.getTemplateCode())||"CASE_REASSIGN".equals(todo.getTemplateCode());}
    @Override public void complete(TodoInstance todo,Map<String,Object> p,Long id,String name){CaseAssignmentCommand command=new CaseAssignmentCommand();command.setCaseId(todo.getBusinessId());command.setMainLawyerId(Long.valueOf(String.valueOf(p.get("lawyerId"))));command.setAssignMethod(text(p.get("assignMethod"),"manual"));command.setPriority(text(p.get("priority"),"medium"));command.setAssignReason(text(p.get("assignReason"),"normal"));command.setNotifyFlag("Y");command.setAssistantLawyerIds(text(p.get("assistantLawyerIds"),null));service.assign(command,new BusinessActor(id,name,name,todo.getOwnerDeptId(),id==1));}
    private String text(Object value,String fallback){return value==null||String.valueOf(value).isBlank()?fallback:String.valueOf(value);}
}
