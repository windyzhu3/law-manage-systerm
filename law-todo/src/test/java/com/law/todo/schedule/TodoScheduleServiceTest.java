package com.law.todo.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import com.law.todo.application.TodoRoutingService;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.schedule.TodoScheduleService.CreateSchedulePlanCommand;

class TodoScheduleServiceTest
{
    private static final LocalDateTime NOW=LocalDateTime.of(2026,7,25,8,30);

    @Test
    void createsImmutableDefaultWindowsInAsiaShanghai()
    {
        TodoMapper mapper=mock(TodoMapper.class);
        doAnswer(invocation->{invocation.<Map<String,Object>>getArgument(0).put("planId",3L);return 1;})
                .when(mapper).insertSchedulePlan(anyMap());
        List<Map<String,Object>> inserted=new ArrayList<>();
        doAnswer(invocation->{inserted.add(new HashMap<>(invocation.getArgument(0)));return 1;})
                .when(mapper).insertScheduleWindow(anyMap());
        TodoScheduleService service=new TodoScheduleService(mapper,mock(TodoRoutingService.class));

        long planId=service.createPlan(new CreateSchedulePlanCommand(
                7L,22L,"LEAD",91L,NOW,null,4L));

        assertEquals(3L,planId);
        assertEquals(List.of("T0","T1_AM","T1_NOON","T1_PM","T2_AM","T2_NOON","T2_PM"),
                inserted.stream().map(row->row.get("windowCode")).toList());
        assertEquals(NOW,inserted.get(0).get("startAt"));
        assertEquals(NOW.plusHours(2),inserted.get(0).get("dueAt"));
        assertEquals(3,inserted.get(0).get("maxAttempts"));
        assertEquals(LocalDateTime.of(2026,7,26,9,0),inserted.get(1).get("startAt"));
        assertEquals(LocalDateTime.of(2026,7,26,11,0),inserted.get(1).get("dueAt"));
        assertEquals(LocalDateTime.of(2026,7,27,18,0),inserted.get(6).get("dueAt"));
        verify(mapper).insertSchedulePlan(org.mockito.ArgumentMatchers.argThat(
                row->"Asia/Shanghai".equals(row.get("timezone")) && "ACTIVE".equals(row.get("status"))));
    }

    @Test
    void rejectsPlanWithoutAStableSourceIdentity()
    {
        TodoScheduleService service=new TodoScheduleService(mock(TodoMapper.class),mock(TodoRoutingService.class));
        TodoException error=assertThrows(TodoException.class,()->service.createPlan(
                new CreateSchedulePlanCommand(null,22L,"LEAD",91L,NOW,null,4L)));
        assertEquals("TODO_SCHEDULE_PLAN_INVALID",error.getBusinessCode());
    }

    @Test
    void materializesEachDueWindowOnce()
    {
        TodoMapper mapper=mock(TodoMapper.class);
        TodoRoutingService routing=mock(TodoRoutingService.class);
        Map<String,Object> due=dueWindow();
        when(mapper.selectDueScheduleWindows(NOW,NOW.minusMinutes(5),100)).thenReturn(List.of(due),List.of());
        when(mapper.claimScheduleWindow(12L,0,NOW,NOW.minusMinutes(5))).thenReturn(1);
        when(mapper.insertScheduleOccurrenceIfAbsent(anyMap())).thenAnswer(invocation->{
            invocation.<Map<String,Object>>getArgument(0).put("occurrenceId",9L);return 1;
        });
        when(mapper.claimScheduleOccurrence(9L,0,NOW,NOW.minusMinutes(5))).thenReturn(1);
        TodoInstance previous=new TodoInstance();previous.setTodoId(7L);
        when(mapper.selectById(7L)).thenReturn(previous);
        TodoInstance created=new TodoInstance();created.setTodoId(55L);
        when(routing.createScheduledNext(previous,22L,"3:T1_AM:1",
                LocalDateTime.of(2026,7,26,11,0))).thenReturn(created);
        when(mapper.completeScheduleOccurrence(9L,55L,NOW)).thenReturn(1);
        TodoScheduleService service=new TodoScheduleService(mapper,routing);

        assertEquals(1,service.materializeDue(NOW,100));
        assertEquals(0,service.materializeDue(NOW,100));

        verify(mapper,times(1)).insertScheduleOccurrenceIfAbsent(
                org.mockito.ArgumentMatchers.argThat(row->
                        "T1_AM".equals(row.get("windowCode"))
                        && "3:T1_AM:1".equals(row.get("occurrenceKey"))));
        verify(mapper).completeScheduleWindow(12L,1,NOW);
    }

