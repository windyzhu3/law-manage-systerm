package com.law.todo.integration;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.EnumSet;
import java.util.HashMap;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.todo.application.TodoAssignmentResolver;
import com.law.todo.application.TodoAssignmentResolver.Assignment;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.domain.service.WorkingTimeCalculator;
import com.law.todo.domain.service.WorkingTimeCalculator.WorkCalendar;

@Service
public class TodoEventService
{
    private final TodoMapper mapper;private final TodoAssignmentResolver resolver;
    public TodoEventService(TodoMapper mapper,TodoAssignmentResolver resolver){this.mapper=mapper;this.resolver=resolver;}
    public boolean supports(String eventType,String aggregateType){List<Map<String,Object>> rules=mapper.selectTriggerRules(eventType,aggregateType);return rules!=null&&!rules.isEmpty();}
    @Transactional public List<TodoInstance> handle(TodoEvent event)
    {
        List<TodoInstance> result=new ArrayList<>();List<Map<String,Object>> rules=mapper.selectTriggerRules(event.eventType(),event.aggregateType());if(rules==null)return result;
        for(Map<String,Object> rule:rules)
        {
            if(!matches(rule,event.payload()))continue;
            Long version=longValue(value(rule,"template_version_id","templateVersionId"));
            String key=event.eventId()+":"+version+":"+event.aggregateId();
            TodoInstance existing=mapper.selectByTriggerKey(key);
            if(existing!=null){result.add(existing);continue;}
            Assignment assignment=resolver.resolve(text(value(rule,"owner_rule_json","ownerRuleJson")),event.payload());
            TodoInstance todo=new TodoInstance();
            todo.setTodoNo("TD"+UUID.randomUUID().toString().replace("-","").substring(0,20).toUpperCase());
            todo.setTemplateId(longValue(value(rule,"template_id","templateId")));
            todo.setTemplateVersionId(version);
            todo.setTitle(text(value(rule,"template_name","templateName")));
            todo.setBusinessType(event.aggregateType());
            todo.setBusinessId(event.aggregateId());
            todo.setBusinessNo(event.aggregateNo());
            todo.setOwnerId(assignment.ownerId());
            if(assignment.ownerId()!=null)todo.setOwnerDeptId(mapper.selectUserDeptId(assignment.ownerId()));
            todo.setStatus("CREATED");todo.setPriority("NORMAL");todo.setSlaStatus("NORMAL");
            todo.setCreatedAt(LocalDateTime.now());todo.setTriggerEventId(event.eventId());todo.setTriggerIdempotencyKey(key);
            applySla(todo,text(value(rule,"sla_rule_json","slaRuleJson")));
            mapper.insertInstance(todo);
            createRelation(todo);
            createSla(todo,rule);
            if(assignment.candidateType()!=null){Map<String,Object> c=new HashMap<>();c.put("todoId",todo.getTodoId());c.put("candidateType",assignment.candidateType());c.put("candidateValue",assignment.candidateValue());mapper.insertCandidate(c);}
            result.add(todo);
        }
        return result;
    }
    private boolean matches(Map<String,Object> rule,Map<String,Object> payload){String json=text(value(rule,"condition_json","conditionJson"));if(json==null||json.isBlank())return true;JSONObject conditions=JSON.parseObject(json);for(Map.Entry<String,Object> condition:conditions.entrySet()){Object actual=payload.get(condition.getKey());if(actual==null||!String.valueOf(actual).equals(String.valueOf(condition.getValue())))return false;}return true;}
    private void createRelation(TodoInstance todo){Map<String,Object> relation=new HashMap<>();relation.put("todoId",todo.getTodoId());relation.put("businessType",todo.getBusinessType());relation.put("businessId",todo.getBusinessId());relation.put("businessNo",todo.getBusinessNo());relation.put("relationType","PRIMARY");mapper.insertRelation(relation);}
    private Object value(Map<String,Object> map,String a,String b){return map.containsKey(a)?map.get(a):map.get(b);}private Long longValue(Object v){return v==null?null:Long.valueOf(String.valueOf(v));}private String text(Object v){return v==null?null:String.valueOf(v);}
    private void applySla(TodoInstance todo,String json){if(json==null||json.isBlank())return;JSONObject rule=JSON.parseObject(json);Map<String,Object> calendar=mapper.selectCalendarByCode(rule.getString("calendarCode"));if(calendar==null||calendar.isEmpty())return;LocalDateTime start=todo.getCreatedAt();WorkCalendar c=calendar(calendar);todo.setDueAt(new WorkingTimeCalculator().addWorkingMinutes(start,rule.getLongValue("minutes"),c));}
    private void createSla(TodoInstance todo,Map<String,Object> rule){if(todo.getDueAt()==null)return;String json=text(value(rule,"sla_rule_json","slaRuleJson"));JSONObject r=JSON.parseObject(json);Map<String,Object> calendar=mapper.selectCalendarByCode(r.getString("calendarCode"));Map<String,Object> record=new HashMap<>();record.put("todoId",todo.getTodoId());record.put("calendarId",longValue(value(calendar,"calendar_id","calendarId")));record.put("startAt",todo.getCreatedAt());record.put("dueAt",todo.getDueAt());mapper.insertSlaRecord(record);}
    private WorkCalendar calendar(Map<String,Object> value){Set<DayOfWeek> days=EnumSet.noneOf(DayOfWeek.class);for(String d:text(value(value,"work_days","workDays")).split(","))days.add(DayOfWeek.of(Integer.parseInt(d)));LocalTime start=time(value(value,"work_start","workStart")),end=time(value(value,"work_end","workEnd"));Map<LocalDate,Boolean> exceptions=new HashMap<>();String json=text(value(value,"exception_json","exceptionJson"));if(json!=null&&!json.isBlank())for(Map.Entry<String,Object> e:JSON.parseObject(json).entrySet())exceptions.put(LocalDate.parse(e.getKey()),Boolean.valueOf(String.valueOf(e.getValue())));return new WorkCalendar(days,start,end,exceptions);}
    private LocalTime time(Object value){String s=String.valueOf(value);return LocalTime.parse(s.length()>=8?s.substring(0,8):s);}
}
