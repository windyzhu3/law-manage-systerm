package com.ruoyi.system.service.lead;

import static com.ruoyi.system.support.BusinessFixtures.actor;
import static com.ruoyi.system.support.BusinessFixtures.lead;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.business.event.BusinessEventCommand;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.security.BusinessActorProvider;
import com.law.business.security.LeadPermissions;
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
    @Mock private LeadPermissionPolicy permissions;

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
        verify(events).publish(event.capture(),org.mockito.ArgumentMatchers.eq(actor()));
        assertEquals("LEAD_MOVED_TO_DEAD_POOL:7:71", event.getValue().getIdempotencyKey());
    }

    @Test
    void privilegedRestoreReturnsToPublicPoolWithAuditEventAndOptimisticVersion()
    {
        LeadDeadPoolService service=new LeadDeadPoolService(leads,facts,access,actors,schedules,
                events,permissions);
        BizLead stored=lead(7L,"4","0");
        stored.setLeadNo("L-7");stored.setDisposition("DEAD_POOL");stored.setRowVersion(9);
        when(access.requireDeadPoolRestorable(7L)).thenReturn(stored);
        when(actors.current()).thenReturn(actor());
        when(leads.restoreFromDeadPool(7L,"误判恢复",9,"alice")).thenReturn(1);
        when(facts.insertDeadPoolLogIfAbsent(any())).thenAnswer(invocation->{
            BizLeadDeadPoolLog value=invocation.getArgument(0);
            value.setDeadPoolLogId(72L);return 1;
        });

        LeadDeadPoolService.DeadPoolOutcome outcome=
                service.restoreToPublicPool(7L,"restore-1","误判恢复");

        assertEquals(72L,outcome.deadPoolLogId());
        verify(permissions).require(LeadPermissions.DEAD_POOL_RESTORE);
        ArgumentCaptor<BusinessEventCommand> event=ArgumentCaptor.forClass(BusinessEventCommand.class);
        verify(events).publish(event.capture(),org.mockito.ArgumentMatchers.eq(actor()));
        assertEquals("LEAD_DEAD_POOL_RESTORED:7:72",event.getValue().getIdempotencyKey());
    }

    @Test
    void exactRestoreReplayDoesNotMutateLeadAgain()
    {
        LeadDeadPoolService service=new LeadDeadPoolService(leads,facts,access,actors,schedules,
                events,permissions);
        BizLeadDeadPoolLog existing=new BizLeadDeadPoolLog();
        existing.setDeadPoolLogId(72L);existing.setLeadId(7L);existing.setActionType("RESTORE");
        existing.setReasonDetail("误判恢复");
        when(facts.selectDeadPoolLogByIdempotencyKey("LEAD_DEAD_POOL_RESTORE:7:restore-1"))
                .thenReturn(existing);

        LeadDeadPoolService.DeadPoolOutcome outcome=
                service.restoreToPublicPool(7L,"restore-1","误判恢复");

        assertEquals(true,outcome.replayed());
        verify(access,never()).requireDeadPoolRestorable(7L);
        verify(leads,never()).restoreFromDeadPool(any(),any(),any(),any());
    }
}
