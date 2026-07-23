package com.law.todo.application;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson2.JSON;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoConfigurationCommands.ConfigurationSimulationCommand;
import com.law.todo.mapper.TodoConfigurationMapper;

/** Persists the sole simulation-side effect in its own transaction. */
@Service
public class TodoConfigurationSimulationAuditService
{
    private static final int MAX_AUDIT_TEXT=512;
    private final TodoConfigurationMapper mapper;

    public TodoConfigurationSimulationAuditService(TodoConfigurationMapper mapper){this.mapper=mapper;}

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void record(ConfigurationSimulationCommand command,Actor actor,long duration,Map<String,Object> result)
    {record(command,actor,duration,result,TodoSensitiveDataPolicy.heuristicOnly());}

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void record(ConfigurationSimulationCommand command,Actor actor,long duration,Map<String,Object> result,
            TodoSensitiveDataPolicy policy)
    {
        TodoSensitiveDataPolicy effective=policy==null?TodoSensitiveDataPolicy.heuristicOnly():policy;
        Map<String,Object> row=new LinkedHashMap<>();
        row.put("requestId",command.requestId());row.put("templateVersionId",command.versionId());
        row.put("eventType",command.eventType());row.put("businessType",command.businessType());row.put("businessId",command.businessId());
        row.put("inputSummaryJson",JSON.toJSONString(inputSummary(command.payload())));
        row.put("resultJson",JSON.toJSONString(effective.redact(jsonTree(result))));
        row.put("durationMs",duration);row.put("operatorId",actor.userId());mapper.insertSimulationRecord(row);
    }

    private Map<String,Object> inputSummary(Map<String,Object> payload)
    {
        List<Map<String,Object>> fields=new ArrayList<>();describe("payload",payload,fields);Map<String,Object> result=new LinkedHashMap<>();
        result.put("fields",fields);return result;
    }

    private void describe(String path,Object value,List<Map<String,Object>> fields)
    {
        Map<String,Object> field=new LinkedHashMap<>();field.put("path",path);
        if(value instanceof Map<?,?> map)
        {
            field.put("type","OBJECT");field.put("size",map.size());fields.add(field);int[] redacted={0};
            map.entrySet().stream().sorted(Comparator.comparing(entry->String.valueOf(entry.getKey()))).forEach(entry ->
                    describe(path+"."+safePathSegment(String.valueOf(entry.getKey()),redacted),entry.getValue(),fields));
            return;
        }
        if(value instanceof Iterable<?> values)
        {
            List<Object> copy=new ArrayList<>();values.forEach(copy::add);field.put("type","ARRAY");field.put("size",copy.size());fields.add(field);
            for(Object entry:copy)describe(path+"[]",entry,fields);return;
        }
        if(value!=null&&value.getClass().isArray())
        {
            int size=Array.getLength(value);field.put("type","ARRAY");field.put("size",size);fields.add(field);
            for(int index=0;index<size;index++)describe(path+"[]",Array.get(value,index),fields);return;
        }
        field.put("type",type(value));fields.add(field);
    }

    private String safePathSegment(String key,int[] redacted)
    {
        if(TodoSensitiveDataPolicy.sensitiveKey(key)||!key.matches("[A-Za-z][A-Za-z0-9_-]{0,63}"))
            return "redacted"+(++redacted[0]);
        return key;
    }

    private String type(Object value)
    {
        if(value==null)return "NULL";
        if(value instanceof Boolean)return "BOOLEAN";
        if(value instanceof Number)return "NUMBER";
        return "STRING";
    }

    private Object jsonTree(Object value)
    {
        try{return JSON.parse(JSON.toJSONString(value));}
        catch(RuntimeException ignored){return value;}
    }

    private String bounded(String value)
    {return value.length()<=MAX_AUDIT_TEXT?value:value.substring(0,MAX_AUDIT_TEXT)+"[TRUNCATED]";}
}