    @Test
    void failedTodoCreationRestoresRetryableStateWithStableErrorCode()
    {
        TodoMapper mapper=mock(TodoMapper.class);
        TodoRoutingService routing=mock(TodoRoutingService.class);
        when(mapper.selectDueScheduleWindows(NOW,NOW.minusMinutes(5),10)).thenReturn(List.of(dueWindow()));
        when(mapper.claimScheduleWindow(12L,0,NOW,NOW.minusMinutes(5))).thenReturn(1);
        when(mapper.insertScheduleOccurrenceIfAbsent(anyMap())).thenAnswer(invocation->{
            invocation.<Map<String,Object>>getArgument(0).put("occurrenceId",9L);return 1;
        });
        when(mapper.claimScheduleOccurrence(9L,0,NOW,NOW.minusMinutes(5))).thenReturn(1);
        TodoInstance previous=new TodoInstance();previous.setTodoId(7L);
        when(mapper.selectById(7L)).thenReturn(previous);
        when(routing.createScheduledNext(any(),eq(22L),eq("3:T1_AM:1"),any()))
                .thenThrow(new TodoException("TODO_OWNER_UNRESOLVED","owner missing"));
        TodoScheduleService service=new TodoScheduleService(mapper,routing);

        assertEquals(0,service.materializeDue(NOW,10));

        verify(mapper).retryScheduleOccurrence(9L,1,"TODO_OWNER_UNRESOLVED","owner missing",NOW);
        verify(mapper).retryScheduleWindow(12L,1,"TODO_OWNER_UNRESOLVED",NOW);
        verify(mapper,never()).completeScheduleWindow(any(),any(Integer.class),any());
    }

    @Test
    void connectedResultCancelsFutureWindows()
    {
        TodoMapper mapper=mock(TodoMapper.class);
        when(mapper.selectScheduleOccurrenceById(9L)).thenReturn(Map.of(
                "planId",3L,"status","MATERIALIZED"));
        when(mapper.selectSchedulePlanForUpdate(3L)).thenReturn(Map.of("planId",3L,"status","ACTIVE"));
        when(mapper.recordScheduleOccurrenceResult(9L,"CONNECTED",NOW)).thenReturn(1);
        TodoScheduleService service=new TodoScheduleService(mapper,mock(TodoRoutingService.class));

        service.completeOccurrence(9L,"CONNECTED",NOW);

        verify(mapper).recordScheduleOccurrenceResult(9L,"CONNECTED",NOW);
        verify(mapper).cancelFutureScheduleWindows(3L,9L,"CONTACTED",NOW);
        verify(mapper).cancelFutureScheduleOccurrences(3L,9L,"CONTACTED",NOW);
        verify(mapper).completeSchedulePlan(3L,"CONTACTED",NOW);
    }

    @Test
    void nextWindowResultReturnsScheduleOwnedStageAndStartTime()
    {
        TodoMapper mapper=mock(TodoMapper.class);
        when(mapper.selectScheduleOccurrenceById(9L)).thenReturn(Map.of(
                "planId",3L,"windowId",12L,"windowCode","T1_AM","occurrenceNo",1,
                "status","MATERIALIZED"));
        when(mapper.recordScheduleOccurrenceResult(9L,"NEXT_WINDOW",NOW)).thenReturn(1);
        when(mapper.selectNextScheduleWindow(3L,12L)).thenReturn(Map.of(
                "windowCode","T1_NOON","startAt",LocalDateTime.of(2026,7,26,12,0)));

        TodoScheduleService.ScheduleCompletion outcome =
                new TodoScheduleService(mapper,mock(TodoRoutingService.class))
                        .completeOccurrence(9L,"NEXT_WINDOW",NOW);

        assertEquals("T1_NOON",outcome.nextWindowCode());
        assertEquals(LocalDateTime.of(2026,7,26,12,0),outcome.nextStartAt());
    }

