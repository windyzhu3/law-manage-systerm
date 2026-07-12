package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

@ExtendWith(MockitoExtension.class)
class TodoSlaServiceTest
{
    @Mock TodoMapper mapper;

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
        new TodoSlaService(mapper).scanAndEscalate(now);
        verify(mapper).markSlaThreshold(1L,"REMINDED_80",now);
        verify(mapper).markSlaThreshold(1L,"OVERDUE_100",now);
        verify(mapper).markSlaThreshold(1L,"ESCALATED_150",now);
    }
}
