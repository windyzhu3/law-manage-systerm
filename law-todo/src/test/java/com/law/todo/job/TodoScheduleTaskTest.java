package com.law.todo.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.law.todo.schedule.TodoScheduleService;

class TodoScheduleTaskTest
{
    @Test
    void scanDelegatesToTheScheduleEngineWithABoundedBatch()
    {
        TodoScheduleService service=mock(TodoScheduleService.class);
        when(service.materializeDue(any(),org.mockito.ArgumentMatchers.eq(100))).thenReturn(4);

        assertEquals(4,new TodoScheduleTask(service).scan());

        verify(service).materializeDue(any(),org.mockito.ArgumentMatchers.eq(100));
    }
}
