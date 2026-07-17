package com.ruoyi.system.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
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
    @Test void assignmentAndAcceptanceHandlersMapStablePayloads()
    {
        CaseAssignmentService assignments=mock(CaseAssignmentService.class);
        new CaseAssignmentTodoHandler(assignments).complete(todo("CASE_ASSIGN"),Map.of("lawyerId",12L),1L,"admin");
        verify(assignments).assign(argThat(command->Long.valueOf(12L).equals(command.getMainLawyerId())&&"Y".equals(command.getNotifyFlag())),any());
        CaseConfirmationService confirmations=mock(CaseConfirmationService.class);TodoMapper todos=mock(TodoMapper.class);
        TodoInstance accept=todo("CASE_ACCEPT");accept.setTodoId(9L);
        new CaseAcceptanceTodoHandler(confirmations,todos).complete(accept,Map.of("confirmId",33L,"accepted",true,"reason","ok"),12L,"lawyer");
        verify(confirmations).handle(argThat(command->"accepted".equals(command.getConfirmResult())&&Long.valueOf(33L).equals(command.getConfirmId())),any());
        verify(todos).cancelActiveByBusiness("CASE",8L,9L,"lawyer");
    }

    @Test void validatorRejectsStaleAssignment()
    {
        BizCaseMapper mapper=mock(BizCaseMapper.class);when(mapper.selectCaseById(8L)).thenReturn(Map.of("case_status","processing"));
        TodoException error=assertThrows(TodoException.class,()->new CaseTodoValidator(mapper).validate(todo("CASE_ASSIGN"),Map.of("lawyerId",12L)));
        assertEquals("CASE_STATE_STALE",error.getBusinessCode());
    }

    @Test void transferReviewUsesStableKeyWithSnapshotProvenHistoricalFallback()
    {
        CaseTransferService service=mock(CaseTransferService.class);CaseTransferReviewTodoHandler handler=new CaseTransferReviewTodoHandler(service);
        TodoInstance current=todo("CASE_TRANSFER_REVIEW");
        current.setDodSnapshotJson("{\"requiredFields\":[\"transferId\",\"reviewAction\",\"opinion\"]}");
        handler.complete(current,Map.of("transferId",4L,"reviewAction","passed","opinion","approved"),1L,"admin");
        verify(service).approve(argThat(command->Long.valueOf(4L).equals(command.getTransferId())&&"passed".equals(command.getAction())),any());
        assertEquals("TODO_DOD_FIELD_MISSING",assertThrows(TodoException.class,
                ()->handler.complete(current,Map.of("transferId",5L,"action","rejected","opinion","return"),1L,"admin")).getBusinessCode());
        TodoInstance historical=todo("CASE_TRANSFER_REVIEW");
        historical.setDodSnapshotJson("{\"requiredFields\":[\"transferId\",\"action\",\"opinion\"]}");
        handler.complete(historical,Map.of("transferId",5L,"action","rejected","opinion","return"),1L,"admin");
        verify(service).approve(argThat(command->Long.valueOf(5L).equals(command.getTransferId())&&"rejected".equals(command.getAction())),any());
    }

    @Test void transferValidatorRejectsLegacyKeyForCurrentSnapshotButAcceptsHistoricalSnapshot()
    {
        BizCaseMapper mapper=mock(BizCaseMapper.class);when(mapper.selectCaseById(8L)).thenReturn(Map.of("case_status","processing"));
        CaseTodoValidator validator=new CaseTodoValidator(mapper);
        TodoInstance current=todo("CASE_TRANSFER_REVIEW");
        current.setDodSnapshotJson("{\"requiredFields\":[\"transferId\",\"reviewAction\",\"opinion\"]}");
        assertEquals("TODO_DOD_FIELD_MISSING",assertThrows(TodoException.class,
                ()->validator.validate(current,Map.of("transferId",4L,"action","passed","opinion","ok"))).getBusinessCode());
        TodoInstance historical=todo("CASE_TRANSFER_REVIEW");
        historical.setDodSnapshotJson("{\"requiredFields\":[\"transferId\",\"action\",\"opinion\"]}");
        assertDoesNotThrow(()->validator.validate(historical,Map.of("transferId",4L,"action","passed","opinion","ok")));
    }

    private TodoInstance todo(String code){TodoInstance value=new TodoInstance();value.setTemplateCode(code);value.setBusinessType("CASE");value.setBusinessId(8L);value.setOwnerDeptId(3L);return value;}
}
