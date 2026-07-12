package com.law.todo.application;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.law.todo.application.TodoAssignmentResolver.Assignment;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.domain.service.WorkingTimeCalculator;
import com.law.todo.domain.service.WorkingTimeCalculator.WorkCalendar;
import com.law.todo.mapper.TodoMapper;

@Service
public class TodoRoutingService
{
    private final TodoMapper mapper;
    private final TodoAssignmentResolver resolver;

    public TodoRoutingService(TodoMapper mapper)
    {
        this(mapper, new TodoAssignmentResolver());
    }

    public TodoRoutingService(TodoMapper mapper, TodoAssignmentResolver resolver)
    {
        this.mapper = mapper;
        this.resolver = resolver;
    }

    @Transactional
    public TodoInstance createNext(TodoInstance previous, Long templateVersionId, String title, String businessType, Long businessId)
    {
        String key = previous.getTodoId() + ":" + templateVersionId;
        TodoInstance existing = mapper.selectByNextKey(key);
        if (existing != null) return existing;

        Map<String, Object> version = mapper.selectTemplateVersionById(templateVersionId);
        if (version == null || version.isEmpty()) throw new TodoException("TODO_NEXT_TEMPLATE_NOT_FOUND", "下一待办模板版本不存在");
        Assignment assignment = assignment(version, previous);
        TodoInstance next = build(previous, version, assignment, templateVersionId, title, businessType, businessId, key);
        applySla(next, text(value(version, "sla_rule_json", "slaRuleJson")));
        try
        {
            mapper.insertInstance(next);
        }
        catch (DuplicateKeyException duplicate)
        {
            TodoInstance concurrent = mapper.selectByNextKey(key);
            if (concurrent != null) return concurrent;
            throw duplicate;
        }
        insertRelation(next);
        insertCandidate(next, assignment);
        insertSla(next, version);
        return next;
    }

    private TodoInstance build(TodoInstance previous, Map<String, Object> version, Assignment assignment, Long versionId,
        String title, String businessType, Long businessId, String key)
    {
        TodoInstance next = new TodoInstance();
        next.setTodoNo("TD" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase());
        next.setTemplateId(longValue(value(version, "template_id", "templateId")));
        next.setTemplateVersionId(versionId);
        String templateName = text(value(version, "template_name", "templateName"));
        next.setTitle(title == null || title.isBlank() ? templateName : title);
        next.setBusinessType(businessType);next.setBusinessId(businessId);next.setBusinessNo(previous.getBusinessNo());
        next.setOwnerId(assignment.ownerId());
        next.setOwnerDeptId(assignment.ownerId() == null || assignment.ownerId().equals(previous.getOwnerId()) ? previous.getOwnerDeptId() : mapper.selectUserDeptId(assignment.ownerId()));
        next.setStatus("CREATED");next.setPriority("NORMAL");next.setSlaStatus("NORMAL");next.setCreatedAt(LocalDateTime.now());
        next.setPreviousTodoId(previous.getTodoId());next.setRootTodoId(previous.getRootTodoId() == null ? previous.getTodoId() : previous.getRootTodoId());next.setNextIdempotencyKey(key);
        return next;
    }

    private Assignment assignment(Map<String, Object> version, TodoInstance previous)
    {
        String ownerRule = text(value(version, "owner_rule_json", "ownerRuleJson"));
        Map<String, Object> payload = new HashMap<>();payload.put("ownerId", previous.getOwnerId());
        Assignment resolved = resolver.resolve(ownerRule, payload);
        if (resolved.ownerId() == null && resolved.candidateType() == null && (ownerRule == null || ownerRule.isBlank()))
            return new Assignment(previous.getOwnerId(), previous.getOwnerId() == null ? null : "USER", previous.getOwnerId());
        return resolved;
    }

    private void insertRelation(TodoInstance todo)
    {
        Map<String, Object> value = new HashMap<>();value.put("todoId", todo.getTodoId());value.put("businessType", todo.getBusinessType());value.put("businessId", todo.getBusinessId());value.put("businessNo", todo.getBusinessNo());value.put("relationType", "PRIMARY");mapper.insertRelation(value);
    }

    private void insertCandidate(TodoInstance todo, Assignment assignment)
    {
        if (assignment.candidateType() == null) return;
        Map<String, Object> value = new HashMap<>();value.put("todoId", todo.getTodoId());value.put("candidateType", assignment.candidateType());value.put("candidateValue", assignment.candidateValue());mapper.insertCandidate(value);
    }

    private void applySla(TodoInstance todo, String json)
    {
        if (json == null || json.isBlank()) return;
        JSONObject rule = JSON.parseObject(json);Map<String, Object> calendar = mapper.selectCalendarByCode(rule.getString("calendarCode"));
        if (calendar == null || calendar.isEmpty()) throw new TodoException("TODO_SLA_CALENDAR_NOT_FOUND", "下一待办的SLA工作日历不存在");
        todo.setDueAt(new WorkingTimeCalculator().addWorkingMinutes(todo.getCreatedAt(), rule.getLongValue("minutes"), calendar(calendar)));
    }

    private void insertSla(TodoInstance todo, Map<String, Object> version)
    {
        if (todo.getDueAt() == null) return;
        JSONObject rule = JSON.parseObject(text(value(version, "sla_rule_json", "slaRuleJson")));Map<String, Object> calendar = mapper.selectCalendarByCode(rule.getString("calendarCode"));
        Map<String, Object> record = new HashMap<>();record.put("todoId", todo.getTodoId());record.put("calendarId", longValue(value(calendar, "calendar_id", "calendarId")));record.put("startAt", todo.getCreatedAt());record.put("dueAt", todo.getDueAt());mapper.insertSlaRecord(record);
    }

    private WorkCalendar calendar(Map<String, Object> value)
    {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);for (String day : text(value(value, "work_days", "workDays")).split(",")) days.add(DayOfWeek.of(Integer.parseInt(day)));
        Map<LocalDate, Boolean> exceptions = new HashMap<>();String json = text(value(value, "exception_json", "exceptionJson"));if (json != null && !json.isBlank()) for (Map.Entry<String, Object> item : JSON.parseObject(json).entrySet()) exceptions.put(LocalDate.parse(item.getKey()), Boolean.valueOf(String.valueOf(item.getValue())));
        return new WorkCalendar(days, time(value(value, "work_start", "workStart")), time(value(value, "work_end", "workEnd")), exceptions);
    }

    private LocalTime time(Object value){String text=String.valueOf(value);return LocalTime.parse(text.length()>=8?text.substring(0,8):text);}
    private Object value(Map<String,Object> map,String snake,String camel){return map.containsKey(snake)?map.get(snake):map.get(camel);}
    private Long longValue(Object value){return value==null?null:Long.valueOf(String.valueOf(value));}
    private String text(Object value){return value==null?null:String.valueOf(value);}
}
