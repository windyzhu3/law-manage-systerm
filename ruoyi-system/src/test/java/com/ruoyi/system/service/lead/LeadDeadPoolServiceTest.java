package com.ruoyi.system.service.lead;

import static com.ruoyi.system.support.BusinessFixtures.actor;
import static com.ruoyi.system.support.BusinessFixtures.lead;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.business.event.BusinessEventCommand;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.security.BusinessActorProvider;
import com.law.todo.schedule.TodoScheduleService;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.domain.BizLeadDeadPoolLog;
import com.ruoyi.system.mapper.BizLeadMapper;
import com.ruoyi.system.mapper.LeadFlowMapper;

@ExtendWith(MockitoExtension.class)
class LeadDeadPoolServiceTest
{
    @Mock private BizLeadMapper leads;
    @Mock private LeadFlowMapper facts;
    @Mock private LeadAccessPolicy access;
    @Mock private BusinessActorProvider actors;
    @Mock private TodoScheduleService schedules;
    @Mock private BusinessEventPublisher events;

    @Test
    void confirmed_invalid_cancels_retries_and_writes_dead_pool_log()
    {
        LeadDeadPoolService service = new LeadDeadPoolService(leads, facts, access, actors, schedules, events);
        BizLead stored = lead(7L, "2", "0");
        stored.setLeadNo("L-7");
        stored.setDisposition("ACTIVE");
        stored.setInvalidReviewStatus("CONFIRMED");
        stored.setRowVersion(6);
        when(leads.moveToDeadPool(7L, "NO_DEMAND", 6, "alice")).thenReturn(1);
        when(facts.selectActiveRetryPlanId(7L)).thenReturn(81L);
        when(facts.insertDeadPoolLogIfAbsent(any())).thenAnswer(invocation -> {
            BizLeadDeadPoolLog value = invocation.getArgument(0);
            value.setDeadPoolLogId(71L);
            return 1;
        });

        LeadDeadPoolService.DeadPoolOutcome outcome = service.enterConfirmedInvalid(
                stored, 6, 21L, 61L, "NO_DEMAND", "confirmed", actor());

        assertEquals(71L, outcome.deadPoolLogId());
        verify(schedules).cancelPlan(org.mockito.ArgumentMatchers.eq(81L),
                org.mockito.ArgumentMatchers.eq("TRUE_INVALID"), any());
        ArgumentCaptor<BusinessEventCommand> event = ArgumentCaptor.forClass(BusinessEventCommand.class);
        verify(events).publish(event.capture());
        assertEquals("LEAD_MOVED_TO_DEAD_POOL:7:71", event.getValue().getIdempotencyKey());
    }
}
