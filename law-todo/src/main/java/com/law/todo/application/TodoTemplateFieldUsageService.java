package com.law.todo.application;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.law.todo.domain.TodoException;

/** Extracts only business payload fields that the selected template actually uses. */
@Service
public class TodoTemplateFieldUsageService
{
    public Map<String,List<FieldUsage>> usages(String definitionJson)
    {
        if(definitionJson==null||definitionJson.isBlank()||!JSON.isValidObject(definitionJson))
            throw new TodoException("TODO_TEMPLATE_JSON_INVALID","Template definition must be a JSON object");
        JSONObject definition=JSON.parseObject(definitionJson);
        Map<String,List<FieldUsage>> result=new LinkedHashMap<>();

        JSONObject event=definition.getJSONObject("event");
        if(event!=null)addPredicateFields(result,event.get("condition"),"TRIGGER_INPUT","TRIGGER");

        JSONObject owner=definition.getJSONObject("owner");
        if(owner!=null)addOwnerFields(result,owner.getJSONObject("config"));

        JSONObject dod=definition.getJSONObject("dod");
        if(dod!=null)addDodFields(result,dod.getJSONObject("config"));

        JSONObject ui=definition.getJSONObject("ui");
        if(ui!=null)addUiFields(result,ui.getJSONObject("config"));

        JSONObject sla=definition.getJSONObject("sla");
        if(sla!=null)addPredicateFields(result,sla.get("config"),"SLA_INPUT","SLA");

        JSONObject routing=definition.getJSONObject("routing");
        if(routing!=null)addPredicateFields(result,routing.get("config"),"ROUTING_INPUT","ROUTING");

        Map<String,List<FieldUsage>> immutable=new LinkedHashMap<>();
        result.forEach((path,values)->immutable.put(path,List.copyOf(values)));
        return Collections.unmodifiableMap(immutable);
    }

    private void addOwnerFields(Map<String,List<FieldUsage>> result,JSONObject config)
    {
        if(config==null)return;
        for(String key:List.of("field","sourceField","operand"))
        {
            String path=fieldPath(config.get(key));
            if(path!=null)add(result,path,new FieldUsage(path,"OWNER_INPUT","OWNER",true,
                    "负责人规则需要该字段",false,null,null));
        }
        addPredicateFields(result,config.get("fallback"),"OWNER_INPUT","OWNER");
    }

    private void addDodFields(Map<String,List<FieldUsage>> result,JSONObject config)
    {
        if(config==null)return;
        JSONArray required=config.getJSONArray("requiredFields");
        if(required!=null)for(Object value:required)
        {
            String path=fieldPath(value);
            if(path!=null)add(result,path,new FieldUsage(path,"COMPLETION_INPUT","DOD",true,
                    "完成条件要求填写",false,null,null));
        }
        JSONArray conditional=config.getJSONArray("conditionalRequired");
        if(conditional!=null)for(Object value:conditional)
        {
            if(!(value instanceof JSONObject rule))continue;
            String path=fieldPath(rule.get("field"));JSONObject when=rule.getJSONObject("when");
            String conditionField=when==null?null:fieldPath(when.get("field"));
            Object conditionValue=when==null?null:first(when,"equals","value");
            if(path!=null)add(result,path,new FieldUsage(path,"COMPLETION_INPUT","DOD",false,
                    "满足条件时要求填写",true,conditionField,conditionValue));
            if(conditionField!=null)add(result,conditionField,new FieldUsage(conditionField,
                    "COMPLETION_INPUT","DOD",true,"控制条件字段",false,null,null));
        }
    }

    private void addUiFields(Map<String,List<FieldUsage>> result,JSONObject config)
    {
        if(config==null)return;
        JSONArray fields=config.getJSONArray("fields");
        if(fields==null)return;
        for(Object value:fields)
        {
            String path=value instanceof JSONObject field?fieldPath(first(field,"key","field","path")):fieldPath(value);
            if(path!=null)add(result,path,new FieldUsage(path,"COMPLETION_INPUT","DOD",false,
                    "员工完成表单展示字段",false,null,null));
        }
    }

    private void addPredicateFields(Map<String,List<FieldUsage>> result,Object value,String stage,String step)
    {
        if(value instanceof JSONObject object)
        {
            Object candidate=first(object,"field","fieldPath","sourceField");
            String path=fieldPath(candidate);
            if(path!=null)add(result,path,new FieldUsage(path,stage,step,false,
                    "规则表达式引用字段",false,null,null));
            for(String key:object.keySet())addPredicateFields(result,object.get(key),stage,step);
        }
        else if(value instanceof JSONArray array)
            for(Object item:array)addPredicateFields(result,item,stage,step);
        else if(value instanceof Map<?,?> map)
            addPredicateFields(result,JSON.parseObject(JSON.toJSONString(map)),stage,step);
        else if(value instanceof Iterable<?> values)
            for(Object item:values)addPredicateFields(result,item,stage,step);
    }

    private void add(Map<String,List<FieldUsage>> result,String path,FieldUsage usage)
    {
        List<FieldUsage> values=result.computeIfAbsent(path,ignored->new ArrayList<>());
        if(!values.contains(usage))values.add(usage);
    }

    private Object first(JSONObject object,String... keys)
    {
        for(String key:keys)if(object.containsKey(key)&&object.get(key)!=null)return object.get(key);
        return null;
    }

    private String fieldPath(Object raw)
    {
        if(raw==null)return null;
        String path=String.valueOf(raw).trim();
        if(path.startsWith("payload."))path=path.substring("payload.".length());
        if(path.isBlank()||!path.matches("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)*"))return null;
        return path;
    }

    public record FieldUsage(String path,String stage,String stepCode,boolean required,String reason,
            boolean conditional,String conditionField,Object conditionValue) { }
}
