package com.law.todo.application;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
import com.law.todo.assignment.OwnerResolutionContext;
import com.law.todo.assignment.OwnerResolutionResult;
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
import com.law.todo.definition.model.TodoDefinitionDocument.OwnerRule;

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
                for (NextTask task : result.tasks())
                    createNext(previous, task, hash, schemaVersion, payload);
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
        Assignment assignment = assignment(version, previous, Map.of());
        requireAssignment(assignment);
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
        return createNext(previous,task,definitionHash,payloadSchemaVersion,Map.of());
    }

    @Transactional
    public TodoInstance createNext(TodoInstance previous, NextTask task, String definitionHash,
            int payloadSchemaVersion,Map<String,Object> routingPayload)
    {
        String businessType = previous.getBusinessType();Long businessId = previous.getBusinessId();
        String key = task.token().rootTodoId() + ":" + task.nodeKey() + ":" + businessType + ":" + businessId + ":" + task.token().occurrence();
        TodoInstance existing = mapper.selectByNextKey(key);
        if (existing != null) return existing;
        Map<String, Object> version = mapper.selectTemplateVersionById(task.templateVersionId());
        if (version == null || version.isEmpty()) throw new TodoException("TODO_NEXT_TEMPLATE_NOT_FOUND", "Next Todo template version does not exist");
        if (!"PUBLISHED".equals(text(version.get("status"))))
            throw new TodoException("TODO_ROUTE_TASK_VERSION_NOT_PUBLISHED", "TASK must reference a published template version");
        Assignment assignment = assignment(version, previous, routingPayload);
        requireAssignment(assignment);
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

    /**
     * Materializes one persisted schedule occurrence. Its identity intentionally does not collapse
     * with graph or legacy next-task identity.
     */
    @Transactional
    public TodoInstance createScheduledNext(TodoInstance previous,Long templateVersionId,
            String occurrenceKey,LocalDateTime dueAt)
    {
        if(occurrenceKey==null||occurrenceKey.isBlank())
            throw new TodoException("TODO_SCHEDULE_OCCURRENCE_KEY_REQUIRED","Schedule occurrence key is required");
        if(dueAt==null)
            throw new TodoException("TODO_SCHEDULE_DUE_AT_REQUIRED","Schedule due time is required");
        String normalizedOccurrenceKey=occurrenceKey.trim();
        String key="SCHEDULE:"+normalizedOccurrenceKey;
        Map<String,Object> identity=mapper.selectScheduleOccurrenceIdentityByKey(normalizedOccurrenceKey);
        if(identity==null||!normalizedOccurrenceKey.equals(text(identity.get("occurrenceKey"))))
            throw new TodoException("TODO_SCHEDULE_OCCURRENCE_NOT_CLAIMED","Schedule occurrence claim does not exist");
        Long planId=longValue(identity.get("planId"));
        Map<String,Object> plan=planId==null?null:mapper.selectSchedulePlanForUpdate(planId);
        if(plan==null||!"ACTIVE".equals(text(plan.get("status"))))
            throw new TodoException("TODO_SCHEDULE_OCCURRENCE_NOT_CLAIMED","Schedule plan is no longer active");
        Long fencedTemplateVersionId=longValue(plan.get("templateVersionId"));
        if(fencedTemplateVersionId!=null&&!fencedTemplateVersionId.equals(templateVersionId))
            throw new TodoException("TODO_SCHEDULE_TEMPLATE_MISMATCH","Schedule occurrence template version changed");
        Map<String,Object> fence=mapper.selectScheduleOccurrenceWindowForUpdate(normalizedOccurrenceKey,planId);
        if(fence==null||!normalizedOccurrenceKey.equals(text(fence.get("occurrenceKey")))
                ||!sameIdentity(identity,fence,"occurrenceId")||!sameIdentity(identity,fence,"windowId"))
            throw new TodoException("TODO_SCHEDULE_OCCURRENCE_NOT_CLAIMED","Schedule occurrence identity changed");
        Map<String,Object> version=mapper.selectTemplateVersionById(templateVersionId);
        if(version==null||version.isEmpty())
            throw new TodoException("TODO_NEXT_TEMPLATE_NOT_FOUND","Scheduled Todo template version does not exist");
        if(!"PUBLISHED".equals(text(version.get("status"))))
            throw new TodoException("TODO_SCHEDULE_TEMPLATE_NOT_PUBLISHED","Schedule must reference a published template version");
        String templateBusinessType=text(value(version,"business_type","businessType"));
        if(templateBusinessType!=null&&!templateBusinessType.equals(previous.getBusinessType()))
            throw new TodoException("TODO_SCHEDULE_TEMPLATE_BUSINESS_MISMATCH","Scheduled template business type does not match the previous Todo");
        ScheduledGraph graph=scheduledGraph(version);
        String occurrenceStatus=text(fence.get("status"));
        TodoInstance existing=mapper.selectByNextKey(key);
        if("MATERIALIZED".equals(occurrenceStatus))
        {
            Long linkedTodoId=longValue(fence.get("todoId"));
            if(linkedTodoId==null||existing==null||!linkedTodoId.equals(existing.getTodoId()))
                throw new TodoException("TODO_SCHEDULE_OCCURRENCE_LINK_INVALID",
                        "Materialized occurrence has no exact linked Todo");
            return requireScheduledRouteSnapshot(existing,normalizedOccurrenceKey,graph.start(),
                    templateVersionId,previous);
        }
        if(!"CLAIMED".equals(occurrenceStatus)||!"PROCESSING".equals(text(fence.get("windowStatus"))))
            throw new TodoException("TODO_SCHEDULE_OCCURRENCE_NOT_CLAIMED","Schedule occurrence is no longer claimable");
        int occurrenceVersion=Integer.parseInt(String.valueOf(fence.get("version")));
        if(existing!=null)
        {
            requireScheduledRouteSnapshot(existing,normalizedOccurrenceKey,graph.start(),
                    templateVersionId,previous);
            linkScheduleOccurrence(normalizedOccurrenceKey,existing.getTodoId(),occurrenceVersion);
            return existing;
        }
        Assignment assignment=scheduledAssignment(version,previous);
        if(assignment.ownerId()==null&&assignment.candidateType()==null)
            throw new TodoException("TODO_OWNER_UNRESOLVED","Scheduled Todo owner could not be resolved");
        TodoInstance next=build(previous,version,assignment,templateVersionId,null,
                previous.getBusinessType(),previous.getBusinessId(),key);
        next.setRootTodoId(null);
        next.setOccurrenceKey(normalizedOccurrenceKey);
        next.setDefinitionHash(text(value(version,"definition_hash","definitionHash")));
        next.setRouteDefinitionVersionId(templateVersionId);
        next.setUiSchemaSnapshot(text(value(version,"ui_schema_json","uiSchemaJson")));
        next.setSlaSnapshot(text(value(version,"sla_rule_json","slaRuleJson")));
        snapshotScheduledGraph(next,graph);
        next.setDueAt(dueAt);
        if(!dueAt.isAfter(next.getCreatedAt()))next.setSlaStatus("OVERDUE");
        try { mapper.insertInstance(next); }
        catch(DuplicateKeyException duplicate)
        {
            TodoInstance concurrent=mapper.selectByNextKey(key);
            if(concurrent!=null)
            {
                requireScheduledRouteSnapshot(concurrent,normalizedOccurrenceKey,graph.start(),
                        templateVersionId,previous);
                linkScheduleOccurrence(normalizedOccurrenceKey,concurrent.getTodoId(),occurrenceVersion);
                return concurrent;
            }
            throw duplicate;
        }
        if(next.getTodoId()==null)
            throw new TodoException("TODO_SCHEDULE_TODO_ID_MISSING","Scheduled Todo identity was not generated");
        initializeScheduledRootRoute(next);
        insertRelation(next);insertCandidate(next,assignment);
        insertScheduledSla(next,previous,version);
        linkScheduleOccurrence(normalizedOccurrenceKey,next.getTodoId(),occurrenceVersion);
        return next;
    }

    private boolean sameIdentity(Map<String,Object> expected,Map<String,Object> actual,String key)
    {
        Long expectedValue=longValue(expected.get(key));
        Long actualValue=longValue(actual.get(key));
        return expectedValue!=null&&expectedValue.equals(actualValue);
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

    private Assignment assignment(Map<String, Object> version, TodoInstance previous,
            Map<String,Object> routingPayload)
    {
        String document=text(value(version,"compiled_json","compiledJson"));
        if(document==null||document.isBlank())document=text(value(version,"definition_json","definitionJson"));
        if(document!=null&&!document.isBlank())
        {
            try
            {
                TodoDefinitionDocument definition=new TodoDefinitionCodec().read(document);
                if(definition.owner()==null)
                    throw new TodoException("TODO_OWNER_RULE_RESOLUTION_FAILED",
                            "Canonical definition is missing its owner rule");
                Map<String,Object> payload=new LinkedHashMap<>();
                if(previous.getOwnerId()!=null)payload.put("ownerId",previous.getOwnerId());
                if(routingPayload!=null)payload.putAll(routingPayload);
                OwnerResolutionResult resolved=resolver.resolve(
                        resolveStableOwnerReferences(definition.owner()),
                        new OwnerResolutionContext(payload,previous.getBusinessType(),
                                previous.getBusinessId(),LocalDateTime.now()));
                if(resolved.ownerId()!=null)
                    return new Assignment(resolved.ownerId(),"USER",resolved.ownerId());
                if(!resolved.candidateUserIds().isEmpty())
                    return new Assignment(null,"USER",resolved.candidateUserIds().get(0));
                return new Assignment(null,null,null);
            }
            catch(TodoException explicit){throw explicit;}
            catch(RuntimeException invalid)
            {
                throw new TodoException("TODO_OWNER_RULE_RESOLUTION_FAILED",
                        "Canonical owner definition cannot be resolved");
            }
        }
        String ownerRule = text(value(version, "owner_rule_json", "ownerRuleJson"));
        Map<String, Object> payload = new HashMap<>();
        payload.put("ownerId", previous.getOwnerId());
        if(routingPayload!=null)payload.putAll(routingPayload);
        Assignment resolved = resolver.resolve(ownerRule, payload);
        if (resolved.ownerId() == null && resolved.candidateType() == null && (ownerRule == null || ownerRule.isBlank()))
            return new Assignment(previous.getOwnerId(), previous.getOwnerId() == null ? null : "USER", previous.getOwnerId());
        return resolved;
    }

    private void requireAssignment(Assignment assignment)
    {
        if(assignment.ownerId()==null&&assignment.candidateType()==null)
            throw new TodoException("TODO_OWNER_UNRESOLVED",
                    "Todo owner or candidate could not be resolved");
    }

    private Assignment scheduledAssignment(Map<String,Object> version,TodoInstance previous)
    {
        String document=text(value(version,"compiled_json","compiledJson"));
        if(document==null||document.isBlank())document=text(value(version,"definition_json","definitionJson"));
        if(document==null||document.isBlank())
            return resolveScheduledOwner(legacyScheduledOwnerRule(
                    text(value(version,"owner_rule_json","ownerRuleJson")),previous),previous);
        try
        {
            TodoDefinitionDocument definition=new TodoDefinitionCodec().read(document);
            if(definition.owner()==null)
                throw new TodoException("TODO_OWNER_RULE_RESOLUTION_FAILED","Canonical definition is missing its owner rule");
            return resolveScheduledOwner(resolveStableOwnerReferences(definition.owner()),previous);
        }
        catch(TodoException explicit){throw explicit;}
        catch(RuntimeException invalid)
        {
            throw new TodoException("TODO_OWNER_RULE_RESOLUTION_FAILED","Canonical owner definition cannot be resolved");
        }
    }

    private OwnerRule legacyScheduledOwnerRule(String rule,TodoInstance previous)
    {
        String value=rule==null?"":rule.trim();
        if(value.startsWith("\"")&&value.endsWith("\"")&&value.length()>=2)
            value=value.substring(1,value.length()-1);
        Map<String,Object> config=new HashMap<>();
        if(value.isBlank()||"OWNER".equals(value))
        {
            if(previous.getOwnerId()==null)
                throw new TodoException("TODO_OWNER_UNRESOLVED","Legacy OWNER has no previous owner");
            config.put("type","USER");config.put("operand",previous.getOwnerId());
            return new OwnerRule(config);
        }
        if(value.startsWith("PAYLOAD:"))
        {
            config.put("type","PAYLOAD");config.put("field",value.substring(8));
            return new OwnerRule(config);
        }
        String[] parts=value.split(":",2);
        if(parts.length==2&&java.util.Set.of("USER","ROLE","DEPT","POST").contains(parts[0]))
        {
            try
            {
                config.put("type",parts[0]);config.put("operand",Long.valueOf(parts[1]));
                return new OwnerRule(config);
            }
            catch(NumberFormatException invalid)
            {
                throw new TodoException("TODO_OWNER_RULE_RESOLUTION_FAILED","Legacy owner operand is invalid");
            }
        }
        throw new TodoException("TODO_OWNER_RULE_RESOLUTION_FAILED","Legacy owner rule is unsafe for scheduled creation");
    }

    private Assignment resolveScheduledOwner(OwnerRule owner,TodoInstance previous)
    {
        Map<String,Object> payload=new HashMap<>();payload.put("ownerId",previous.getOwnerId());
        OwnerResolutionResult resolved=resolver.resolve(owner,new OwnerResolutionContext(
                payload,previous.getBusinessType(),previous.getBusinessId(),LocalDateTime.now()));
        if(resolved.ownerId()!=null)return new Assignment(resolved.ownerId(),null,null);
        if(!resolved.candidateUserIds().isEmpty())
            return new Assignment(null,"USER",resolved.candidateUserIds().get(0));
        throw new TodoException("TODO_OWNER_UNRESOLVED","No eligible owner or candidate is available");
    }

    private OwnerRule resolveStableOwnerReferences(OwnerRule owner)
    {
        Object normalized=normalizeStableOwnerReferences(owner.config());
        if(!(normalized instanceof Map<?,?> raw))
            throw new TodoException("TODO_OWNER_RULE_RESOLUTION_FAILED","Canonical owner rule must be an object");
        Map<String,Object> config=new java.util.LinkedHashMap<>();
        raw.forEach((key,entry)->config.put(String.valueOf(key),entry));
        return new OwnerRule(config);
    }

    private Object normalizeStableOwnerReferences(Object input)
    {
        if(input instanceof java.util.Collection<?> entries)
        {
            java.util.List<Object> normalized=new java.util.ArrayList<>();
            for(Object entry:entries)normalized.add(normalizeStableOwnerReferences(entry));
            return normalized;
        }
        if(!(input instanceof Map<?,?> raw))return input;
        Map<String,Object> config=new java.util.LinkedHashMap<>();
        raw.forEach((key,entry)->config.put(String.valueOf(key),entry));
        Object roleKey=config.get("roleKey");
        if(roleKey!=null&&!String.valueOf(roleKey).isBlank())
        {
            Long id=mapper.selectRoleIdByKey(String.valueOf(roleKey));
            if(id==null)throw new TodoException("TODO_OWNER_ROLE_KEY_NOT_FOUND","Owner roleKey is unknown or disabled");
            config.put("operand",id);
        }
        Object departmentCode=config.get("departmentCode");
        if(departmentCode!=null&&!String.valueOf(departmentCode).isBlank())
        {
            Long id=mapper.selectDepartmentIdByCode(String.valueOf(departmentCode));
            if(id==null)throw new TodoException("TODO_OWNER_DEPARTMENT_CODE_NOT_FOUND","Owner departmentCode is unknown or disabled");
            config.put("operand",id);
        }
        for(Map.Entry<String,Object> entry:new java.util.ArrayList<>(config.entrySet()))
            if(entry.getValue() instanceof Map<?,?>||entry.getValue() instanceof java.util.Collection<?>)
                config.put(entry.getKey(),normalizeStableOwnerReferences(entry.getValue()));
        return config;
    }

    private ScheduledGraph scheduledGraph(Map<String,Object> version)
    {
        String document=text(value(version,"compiled_json","compiledJson"));
        if(document==null||document.isBlank())document=text(value(version,"definition_json","definitionJson"));
        if(document==null||document.isBlank())
            throw new TodoException("TODO_SCHEDULE_ROUTE_DEFINITION_REQUIRED",
                    "Scheduled Todo requires an executable route definition");
        TodoDefinitionDocument definition=new TodoDefinitionCodec().read(document);
        String start=definition.routing()==null?null:text(definition.routing().config().get("start"));
        if(start==null||start.isBlank()||!definition.routing().config().containsKey("nodes"))
            throw new TodoException("TODO_SCHEDULE_ROUTE_DEFINITION_REQUIRED",
                    "Scheduled Todo requires an executable route start node");
        return new ScheduledGraph(start,definition.event()==null?1:definition.event().payloadVersion());
    }

    private void snapshotScheduledGraph(TodoInstance todo,ScheduledGraph graph)
    {
        todo.setPayloadSchemaVersion(graph.payloadVersion());
        todo.setRouteNodeKey(graph.start());
    }

    private void initializeScheduledRootRoute(TodoInstance todo)
    {
        if(todo.getTodoId()==null||todo.getRouteNodeKey()==null)
            throw new TodoException("TODO_SCHEDULE_ROUTE_SNAPSHOT_INVALID",
                    "Scheduled Todo route identity cannot be initialized");
        RouteToken token=new RouteToken(todo.getTodoId(),todo.getRouteNodeKey(),null,0,
                RouteTokenStatus.ACTIVE);
        todo.setRootTodoId(todo.getTodoId());
        todo.setRouteToken(JSON.toJSONString(token));
        if(mapper.updateInitialRouteSnapshot(todo.getTodoId(),todo.getTodoId(),
                todo.getRouteToken(),todo.getOccurrenceKey())!=1)
            throw new TodoException("TODO_SCHEDULE_ROUTE_SNAPSHOT_PERSIST_FAILED",
                    "Scheduled Todo route identity could not be persisted");
    }

    private TodoInstance requireScheduledRouteSnapshot(TodoInstance todo,String occurrenceKey,
            String start,Long templateVersionId,TodoInstance previous)
    {
        if(todo==null||todo.getTodoId()==null||!todo.getTodoId().equals(todo.getRootTodoId())
                ||!start.equals(todo.getRouteNodeKey())
                ||!occurrenceKey.equals(todo.getOccurrenceKey())
                ||!java.util.Objects.equals(templateVersionId,todo.getTemplateVersionId())
                ||!java.util.Objects.equals(previous.getBusinessType(),todo.getBusinessType())
                ||!java.util.Objects.equals(previous.getBusinessId(),todo.getBusinessId())
                ||todo.getRouteToken()==null||todo.getRouteToken().isBlank())
            throw new TodoException("TODO_SCHEDULE_ROUTE_SNAPSHOT_INVALID",
                    "Existing scheduled Todo has an incomplete route identity");
        try
        {
            RouteToken token=JSON.parseObject(todo.getRouteToken(),RouteToken.class);
            if(token==null||!todo.getTodoId().equals(token.rootTodoId())
                    ||!start.equals(token.nodeKey())||token.branchKey()!=null
                    ||token.occurrence()!=0||token.status()!=RouteTokenStatus.ACTIVE)
                throw new TodoException("TODO_SCHEDULE_ROUTE_SNAPSHOT_INVALID",
                        "Existing scheduled Todo route token does not match its persisted identity");
        }
        catch(TodoException explicit){throw explicit;}
        catch(RuntimeException malformed)
        {
            throw new TodoException("TODO_SCHEDULE_ROUTE_SNAPSHOT_INVALID",
                    "Existing scheduled Todo route token is malformed");
        }
        return todo;
    }

    private record ScheduledGraph(String start,int payloadVersion) { }

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
        String json=text(value(version, "sla_rule_json", "slaRuleJson"));
        if(json==null||json.isBlank())
            throw new TodoException("TODO_SLA_RULE_REQUIRED","A due Todo must have an SLA rule");
        JSONObject rule = JSON.parseObject(json);Map<String, Object> calendar = mapper.selectCalendarByCode(rule.getString("calendarCode"));
        if(calendar==null||calendar.isEmpty())
            throw new TodoException("TODO_SLA_CALENDAR_NOT_FOUND","The Todo SLA work calendar does not exist");
        TodoSlaService.ThresholdPlan plan=new TodoSlaService(mapper,null).planThresholds(todo.getCreatedAt(),todo.getDueAt(),calendar(calendar));
        Map<String, Object> record = new HashMap<>();record.put("todoId", todo.getTodoId());record.put("calendarId", longValue(value(calendar, "calendar_id", "calendarId")));record.put("startAt", todo.getCreatedAt());record.put("dueAt", todo.getDueAt());record.put("remind80DueAt",plan.remind80DueAt());record.put("overdue100DueAt",plan.overdue100DueAt());record.put("escalate150DueAt",plan.escalate150DueAt());mapper.insertSlaRecord(record);
    }

    private void insertScheduledSla(TodoInstance todo,TodoInstance previous,Map<String,Object> version)
    {
        String json=text(value(version,"sla_rule_json","slaRuleJson"));
        if(json==null||json.isBlank())
            throw new TodoException("TODO_SLA_RULE_REQUIRED","A scheduled Todo must have an SLA rule");
        JSONObject rule=JSON.parseObject(json);
        Map<String,Object> workCalendar=mapper.selectCalendarByCode(rule.getString("calendarCode"));
        if(workCalendar==null||workCalendar.isEmpty())
            throw new TodoException("TODO_SLA_CALENDAR_NOT_FOUND","The scheduled Todo SLA work calendar does not exist");
        boolean overdue=!todo.getDueAt().isAfter(todo.getCreatedAt());
        LocalDateTime startAt=todo.getCreatedAt();
        if(overdue)
        {
            startAt=previous.getCreatedAt();
            if(startAt==null||!startAt.isBefore(todo.getDueAt()))
                startAt=todo.getDueAt().minusMinutes(1);
        }
        TodoSlaService.ThresholdPlan plan=new TodoSlaService(mapper,null)
                .planThresholds(startAt,todo.getDueAt(),calendar(workCalendar));
        Map<String,Object> record=new HashMap<>();
        record.put("todoId",todo.getTodoId());
        record.put("calendarId",longValue(value(workCalendar,"calendar_id","calendarId")));
        record.put("startAt",startAt);record.put("dueAt",todo.getDueAt());
        record.put("remind80DueAt",plan.remind80DueAt());
        record.put("overdue100DueAt",todo.getDueAt());
        record.put("escalate150DueAt",plan.escalate150DueAt());
        record.put("remind80At",overdue?todo.getCreatedAt():null);
        record.put("overdue100At",overdue?todo.getCreatedAt():null);
        mapper.insertScheduledSlaRecord(record);
    }

    private void linkScheduleOccurrence(String occurrenceKey,Long todoId,int expectedVersion)
    {
        if(todoId==null||mapper.linkScheduleOccurrenceByKey(
                occurrenceKey,todoId,expectedVersion,LocalDateTime.now())!=1)
            throw new TodoException("TODO_SCHEDULE_OCCURRENCE_FENCE_LOST",
                    "Schedule occurrence was cancelled or reclaimed before Todo commit");
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
