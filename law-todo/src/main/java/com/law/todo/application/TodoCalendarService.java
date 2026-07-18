package com.law.todo.application;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.law.todo.application.command.TodoManagementCommands.CalendarCommand;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoMapper;

@Service
public class TodoCalendarService
{
    private final TodoMapper mapper;public TodoCalendarService(TodoMapper mapper){this.mapper=mapper;}
    public List<Map<String,Object>> list(){return mapper.selectCalendars();}
    @Transactional public int save(CalendarCommand command){validate(command);Map<String,Object> value=new java.util.HashMap<>();value.put("calendarId",command.calendarId());value.put("calendarCode",command.calendarCode());value.put("calendarName",command.calendarName());value.put("timezone",command.timezone());value.put("workDays",command.workDays());value.put("workStart",command.workStart());value.put("workEnd",command.workEnd());value.put("exceptionJson",command.exceptionJson());value.put("status",command.status()==null?"0":command.status());return save(value);}
    @Transactional public int save(Map<String,Object> value){return value.get("calendarId")==null?mapper.insertCalendar(value):mapper.updateCalendar(value);}
    private void validate(CalendarCommand command){try{ZoneId.of(command.timezone());}catch(Exception e){throw new TodoException("TODO_CALENDAR_TIMEZONE_INVALID","Calendar timezone is invalid");}if(command.exceptionJson()!=null&&!command.exceptionJson().isBlank()){if(!JSON.isValidObject(command.exceptionJson()))throw new TodoException("TODO_CALENDAR_JSON_INVALID","Calendar exceptions must be a JSON object");JSONObject exceptions=JSON.parseObject(command.exceptionJson());for(String date:exceptions.keySet()){try{LocalDate.parse(date);}catch(Exception e){throw new TodoException("TODO_CALENDAR_DATE_INVALID","Calendar exception date is invalid");}if(!(exceptions.get(date) instanceof Boolean))throw new TodoException("TODO_CALENDAR_DATE_INVALID","Calendar exception must be boolean");}}LocalTime start=LocalTime.parse(command.workStart()),end=LocalTime.parse(command.workEnd());if(!end.isAfter(start))throw new TodoException("TODO_CALENDAR_TIME_INVALID","Calendar end time must be after start time");}
}
