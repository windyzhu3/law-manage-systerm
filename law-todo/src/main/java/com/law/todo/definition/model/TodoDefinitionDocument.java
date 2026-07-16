package com.law.todo.definition.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Versioned source document used by both definition tooling and the runtime compiler.
 * Rule sections deliberately own their configuration maps so callers cannot mutate a
 * definition after it has been read or persisted.
 */
public record TodoDefinitionDocument(
        int schemaVersion,
        String templateCode,
        EventRule event,
        OwnerRule owner,
        DodRule dod,
        SlaRule sla,
        UiSchema ui,
        RoutingGraph routing,
        List<AutoActionRule> autoActions,
        List<String> decisionRefs,
        List<String> acceptanceRefs)
{
    public TodoDefinitionDocument
    {
        autoActions = immutableList(autoActions);
        decisionRefs = immutableList(decisionRefs);
        acceptanceRefs = immutableList(acceptanceRefs);
    }

    public record EventRule(String eventType, int payloadVersion, Map<String, Object> condition)
    {
        public EventRule
        {
            condition = immutableMap(condition);
        }
    }

    public record OwnerRule(Map<String, Object> config)
    {
        public OwnerRule
        {
            config = immutableMap(config);
        }
    }

    public record DodRule(Map<String, Object> config)
    {
        public DodRule
        {
            config = immutableMap(config);
        }
    }

    public record SlaRule(Map<String, Object> config)
    {
        public SlaRule
        {
            config = immutableMap(config);
        }
    }

    public record UiSchema(Map<String, Object> config)
    {
        public UiSchema
        {
            config = immutableMap(config);
        }
    }

    public record RoutingGraph(Map<String, Object> config)
    {
        public RoutingGraph
        {
            config = immutableMap(config);
        }
    }

    public record AutoActionRule(Map<String, Object> config)
    {
        public AutoActionRule
        {
            config = immutableMap(config);
        }
    }

    private static Map<String, Object> immutableMap(Map<String, Object> value)
    {
        if (value == null || value.isEmpty())
            return Map.of();
        Map<String, Object> copy = new LinkedHashMap<>();
        value.forEach((key, entry) -> copy.put(key, immutableJsonValue(entry)));
        return Collections.unmodifiableMap(copy);
    }

    private static Object immutableJsonValue(Object value)
    {
        if (value instanceof Map<?, ?> map)
        {
            Map<Object, Object> copy = new LinkedHashMap<>();
            map.forEach((key, entry) -> copy.put(key, immutableJsonValue(entry)));
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list)
        {
            List<Object> copy = new java.util.ArrayList<>(list.size());
            list.forEach(entry -> copy.add(immutableJsonValue(entry)));
            return Collections.unmodifiableList(copy);
        }
        return value;
    }

    private static <T> List<T> immutableList(List<T> value)
    {
        return value == null || value.isEmpty() ? List.of() : List.copyOf(value);
    }
}
