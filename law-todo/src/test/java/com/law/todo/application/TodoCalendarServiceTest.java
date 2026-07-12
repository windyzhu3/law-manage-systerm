package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.law.todo.application.command.TodoManagementCommands.CalendarCommand;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoMapper;

class TodoCalendarServiceTest
{
    private final TodoCalendarService service=new TodoCalendarService(Mockito.mock(TodoMapper.class));

    @Test void rejectsInvalidExceptionJson()
    {
        TodoException error=assertThrows(TodoException.class,()->service.save(command("09:00:00","18:00:00","not-json")));
        assertEquals("TODO_CALENDAR_JSON_INVALID",error.getBusinessCode());
    }

    @Test void rejectsEndBeforeStart()
    {
        TodoException error=assertThrows(TodoException.class,()->service.save(command("18:00:00","09:00:00","{}")));
        assertEquals("TODO_CALENDAR_TIME_INVALID",error.getBusinessCode());
    }

    private CalendarCommand command(String start,String end,String exceptions){return new CalendarCommand(null,"DEFAULT","默认","Asia/Shanghai","1,2,3,4,5",start,end,exceptions,"0");}
}
