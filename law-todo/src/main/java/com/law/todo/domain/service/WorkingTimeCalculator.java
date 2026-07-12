package com.law.todo.domain.service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;

public class WorkingTimeCalculator
{
    public record WorkCalendar(Set<DayOfWeek> workDays,LocalTime workStart,LocalTime workEnd,Map<LocalDate,Boolean> exceptions)
    {
        public boolean working(LocalDate date){Boolean override=exceptions.get(date);return override!=null?override:workDays.contains(date.getDayOfWeek());}
    }
    public LocalDateTime addWorkingMinutes(LocalDateTime start,long minutes,WorkCalendar calendar)
    {
        if(minutes<0)throw new IllegalArgumentException("minutes must be non-negative");LocalDateTime cursor=normalize(start,calendar);long remaining=minutes;
        while(remaining>0){LocalDateTime end=LocalDateTime.of(cursor.toLocalDate(),calendar.workEnd());long available=Math.max(0,Duration.between(cursor,end).toMinutes());if(remaining<=available)return cursor.plusMinutes(remaining);remaining-=available;cursor=nextDay(cursor.toLocalDate().plusDays(1),calendar);}return cursor;
    }
    public long workingMinutesBetween(LocalDateTime start,LocalDateTime end,WorkCalendar calendar)
    {
        if(end==null||start==null||!end.isAfter(start))return 0;LocalDateTime cursor=normalize(start,calendar);long result=0;
        while(cursor.isBefore(end)){LocalDateTime workEnd=LocalDateTime.of(cursor.toLocalDate(),calendar.workEnd());LocalDateTime segmentEnd=end.isBefore(workEnd)?end:workEnd;if(segmentEnd.isAfter(cursor))result+=Duration.between(cursor,segmentEnd).toMinutes();if(!segmentEnd.isBefore(workEnd)||!segmentEnd.isBefore(end))cursor=nextDay(cursor.toLocalDate().plusDays(1),calendar);else break;}
        return result;
    }
    private LocalDateTime normalize(LocalDateTime value,WorkCalendar c){LocalDate d=value.toLocalDate();if(!c.working(d))return nextDay(d.plusDays(1),c);if(value.toLocalTime().isBefore(c.workStart()))return LocalDateTime.of(d,c.workStart());if(!value.toLocalTime().isBefore(c.workEnd()))return nextDay(d.plusDays(1),c);return value;}
    private LocalDateTime nextDay(LocalDate date,WorkCalendar c){LocalDate d=date;while(!c.working(d))d=d.plusDays(1);return LocalDateTime.of(d,c.workStart());}
}
