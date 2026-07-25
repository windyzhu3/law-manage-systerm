package com.ruoyi.system.service.lead;

import static com.ruoyi.system.support.BusinessFixtures.actor;
import static com.ruoyi.system.support.BusinessFixtures.lead;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.lead.dto.LeadRetryCompleteCommand;
import com.law.business.security.BusinessActorProvider;
import com.law.todo.schedule.TodoScheduleService;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.domain.BizLeadRetryRecord;
import com.ruoyi.system.mapper.BizLeadMapper;
import com.ruoyi.system.mapper.LeadFlowMapper;
import com.ruoyi.system.service.ISysDictTypeService;

@ExtendWith(MockitoExtension.class)
class LeadRetryServiceTest
{
    @Mock private BizLeadMapper leads;
    @Mock private LeadFlowMapper facts;
    @Mock private LeadAccessPolicy access;
    @Mock private LeadCallRecordService calls;
    @Mock private BusinessActorProvider actors;
    @Mock private ISysDictTypeService dictionaries;
    @Mock private TodoScheduleService schedules;
    @Mock private LeadPoolService pool;
    @Mock private BusinessEventPublisher events;
    private LeadRetryService service;
    private BizLead stored;

    @BeforeEach
    void setUp()
    {
        service = new LeadRetryService(leads, facts, access, calls, actors, dictionaries, schedules, pool, events);
        stored = lead(7L, "2", "0");
        stored.setLeadNo("L-7");
        stored.setOwnerId(8L);
        stored.setDisposition("ACTIVE");
        stored.setFirstContactResult("UNREACHABLE");
        stored.setRetryStage("T1_AM");
        stored.setRowVersion(5);
    }

