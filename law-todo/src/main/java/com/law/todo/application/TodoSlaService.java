package com.law.todo.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Set;
import com.alibaba.fastjson2.JSON;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.domain.TodoAccessPolicy;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.domain.service.WorkingTimeCalculator;
import com.law.todo.domain.service.WorkingTimeCalculator.WorkCalendar;

@Service
public class TodoSlaService
{
    private final TodoMapper mapper;private final TodoAccessPolicy access;
    public TodoSlaService(TodoMapper mapper,TodoAccessPolicy access){this.mapper=mapper;this.access=access;}
    @Transactional public int scanAndEscalate(LocalDateTime now){List<Map<String,Object>> items=mapper.selectSlaScanItems(now);int changed=0;if(items==null)return 0;for(Map<String,Object> item:items){Long id=Long.valueOf(String.valueOf(value(item,"todo_id","todoId")));int percent=percent(item,now);if(percent>=80)changed+=mark(id,"REMINDED_80",now);if(percent>=100)changed+=mark(id,"OVERDUE_100",now);if(percent>=150)changed+=mark(id,"ESCALATED_150",now);}return changed;}
    @Transactional public boolean pause(Long todoId,Long userId,LocalDateTime now){requireOwner(todoId,userId);return mapper.pauseSla(todoId,now)>0;}
    @Transactional public boolean resume(Long todoId,Long userId,LocalDateTime now){requireOwner(todoId,userId);return mapper.resumeSla(todoId,now)>0;}
    private Object value(Map<String,Object> m,String a,String b){return m.containsKey(a)?m.get(a):m.get(b);}
    private int mark(Long id,String threshold,LocalDateTime now){int changed=mapper.markSlaThreshold(id,threshold,now);if(changed>0){mapper.insertSlaNotification(id,threshold,now);if("ESCALATED_150".equals(threshold))mapper.insertSupervisorEscalationNotification(id,now);}return changed;}
    private void requireOwner(Long todoId,Long userId){TodoInstance todo=mapper.selectById(todoId);if(todo==null)throw new TodoException("TODO_NOT_FOUND","待办不存在");if(!access.canOperate(todo,userId))throw new TodoException("TODO_ACCESS_DENIED","无权控制该待办的SLA计时");}
    private int percent(Map<String,Object> item,LocalDateTime now){Object supplied=item.get("percent");if(supplied!=null)return Integer.parseInt(String.valueOf(supplied));LocalDateTime start=dateTime(value(item,"start_at","startAt")),due=dateTime(value(item,"due_at","dueAt"));WorkCalendar calendar=calendar(item);WorkingTimeCalculator calculator=new WorkingTimeCalculator();long total=Math.max(1,calculator.workingMinutesBetween(start,due,calendar));long elapsed=calculator.workingMinutesBetween(start,now,calendar);return (int)Math.min(999,elapsed*100/total);}
    private WorkCalendar calendar(Map<String,Object> item){Set<DayOfWeek> days=EnumSet.noneOf(DayOfWeek.class);for(String day:String.valueOf(value(item,"work_days","workDays")).split(","))days.add(DayOfWeek.of(Integer.parseInt(day)));Map<LocalDate,Boolean> exceptions=new HashMap<>();String json=String.valueOf(value(item,"exception_json","exceptionJson"));if(json!=null&&!"null".equals(json)&&!json.isBlank())for(Map.Entry<String,Object> entry:JSON.parseObject(json).entrySet())exceptions.put(LocalDate.parse(entry.getKey()),Boolean.valueOf(String.valueOf(entry.getValue())));return new WorkCalendar(days,time(value(item,"work_start","workStart")),time(value(item,"work_end","workEnd")),exceptions);}
    private LocalDateTime dateTime(Object value){return value instanceof LocalDateTime time?time:LocalDateTime.parse(String.valueOf(value).replace(' ','T'));}
    private LocalTime time(Object value){String text=String.valueOf(value);return LocalTime.parse(text.length()>=8?text.substring(0,8):text);}
}
