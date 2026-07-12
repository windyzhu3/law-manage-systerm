package com.ruoyi.system.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;
import com.ruoyi.system.mapper.BizCaseMapper;
import com.ruoyi.system.service.casecenter.*;
import com.ruoyi.system.service.event.*;

class CaseManagementTodoFlowTest
{
    @Test void assignmentAndAcceptanceHandlersMapStablePayloads()
    {
        CaseAssignmentService assignments=mock(CaseAssignmentService.class);CaseAssignmentTodoHandler assign=new CaseAssignmentTodoHandler(assignments);assign.complete(todo("CASE_ASSIGN"),Map.of("lawyerId",12L),1L,"admin");verify(assignments).assign(argThat(row->Long.valueOf(12L).equals(row.get("mainLawyerId"))&&"Y".equals(row.get("notifyFlag"))),any());
        CaseConfirmationService confirmations=mock(CaseConfirmationService.class);new CaseAcceptanceTodoHandler(confirmations).complete(todo("CASE_ACCEPT"),Map.of("confirmId",33L,"accepted",false,"reason","冲突"),12L,"lawyer");verify(confirmations).handle(argThat(row->"rejected".equals(row.get("confirmResult"))),any());
    }
    @Test void validatorRejectsStaleAssignment()
    {
        BizCaseMapper mapper=mock(BizCaseMapper.class);when(mapper.selectCaseById(8L)).thenReturn(Map.of("case_status","processing"));TodoException error=assertThrows(TodoException.class,()->new CaseTodoValidator(mapper).validate(todo("CASE_ASSIGN"),Map.of("lawyerId",12L)));assertEquals("CASE_STATE_STALE",error.getBusinessCode());
    }
    @Test void transferReviewUsesTypedActor()
    {
        CaseTransferService service=mock(CaseTransferService.class);new CaseTransferReviewTodoHandler(service).complete(todo("CASE_TRANSFER_REVIEW"),Map.of("transferId",4L,"action","passed","opinion","同意"),1L,"admin");verify(service).approve(anyMap(),any());
    }
    private TodoInstance todo(String code){TodoInstance value=new TodoInstance();value.setTemplateCode(code);value.setBusinessType("CASE");value.setBusinessId(8L);value.setOwnerDeptId(3L);return value;}
}
