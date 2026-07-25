package com.ruoyi.system.service.lead;

import static com.ruoyi.system.support.BusinessFixtures.actor;
import static com.ruoyi.system.support.BusinessFixtures.lead;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.security.LeadPermissions;
import com.law.business.security.BusinessActorProvider;
import com.law.business.shared.status.LeadStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.mapper.BizLeadMapper;

@ExtendWith(MockitoExtension.class)
class LeadPoolServiceTest
{
    @Mock private BizLeadMapper mapper;
    @Mock private LeadAccessPolicy access;
    @Mock private BusinessActorProvider actors;
    @Mock private BusinessEventPublisher events;
    private LeadPoolService service;

    @BeforeEach
    void setUp()
    {
        service = new LeadPoolService(mapper, access, actors, events);
    }

    @Test
    void concurrentClaimIsRejected()
    {
        BizLead lead = lead(7L, LeadStatus.UNASSIGNED.code(), "0");
        lead.setPoolStatus("1");
        lead.setDisposition("PUBLIC_POOL");
        lead.setRowVersion(5);
        when(access.requireReadable(7L, false, true)).thenReturn(lead);
        when(actors.current()).thenReturn(actor());
        when(mapper.claimLead(7L, 8L, 3L, "alice", LeadStatus.UNASSIGNED.code(), 5)).thenReturn(0);

        ServiceException exception = assertThrows(ServiceException.class, () -> service.claim(7L));

        assertEquals("CONCURRENT_MODIFICATION", exception.getBusinessCode());
        verify(mapper, never()).insertAssignmentLog(any());
        verify(events, never()).publish(any(),any());
    }

    @Test
    void public_pool_claim_cannot_read_dead_pool_lead()
    {
        BizLead lead = lead(7L, LeadStatus.UNASSIGNED.code(), "0");
        lead.setPoolStatus("1");
        lead.setDisposition("DEAD_POOL");
        lead.setRowVersion(5);
        when(access.requireReadable(7L, false, true)).thenReturn(lead);

        ServiceException error = assertThrows(ServiceException.class, () -> service.claim(7L));

        assertEquals("ACCESS_DENIED", error.getBusinessCode());
        verify(mapper, never()).claimLead(any(), any(), any(), any(), any(), any());
    }

    @Test
    void staleMoveToPoolDoesNotWriteLogOrPublishEvent()
    {
        BizLead lead = lead(7L, LeadStatus.FOLLOWING.code(), "0");
        lead.setPoolStatus("0");
        lead.setDisposition("ACTIVE");
        lead.setRowVersion(6);
        when(access.requireOperable(7L)).thenReturn(lead);
        when(actors.current()).thenReturn(actor());
        when(mapper.moveToPool(7L, "stale", "alice", LeadStatus.FOLLOWING.code(), 6)).thenReturn(0);

        try (var security = org.mockito.Mockito.mockStatic(SecurityUtils.class))
        {
            security.when(() -> SecurityUtils.hasPermi(LeadPermissions.MOVE_POOL)).thenReturn(true);
            ServiceException exception = assertThrows(ServiceException.class,
                    () -> service.moveToPool(7L, "stale"));
            assertEquals("CONCURRENT_MODIFICATION", exception.getBusinessCode());
        }
        verify(mapper, never()).insertAssignmentLog(any());
        verify(events, never()).publish(any(),any());
    }
}
