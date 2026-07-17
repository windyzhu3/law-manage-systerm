package com.ruoyi.system.service.event;

import java.util.Map;
import org.springframework.stereotype.Component;
import com.law.business.lawcase.dto.CaseTransferApprovalCommand;
import com.law.business.security.BusinessActor;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.spi.TodoCompletionHandler;
import com.ruoyi.system.service.casecenter.CaseTransferService;

@Component
public class CaseTransferReviewTodoHandler implements TodoCompletionHandler
{
    private final CaseTransferService service;

    public CaseTransferReviewTodoHandler(CaseTransferService service)
    {
        this.service = service;
    }

    @Override
    public boolean supports(TodoInstance todo)
    {
        return "CASE_TRANSFER_REVIEW".equals(todo.getTemplateCode());
    }

    @Override
    public void complete(TodoInstance todo,Map<String,Object> payload,Long userId,String userName)
    {
        CaseTransferApprovalCommand command = new CaseTransferApprovalCommand();
        command.setTransferId(longValue(payload.get("transferId")));
        command.setAction(reviewAction(payload));
        command.setOpinion(text(payload.get("opinion")));
        service.approve(command,new BusinessActor(userId,userName,userName,todo.getOwnerDeptId(),Long.valueOf(1L).equals(userId)));
    }

    private String reviewAction(Map<String,Object> payload)
    {
        Object stable = payload.get("reviewAction");
        return text(stable == null ? payload.get("action") : stable);
    }

    private Long longValue(Object value)
    {
        return value == null ? null : value instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(value));
    }

    private String text(Object value)
    {
        return value == null ? null : String.valueOf(value);
    }
}
