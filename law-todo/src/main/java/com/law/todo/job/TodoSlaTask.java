package com.law.todo.job;

import java.time.LocalDateTime;
import org.springframework.stereotype.Component;
import com.law.todo.application.TodoSlaService;
import com.law.todo.application.TodoAutoActionService;

@Component("todoSlaTask")
public class TodoSlaTask
{
    private final TodoSlaService service;private final TodoAutoActionService autoActions;
    public TodoSlaTask(TodoSlaService service,TodoAutoActionService autoActions){this.service=service;this.autoActions=autoActions;}
    public int scan(){LocalDateTime now=LocalDateTime.now();return service.scanAndEscalate(now)+autoActions.scanDue(now);}
}
