package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.HashMap;
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
import com.law.todo.extension.TodoExtensionCommands.CyclePolicy;
import com.law.todo.extension.TodoExtensionCommands.DurationUnit;

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

    @Test void calculatesOnlyWorkingMinutesAcrossWeekend()
    {
        WorkCalendar c=new WorkCalendar(Set.of(DayOfWeek.MONDAY,DayOfWeek.TUESDAY,DayOfWeek.WEDNESDAY,DayOfWeek.THURSDAY,DayOfWeek.FRIDAY),LocalTime.of(9,0),LocalTime.of(18,0),Map.of());
        long minutes=new WorkingTimeCalculator().workingMinutesBetween(LocalDateTime.of(2026,7,10,17,0),LocalDateTime.of(2026,7,13,10,0),c);
        assertEquals(120,minutes);
    }

    @Test void weekendDoesNotAdvanceSlaThreshold()
    {
        LocalDateTime now=LocalDateTime.of(2026,7,11,12,0);
        when(mapper.selectSlaScanItems(now)).thenReturn(List.of(Map.of("todo_id",1L,"version",3,"due_at",LocalDateTime.of(2026,7,13,10,0),"remind80_due_at",LocalDateTime.of(2026,7,13,9,36),"overdue100_due_at",LocalDateTime.of(2026,7,13,10,0),"escalate150_due_at",LocalDateTime.of(2026,7,13,11,0))));

        assertEquals(0,new TodoSlaService(mapper,access).scanAndEscalate(now));
        verify(mapper,never()).markSlaThreshold(any(),any(),any(),any(),any(),any());
    }

    @Test void scanMarksReachedThresholdsOnce()
    {
        LocalDateTime now=LocalDateTime.of(2026,7,12,12,0);
        LocalDateTime p80=now.minusHours(3),p100=now.minusHours(2),p150=now.minusHours(1);
        LocalDateTime due=now.minusHours(2);
        when(mapper.selectSlaScanItems(now)).thenReturn(List.of(Map.of("todo_id",1L,"version",4,"due_at",due,"remind80_due_at",p80,"overdue100_due_at",p100,"escalate150_due_at",p150)));
        when(mapper.markSlaThreshold(1L,"REMINDED_80",p80,due,4,now)).thenReturn(1);
        when(mapper.markSlaThreshold(1L,"OVERDUE_100",p100,due,5,now)).thenReturn(1);
        when(mapper.markSlaThreshold(1L,"ESCALATED_150",p150,due,6,now)).thenReturn(1);
        new TodoSlaService(mapper,access).scanAndEscalate(now);
        verify(mapper).markSlaThreshold(1L,"REMINDED_80",p80,due,4,now);
        verify(mapper).markSlaThreshold(1L,"OVERDUE_100",p100,due,5,now);
        verify(mapper).markSlaThreshold(1L,"ESCALATED_150",p150,due,6,now);
        verify(mapper).insertSlaNotification(1L,"REMINDED_80",now);
        verify(mapper).insertSlaNotification(1L,"OVERDUE_100",now);
        verify(mapper).insertSlaNotification(1L,"ESCALATED_150",now);
        verify(mapper).insertSupervisorEscalationNotification(1L,now);
    }

    @Test void staleScanCannotFireAfterApprovalReplansThreshold()
    {
        LocalDateTime now=LocalDateTime.of(2026,7,12,12,0),oldPlan=now.minusMinutes(1);
        LocalDateTime due=now.plusHours(3);
        when(mapper.selectSlaScanItems(now)).thenReturn(List.of(Map.of("todo_id",1L,"version",4,"due_at",due,
            "remind80_due_at",oldPlan,"overdue100_due_at",now.plusHours(1),"escalate150_due_at",now.plusHours(2))));
        when(mapper.markSlaThreshold(1L,"REMINDED_80",oldPlan,due,4,now)).thenReturn(0);

        assertEquals(0,new TodoSlaService(mapper,access).scanAndEscalate(now));

        verify(mapper,never()).insertSlaNotification(any(),any(),any());
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

    @Test void producesBoundedWorkingDayCycleOccurrences()
    {
        WorkCalendar c=new WorkCalendar(Set.of(DayOfWeek.MONDAY,DayOfWeek.TUESDAY,DayOfWeek.WEDNESDAY,DayOfWeek.THURSDAY,DayOfWeek.FRIDAY),LocalTime.of(9,0),LocalTime.of(18,0),Map.of());
        CyclePolicy policy=new CyclePolicy(17L,1,DurationUnit.WORKING_DAYS,3);

        var values=new TodoSlaService(mapper,access).occurrences("cycle-a",LocalDateTime.of(2026,7,10,9,0),policy,c);

        assertEquals(List.of("cycle-a:1","cycle-a:2","cycle-a:3"),values.stream().map(value->value.occurrenceKey()).toList());
        assertEquals(LocalDateTime.of(2026,7,13,9,0),values.get(0).dueAt());
    }

    @Test void plansAllThresholdsOverTheWorkingCalendarInterval()
    {
        WorkCalendar c=new WorkCalendar(Set.of(DayOfWeek.MONDAY,DayOfWeek.TUESDAY,DayOfWeek.WEDNESDAY,DayOfWeek.THURSDAY,DayOfWeek.FRIDAY),LocalTime.of(9,0),LocalTime.of(18,0),Map.of());
        var plan=new TodoSlaService(mapper,access).planThresholds(LocalDateTime.of(2026,7,10,17,0),LocalDateTime.of(2026,7,13,10,0),c);
        assertEquals(LocalDateTime.of(2026,7,13,9,36),plan.remind80DueAt());
        assertEquals(LocalDateTime.of(2026,7,13,10,0),plan.overdue100DueAt());
        assertEquals(LocalDateTime.of(2026,7,13,11,0),plan.escalate150DueAt());
    }

    @Test void plannedThresholdsRoundUpLikeLegacyIntegerPercentChecks()
    {
        WorkCalendar c=new WorkCalendar(Set.of(DayOfWeek.MONDAY),LocalTime.of(9,0),LocalTime.of(18,0),Map.of());
        var plan=new TodoSlaService(mapper,access).planThresholds(LocalDateTime.of(2026,7,13,9,0),LocalDateTime.of(2026,7,13,9,7),c);

        assertEquals(LocalDateTime.of(2026,7,13,9,6),plan.remind80DueAt());
        assertEquals(LocalDateTime.of(2026,7,13,9,7),plan.overdue100DueAt());
        assertEquals(LocalDateTime.of(2026,7,13,9,11),plan.escalate150DueAt());
    }

    @Test void legacyNullPlansUseWorkingCalendarAcrossWeekendAndHoliday()
    {
        LocalDateTime start=LocalDateTime.of(2026,7,10,9,0),due=LocalDateTime.of(2026,7,14,10,0),now=LocalDateTime.of(2026,7,11,12,0);
        Map<String,Object> legacy=new HashMap<>();legacy.put("todo_id",1L);legacy.put("version",7);legacy.put("start_at",start);legacy.put("due_at",due);
        legacy.put("remind80_due_at",null);legacy.put("overdue100_due_at",null);legacy.put("escalate150_due_at",null);
        legacy.put("work_days","1,2,3,4,5");legacy.put("work_start","09:00:00");legacy.put("work_end","18:00:00");legacy.put("exception_json","{\"2026-07-13\":false}");
        when(mapper.selectSlaScanItems(now)).thenReturn(List.of(legacy));
        when(mapper.markSlaThreshold(1L,"REMINDED_80",null,due,7,now)).thenReturn(1);

        assertEquals(1,new TodoSlaService(mapper,access).scanAndEscalate(now));

        verify(mapper).markSlaThreshold(1L,"REMINDED_80",null,due,7,now);
        verify(mapper,never()).markSlaThreshold(1L,"OVERDUE_100",null,due,8,now);
    }

    @Test void staleLegacyScanCannotFireAfterApprovalChangesEffectiveDue()
    {
        LocalDateTime start=LocalDateTime.of(2026,7,10,9,0),due=LocalDateTime.of(2026,7,10,10,0),now=LocalDateTime.of(2026,7,10,9,49);
        Map<String,Object> legacy=new HashMap<>();legacy.put("todo_id",1L);legacy.put("version",2);legacy.put("start_at",start);legacy.put("due_at",due);
        legacy.put("remind80_due_at",null);legacy.put("overdue100_due_at",null);legacy.put("escalate150_due_at",null);
        legacy.put("work_days","1,2,3,4,5");legacy.put("work_start","09:00:00");legacy.put("work_end","18:00:00");legacy.put("exception_json","{}");
        when(mapper.selectSlaScanItems(now)).thenReturn(List.of(legacy));
        when(mapper.markSlaThreshold(1L,"REMINDED_80",null,due,2,now)).thenReturn(0);

        assertEquals(0,new TodoSlaService(mapper,access).scanAndEscalate(now));
        verify(mapper,never()).insertSlaNotification(any(),any(),any());
    }
}
