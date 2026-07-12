package com.law.todo.job;

import java.time.LocalDateTime;
import org.springframework.stereotype.Component;
import com.law.todo.application.TodoSlaService;

@Component("todoSlaTask")
public class TodoSlaTask
{
    private final TodoSlaService service;
    public TodoSlaTask(TodoSlaService service){this.service=service;}
    public int scan(){return service.scanAndEscalate(LocalDateTime.now());}
}
