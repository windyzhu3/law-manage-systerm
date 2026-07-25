package com.law.todo.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.law.todo.definition.model.TodoDefinitionDocument.UiSchema;
import com.law.todo.mapper.TodoConfigurationMapper;

/** Adds current governed option labels to an immutable published UI definition. */
@Service
public class TodoFormOptionService
{
    private final TodoConfigurationMapper mapper;

    public TodoFormOptionService(TodoConfigurationMapper mapper)
    {
        this.mapper=mapper;
    }

    public UiSchema project(UiSchema source)
    {
        if(source==null)return new UiSchema(Map.of());
        Map<String,Object> config=new LinkedHashMap<>(source.config());
        Object rawFields=config.get("fields");
        if(!(rawFields instanceof List<?> fields))return source;
        Map<String,List<Map<String,Object>>> optionsByType=new LinkedHashMap<>();
        List<Object> projected=new ArrayList<>(fields.size());
        for(Object rawField:fields)
        {
            if(!(rawField instanceof Map<?,?> field))
            {
                projected.add(rawField);
                continue;
            }
            Map<String,Object> copy=copy(field);
            String dictType=text(copy.get("dictType"));
            if(dictType!=null&&!dictType.isBlank())
                copy.put("options",optionsByType.computeIfAbsent(dictType,this::options));
            projected.add(copy);
        }
        config.put("fields",projected);
        return new UiSchema(config);
    }

    private List<Map<String,Object>> options(String dictType)
    {
        List<Map<String,Object>> rows=mapper.selectEnabledDictionaryData(dictType);
        if(rows==null||rows.isEmpty())return List.of();
        return rows.stream().map(row->Map.<String,Object>of(
                "label",value(row,"dict_label","dictLabel"),
                "value",value(row,"dict_value","dictValue"))).toList();
    }

    private Map<String,Object> copy(Map<?,?> source)
    {
        Map<String,Object> result=new LinkedHashMap<>();
        source.forEach((key,value)->result.put(String.valueOf(key),value));
        return result;
    }

    private Object value(Map<String,Object> row,String snake,String camel)
    {
        return row.containsKey(snake)?row.get(snake):row.get(camel);
    }

    private String text(Object value)
    {
        return value==null?null:String.valueOf(value);
    }
}
