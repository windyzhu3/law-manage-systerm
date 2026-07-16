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
import org.springframework.beans.factory.annotation.Autowired;
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
import com.law.todo.routing.TodoRoutingEngine.NextTask;
import com.law.todo.routing.TodoRoutingEngine;
import com.law.todo.routing.TodoRoutingEngine.RouteContext;
import com.law.todo.routing.TodoRoutingEngine.RouteStatus;
import com.law.todo.routing.TodoRoutingEngine.RoutingResult;
import com.law.todo.routing.RouteToken;
import com.law.todo.routing.RouteTokenStatus;
import com.law.todo.definition.codec.TodoDefinitionCodec;
import com.law.todo.definition.model.TodoDefinitionDocument;

@Service
public class TodoRoutingService
{
    private final TodoMapper mapper;
    private final TodoAssignmentResolver resolver;
    private final TodoRoutingEngine engine;

    public TodoRoutingService(TodoMapper mapper)
    {
        this(mapper, new TodoAssignmentResolver(), new TodoRoutingEngine(mapper));
    }

    public TodoRoutingService(TodoMapper mapper, TodoAssignmentResolver resolver)
    {
        this(mapper, resolver, new TodoRoutingEngine(mapper));
    }

    @Autowired
    public TodoRoutingService(TodoMapper mapper, TodoAssignmentResolver resolver, TodoRoutingEngine engine)
    {
        this.mapper = mapper;
        this.resolver = resolver;
        this.engine = engine;
    }