    @Test
    void workerFirstConnectedResultCancelsLinkedFutureTodoAndRowsIdempotently()
    {
        TodoMapper mapper=mock(TodoMapper.class);
        Map<String,Object> accepted=Map.of(
                "occurrenceId",9L,"planId",3L,"todoId",44L,"status","MATERIALIZED");
        Map<String,Object> completed=Map.of(
                "occurrenceId",9L,"planId",3L,"todoId",44L,
                "status","COMPLETED","resultCode","CONNECTED");
        when(mapper.selectScheduleOccurrenceById(9L)).thenReturn(accepted,completed,completed);
        when(mapper.selectSchedulePlanForUpdate(3L)).thenReturn(Map.of("planId",3L,"status","ACTIVE"));
        when(mapper.recordScheduleOccurrenceResult(9L,"CONNECTED",NOW)).thenReturn(1,0);
        when(mapper.selectActiveLinkedScheduleTodosForUpdate(3L,9L)).thenReturn(
                List.of(Map.of("occurrenceId",10L,"todoId",55L,"status","CREATED")),
                List.of());
        when(mapper.updateStatusConditionally(55L,"CREATED","CANCELLED",null,"TODO_AUTO_ACTION"))
                .thenReturn(1);
        when(mapper.insertActionIfAbsent(anyMap())).thenReturn(1);
        TodoScheduleService service=new TodoScheduleService(mapper,mock(TodoRoutingService.class));

        service.completeOccurrence(9L,"CONNECTED",NOW);

        InOrder order=inOrder(mapper);
        order.verify(mapper).selectSchedulePlanForUpdate(3L);
        order.verify(mapper).recordScheduleOccurrenceResult(9L,"CONNECTED",NOW);
        order.verify(mapper).completeSchedulePlan(3L,"CONTACTED",NOW);
        order.verify(mapper).selectActiveLinkedScheduleTodosForUpdate(3L,9L);
        order.verify(mapper).updateStatusConditionally(55L,"CREATED","CANCELLED",null,"TODO_AUTO_ACTION");
        order.verify(mapper).insertActionIfAbsent(org.mockito.ArgumentMatchers.argThat(row->
                Long.valueOf(55L).equals(row.get("todoId"))
                        && "CANCEL".equals(row.get("actionType"))
                        && "SYSTEM".equals(row.get("actionSource"))
                        && "MATERIALIZED_SCHEDULE_CANCELLED".equals(row.get("opinion"))));
        order.verify(mapper).cancelFutureScheduleWindows(3L,9L,"CONTACTED",NOW);
        order.verify(mapper).cancelFutureScheduleOccurrences(3L,9L,"CONTACTED",NOW);
        verify(mapper,never()).updateStatusConditionally(eq(44L),any(),any(),any(),any());

        service.completeOccurrence(9L,"CONNECTED",NOW);

        verify(mapper,times(1))
                .updateStatusConditionally(55L,"CREATED","CANCELLED",null,"TODO_AUTO_ACTION");
        verify(mapper,times(1)).insertActionIfAbsent(anyMap());
        verify(mapper,times(2)).cancelFutureScheduleWindows(3L,9L,"CONTACTED",NOW);
        verify(mapper,times(2)).cancelFutureScheduleOccurrences(3L,9L,"CONTACTED",NOW);
    }

