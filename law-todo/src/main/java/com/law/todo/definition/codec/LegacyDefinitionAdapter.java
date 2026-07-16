package com.law.todo.definition.codec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.fastjson2.JSON;
import com.law.todo.definition.model.TodoDefinitionDocument;
import com.law.todo.definition.model.TodoDefinitionDocument.AutoActionRule;
import com.law.todo.definition.model.TodoDefinitionDocument.DodRule;
import com.law.todo.definition.model.TodoDefinitionDocument.EventRule;
import com.law.todo.definition.model.TodoDefinitionDocument.OwnerRule;
import com.law.todo.definition.model.TodoDefinitionDocument.RoutingGraph;
import com.law.todo.definition.model.TodoDefinitionDocument.SlaRule;
import com.law.todo.definition.model.TodoDefinitionDocument.UiSchema;

/** Converts the five legacy template-version JSON columns into schema version one. */
public final class LegacyDefinitionAdapter
{
    public TodoDefinitionDocument fromLegacy(Map<String, Object> row)
    {
        String eventType = text(value(row, "event_type", "eventType"));
        int payloadVersion = intValue(value(row, "payload_version", "payloadVersion"), 1);
        EventRule event = new EventRule(eventType, payloadVersion,
                object(value(row, "condition_json", "conditionJson")));

        return new TodoDefinitionDocument(1,
                text(value(row, "template_code", "templateCode")),
                event,
                ownerRule(value(row, "owner_rule_json", "ownerRuleJson")),
                new DodRule(object(value(row, "dod_rule_json", "dodRuleJson"))),
                new SlaRule(object(value(row, "sla_rule_json", "slaRuleJson"))),
                new UiSchema(object(value(row, "ui_schema_json", "uiSchemaJson"))),
                new RoutingGraph(object(value(row, "next_rule_json", "nextRuleJson"))),
                actionRules(value(row, "auto_actions_json", "autoActionsJson")),
                strings(value(row, "decision_refs_json", "decisionRefsJson")),
                strings(value(row, "acceptance_refs_json", "acceptanceRefsJson")));
    }

    private static OwnerRule ownerRule(Object value)
    {
        Object parsed = parsed(value);
        if (parsed instanceof String scalar)
        {
            String[] parts = scalar.split(":", 2);
            if (parts.length == 2 && ownerType(parts[0]) && !parts[1].isBlank())
                return new OwnerRule(Map.of("type", parts[0], "operand", parts[1]));
        }
        return new OwnerRule(objectValue(parsed));
    }

    private static boolean ownerType(String value)
    {
        return "PAYLOAD".equals(value) || "USER".equals(value) || "ROLE".equals(value)
                || "DEPT".equals(value) || "POST".equals(value);
    }

    private static List<AutoActionRule> actionRules(Object value)
    {
        List<Object> entries = array(value);
        List<AutoActionRule> rules = new ArrayList<>(entries.size());
        for (Object entry : entries)
            rules.add(new AutoActionRule(object(entry)));
        return rules;
    }

    private static List<String> strings(Object value)
    {
        List<Object> entries = array(value);
        List<String> result = new ArrayList<>(entries.size());
        for (Object entry : entries)
            result.add(String.valueOf(entry));
        return result;
    }

    private static Map<String, Object> object(Object value)
    {
        return objectValue(parsed(value));
    }

    private static Map<String, Object> objectValue(Object parsed)
    {
        if (parsed == null)
            return Map.of();
        if (parsed instanceof Map<?, ?> map)
        {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, entry) -> result.put(String.valueOf(key), entry));
            return result;
        }
        return Map.of("type", parsed);
    }

    private static List<Object> array(Object value)
    {
        Object parsed = parsed(value);
        if (parsed == null)
            return List.of();
        if (parsed instanceof List<?> list)
            return new ArrayList<>(list);
        return List.of(parsed);
    }

    private static Object parsed(Object value)
    {
        if (!(value instanceof String text))
            return value;
        if (text.isBlank())
            return null;
        return JSON.parse(text);
    }

    private static Object value(Map<String, Object> row, String snakeCase, String camelCase)
    {
        if (row == null)
            return null;
        return row.containsKey(snakeCase) ? row.get(snakeCase) : row.get(camelCase);
    }

    private static String text(Object value)
    {
        return value == null ? null : String.valueOf(value);
    }

    private static int intValue(Object value, int defaultValue)
    {
        if (value == null)
            return defaultValue;
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }
}
