package com.law.todo.job;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.law.todo.schedule.TodoScheduleService;

@Component("todoScheduleTask")
public class TodoScheduleTask
{
    private static final int BATCH_SIZE=100;
    private final TodoScheduleService service;

    public TodoScheduleTask(TodoScheduleService service)
    {
        this.service=service;
    }

    public int scan()
    {
        return service.materializeDue(LocalDateTime.now(),BATCH_SIZE);
    }
}
