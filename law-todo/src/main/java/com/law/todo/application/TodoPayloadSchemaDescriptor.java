package com.law.todo.application;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.law.todo.domain.TodoException;

/** Converts a stored event payload schema into fields that business editors can use directly. */
public class TodoPayloadSchemaDescriptor
{
    public List<PayloadFieldDescriptor> describe(String schemaJson,String sourceObject)
    {
        if(schemaJson==null||schemaJson.isBlank())return List.of();
        JSONObject schema;
        try
        {
            if(!JSON.isValidObject(schemaJson))throw new IllegalArgumentException("Schema must be a JSON object");
            schema=JSON.parseObject(schemaJson);
        }
        catch(RuntimeException invalid)
        {throw new TodoException("TODO_EVENT_SCHEMA_INVALID","Event payload schema must be a JSON object");}
        if(schema==null)throw new TodoException("TODO_EVENT_SCHEMA_INVALID","Event payload schema must be a JSON object");
        List<PayloadFieldDescriptor> fields=new ArrayList<>();
        flatten(fields,"",schema,required(schema),sourceObject,0);
        return List.copyOf(fields);
    }

    private void flatten(List<PayloadFieldDescriptor> fields,String prefix,JSONObject schema,Set<String> required,
            String sourceObject,int depth)
    {
        JSONObject properties=schema.getJSONObject("properties");
        if(properties==null||depth>1)return;
        for(String name:properties.keySet())
        {
            JSONObject property=properties.getJSONObject(name);if(property==null)continue;
            String path=prefix.isEmpty()?name:prefix+"."+name;
            String type=type(property);
            if("object".equals(type))
            {
                if(depth<1)flatten(fields,path,property,required(property),sourceObject,depth+1);
                continue;
            }
            boolean sensitive=sensitive(property);
            fields.add(new PayloadFieldDescriptor(path,label(property),type,required.contains(name),sensitive?null:example(property),sourceObject,
                    sensitive,operators(type),options(property),semantic(property),text(property,"x-option-source","optionSource"),
                    text(property,"x-dict-type","dictType"),text(property,"displayPattern","x-display-pattern")));
        }
    }

    private Set<String> required(JSONObject schema)
    {
        JSONArray names=schema.getJSONArray("required");
        if(names==null)return Set.of();Set<String> result=new LinkedHashSet<>();
        for(Object name:names)if(name!=null&&!String.valueOf(name).isBlank())result.add(String.valueOf(name));
        return Set.copyOf(result);
    }
    private String type(JSONObject property)
    {String type=property.getString("type");return type==null||type.isBlank()?"string":type;}
    private String label(JSONObject property)
    {String title=property.getString("title");return businessLabel(title)?title.trim():"业务字段";}
    private boolean businessLabel(String value)
    {
        if(value==null||value.isBlank())return false;String label=value.trim();
        return !(label.startsWith("{")||label.startsWith("[")||label.matches("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)*"));
    }
    private Object example(JSONObject property)
    {
        JSONArray examples=property.getJSONArray("examples");
        if(examples!=null&&!examples.isEmpty())return examples.get(0);
        return property.get("example");
    }
    private boolean sensitive(JSONObject property)
    {return Boolean.TRUE.equals(property.getBoolean("x-sensitive"))||Boolean.TRUE.equals(property.getBoolean("sensitive"));}
    private String semantic(JSONObject property)
    {
        String value=text(property,"x-semantic-type","semanticType");
        return value==null||value.isBlank()?"PLAIN_VALUE":value.trim().toUpperCase();
    }
    private String text(JSONObject property,String... keys)
    {
        for(String key:keys)
        {
            String value=property.getString(key);
            if(value!=null&&!value.isBlank())return value.trim();
        }
        return null;
    }
    private List<Object> options(JSONObject property)
    {
        JSONArray values=property.getJSONArray("enum");
        return values==null?List.of():List.copyOf(values.toList(Object.class));
    }
    private List<String> operators(String type)
    {
        return switch(type)
        {
            case "integer","number" -> List.of("EQ","NE","GT","GTE","LT","LTE","IN","NOT_IN","PRESENT");
            case "boolean" -> List.of("EQ","NE","PRESENT");
            default -> List.of("EQ","NE","IN","NOT_IN","CONTAINS","PRESENT");
        };
    }

    public record PayloadFieldDescriptor(String path,String label,String type,boolean required,Object example,
            String sourceObject,boolean sensitive,List<String> operators,List<Object> options,
            String semanticType,String optionSource,String dictType,String displayPattern)
    {
        public PayloadFieldDescriptor
        {
            operators=operators==null?List.of():List.copyOf(operators);
            options=options==null?List.of():List.copyOf(options);
            semanticType=semanticType==null||semanticType.isBlank()?"PLAIN_VALUE":semanticType.trim().toUpperCase();
            if(sensitive)example=null;
        }
    }
}
