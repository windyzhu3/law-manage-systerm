package com.ruoyi.system.service.event;

import java.util.Map;
import org.springframework.stereotype.Component;
import com.law.business.lawcase.dto.CaseConfirmCommand;
import com.law.business.security.BusinessActor;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.spi.TodoCompletionHandler;
import com.ruoyi.system.service.casecenter.CaseConfirmationService;

@Component
public class CaseAcceptanceTodoHandler implements TodoCompletionHandler
{
    private final CaseConfirmationService service;
    private final TodoMapper todos;

    public CaseAcceptanceTodoHandler(CaseConfirmationService service, TodoMapper todos)
    {
        this.service = service; this.todos = todos;
    }

    @Override
    public boolean supports(TodoInstance todo)
    {
        return "CASE_ACCEPT".equals(todo.getTemplateCode());
    }

    @Override
    public void complete(TodoInstance todo, Map<String,Object> payload, Long userId, String userName)
    {
        boolean accepted = Boolean.parseBoolean(String.valueOf(payload.get("accepted")));
        CaseConfirmCommand command = new CaseConfirmCommand();
        command.setConfirmId(longValue(payload.get("confirmId")));
        command.setConfirmResult(accepted ? "accepted" : "rejected");
        command.setRemark(text(payload.get("reason")));
        service.handle(command, new BusinessActor(userId, userName, userName,
                todo.getOwnerDeptId(), Long.valueOf(1L).equals(userId)));
        if (accepted) todos.cancelActiveByBusiness("CASE", todo.getBusinessId(), todo.getTodoId(), userName);
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