    @Test
    void reclaimsStaleWindowAndOccurrenceClaimsAfterLeaseTimeout()
    {
        TodoMapper mapper=mock(TodoMapper.class);
        TodoRoutingService routing=mock(TodoRoutingService.class);
        Map<String,Object> due=dueWindow();
        due.put("status","PROCESSING");due.put("version",3);
        when(mapper.selectDueScheduleWindows(NOW,NOW.minusMinutes(5),10)).thenReturn(List.of(due));
        when(mapper.claimScheduleWindow(12L,3,NOW,NOW.minusMinutes(5))).thenReturn(1);
        when(mapper.insertScheduleOccurrenceIfAbsent(anyMap())).thenReturn(0);
        when(mapper.selectScheduleOccurrenceByKey("3:T1_AM:1")).thenReturn(Map.of(
                "occurrenceId",9L,"status","CLAIMED","version",4));
        when(mapper.claimScheduleOccurrence(9L,4,NOW,NOW.minusMinutes(5))).thenReturn(1);
        TodoInstance previous=new TodoInstance();previous.setTodoId(7L);
        when(mapper.selectById(7L)).thenReturn(previous);
        TodoInstance created=new TodoInstance();created.setTodoId(55L);
        when(routing.createScheduledNext(previous,22L,"3:T1_AM:1",
                LocalDateTime.of(2026,7,26,11,0))).thenReturn(created);
        when(mapper.completeScheduleOccurrence(9L,55L,NOW)).thenReturn(1);

        assertEquals(1,new TodoScheduleService(mapper,routing).materializeDue(NOW,10));

        verify(mapper).claimScheduleWindow(12L,3,NOW,NOW.minusMinutes(5));
        verify(mapper).claimScheduleOccurrence(9L,4,NOW,NOW.minusMinutes(5));
    }

    @Test
    void outOfOrderCompletionCannotCancelPlan()
    {
        TodoMapper mapper=mock(TodoMapper.class);
        when(mapper.selectScheduleOccurrenceById(9L)).thenReturn(Map.of(
                "planId",3L,"status","CLAIMED"));
        when(mapper.selectSchedulePlanForUpdate(3L)).thenReturn(Map.of("planId",3L,"status","ACTIVE"));
        when(mapper.recordScheduleOccurrenceResult(9L,"CONNECTED",NOW)).thenReturn(0);
        TodoScheduleService service=new TodoScheduleService(mapper,mock(TodoRoutingService.class));

        TodoException error=assertThrows(TodoException.class,
                ()->service.completeOccurrence(9L,"CONNECTED",NOW));

        assertEquals("TODO_SCHEDULE_RESULT_NOT_ACCEPTED",error.getBusinessCode());
        verify(mapper,never()).cancelFutureScheduleWindows(any(),any(),any(),any());
        verify(mapper,never()).completeSchedulePlan(any(),any(),any());
    }

    @Test
    void acceptedConnectedCompletionReplayIsIdempotent()
    {
        TodoMapper mapper=mock(TodoMapper.class);
        when(mapper.selectScheduleOccurrenceById(9L)).thenReturn(Map.of(
                "planId",3L,"status","COMPLETED","resultCode","CONNECTED"));
        when(mapper.selectSchedulePlanForUpdate(3L)).thenReturn(Map.of("planId",3L,"status","CONTACTED"));
        when(mapper.recordScheduleOccurrenceResult(9L,"CONNECTED",NOW)).thenReturn(0);
        TodoScheduleService service=new TodoScheduleService(mapper,mock(TodoRoutingService.class));

        service.completeOccurrence(9L,"CONNECTED",NOW);

        verify(mapper).completeSchedulePlan(3L,"CONTACTED",NOW);
    }

    @Test
    void cancellingPlanCancelsWindowsAndUnmaterializedOccurrences()
    {
        TodoMapper mapper=mock(TodoMapper.class);
        when(mapper.selectSchedulePlanForUpdate(3L)).thenReturn(Map.of("planId",3L,"status","ACTIVE"));
        TodoScheduleService service=new TodoScheduleService(mapper,mock(TodoRoutingService.class));

        service.cancelPlan(3L,"CONVERTED",NOW);

        InOrder fenceOrder=inOrder(mapper);
        fenceOrder.verify(mapper).selectSchedulePlanForUpdate(3L);
        fenceOrder.verify(mapper).completeSchedulePlan(3L,"CONVERTED",NOW);
        fenceOrder.verify(mapper).cancelFutureScheduleWindows(3L,null,"CONVERTED",NOW);
        fenceOrder.verify(mapper).cancelFutureScheduleOccurrences(3L,null,"CONVERTED",NOW);
    }

    private Map<String,Object> dueWindow()
    {
        Map<String,Object> row=new HashMap<>();
        row.put("windowId",12L);row.put("planId",3L);row.put("windowCode","T1_AM");
        row.put("occurrenceNo",1);row.put("previousTodoId",7L);row.put("templateVersionId",22L);
        row.put("dueAt",LocalDateTime.of(2026,7,26,11,0));row.put("version",0);
        return row;
    }
}