    @Test
    void next_window_keeps_plan_active_and_advances_stage()
    {
        LeadRetryCompleteCommand command = command("NEXT_WINDOW");
        command.setNextWindowCode("CALLER_FORGED_STAGE");
        command.setNextRetryTime(Date.from(LocalDateTime.of(2026, 7, 26, 12, 0)
                .atZone(ZoneId.systemDefault()).toInstant()));
        common(command);
        generatedRetry(91L);
        when(schedules.completeOccurrence(org.mockito.ArgumentMatchers.eq(41L),
                org.mockito.ArgumentMatchers.eq("NEXT_WINDOW"), any())).thenReturn(
                new TodoScheduleService.ScheduleCompletion(31L, "T1_AM", 1, "T1_NOON",
                        LocalDateTime.of(2026, 7, 26, 12, 0), false));
        when(leads.advanceRetryStage(7L, "T1_AM", "T1_NOON", 1, command.getNextRetryTime(), 5, "alice"))
                .thenReturn(1);

        LeadRetryService.RetryOutcome outcome = service.completeWindow(command);

        assertEquals("T1_NOON", outcome.nextStage());
        ArgumentCaptor<BizLeadRetryRecord> retry = ArgumentCaptor.forClass(BizLeadRetryRecord.class);
        verify(facts).insertRetryRecordIfAbsent(retry.capture());
        assertEquals("T1_NOON", retry.getValue().getNextWindowCode());
        verify(schedules).completeOccurrence(org.mockito.ArgumentMatchers.eq(41L),
                org.mockito.ArgumentMatchers.eq("NEXT_WINDOW"), any());
        verify(pool, never()).moveToPoolBySystem(any(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    void connected_cancels_future_occurrences_and_publishes_valid()
    {
        LeadRetryCompleteCommand command = command("CONNECTED");
        command.setContactName("Client");
        command.setCity("Shanghai");
        command.setLegalDemand("Demand");
        command.setVisited("0");
        common(command);
        generatedRetry(92L);
        when(schedules.completeOccurrence(org.mockito.ArgumentMatchers.eq(41L),
                org.mockito.ArgumentMatchers.eq("CONNECTED"), any())).thenReturn(
                new TodoScheduleService.ScheduleCompletion(31L, "T1_AM", 1, null, null, false));
        when(leads.completeRetryConnected(7L, "T1_AM", "Client", "Shanghai", "Demand", "0", 5, "alice"))
                .thenReturn(1);

        LeadRetryService.RetryOutcome outcome = service.completeWindow(command);

        assertEquals("CONNECTED", outcome.result());
        verify(schedules).completeOccurrence(org.mockito.ArgumentMatchers.eq(41L),
                org.mockito.ArgumentMatchers.eq("CONNECTED"), any());
        verify(events).publish(any());
    }

    @Test
    void exhausted_moves_to_public_pool_and_clears_owner()
    {
        LeadRetryCompleteCommand command = command("EXHAUSTED");
        common(command);
        generatedRetry(93L);
        when(schedules.completeOccurrence(org.mockito.ArgumentMatchers.eq(41L),
                org.mockito.ArgumentMatchers.eq("EXHAUSTED"), any())).thenReturn(
                new TodoScheduleService.ScheduleCompletion(31L, "T1_AM", 1, null, null, false));
        when(leads.advanceRetryStage(7L, "T1_AM", "EXHAUSTED", 1, null, 5, "alice")).thenReturn(1);
        when(pool.moveToPoolBySystem(stored, 6, "RETRY_EXHAUSTED")).thenReturn(1);

        LeadRetryService.RetryOutcome outcome = service.completeWindow(command);

        assertEquals("EXHAUSTED", outcome.result());
        verify(pool).moveToPoolBySystem(stored, 6, "RETRY_EXHAUSTED");
        verify(events).publish(any());
    }

    @Test
    void repeated_window_completion_returns_existing_outcome()
    {
        LeadRetryCompleteCommand command = command("NEXT_WINDOW");
        BizLeadRetryRecord existing = new BizLeadRetryRecord();
        existing.setRetryRecordId(94L);
        existing.setLeadId(7L);
        existing.setPlanId(31L);
        existing.setWindowCode("T1_AM");
        existing.setAttemptNo(1);
        existing.setContactResult("NEXT_WINDOW");
        existing.setNextWindowCode("T1_NOON");
        when(access.requireReadable(7L, false, true)).thenReturn(stored);
        when(actors.current()).thenReturn(actor());
        when(facts.selectRetryRecordByIdempotencyKey("LEAD_RETRY_OCCURRENCE:41")).thenReturn(existing);

        LeadRetryService.RetryOutcome outcome = service.completeWindow(command);

        assertEquals(true, outcome.replayed());
        assertEquals(94L, outcome.retryRecordId());
        verify(schedules, never()).completeOccurrence(any(), any(), any());
        verify(leads, never()).advanceRetryStage(any(), any(), any(), any(), any(), any(), any());
    }

    private void common(LeadRetryCompleteCommand command)
    {
        when(access.requireReadable(7L, false, true)).thenReturn(stored);
        when(actors.current()).thenReturn(actor());
        SysDictData value = new SysDictData();
        value.setDictValue(command.getResult());
        when(dictionaries.selectDictDataByType("law_retry_result")).thenReturn(List.of(value));
        when(calls.recordForLead(command.getCallRecord(), stored, actor())).thenReturn(
                new LeadCallRecordService.CallRecordOutcome(51L, false));
    }

    private void generatedRetry(Long id)
    {
        when(facts.insertRetryRecordIfAbsent(any())).thenAnswer(invocation -> {
            BizLeadRetryRecord value = invocation.getArgument(0);
            value.setRetryRecordId(id);
            return 1;
        });
    }

    private LeadRetryCompleteCommand command(String result)
    {
        LeadRetryCompleteCommand value = new LeadRetryCompleteCommand();
        value.setLeadId(7L);
        value.setTodoId(21L);
        value.setOccurrenceId(41L);
        value.setOccurrenceNo(1);
        value.setPlanId(31L);
        value.setWindowCode("T1_AM");
        value.setAttemptNo(1);
        value.setResult(result);
        value.setCallRecord(LeadCallRecordServiceTest.command(7L, 21L, "retry-call-" + result));
        return value;
    }
}
