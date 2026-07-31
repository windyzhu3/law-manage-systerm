package com.ruoyi.system.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.law.business.lead.dto.LeadProgressCompleteCommand;
import com.law.todo.application.CompletionContext;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.spi.TodoCompletionHandler.CompletionResult;
import com.law.todo.spi.TodoCompletionHandler.SimulationResult;
import com.ruoyi.system.service.event.LeadProgressHandoffTodoHandler;
import com.ruoyi.system.service.lead.LeadProgressCycleService;

class LeadProgressHandoffTodoHandlerTest
{
    @Test
    void exposes_the_approved_stage_three_boundary_handler()
    {
        LocalDateTime progressAt=LocalDateTime.of(2026,7,31,10,0);
        TodoInstance todo=new TodoInstance();todo.setTodoId(7001L);todo.setTemplateCode("TD-004");
        todo.setTemplateVersionId(88L);todo.setBusinessType("LEAD");todo.setBusinessId(91L);
        LeadProgressCycleService cycles=org.mockito.Mockito.mock(LeadProgressCycleService.class);
        when(cycles.complete(any(),same(todo))).thenReturn(
                new LeadProgressCycleService.ProgressCycleOutcome(
                        90L,81L,progressAt.plusDays(5),false));
        LeadProgressHandoffTodoHandler handler=new LeadProgressHandoffTodoHandler(cycles);

        assertTrue(handler.supports(todo));
        assertEquals("TD-004_COMPLETE",handler.catalogCode());
        CompletionResult result=handler.handle(CompletionContext.human(todo,Map.of(
                "progressType","PHONE","progressAt",progressAt.toString(),"remark","quoted"),
                8L,"alice"));

        assertTrue(result.completeTodo());
        assertEquals(Map.of("result","PROGRESS_RECORDED","followupId",90L,
                "schedulePlanId",81L,"nextDueAt","2026-08-05T10:00","replayed",false),
                result.routingPayload());
        ArgumentCaptor<LeadProgressCompleteCommand> command=
                ArgumentCaptor.forClass(LeadProgressCompleteCommand.class);
        verify(cycles).complete(command.capture(),same(todo));
        assertEquals(91L,command.getValue().getLeadId());
        assertEquals(7001L,command.getValue().getTodoId());
        assertEquals("PHONE",command.getValue().getProgressType());
        assertEquals(progressAt,command.getValue().getProgressAt());
        assertEquals("quoted",command.getValue().getRemark());
    }

    @Test
    void simulatesTheRecurringEffectWithoutWritingProgressOrSchedules()
    {
        TodoInstance todo=new TodoInstance();
        todo.setTodoId(7002L);
        todo.setTemplateCode("TD-004");
        todo.setBusinessType("LEAD");
        todo.setBusinessId(92L);
        LeadProgressCycleService cycles=org.mockito.Mockito.mock(LeadProgressCycleService.class);
        LeadProgressHandoffTodoHandler handler=new LeadProgressHandoffTodoHandler(cycles);
        Map<String,Object> payload=Map.of(
                "progressType","WECHAT","progressAt","2026-07-31T11:00:00");

        assertTrue(handler.supportsSimulation());
        SimulationResult result=handler.simulate(todo,payload);

        assertEquals(Map.of("progressType","WECHAT","progressAt","2026-07-31T11:00:00",
                "result","PROGRESS_RECORDED"),result.routingPayload());
        assertTrue(result.producedTemplateCodes().isEmpty(),
                "SCHEDULE_SELF is an outcome effect, not an ordinary graph-produced task");
        verifyNoInteractions(cycles);
    }
}
