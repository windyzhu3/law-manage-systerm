package com.law.todo.integration;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.todo.application.TodoAssignmentResolver;
import com.law.todo.application.TodoAssignmentResolver.Assignment;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;

@Service
public class TodoEventService
{
    private final TodoMapper mapper;private final TodoAssignmentResolver resolver;
    public TodoEventService(TodoMapper mapper,TodoAssignmentResolver resolver){this.mapper=mapper;this.resolver=resolver;}
    public boolean supports(String eventType,String aggregateType){List<Map<String,Object>> rules=mapper.selectTriggerRules(eventType,aggregateType);return rules!=null&&!rules.isEmpty();}
    @Transactional public List<TodoInstance> handle(TodoEvent event)
    {
        List<TodoInstance> result=new ArrayList<>();List<Map<String,Object>> rules=mapper.selectTriggerRules(event.eventType(),event.aggregateType());if(rules==null)return result;
        for(Map<String,Object> rule:rules){Long version=longValue(value(rule,"template_version_id","templateVersionId"));String key=event.eventId()+":"+version+":"+event.aggregateId();TodoInstance existing=mapper.selectByTriggerKey(key);if(existing!=null){result.add(existing);continue;}Assignment assignment=resolver.resolve(text(value(rule,"owner_rule_json","ownerRuleJson")),event.payload());TodoInstance todo=new TodoInstance();todo.setTodoNo("TD"+UUID.randomUUID().toString().replace("-","").substring(0,20).toUpperCase());todo.setTemplateId(longValue(value(rule,"template_id","templateId")));todo.setTemplateVersionId(version);todo.setTitle(text(value(rule,"template_name","templateName")));todo.setBusinessType(event.aggregateType());todo.setBusinessId(event.aggregateId());todo.setBusinessNo(event.aggregateNo());todo.setOwnerId(assignment.ownerId());todo.setStatus("CREATED");todo.setPriority("NORMAL");todo.setSlaStatus("NORMAL");todo.setCreatedAt(LocalDateTime.now());todo.setTriggerEventId(event.eventId());todo.setTriggerIdempotencyKey(key);mapper.insertInstance(todo);if(assignment.candidateType()!=null){Map<String,Object> c=new java.util.HashMap<>();c.put("todoId",todo.getTodoId());c.put("candidateType",assignment.candidateType());c.put("candidateValue",assignment.candidateValue());mapper.insertCandidate(c);}result.add(todo);}return result;
    }
    private Object value(Map<String,Object> map,String a,String b){return map.containsKey(a)?map.get(a):map.get(b);}private Long longValue(Object v){return v==null?null:Long.valueOf(String.valueOf(v));}private String text(Object v){return v==null?null:String.valueOf(v);}
}