    /** Compatibility boundary: canonical graphs are executed, otherwise legacy single-next is preserved. */
    @Transactional
    public RoutingResult advance(TodoInstance previous, Map<String, Object> payload)
    {
        Long routeVersionId = previous.getRouteDefinitionVersionId() == null ? previous.getTemplateVersionId() : previous.getRouteDefinitionVersionId();
        Map<String, Object> version = mapper.selectTemplateVersionById(routeVersionId);
        if (version == null || version.isEmpty()) return new RoutingResult(RouteStatus.ENDED, java.util.List.of());
        String loadedHash = text(value(version, "definition_hash", "definitionHash"));
        if (previous.getDefinitionHash() != null && !previous.getDefinitionHash().equals(loadedHash))
            throw new TodoException("TODO_ROUTE_DEFINITION_HASH_MISMATCH", "Routing definition snapshot does not match the persisted definition");
        String compiled = text(value(version, "compiled_json", "compiledJson"));
        if (compiled == null || compiled.isBlank()) compiled = text(value(version, "definition_json", "definitionJson"));
        if (compiled != null && !compiled.isBlank())
        {
            TodoDefinitionDocument definition = new TodoDefinitionCodec().read(compiled);
            if (definition.routing() != null && definition.routing().config().containsKey("nodes"))
            {
                String start = text(definition.routing().config().get("start"));
                Long root = previous.getRootTodoId() == null ? previous.getTodoId() : previous.getRootTodoId();
                RouteToken token = previous.getRouteToken() == null || previous.getRouteToken().isBlank()
                        ? new RouteToken(root, previous.getRouteNodeKey() == null ? start : previous.getRouteNodeKey(), null, 0, RouteTokenStatus.ACTIVE)
                        : JSON.parseObject(previous.getRouteToken(), RouteToken.class);
                int schemaVersion = previous.getPayloadSchemaVersion() != null ? previous.getPayloadSchemaVersion()
                        : definition.event() == null ? 1 : definition.event().payloadVersion();
                String hash = previous.getDefinitionHash() == null ? text(value(version, "definition_hash", "definitionHash")) : previous.getDefinitionHash();
                RoutingResult result = engine.advance(new RouteContext(definition.routing(), hash, schemaVersion, previous, token, payload));
                for (NextTask task : result.tasks()) createNext(previous, task, hash, schemaVersion);
                return result;
            }
        }
        String nextJson = text(value(version, "next_rule_json", "nextRuleJson"));
        if (nextJson == null || nextJson.isBlank()) return new RoutingResult(RouteStatus.ENDED, java.util.List.of());
        JSONObject next = JSON.parseObject(nextJson);Long versionId = next.getLong("templateVersionId");
        if (versionId == null) return new RoutingResult(RouteStatus.ENDED, java.util.List.of());
        createNext(previous, versionId, next.getString("title"), next.getString("businessType") == null ? previous.getBusinessType() : next.getString("businessType"), previous.getBusinessId());
        return new RoutingResult(RouteStatus.ADVANCED, java.util.List.of());
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

    /** Graph-routing facade. The occurrence identity is intentionally independent of template version. */
    @Transactional
    public TodoInstance createNext(TodoInstance previous, NextTask task, String definitionHash, int payloadSchemaVersion)
    {
        String businessType = previous.getBusinessType();Long businessId = previous.getBusinessId();
        String key = task.token().rootTodoId() + ":" + task.nodeKey() + ":" + businessType + ":" + businessId + ":" + task.token().occurrence();
        TodoInstance existing = mapper.selectByNextKey(key);
        if (existing != null) return existing;
        Map<String, Object> version = mapper.selectTemplateVersionById(task.templateVersionId());
        if (version == null || version.isEmpty()) throw new TodoException("TODO_NEXT_TEMPLATE_NOT_FOUND", "Next Todo template version does not exist");
        if (!"PUBLISHED".equals(text(version.get("status"))))
            throw new TodoException("TODO_ROUTE_TASK_VERSION_NOT_PUBLISHED", "TASK must reference a published template version");
        Assignment assignment = assignment(version, previous);
        TodoInstance next = build(previous, version, assignment, task.templateVersionId(), null, businessType, businessId, key);
        next.setDefinitionHash(definitionHash);
        next.setRouteDefinitionVersionId(previous.getRouteDefinitionVersionId() == null ? previous.getTemplateVersionId() : previous.getRouteDefinitionVersionId());
        next.setUiSchemaSnapshot(text(value(version, "ui_schema_json", "uiSchemaJson")));
        next.setSlaSnapshot(text(value(version, "sla_rule_json", "slaRuleJson")));
        next.setRouteNodeKey(task.nodeKey());next.setRouteToken(JSON.toJSONString(task.token()));
        next.setOccurrenceKey(key);next.setPayloadSchemaVersion(payloadSchemaVersion);
        applySla(next, next.getSlaSnapshot());
        try { mapper.insertInstance(next); }
        catch (DuplicateKeyException duplicate)
        {
            TodoInstance concurrent = mapper.selectByNextKey(key);if (concurrent != null) return concurrent;throw duplicate;
        }
        insertRelation(next);insertCandidate(next, assignment);insertSla(next, version);return next;
    }

    private TodoInstance build(TodoInstance previous, Map<String, Object> version, Assignment assignment, Long versionId,
        String title, String businessType, Long businessId, String key)
    {
        TodoInstance next = new TodoInstance();
        next.setTodoNo("TD" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase());
        next.setTemplateId(longValue(value(version, "template_id", "templateId")));
        next.setTemplateVersionId(versionId);
        next.setTemplateCode(text(value(version,"template_code","templateCode")));
        String templateName = text(value(version, "template_name", "templateName"));
        next.setTitle(title == null || title.isBlank() ? templateName : title);
        next.setBusinessType(businessType);next.setBusinessId(businessId);next.setBusinessNo(previous.getBusinessNo());
        next.setOwnerId(assignment.ownerId());
        next.setOwnerDeptId(assignment.ownerId() == null || assignment.ownerId().equals(previous.getOwnerId()) ? previous.getOwnerDeptId() : mapper.selectUserDeptId(assignment.ownerId()));
        next.setStatus("CREATED");next.setPriority("NORMAL");next.setSlaStatus("NORMAL");next.setCreatedAt(LocalDateTime.now());
        next.setDodSnapshotJson(text(value(version, "dod_rule_json", "dodRuleJson")));
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
        JSONObject rule = JSON.parseObject(json);if (rule.getLongValue("minutes") <= 0 || rule.getString("calendarCode") == null) return;
        Map<String, Object> calendar = mapper.selectCalendarByCode(rule.getString("calendarCode"));
        if (calendar == null || calendar.isEmpty()) throw new TodoException("TODO_SLA_CALENDAR_NOT_FOUND", "下一待办的SLA工作日历不存在");
        todo.setDueAt(new WorkingTimeCalculator().addWorkingMinutes(todo.getCreatedAt(), rule.getLongValue("minutes"), calendar(calendar)));
    }

    private void insertSla(TodoInstance todo, Map<String, Object> version)
    {
        if (todo.getDueAt() == null) return;
        JSONObject rule = JSON.parseObject(text(value(version, "sla_rule_json", "slaRuleJson")));Map<String, Object> calendar = mapper.selectCalendarByCode(rule.getString("calendarCode"));
        TodoSlaService.ThresholdPlan plan=new TodoSlaService(mapper,null).planThresholds(todo.getCreatedAt(),todo.getDueAt(),calendar(calendar));
        Map<String, Object> record = new HashMap<>();record.put("todoId", todo.getTodoId());record.put("calendarId", longValue(value(calendar, "calendar_id", "calendarId")));record.put("startAt", todo.getCreatedAt());record.put("dueAt", todo.getDueAt());record.put("remind80DueAt",plan.remind80DueAt());record.put("overdue100DueAt",plan.overdue100DueAt());record.put("escalate150DueAt",plan.escalate150DueAt());mapper.insertSlaRecord(record);
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
