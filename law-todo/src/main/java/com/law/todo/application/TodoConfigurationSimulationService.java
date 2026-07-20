package com.law.todo.application;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson2.JSON;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoConfigurationCommands.ConfigurationSimulationCommand;
import com.law.todo.application.view.TodoConfigurationViews.ConfigurationSimulationResult;
import com.law.todo.application.view.TodoSimulationView;
import com.law.todo.mapper.TodoConfigurationMapper;

/**
 * Configuration simulation boundary. It delegates to the pure definition simulator and only writes a
 * redacted audit record; it deliberately has no runtime Todo or organization mutation dependency.
 */
@Service
public class TodoConfigurationSimulationService
{
    private static final int MAX_AUDIT_TEXT=512;
    private static final Pattern EMAIL=Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern PHONE=Pattern.compile("(?<!\\d)(?:\\+?\\d[ -]?){8,15}(?!\\d)");
    private static final Pattern IDENTITY=Pattern.compile("(?i)\\b(?:\\d{15}|\\d{17}[0-9X]|\\d{3}-\\d{2}-\\d{4})\\b");
    private static final Pattern FILE_URL=Pattern.compile("(?i)(?:file://|https?://\\S*(?:/file(?:/|[?#]|$)|fileurl))");
    private static final Pattern SECRET_VALUE=Pattern.compile("(?i)(?:bearer\\s+|(?:secret|token|password|passwd|credential|authorization|api[-_ ]?key)\\s*[=:]?\\s*)\\S+");

    private final TodoDefinitionSimulationService definitions;
    private final TodoConfigurationMapper configMapper;

    public TodoConfigurationSimulationService(TodoDefinitionSimulationService definitions,TodoConfigurationMapper configMapper)
    {this.definitions=definitions;this.configMapper=configMapper;}

    @Transactional(noRollbackFor=RuntimeException.class)
    public ConfigurationSimulationResult simulate(ConfigurationSimulationCommand command,Actor actor)
    {
        long started=System.nanoTime();
        TodoSimulationView simulation;
        try
        {
            simulation=definitions.simulate(command.versionId(),command.toDefinitionCommand());
        }
        catch(RuntimeException failure)
        {
            try{insertAudit(command,actor,duration(started),failedResult(command,failure));}
            catch(RuntimeException auditFailure){failure.addSuppressed(auditFailure);}
            throw failure;
        }
        long duration=duration(started);
        insertAudit(command,actor,duration,orderedResult(simulation));
        return new ConfigurationSimulationResult(simulation,duration);
    }

    private void insertAudit(ConfigurationSimulationCommand command,Actor actor,long duration,Map<String,Object> result)
    {
        Map<String,Object> row=new LinkedHashMap<>();
        row.put("requestId",command.requestId());row.put("templateVersionId",command.versionId());
        row.put("eventType",command.eventType());row.put("businessType",command.businessType());row.put("businessId",command.businessId());
        row.put("inputSummaryJson",json(command.payload()));row.put("resultJson",json(result));
        row.put("durationMs",duration);row.put("operatorId",actor.userId());
        configMapper.insertSimulationRecord(row);
    }

    /** Keeps persisted result sections in the configured reader order. */
    private Map<String,Object> orderedResult(TodoSimulationView simulation)
    {
        Map<String,Object> result=new LinkedHashMap<>();
        Map<String,Object> state=new LinkedHashMap<>();state.put("status",simulation.trigger().status());
        state.put("eventType",simulation.trigger().eventType());state.put("trace",simulation.trigger().trace());
        Map<String,Object> template=new LinkedHashMap<>();template.put("versionId",simulation.versionId());
        template.put("definitionHash",simulation.definitionHash());
        result.put("state",state);result.put("template",template);
        result.put("owner",simulation.owner());result.put("sla",simulation.sla());result.put("dod",simulation.form().dod());
        result.put("route",simulation.routes());result.put("card",simulation.form().ui());
        result.put("log",Map.of("autoActions",simulation.autoActions(),"handlers",simulation.handlers(),"issues",simulation.issues()));
        return result;
    }

    private Map<String,Object> failedResult(ConfigurationSimulationCommand command,RuntimeException failure)
    {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("state",Map.of("status","FAILED"));
        result.put("template",Map.of("versionId",command.versionId()));result.put("owner",Map.of());result.put("sla",Map.of());
        result.put("dod",Map.of());result.put("route",List.of());result.put("card",Map.of());
        result.put("log",Map.of("errorType",failure.getClass().getSimpleName(),"message",message(failure)));
        return result;
    }

    private long duration(long started){return TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);}
    private String message(RuntimeException failure){return failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage();}
    private String json(Object value){return JSON.toJSONString(sanitize(jsonTree(value)));}

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
