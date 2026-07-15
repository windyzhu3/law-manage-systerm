package com.ruoyi.system.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;
import com.ruoyi.system.mapper.BizCaseMapper;
import com.ruoyi.system.service.casecenter.CaseAssignmentService;
import com.ruoyi.system.service.casecenter.CaseConfirmationService;
import com.ruoyi.system.service.casecenter.CaseTransferService;
import com.ruoyi.system.service.event.CaseAcceptanceTodoHandler;
import com.ruoyi.system.service.event.CaseAssignmentTodoHandler;
import com.ruoyi.system.service.event.CaseTodoValidator;
import com.ruoyi.system.service.event.CaseTransferReviewTodoHandler;

class CaseManagementTodoFlowTest
{
    @Test
    void assignmentAndAcceptanceHandlersMapStablePayloads()
    {
        CaseAssignmentService assignments = mock(CaseAssignmentService.class);
        new CaseAssignmentTodoHandler(assignments).complete(todo("CASE_ASSIGN"), Map.of("lawyerId", 12L), 1L, "admin");
        verify(assignments).assign(argThat(command -> Long.valueOf(12L).equals(command.getMainLawyerId())
                && "Y".equals(command.getNotifyFlag())), any());

        CaseConfirmationService confirmations = mock(CaseConfirmationService.class);
        TodoMapper todos = mock(TodoMapper.class);
        TodoInstance accept = todo("CASE_ACCEPT"); accept.setTodoId(9L);
        new CaseAcceptanceTodoHandler(confirmations, todos).complete(accept,
                Map.of("confirmId", 33L, "accepted", true, "reason", "同意"), 12L, "lawyer");
        verify(confirmations).handle(argThat(row -> "accepted".equals(row.get("confirmResult"))), any());
        verify(todos).cancelActiveByBusiness("CASE", 8L, 9L, "lawyer");
    }

    @Test
    void validatorRejectsStaleAssignment()
    {
        BizCaseMapper mapper = mock(BizCaseMapper.class);
        when(mapper.selectCaseById(8L)).thenReturn(Map.of("case_status", "processing"));
        TodoException error = assertThrows(TodoException.class,
                () -> new CaseTodoValidator(mapper).validate(todo("CASE_ASSIGN"), Map.of("lawyerId", 12L)));
        assertEquals("CASE_STATE_STALE", error.getBusinessCode());
    }

    @Test
    void transferReviewUsesTypedCommandAndActor()
    {
        CaseTransferService service = mock(CaseTransferService.class);
        new CaseTransferReviewTodoHandler(service).complete(todo("CASE_TRANSFER_REVIEW"),
                Map.of("transferId", 4L, "action", "passed", "opinion", "同意"), 1L, "admin");
        verify(service).approve(argThat(command -> Long.valueOf(4L).equals(command.getTransferId())
                && "passed".equals(command.getAction())), any());
    }

    private TodoInstance todo(String code)
    {
        TodoInstance value = new TodoInstance(); value.setTemplateCode(code); value.setBusinessType("CASE");
        value.setBusinessId(8L); value.setOwnerDeptId(3L); return value;
    }
}
