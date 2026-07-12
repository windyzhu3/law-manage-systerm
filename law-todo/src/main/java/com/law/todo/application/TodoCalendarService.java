package com.law.todo.application;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.todo.mapper.TodoMapper;

@Service
public class TodoCalendarService
{
    private final TodoMapper mapper;public TodoCalendarService(TodoMapper mapper){this.mapper=mapper;}
    public List<Map<String,Object>> list(){return mapper.selectCalendars();}
    @Transactional public int save(Map<String,Object> value){return value.get("calendarId")==null?mapper.insertCalendar(value):mapper.updateCalendar(value);}
}
