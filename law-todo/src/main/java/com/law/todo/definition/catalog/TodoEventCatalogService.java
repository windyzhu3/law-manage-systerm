package com.law.todo.definition.catalog;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.law.todo.mapper.TodoMapper;

@Service
public class TodoEventCatalogService
{
    private final TodoMapper mapper;

    public TodoEventCatalogService(TodoMapper mapper)
    {
        this.mapper = mapper;
    }

    public String payloadSchema(String eventType, int payloadVersion)
    {
        ActiveEventCatalog entry=activeEntry(eventType,payloadVersion);
        return entry==null?null:entry.payloadSchemaJson();
    }

    public ActiveEventCatalog activeEntry(String eventType,int payloadVersion)
    {
        Map<String, Object> entry = mapper.selectEventCatalog(eventType, payloadVersion);
        if (entry == null || entry.isEmpty()
                || !"ACTIVE".equals(text(value(entry, "status", "status"))))
            return null;
        String schema=text(value(entry,"payload_schema_json","payloadSchemaJson"));
        if(schema==null||schema.isBlank())return null;
        return new ActiveEventCatalog(text(value(entry,"business_object_type","businessObjectType")),schema);
    }

    /** Read-only managed catalogue used by configuration surfaces. */
    public List<Map<String,Object>> entries()
    {
        List<Map<String,Object>> entries=mapper.selectEventCatalogs();
        return entries==null?List.of():List.copyOf(entries);
    }

    private static Object value(Map<String, Object> row, String snakeCase, String camelCase)
    {
        return row.containsKey(snakeCase) ? row.get(snakeCase) : row.get(camelCase);
    }

    private static String text(Object value)
    {
        return value == null ? null : String.valueOf(value);
    }

    public record ActiveEventCatalog(String businessObjectType,String payloadSchemaJson) { }
}
