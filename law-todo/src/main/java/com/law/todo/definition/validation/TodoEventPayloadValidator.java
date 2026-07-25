package com.law.todo.definition.validation;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Collection;
import java.util.Map;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.law.todo.domain.TodoException;

public class TodoEventPayloadValidator
{
    public void validate(String schemaJson, Map<String, Object> payload)
    {
        JSONObject schema = JSON.parseObject(schemaJson);
        if (payload == null) invalid(null);
        validateObject(schema, payload, null);
    }

    private void validateObject(JSONObject schema, Map<String, Object> payload, String field)
    {
        if (!"object".equals(schema.getString("type")) || !isType("object", payload))
            invalid(field);
        JSONArray required = schema.getJSONArray("required");
        if (required != null)
            for (Object name : required)
            {
                String requiredField = String.valueOf(name);
                if (!payload.containsKey(requiredField) || payload.get(requiredField) == null)
                    invalid(requiredField);
            }
        JSONObject properties = schema.getJSONObject("properties");
        if (properties == null) return;
        for (Map.Entry<String, Object> entry : properties.entrySet())
        {
            String property = entry.getKey();
            if (!payload.containsKey(property) || payload.get(property) == null) continue;
            validateField(property, JSONObject.from(entry.getValue()), payload.get(property));
        }
    }

    private void validateField(String field, JSONObject schema, Object value)
    {
        String type = schema.getString("type");
        if (!isType(type, value)) invalid(field);
        if ("string".equals(type))
        {
            JSONArray allowed = schema.getJSONArray("enum");
            if (allowed != null && !allowed.contains(value)) invalid(field);
            if ("date-time".equals(schema.getString("format"))) validateDateTime(field, (String) value);
        }
        if ("object".equals(type)) validateObject(schema, castMap(value), field);
    }

    private boolean isType(String type, Object value)
    {
        return switch (type)
        {
            case "object" -> value instanceof Map<?, ?>;
            case "string" -> value instanceof String;
            case "integer" -> isInteger(value);
            case "number" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            case "array" -> value instanceof Collection<?> || (value != null && value.getClass().isArray());
            default -> false;
        };
    }

    private boolean isInteger(Object value)
    {
        if (!(value instanceof Number number)) return false;
        if (number instanceof Byte || number instanceof Short || number instanceof Integer
                || number instanceof Long || number instanceof java.math.BigInteger) return true;
        try { return new BigDecimal(number.toString()).stripTrailingZeros().scale() <= 0; }
        catch (NumberFormatException error) { return false; }
    }

    private void validateDateTime(String field, String value)
    {
        try { TemporalAccessor ignored = DateTimeFormatter.ISO_DATE_TIME.parse(value); }
        catch (RuntimeException error) { invalid(field); }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value)
    {
        return (Map<String, Object>) value;
    }

    private void invalid(String field)
    {
        throw new TodoException("TODO_EVENT_PAYLOAD_INVALID",
                "Event payload field is missing or invalid: " + field);
    }
}
