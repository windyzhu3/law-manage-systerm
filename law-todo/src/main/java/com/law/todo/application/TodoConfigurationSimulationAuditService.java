package com.law.todo.application;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

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
    private static final Pattern EMAIL=Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern PHONE=Pattern.compile("(?<!\\d)(?:\\+?\\d[ -]?){8,15}(?!\\d)");
    private static final Pattern IDENTITY=Pattern.compile("(?i)\\b(?:\\d{15}|\\d{17}[0-9X]|\\d{3}-\\d{2}-\\d{4})\\b");
    private static final Pattern FILE_URL=Pattern.compile("(?i)(?:file://|https?://\\S*(?:/file(?:/|[?#]|$)|fileurl))");
    private static final Pattern SECRET_VALUE=Pattern.compile("(?i)(?:bearer\\s+|(?:secret|token|password|passwd|credential|authorization|api[-_ ]?key)\\s*[=:]?\\s*)\\S+");
    private final TodoConfigurationMapper mapper;

    public TodoConfigurationSimulationAuditService(TodoConfigurationMapper mapper){this.mapper=mapper;}

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void record(ConfigurationSimulationCommand command,Actor actor,long duration,Map<String,Object> result)
    {
        Map<String,Object> row=new LinkedHashMap<>();
        row.put("requestId",command.requestId());row.put("templateVersionId",command.versionId());
        row.put("eventType",command.eventType());row.put("businessType",command.businessType());row.put("businessId",command.businessId());
        row.put("inputSummaryJson",JSON.toJSONString(inputSummary(command.payload())));
        row.put("resultJson",JSON.toJSONString(sanitize(jsonTree(result))));
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
        if(sensitiveKey(key)||!key.matches("[A-Za-z][A-Za-z0-9_-]{0,63}"))return "redacted"+(++redacted[0]);
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

    private Object sanitize(Object value)
    {
        if(value==null)return null;
        if(value instanceof Map<?,?> source)
        {
            Map<String,Object> clean=new LinkedHashMap<>();int redacted=0;
            for(Map.Entry<?,?> entry:source.entrySet())
            {
                String rawKey=String.valueOf(entry.getKey());String key=bounded(rawKey);
                if(sensitiveKey(rawKey))clean.put("redacted_"+(++redacted),"[REDACTED]");
                else clean.put(key,sanitize(entry.getValue()));
            }
            return clean;
        }
        if(value instanceof Iterable<?> source)
        {
            List<Object> clean=new ArrayList<>();for(Object entry:source)clean.add(sanitize(entry));return clean;
        }
        if(value.getClass().isArray())
        {
            List<Object> clean=new ArrayList<>();for(int i=0;i<Array.getLength(value);i++)clean.add(sanitize(Array.get(value,i)));return clean;
        }
        if(value instanceof Number||value instanceof Boolean)return value;
        return sanitizedText(String.valueOf(value));
    }

    private boolean sensitiveKey(String value)
    {
        String key=value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");
        return key.contains("secret")||key.contains("token")||key.contains("password")||key.contains("passwd")
                ||key.contains("credential")||key.contains("authorization")||key.contains("apikey")||key.contains("accesskey")
                ||key.contains("fileurl")||key.contains("phone")||key.contains("mobile")||key.contains("email")
                ||key.contains("idcard")||key.contains("identity")||key.contains("ssn")||key.contains("passport")
                ||key.contains("naturalperson")||key.contains("personname")||key.contains("fullname");
    }

    private String sanitizedText(String value)
    {
        if(EMAIL.matcher(value).find()||PHONE.matcher(value).find()||IDENTITY.matcher(value).find()
                ||FILE_URL.matcher(value).find()||SECRET_VALUE.matcher(value).find())return "[REDACTED]";
        return bounded(value);
    }

    private String bounded(String value)
    {return value.length()<=MAX_AUDIT_TEXT?value:value.substring(0,MAX_AUDIT_TEXT)+"[TRUNCATED]";}
}
