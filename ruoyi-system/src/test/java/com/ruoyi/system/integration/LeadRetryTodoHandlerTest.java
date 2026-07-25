package com.ruoyi.system.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.business.lead.dto.LeadRetryCompleteCommand;
import com.law.todo.application.CompletionContext;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.schedule.TodoScheduleService;
import com.law.todo.spi.TodoCompletionHandler.CompletionResult;
import com.ruoyi.system.service.event.LeadRetryTodoHandler;
import com.ruoyi.system.service.lead.LeadRetryService;
import com.ruoyi.system.service.lead.LeadRetryService.RetryOutcome;

@ExtendWith(MockitoExtension.class)
class LeadRetryTodoHandlerTest
{
    @Mock LeadRetryService retries;
    @Mock TodoScheduleService schedules;

    @Test
    void server_outcome_retains_current_todo_and_replaces_client_branch_authority()
    {
        LeadRetryTodoHandler handler=new LeadRetryTodoHandler(retries,schedules);
        TodoInstance todo=todo();
        when(schedules.requireOccurrenceIdForTodo("81:T1_AM:1",31L)).thenReturn(91L);
        when(retries.completeWindow(org.mockito.ArgumentMatchers.any())).thenReturn(
                new RetryOutcome("CONTINUE_CURRENT_WINDOW",71L,"T1_AM",1,false));

        CompletionResult result=handler.handle(CompletionContext.human(todo,Map.of(
                "result","EXHAUSTED",
                "planId",998L,
                "nextWindowCode","CLIENT_CHOICE",
                "contactedAt","2026-07-26T10:00:00",
                "attemptCount",1),8L,"alice"));

        assertEquals(false,result.completeTodo());
        assertEquals(Map.of("result","CONTINUE_CURRENT_WINDOW","retryRecordId",71L,
                "nextStage","T1_AM","attemptNo",1,"replayed",false),
                result.routingPayload());
    }

    @Test
    void all_terminal_server_outcomes_complete_and_expose_only_authoritative_routing_fields()
    {
        LeadRetryTodoHandler handler=new LeadRetryTodoHandler(retries,schedules);
        TodoInstance todo=todo();
        when(schedules.requireOccurrenceIdForTodo("81:T1_AM:1",31L)).thenReturn(91L);
        when(retries.completeWindow(org.mockito.ArgumentMatchers.any())).thenReturn(
                new RetryOutcome("CONNECTED",72L,null,1,false),
                new RetryOutcome("NEXT_WINDOW",73L,"T2_PM",2,false),
                new RetryOutcome("EXHAUSTED",74L,"EXHAUSTED",3,true));
        Map<String,Object> client=Map.of(
                "result","CONTINUE_CURRENT_WINDOW",
                "planId",998L,
                "nextWindowCode","CLIENT_CHOICE",
                "contactedAt","2026-07-26T10:00:00",
                "attemptCount",1);

        CompletionResult connected=handler.handle(
                CompletionContext.human(todo,client,8L,"alice"));
        CompletionResult next=handler.handle(
                CompletionContext.human(todo,client,8L,"alice"));
        CompletionResult exhausted=handler.handle(
                CompletionContext.human(todo,client,8L,"alice"));

        assertTrue(connected.completeTodo());
        assertEquals(Map.of("result","CONNECTED","retryRecordId",72L,
                "attemptNo",1,"replayed",false),connected.routingPayload());
        assertTrue(next.completeTodo());
        assertEquals(Map.of("result","NEXT_WINDOW","retryRecordId",73L,
                "nextStage","T2_PM","attemptNo",2,"replayed",false),next.routingPayload());
        assertTrue(exhausted.completeTodo());
        assertEquals(Map.of("result","EXHAUSTED","retryRecordId",74L,
                "nextStage","EXHAUSTED","attemptNo",3,"replayed",true),
                exhausted.routingPayload());
    }

    @Test
    void derives_schedule_occurrence_from_persisted_todo_context()
    {
        LeadRetryTodoHandler handler = new LeadRetryTodoHandler(retries, schedules);
        TodoInstance todo = todo();
        when(schedules.requireOccurrenceIdForTodo("81:T1_AM:1", 31L)).thenReturn(91L);

        assertTrue(handler.supports(todo));
        assertEquals("TD-003_COMPLETE", handler.catalogCode());
        handler.complete(todo, Map.of(
                "occurrenceId", 999L,
                "result", "CONNECTED",
                "contactName", "王五",
                "city", "南京",
                "legalDemand", "股权争议",
                "visited", "0",
                "planId", 998L,
                "nextWindowCode", "CLIENT_CHOICE",
                "nextRetryTime", "2030-01-01T00:00:00",
                "callRecord", Map.of(
                        "callChannel", "MANUAL",
                        "businessOccurrenceKey", "retry-call-1",
                        "startedAt", "2026-07-26T11:00:00")), 8L, "alice");

        ArgumentCaptor<LeadRetryCompleteCommand> captured =
                ArgumentCaptor.forClass(LeadRetryCompleteCommand.class);
        verify(retries).completeWindow(captured.capture());
        LeadRetryCompleteCommand command = captured.getValue();
        assertEquals(7L, command.getLeadId());
        assertEquals(31L, command.getTodoId());
        assertEquals(91L, command.getOccurrenceId());
        assertEquals("CONNECTED", command.getResult());
        assertNull(command.getPlanId());
        assertNull(command.getNextWindowCode());
        assertNull(command.getNextRetryTime());
        assertEquals(LocalDateTime.of(2026,7,26,11,0),
                command.getCallRecord().getStartedAt());
    }

    @Test
    void existing_retry_form_derives_stable_attempt_evidence_without_client_occurrence_authority()
    {
        LeadRetryTodoHandler handler=new LeadRetryTodoHandler(retries,schedules);
        TodoInstance todo=todo();
        when(schedules.requireOccurrenceIdForTodo("81:T1_AM:1",31L)).thenReturn(91L);

        handler.complete(todo,Map.of(
                "contactResult","NEXT_WINDOW","contactedAt","2026-07-26T13:00:00",
                "attemptCount",2),8L,"alice");

        ArgumentCaptor<LeadRetryCompleteCommand> captured=
                ArgumentCaptor.forClass(LeadRetryCompleteCommand.class);
        verify(retries).completeWindow(captured.capture());
        assertEquals("NEXT_WINDOW",captured.getValue().getResult());
        assertEquals("TD-003:31:81:T1_AM:1:ATTEMPT:2",
                captured.getValue().getCallRecord().getBusinessOccurrenceKey());
    }

    private TodoInstance todo()
    {
        TodoInstance todo = new TodoInstance();
        todo.setTodoId(31L);
        todo.setTemplateCode("TD-003");
        todo.setBusinessType("LEAD");
        todo.setBusinessId(7L);
        todo.setOwnerId(8L);
        todo.setOwnerDeptId(3L);
        todo.setOccurrenceKey("81:T1_AM:1");
        return todo;
    }
}
