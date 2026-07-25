package com.law.todo.schedule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** A persisted schedule window. Relative policy and absolute timestamps are both retained. */
public record TodoScheduleWindow(
        String windowCode,
        int windowOrder,
        int dayOffset,
        LocalTime startTime,
        LocalTime endTime,
        LocalDateTime startAt,
        LocalDateTime dueAt,
        int maxAttempts,
        int occurrenceNo)
{
    public static TodoScheduleWindow t0(LocalDateTime firstContactCompletedAt)
    {
        return new TodoScheduleWindow("T0",0,0,firstContactCompletedAt.toLocalTime(),
                firstContactCompletedAt.plusHours(2).toLocalTime(),firstContactCompletedAt,
                firstContactCompletedAt.plusHours(2),3,1);
    }

    public static TodoScheduleWindow fixed(String code,int order,int dayOffset,
            LocalTime start,LocalTime end,LocalDate anchor)
    {
        LocalDate date=anchor.plusDays(dayOffset);
        return new TodoScheduleWindow(code,order,dayOffset,start,end,
                LocalDateTime.of(date,start),LocalDateTime.of(date,end),1,1);
    }
}
