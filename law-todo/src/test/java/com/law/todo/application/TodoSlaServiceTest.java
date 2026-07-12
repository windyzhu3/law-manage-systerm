package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.todo.domain.service.WorkingTimeCalculator;
import com.law.todo.domain.service.WorkingTimeCalculator.WorkCalendar;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.domain.TodoAccessPolicy;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;

@ExtendWith(MockitoExtension.class)
class TodoSlaServiceTest
{
    @Mock TodoMapper mapper; @Mock TodoAccessPolicy access;

    @Test void addsWorkingTimeAcrossWeekend()
    {
        WorkCalendar c=new WorkCalendar(Set.of(DayOfWeek.MONDAY,DayOfWeek.TUESDAY,DayOfWeek.WEDNESDAY,DayOfWeek.THURSDAY,DayOfWeek.FRIDAY),LocalTime.of(9,0),LocalTime.of(18,0),Map.of());
        LocalDateTime due=new WorkingTimeCalculator().addWorkingMinutes(LocalDateTime.of(2026,7,10,17,0),120,c);
        assertEquals(LocalDateTime.of(2026,7,13,10,0),due);
    }

    @Test void exceptionCanMakeSaturdayWorkingDay()
    {
        WorkCalendar c=new WorkCalendar(Set.of(DayOfWeek.MONDAY),LocalTime.of(9,0),LocalTime.of(18,0),Map.of(LocalDate.of(2026,7,11),true));
        assertEquals(LocalDateTime.of(2026,7,11,10,0),new WorkingTimeCalculator().addWorkingMinutes(LocalDateTime.of(2026,7,11,9,0),60,c));
    }

    @Test void scanMarksReachedThresholdsOnce()
    {
        LocalDateTime now=LocalDateTime.of(2026,7,12,12,0);
        when(mapper.selectSlaScanItems(now)).thenReturn(List.of(Map.of("todo_id",1L,"percent",151)));
        when(mapper.markSlaThreshold(1L,"REMINDED_80",now)).thenReturn(1);
        when(mapper.markSlaThreshold(1L,"OVERDUE_100",now)).thenReturn(1);
        when(mapper.markSlaThreshold(1L,"ESCALATED_150",now)).thenReturn(1);
        new TodoSlaService(mapper,access).scanAndEscalate(now);
        verify(mapper).markSlaThreshold(1L,"REMINDED_80",now);
        verify(mapper).markSlaThreshold(1L,"OVERDUE_100",now);
        verify(mapper).markSlaThreshold(1L,"ESCALATED_150",now);
        verify(mapper).insertSlaNotification(1L,"REMINDED_80",now);
        verify(mapper).insertSlaNotification(1L,"OVERDUE_100",now);
        verify(mapper).insertSlaNotification(1L,"ESCALATED_150",now);
        verify(mapper).insertSupervisorEscalationNotification(1L,now);
    }

    @Test void ownerCanPauseSla()
    {
        TodoInstance todo=new TodoInstance();todo.setTodoId(1L);when(mapper.selectById(1L)).thenReturn(todo);when(access.canOperate(todo,7L)).thenReturn(true);when(mapper.pauseSla(1L,LocalDateTime.MIN)).thenReturn(1);
        assertEquals(true,new TodoSlaService(mapper,access).pause(1L,7L,LocalDateTime.MIN));
    }

    @Test void nonOwnerCannotPauseSla()
    {
        TodoInstance todo=new TodoInstance();todo.setTodoId(1L);when(mapper.selectById(1L)).thenReturn(todo);when(access.canOperate(todo,9L)).thenReturn(false);
        assertThrows(TodoException.class,()->new TodoSlaService(mapper,access).pause(1L,9L,LocalDateTime.MIN));
    }
}
