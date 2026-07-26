package com.ruoyi.system.service.lead;

import static com.ruoyi.system.support.BusinessFixtures.actor;
import static com.ruoyi.system.support.BusinessFixtures.lead;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import com.ruoyi.common.exception.ServiceException;
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
    void third_unconnected_attempt_completes_current_window_and_uses_server_selected_next_window()
    {
        LeadRetryCompleteCommand command = command("NEXT_WINDOW");
        command.setNextWindowCode("CALLER_FORGED_STAGE");
        command.setNextRetryTime(Date.from(LocalDateTime.of(2026, 7, 26, 12, 0)
                .atZone(ZoneId.of("UTC")).toInstant()));
        common(command,context(7L,21L,"T1_AM",3),3);
        generatedRetry(91L);
        when(schedules.completeAttemptLimit(any(TodoScheduleService.ScheduleOccurrenceContext.class),
                any())).thenReturn(
                completion("T1_NOON",LocalDateTime.of(2026,7,26,12,0)));
        Date authoritativeNext=Date.from(LocalDateTime.of(2026,7,26,12,0)
                .atZone(ZoneId.of("Asia/Shanghai")).toInstant());
        when(leads.advanceRetryStage(7L, "T1_AM", "T1_NOON", 3, authoritativeNext, 5, "alice"))
                .thenReturn(1);

        LeadRetryService.RetryOutcome outcome = service.completeWindow(command);

        assertEquals("T1_NOON", outcome.nextStage());
        assertEquals(3,outcome.attemptNo());
        ArgumentCaptor<BizLeadRetryRecord> retry = ArgumentCaptor.forClass(BizLeadRetryRecord.class);
        verify(facts).insertRetryRecordIfAbsent(retry.capture());
        assertEquals("T1_NOON", retry.getValue().getNextWindowCode());
        verify(schedules).completeAttemptLimit(any(TodoScheduleService.ScheduleOccurrenceContext.class),any());
        verify(pool, never()).moveToPoolBySystem(any(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    void first_unconnected_t0_attempt_uses_immutable_call_count_and_continues_current_todo()
    {
        stored.setRetryStage("T0");
        LeadRetryCompleteCommand command=command("NEXT_WINDOW");
        common(command,context(7L,21L,"T0",3),1);
        generatedRetry(95L);
        when(leads.advanceRetryStage(7L,"T0","T0",1,stored.getNextRetryTime(),5,"alice"))
                .thenReturn(1);

        LeadRetryService.RetryOutcome outcome=service.completeWindow(command);

        assertEquals("CONTINUE_CURRENT_WINDOW",outcome.result());
        assertEquals(1,outcome.attemptNo());
        assertEquals("T0",outcome.nextStage());
        verify(schedules,never()).completeOccurrence(
                any(TodoScheduleService.ScheduleOccurrenceContext.class),any(),any());
    }

    @Test
    void second_unconnected_attempt_uses_immutable_call_count_and_stays_on_current_todo()
    {
        LeadRetryCompleteCommand command=command("NEXT_WINDOW");
        common(command,context(7L,21L,"T1_AM",3),2);
        generatedRetry(97L);
        when(leads.advanceRetryStage(7L,"T1_AM","T1_AM",2,stored.getNextRetryTime(),5,"alice"))
                .thenReturn(1);

        LeadRetryService.RetryOutcome outcome=service.completeWindow(command);

        assertEquals("CONTINUE_CURRENT_WINDOW",outcome.result());
        assertEquals(2,outcome.attemptNo());
        assertEquals("T1_AM",outcome.nextStage());
        verify(schedules,never()).completeOccurrence(
                any(TodoScheduleService.ScheduleOccurrenceContext.class),any(),any());
        verify(schedules,never()).completeAttemptLimit(
                any(TodoScheduleService.ScheduleOccurrenceContext.class),any());
    }

    @Test
    void configured_attempt_limit_drives_server_owned_exhaustion()
    {
        LeadRetryCompleteCommand command=command("NEXT_WINDOW");
        common(command,context(7L,21L,"T1_AM",2),2);
        generatedRetry(96L);
        when(schedules.completeAttemptLimit(any(TodoScheduleService.ScheduleOccurrenceContext.class),any()))
                .thenReturn(completion(null,null));
        when(leads.advanceRetryStage(7L,"T1_AM","EXHAUSTED",2,null,5,"alice")).thenReturn(1);
        when(pool.moveToPoolBySystem(stored,6,"RETRY_EXHAUSTED")).thenReturn(1);

        LeadRetryService.RetryOutcome outcome=service.completeWindow(command);

        assertEquals("EXHAUSTED",outcome.result());
        assertEquals(2,outcome.attemptNo());
        verify(schedules).cancelPlan(org.mockito.ArgumentMatchers.eq(31L),
                org.mockito.ArgumentMatchers.eq("EXHAUSTED"),any());
        verify(pool).moveToPoolBySystem(stored,6,"RETRY_EXHAUSTED");
    }

    @Test
    void connected_cancels_future_occurrences_and_publishes_valid()
    {
        LeadRetryCompleteCommand command = command("CONNECTED");
        command.setContactName("Client");
        command.setCity("Shanghai");
        command.setLegalDemand("Demand");
        command.setVisited("0");
        common(command,context(7L,21L),1);
        generatedRetry(92L);
        when(schedules.completeOccurrence(any(TodoScheduleService.ScheduleOccurrenceContext.class),
                org.mockito.ArgumentMatchers.eq("CONNECTED"), any())).thenReturn(
                completion(null,null));
        when(leads.completeRetryConnected(7L, "T1_AM", "Client", "Shanghai", "Demand", "0", 5, "alice"))
                .thenReturn(1);

        LeadRetryService.RetryOutcome outcome = service.completeWindow(command);

        assertEquals("CONNECTED", outcome.result());
        verify(schedules).completeOccurrence(any(TodoScheduleService.ScheduleOccurrenceContext.class),
                org.mockito.ArgumentMatchers.eq("CONNECTED"), any());
        verify(events).publish(any(),org.mockito.ArgumentMatchers.eq(actor()));
    }

    @Test
    void exhausted_moves_to_public_pool_and_clears_owner()
    {
        LeadRetryCompleteCommand command = command("EXHAUSTED");
        common(command,context(7L,21L),3);
        generatedRetry(93L);
        when(schedules.completeAttemptLimit(any(TodoScheduleService.ScheduleOccurrenceContext.class),
                any())).thenReturn(
                completion(null,null));
        when(leads.advanceRetryStage(7L, "T1_AM", "EXHAUSTED", 3, null, 5, "alice")).thenReturn(1);
        when(pool.moveToPoolBySystem(stored, 6, "RETRY_EXHAUSTED")).thenReturn(1);

        LeadRetryService.RetryOutcome outcome = service.completeWindow(command);

        assertEquals("EXHAUSTED", outcome.result());
        verify(schedules).cancelPlan(org.mockito.ArgumentMatchers.eq(31L),
                org.mockito.ArgumentMatchers.eq("EXHAUSTED"),any());
        verify(pool).moveToPoolBySystem(stored, 6, "RETRY_EXHAUSTED");
        verify(events).publish(any(),org.mockito.ArgumentMatchers.eq(actor()));
    }

    @Test
    void client_cannot_exhaust_a_window_before_its_configured_attempt_limit()
    {
        LeadRetryCompleteCommand command=command("EXHAUSTED");
        common(command,context(7L,21L,"T1_AM",3),2);

        ServiceException error=assertThrows(ServiceException.class,()->service.completeWindow(command));

        assertEquals("LEAD_RETRY_MAX_ATTEMPTS_NOT_REACHED",error.getMessage());
        verify(facts,never()).insertRetryRecordIfAbsent(any());
        verify(schedules,never()).completeAttemptLimit(
                any(TodoScheduleService.ScheduleOccurrenceContext.class),any());
        verify(leads,never()).advanceRetryStage(any(),any(),any(),any(),any(),any(),any());
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
        existing.setTodoId(21L);
        existing.setCallRecordId(51L);
        common(command,context(7L,21L),0);
        when(facts.selectRetryRecordByIdempotencyKey("LEAD_RETRY_ATTEMPT:41:51")).thenReturn(existing);

        LeadRetryService.RetryOutcome outcome = service.completeWindow(command);

        assertEquals(true, outcome.replayed());
        assertEquals(94L, outcome.retryRecordId());
        verify(schedules, never()).completeOccurrence(
                any(TodoScheduleService.ScheduleOccurrenceContext.class),any(),any());
        verify(leads, never()).advanceRetryStage(any(), any(), any(), any(), any(), any(), any());
        verify(facts,never()).countCallRecordsForLeadTodo(any(),any());
    }

    @Test
    void identical_exhausted_retry_replays_server_derived_next_window_without_mutation()
    {
        LeadRetryCompleteCommand command=command("EXHAUSTED");
        common(command,context(7L,21L),3);
        java.util.concurrent.atomic.AtomicReference<BizLeadRetryRecord> persisted=
                new java.util.concurrent.atomic.AtomicReference<>();
        when(facts.insertRetryRecordIfAbsent(any())).thenAnswer(invocation->{
            BizLeadRetryRecord value=invocation.getArgument(0);
            value.setRetryRecordId(98L);persisted.set(value);return 1;
        });
        when(facts.selectRetryRecordByIdempotencyKey("LEAD_RETRY_ATTEMPT:41:51"))
                .thenAnswer(invocation->persisted.get());
        when(schedules.completeAttemptLimit(any(TodoScheduleService.ScheduleOccurrenceContext.class),any()))
                .thenReturn(completion("T1_NOON",LocalDateTime.of(2026,7,26,12,0)));
        Date next=Date.from(LocalDateTime.of(2026,7,26,12,0)
                .atZone(ZoneId.of("Asia/Shanghai")).toInstant());
        when(leads.advanceRetryStage(7L,"T1_AM","T1_NOON",3,next,5,"alice")).thenReturn(1);

        LeadRetryService.RetryOutcome first=service.completeWindow(command);
        LeadRetryService.RetryOutcome replay=service.completeWindow(command);

        assertEquals("NEXT_WINDOW",first.result());
        assertEquals("NEXT_WINDOW",replay.result());
        assertEquals(true,replay.replayed());
        assertEquals(first.retryRecordId(),replay.retryRecordId());
        verify(facts,times(1)).countCallRecordsForLeadTodo(7L,21L);
        verify(facts,times(1)).insertRetryRecordIfAbsent(any());
        verify(schedules,times(1)).completeAttemptLimit(
                any(TodoScheduleService.ScheduleOccurrenceContext.class),any());
        verify(leads,times(1)).advanceRetryStage(7L,"T1_AM","T1_NOON",3,next,5,"alice");
    }

    @Test
    void same_call_cannot_change_from_nonconnected_to_connected_intent()
    {
        LeadRetryCompleteCommand command=command("CONNECTED");
        command.setContactName("Client");command.setCity("Shanghai");
        command.setLegalDemand("Demand");command.setVisited("0");
        BizLeadRetryRecord existing=new BizLeadRetryRecord();
        existing.setRetryRecordId(99L);existing.setLeadId(7L);existing.setPlanId(31L);
        existing.setWindowCode("T1_AM");existing.setAttemptNo(3);
        existing.setContactResult("NEXT_WINDOW");existing.setNextWindowCode("T1_NOON");
        existing.setTodoId(21L);existing.setCallRecordId(51L);
        common(command,context(7L,21L),0);
        when(facts.selectRetryRecordByIdempotencyKey("LEAD_RETRY_ATTEMPT:41:51"))
                .thenReturn(existing);

        ServiceException error=assertThrows(ServiceException.class,
                ()->service.completeWindow(command));

        assertEquals("Retry occurrence belongs to another outcome",error.getMessage());
        verify(facts,never()).countCallRecordsForLeadTodo(any(),any());
        verify(schedules,never()).completeOccurrence(
                any(TodoScheduleService.ScheduleOccurrenceContext.class),any(),any());
    }

    @Test
    void cross_lead_occurrence_is_rejected_before_call_fact_or_schedule_mutation()
    {
        LeadRetryCompleteCommand command = command("CONNECTED");
        when(access.requireReadable(7L, false, true)).thenReturn(stored);
        when(actors.current()).thenReturn(actor());
        when(schedules.lockOccurrenceContext(41L)).thenReturn(context(99L, 21L));

        ServiceException error = assertThrows(ServiceException.class, () -> service.completeWindow(command));

        assertEquals("LEAD_RETRY_STATE_INVALID", error.getMessage());
        verify(calls, never()).recordForLead(any(), any(), any(), any());
        verify(schedules, never()).completeOccurrence(any(TodoScheduleService.ScheduleOccurrenceContext.class),
                any(), any());
        verify(leads, never()).completeRetryConnected(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void cross_todo_occurrence_is_rejected_before_call_fact_or_schedule_mutation()
    {
        LeadRetryCompleteCommand command = command("NEXT_WINDOW");
        when(access.requireReadable(7L, false, true)).thenReturn(stored);
        when(actors.current()).thenReturn(actor());
        when(schedules.lockOccurrenceContext(41L)).thenReturn(context(7L, 999L));

        assertThrows(ServiceException.class, () -> service.completeWindow(command));

        verify(calls, never()).recordForLead(any(), any(), any(), any());
        verify(schedules, never()).completeOccurrence(any(TodoScheduleService.ScheduleOccurrenceContext.class),
                any(), any());
    }

    private TodoScheduleService.ScheduleOccurrenceContext context(Long businessId, Long todoId)
    {
        return context(businessId,todoId,"T1_AM",3);
    }

    private TodoScheduleService.ScheduleOccurrenceContext context(Long businessId,Long todoId,
            String windowCode,int maxAttempts)
    {
        return new TodoScheduleService.ScheduleOccurrenceContext(41L, 31L, 51L, windowCode, 1,
                todoId, "LEAD", businessId, "Asia/Shanghai", 99L, 11L, maxAttempts,
                "MATERIALIZED", null);
    }

    private void common(LeadRetryCompleteCommand command,
            TodoScheduleService.ScheduleOccurrenceContext context,int callCount)
    {
        when(access.requireReadable(7L, false, true)).thenReturn(stored);
        when(actors.current()).thenReturn(actor());
        when(schedules.lockOccurrenceContext(41L)).thenReturn(context);
        SysDictData value = new SysDictData();
        value.setDictValue(command.getResult());
        when(dictionaries.selectDictDataByType("law_retry_result")).thenReturn(List.of(value));
        when(calls.recordForLead(command.getCallRecord(), stored, actor(),21L)).thenReturn(
                new LeadCallRecordService.CallRecordOutcome(51L, false));
        if(callCount>0)when(facts.countCallRecordsForLeadTodo(7L,21L)).thenReturn(callCount);
    }

    private TodoScheduleService.ScheduleCompletion completion(String next,LocalDateTime nextAt)
    {
        return new TodoScheduleService.ScheduleCompletion(31L,"T1_AM",1,next,nextAt,false,
                "LEAD",7L,21L,"Asia/Shanghai",99L,11L,1L,0,3);
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
