package com.ruoyi.system.service.lead;

import static com.ruoyi.system.support.BusinessFixtures.actor;
import static com.ruoyi.system.support.BusinessFixtures.lead;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.security.BusinessActorProvider;
import com.law.business.shared.status.LeadStatus;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.mapper.BizLeadMapper;

@ExtendWith(MockitoExtension.class)
class LeadAssignmentServiceTest
{
    @Mock private BizLeadMapper mapper;
    @Mock private LeadAccessPolicy access;
    @Mock private BusinessActorProvider actors;
    @Mock private BusinessEventPublisher events;
    private LeadAssignmentService service;

    @BeforeEach
    void setUp()
    {
        service = new LeadAssignmentService(mapper, access, actors, events);
    }

    @Test
    void assignmentUsesExpectedStatusAndGeneratedLogId()
    {
        BizLead lead = lead(7L, LeadStatus.UNASSIGNED.code(), "0");
        lead.setPoolStatus("1");
        when(access.requireReadable(7L, false, true)).thenReturn(lead);
        when(actors.current()).thenReturn(actor());
        when(mapper.assignLead(7L, 9L, "alice", LeadStatus.UNASSIGNED.code())).thenReturn(1);
        when(mapper.insertAssignmentLog(argThat(log -> "assign".equals(log.get("actionType")))))
                .thenAnswer(invocation -> { ((Map<String, Object>) invocation.getArgument(0)).put("logId", 21L); return 1; });

        assertEquals(1, service.assign(7L, 9L, "首次分配"));

        verify(events).publish(argThat(event -> "LEAD_ASSIGNED:7:21".equals(event.getIdempotencyKey())
                && Long.valueOf(9L).equals(event.getPayload().get("toOwnerId"))));
    }
}
