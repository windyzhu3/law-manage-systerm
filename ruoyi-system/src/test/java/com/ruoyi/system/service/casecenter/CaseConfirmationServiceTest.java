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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.lawcase.dto.CaseConfirmCommand;
import com.law.business.security.BusinessActorProvider;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.mapper.BizCaseMapper;

@ExtendWith(MockitoExtension.class)
class CaseConfirmationServiceTest
{
    @Mock private BizCaseMapper mapper;
    @Mock private CaseAccessPolicy access;
    @Mock private CaseWorkflowSupport support;
    @Mock private BusinessEventPublisher events;
    @Mock private BusinessActorProvider actors;
    @InjectMocks private CaseConfirmationService service;

    @Test
    void rejectsUnsupportedResultBeforeDatabaseRead()
    {
        CaseConfirmCommand command = command("unknown");

        ServiceException error = assertThrows(ServiceException.class, () -> service.handle(command, actor()));

        assertEquals("VALIDATION_FAILED", error.getBusinessCode());
        verify(access, never()).requireConfirmable(31L, actor());
    }

    @Test
    void acceptanceUsesExactConfirmingStateAndStableEventKey()
    {
        CaseConfirmCommand command = command("accepted");
        when(access.requireConfirmable(31L, actor())).thenReturn(new CaseConfirmContext(
                31L, 8L, "CS-8", "pending", 12L, "接案律师", "accept"));
        when(mapper.updateConfirm(anyMap())).thenReturn(1);
        when(mapper.updateCaseConfirmResult(anyMap())).thenReturn(1);

        assertEquals(1, service.handle(command, actor()));

        ArgumentCaptor<Map<String,Object>> update = mapCaptor();
        verify(mapper).updateCaseConfirmResult(update.capture());
        assertEquals("confirming", update.getValue().get("expectedStatus"));
        assertEquals("processing", update.getValue().get("caseStatus"));
        verify(events).publish(org.mockito.ArgumentMatchers.argThat(event ->
                "CASE_ACCEPTED:8:31".equals(event.getIdempotencyKey())
                && Long.valueOf(31L).equals(event.getPayload().get("confirmId"))));
    }

    @Test
    void rejectionReturnsCaseToPendingAndClearsAssignment()
    {
        CaseConfirmCommand command = command("rejected");
        when(access.requireConfirmable(31L, actor())).thenReturn(new CaseConfirmContext(
                31L, 8L, "CS-8", "pending", 12L, "接案律师", "accept"));
        when(mapper.updateConfirm(anyMap())).thenReturn(1);
        when(mapper.updateCaseConfirmResult(anyMap())).thenReturn(1);

        service.handle(command, actor());

        verify(mapper).updateCaseConfirmResult(org.mockito.ArgumentMatchers.argThat(update ->
                "pending".equals(update.get("caseStatus"))
                && Boolean.TRUE.equals(update.get("clearAssignment"))));
        verify(events).publish(org.mockito.ArgumentMatchers.argThat(event ->
                "CASE_REJECTED:8:31".equals(event.getIdempotencyKey())));
    }

    private CaseConfirmCommand command(String result)
    {
        CaseConfirmCommand value = new CaseConfirmCommand();
        value.setConfirmId(31L); value.setConfirmResult(result); value.setRemark("处理意见");
        return value;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private ArgumentCaptor<Map<String,Object>> mapCaptor()
    {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
    }
}
