package com.ruoyi.system.service.casecenter;

import static com.ruoyi.system.support.BusinessFixtures.actor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.lawcase.dto.CaseTransferApprovalCommand;
import com.law.business.lawcase.dto.CaseTransferCommand;
import com.law.business.security.BusinessActorProvider;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.mapper.BizCaseMapper;
import com.ruoyi.system.service.ISysUserService;

@ExtendWith(MockitoExtension.class)
class CaseTransferServiceTest
{
    @Mock private BizCaseMapper mapper;
    @Mock private CaseAccessPolicy access;
    @Mock private ISysUserService users;
    @Mock private CaseWorkflowSupport support;
    @Mock private BusinessEventPublisher events;
    @Mock private BusinessActorProvider actors;
    @InjectMocks private CaseTransferService service;

    @Test
    void requestUsesGeneratedTransferIdInStableEventKey()
    {
        CaseTransferCommand command = requestCommand();
        when(actors.current()).thenReturn(actor());
        when(access.requireTransferRequestable(8L, actor())).thenReturn(Map.of(
                "case_id", 8L, "case_no", "CS-8", "case_status", "processing",
                "main_lawyer_id", 10L, "main_lawyer_name", "原律师"));
        SysUser target = new SysUser(); target.setUserId(12L); target.setNickName("新律师"); target.setStatus("0");
        when(users.selectUserById(12L)).thenReturn(target);
        when(mapper.selectLawyerProfileByUserId(12L)).thenReturn(Map.of(
                "profileId", 22L, "assignEnabled", "Y", "lawyerRole", "lawyer"));
        when(mapper.insertTransfer(anyMap())).thenAnswer(invocation -> {
            invocation.<Map<String,Object>>getArgument(0).put("transferId", 61L);
            return 1;
        });
        when(mapper.updateCaseStatus(anyMap())).thenReturn(1);

        assertEquals(1, service.request(command));

        verify(events).publish(org.mockito.ArgumentMatchers.argThat(event ->
                "CASE_TRANSFER_REQUESTED:8:61".equals(event.getIdempotencyKey())
                && Long.valueOf(61L).equals(event.getPayload().get("transferId"))
                && Long.valueOf(12L).equals(event.getPayload().get("toLawyerId"))));
    }

    @Test
    void approvalCreatesConfirmationAndPublishesStableEventKey()
    {
        CaseTransferApprovalCommand command = approvalCommand("passed");
        when(access.requireTransferApprovable(61L, actor())).thenReturn(new CaseTransferContext(
                61L, 8L, "CS-8", "TR-61", "pending", 10L, "原律师", 12L, "新律师"));
        when(mapper.updateTransferApproval(anyMap())).thenReturn(1);
        when(mapper.updateCaseStatus(anyMap())).thenReturn(1);
        when(mapper.insertConfirm(anyMap())).thenAnswer(invocation -> {
            invocation.<Map<String,Object>>getArgument(0).put("confirmId", 71L);
            return 1;
        });

        assertEquals(1, service.approve(command, actor()));

        verify(events).publish(org.mockito.ArgumentMatchers.argThat(event ->
                "CASE_TRANSFER_APPROVED:8:61".equals(event.getIdempotencyKey())
                && Long.valueOf(61L).equals(event.getPayload().get("transferId"))
                && Long.valueOf(71L).equals(event.getPayload().get("confirmId"))
                && Boolean.TRUE.equals(event.getPayload().get("requiresAcceptance"))));
    }

    @Test
    void approvalRejectsUnsupportedActionBeforeDatabaseWrite()
    {
        CaseTransferApprovalCommand command = approvalCommand("invalid");

        ServiceException error = assertThrows(ServiceException.class, () -> service.approve(command, actor()));

        assertEquals("VALIDATION_FAILED", error.getBusinessCode());
        verify(mapper, never()).updateTransferApproval(anyMap());
    }

    private CaseTransferCommand requestCommand()
    {
        CaseTransferCommand value = new CaseTransferCommand();
        value.setCaseId(8L); value.setToLawyerId(12L); value.setTransferReason("capacity");
        value.setRiskLevel("medium"); value.setDetail("调整主办律师");
        return value;
    }

    private CaseTransferApprovalCommand approvalCommand(String action)
    {
        CaseTransferApprovalCommand value = new CaseTransferApprovalCommand();
        value.setTransferId(61L); value.setAction(action); value.setOpinion("同意");
        return value;
    }
}
