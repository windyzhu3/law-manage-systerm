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
import com.law.todo.extension.TodoExtensionCommands.CycleOccurrence;
import com.law.todo.extension.TodoExtensionCommands.CyclePolicy;
import com.law.todo.extension.TodoExtensionCommands.DurationPolicy;
import com.law.todo.extension.TodoExtensionCommands.DurationUnit;

@Service
public class TodoSlaService
{
    private final TodoMapper mapper;private final TodoAccessPolicy access;
    public TodoSlaService(TodoMapper mapper,TodoAccessPolicy access){this.mapper=mapper;this.access=access;}
    @Transactional public int scanAndEscalate(LocalDateTime now)
    {
        List<Map<String,Object>> items=mapper.selectSlaScanItems(now);int changed=0;if(items==null)return 0;
        for(Map<String,Object> item:items)
        {
            Long id=Long.valueOf(String.valueOf(value(item,"todo_id","todoId")));int version=Integer.parseInt(String.valueOf(value(item,"version","version")));LocalDateTime due=dateTime(value(item,"due_at","dueAt"));
            LocalDateTime p80=nullableDate(value(item,"remind80_due_at","remind80DueAt"));LocalDateTime p100=nullableDate(value(item,"overdue100_due_at","overdue100DueAt"));LocalDateTime p150=nullableDate(value(item,"escalate150_due_at","escalate150DueAt"));
            Integer legacyPercent=p80==null||p100==null||p150==null?percent(item,now):null;
            int fired=markIfDue(id,"REMINDED_80",80,p80,due,legacyPercent,version,now);changed+=fired;version+=fired;
            fired=markIfDue(id,"OVERDUE_100",100,p100,due,legacyPercent,version,now);changed+=fired;version+=fired;
            fired=markIfDue(id,"ESCALATED_150",150,p150,due,legacyPercent,version,now);changed+=fired;
        }
        return changed;
    }
    @Transactional public boolean pause(Long todoId,Long userId,LocalDateTime now){requireOwner(todoId,userId);return mapper.pauseSla(todoId,now)>0;}
    @Transactional public boolean resume(Long todoId,Long userId,LocalDateTime now){requireOwner(todoId,userId);return mapper.resumeSla(todoId,now)>0;}
    public LocalDateTime addDuration(LocalDateTime start,DurationPolicy policy,WorkCalendar calendar)
    {
        if(start==null||policy==null)throw new IllegalArgumentException("start and policy are required");
        return switch(policy.unit())
        {
            case MINUTES -> start.plusMinutes(policy.value());
            case HOURS -> start.plusHours(policy.value());
            case CALENDAR_DAYS -> start.plusDays(policy.value());
            case WORKING_DAYS -> addWorkingDays(start,policy.value(),calendar);
        };
    }
    public List<CycleOccurrence> occurrences(String cycleKey,LocalDateTime anchor,CyclePolicy policy,WorkCalendar calendar)
    {
        if(cycleKey==null||cycleKey.isBlank())throw new IllegalArgumentException("cycleKey is required");
        List<CycleOccurrence> result=new java.util.ArrayList<>();LocalDateTime due=anchor;
        DurationPolicy duration=new DurationPolicy(policy.policyVersionId(),policy.interval(),policy.unit());
        for(int occurrence=1;occurrence<=policy.maxOccurrences();occurrence++)
        {due=addDuration(due,duration,calendar);result.add(new CycleOccurrence(cycleKey+":"+occurrence,occurrence,due,policy.policyVersionId()));}
        return List.copyOf(result);
    }
    public ThresholdPlan planThresholds(LocalDateTime start,LocalDateTime due,WorkCalendar calendar)
    {
        if(start==null||due==null||calendar==null||!due.isAfter(start))throw new IllegalArgumentException("A positive governed SLA interval is required");
        WorkingTimeCalculator calculator=new WorkingTimeCalculator();long total=Math.max(1,calculator.workingMinutesBetween(start,due,calendar));
        return new ThresholdPlan(calculator.addWorkingMinutes(start,thresholdMinutes(total,80),calendar),calculator.addWorkingMinutes(start,total,calendar),calculator.addWorkingMinutes(start,thresholdMinutes(total,150),calendar));
    }
    public record ThresholdPlan(LocalDateTime remind80DueAt,LocalDateTime overdue100DueAt,LocalDateTime escalate150DueAt) { }
    private LocalDateTime addWorkingDays(LocalDateTime start,long days,WorkCalendar calendar)
    {
        if(calendar==null)throw new IllegalArgumentException("calendar is required for WORKING_DAYS");
        LocalDate date=start.toLocalDate();long remaining=days;
        while(remaining>0){date=date.plusDays(1);if(calendar.working(date))remaining--;}
        LocalTime time=start.toLocalTime();if(time.isBefore(calendar.workStart()))time=calendar.workStart();if(time.isAfter(calendar.workEnd()))time=calendar.workEnd();
        return LocalDateTime.of(date,time);
    }
    private Object value(Map<String,Object> m,String a,String b){return m.containsKey(a)?m.get(a):m.get(b);}
    private long thresholdMinutes(long total,int threshold){return (total*threshold+99)/100;}
    private int markIfDue(Long id,String threshold,int requiredPercent,LocalDateTime planned,LocalDateTime expectedDue,Integer legacyPercent,Integer version,LocalDateTime now){if(planned!=null?planned.isAfter(now):legacyPercent==null||legacyPercent<requiredPercent)return 0;int changed=mapper.markSlaThreshold(id,threshold,planned,expectedDue,version,now);if(changed>0){mapper.insertSlaNotification(id,threshold,now);if("ESCALATED_150".equals(threshold))mapper.insertSupervisorEscalationNotification(id,now);}return changed;}
    private void requireOwner(Long todoId,Long userId){TodoInstance todo=mapper.selectById(todoId);if(todo==null)throw new TodoException("TODO_NOT_FOUND","待办不存在");if(!access.canOperate(todo,userId))throw new TodoException("TODO_ACCESS_DENIED","无权控制该待办的SLA计时");}
    private int percent(Map<String,Object> item,LocalDateTime now){Object supplied=item.get("percent");if(supplied!=null)return Integer.parseInt(String.valueOf(supplied));LocalDateTime start=dateTime(value(item,"start_at","startAt")),due=dateTime(value(item,"due_at","dueAt"));WorkCalendar calendar=calendar(item);WorkingTimeCalculator calculator=new WorkingTimeCalculator();long total=Math.max(1,calculator.workingMinutesBetween(start,due,calendar));long elapsed=calculator.workingMinutesBetween(start,now,calendar);return (int)Math.min(999,elapsed*100/total);}
    private WorkCalendar calendar(Map<String,Object> item){Set<DayOfWeek> days=EnumSet.noneOf(DayOfWeek.class);for(String day:String.valueOf(value(item,"work_days","workDays")).split(","))days.add(DayOfWeek.of(Integer.parseInt(day)));Map<LocalDate,Boolean> exceptions=new HashMap<>();String json=String.valueOf(value(item,"exception_json","exceptionJson"));if(json!=null&&!"null".equals(json)&&!json.isBlank())for(Map.Entry<String,Object> entry:JSON.parseObject(json).entrySet())exceptions.put(LocalDate.parse(entry.getKey()),Boolean.valueOf(String.valueOf(entry.getValue())));return new WorkCalendar(days,time(value(item,"work_start","workStart")),time(value(item,"work_end","workEnd")),exceptions);}
    private LocalDateTime dateTime(Object value){return value instanceof LocalDateTime time?time:LocalDateTime.parse(String.valueOf(value).replace(' ','T'));}
    private LocalDateTime nullableDate(Object value){return value==null?null:dateTime(value);}
    private LocalTime time(Object value){String text=String.valueOf(value);return LocalTime.parse(text.length()>=8?text.substring(0,8):text);}
}
