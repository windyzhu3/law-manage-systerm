package com.law.todo.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;
import com.law.todo.application.TodoAutoActionService;
import com.law.todo.application.TodoSlaService;

class TodoSlaTaskTest
{
    @Test void scheduledScanRunsSlaThresholdsAndControlledAutoActions()
    {
        TodoSlaService sla=mock(TodoSlaService.class);TodoAutoActionService actions=mock(TodoAutoActionService.class);
        when(sla.scanAndEscalate(any())).thenReturn(2);when(actions.scanDue(any())).thenReturn(3);
        assertEquals(5,new TodoSlaTask(sla,actions).scan());
    }
}
