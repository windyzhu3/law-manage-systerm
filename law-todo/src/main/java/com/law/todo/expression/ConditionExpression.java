package com.law.todo.expression;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

/**
 * Data-only condition tree. It deliberately has no hooks for executable
 * expressions, method calls or object-property traversal.
 */
public sealed interface ConditionExpression permits ConditionExpression.GroupCondition,
        ConditionExpression.NotCondition, ConditionExpression.PredicateCondition
{
    Pattern FIELD_PATH = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*");

    enum GroupOperator { AND, OR }

    enum ConditionOperator
    {
        EQ, NE, IN, NOT_IN, GT, GTE, LT, LTE, EXISTS, EMPTY, NOT_EMPTY
    }

    record GroupCondition(GroupOperator operator, List<ConditionExpression> conditions)
            implements ConditionExpression
    {
        public GroupCondition
        {
            Objects.requireNonNull(operator, "operator");
            conditions = conditions == null ? List.of() : List.copyOf(conditions);
            if (conditions.isEmpty() || conditions.stream().anyMatch(Objects::isNull))
                throw new IllegalArgumentException("A condition group requires non-null children");
        }
    }

    record NotCondition(ConditionExpression condition) implements ConditionExpression
    {
        public NotCondition
        {
            Objects.requireNonNull(condition, "condition");
        }
    }

    record PredicateCondition(String field, ConditionOperator operator, Object value)
            implements ConditionExpression
    {
        public PredicateCondition
        {
            if (field == null || !FIELD_PATH.matcher(field).matches())
                throw new IllegalArgumentException("Invalid condition field path: " + field);
            Objects.requireNonNull(operator, "operator");
            value = copyJsonValue(value);
        }
    }

    static ConditionExpression and(ConditionExpression... conditions)
    {
        return new GroupCondition(GroupOperator.AND, Arrays.asList(conditions));
    }

    static ConditionExpression or(ConditionExpression... conditions)
    {
        return new GroupCondition(GroupOperator.OR, Arrays.asList(conditions));
    }

    static ConditionExpression not(ConditionExpression condition)
    {
        return new NotCondition(condition);
    }

    static ConditionExpression eq(String field, Object value)
    {
        return predicate(field, ConditionOperator.EQ, value);
    }

    static ConditionExpression predicate(String field, ConditionOperator operator, Object value)
    {
        return new PredicateCondition(field, operator, value);
    }

    /** Converts the historical flat condition map to an explicit AND-of-EQ tree. */
    static ConditionExpression legacy(Map<String, ?> conditions)
    {
        if (conditions == null || conditions.isEmpty())
            throw new IllegalArgumentException("A legacy condition map cannot be empty");
        List<ConditionExpression> predicates = new ArrayList<>(conditions.size());
        conditions.forEach((field, value) -> predicates.add(eq(field, value)));
        return predicates.size() == 1 ? predicates.get(0)
                : new GroupCondition(GroupOperator.AND, predicates);
    }

    static ConditionExpression fromJson(String json)
    {
        if (json == null || json.isBlank())
            throw new IllegalArgumentException("Condition JSON is required");
        Map<String, Object> document;
        try
        {
            JSONObject parsed = JSON.parseObject(json);
            document = parsed == null ? null : new LinkedHashMap<>(parsed);
        }
        catch (RuntimeException invalid)
        {
            throw new IllegalArgumentException("Condition must be a JSON object", invalid);
        }
        if (document == null)
            throw new IllegalArgumentException("Condition must be a JSON object");
        return fromMap(document);
    }

    /** Decodes either the safe tree form or, when no tree markers exist, a flat legacy map. */
    static ConditionExpression fromMap(Map<String, ?> document)
    {
        if (document == null || document.isEmpty())
            throw new IllegalArgumentException("Condition object cannot be empty");
        if (document.containsKey("field") && document.containsKey("operator"))
            return decodePredicate(document);
        if (treeNode(document))
            return decodeNode(document);
        return legacy(document);
    }

    private static boolean treeNode(Map<String, ?> document)
    {
        Object value = document.get("type");
        if (!(value instanceof String text))
            return false;
        String type = text.toUpperCase(Locale.ROOT);
        return (type.equals("AND") || type.equals("OR")) && document.containsKey("conditions")
                || type.equals("NOT") && document.containsKey("condition");
    }

    private static ConditionExpression decodeNode(Map<String, ?> node)
    {
        String type = enumText(node.get("type"), "condition type");
        return switch (type)
        {
            case "AND", "OR" -> decodeGroup(node, GroupOperator.valueOf(type));
            case "NOT" -> decodeNot(node);
            default -> throw new IllegalArgumentException("Unsupported condition type: " + type);
        };
    }

    private static ConditionExpression decodeGroup(Map<String, ?> node, GroupOperator operator)
    {
        requireKeys(node, Set.of("type", "conditions"));
        if (!(node.get("conditions") instanceof List<?> children))
            throw new IllegalArgumentException("Condition group requires a conditions array");
        List<ConditionExpression> decoded = new ArrayList<>(children.size());
        for (Object child : children)
        {
            if (!(child instanceof Map<?, ?> map))
                throw new IllegalArgumentException("Condition group children must be objects");
            decoded.add(fromMap(stringMap(map)));
        }
        return new GroupCondition(operator, decoded);
    }

    private static ConditionExpression decodeNot(Map<String, ?> node)
    {
        requireKeys(node, Set.of("type", "condition"));
        if (!(node.get("condition") instanceof Map<?, ?> map))
            throw new IllegalArgumentException("NOT requires a condition object");
        return not(fromMap(stringMap(map)));
    }

    private static ConditionExpression decodePredicate(Map<String, ?> node)
    {
        requireKeys(node, Set.of("field", "operator", "value"));
        Object field = node.get("field");
        if (!(field instanceof String path))
            throw new IllegalArgumentException("Predicate field must be a string");
        ConditionOperator operator;
        try
        {
            operator = ConditionOperator.valueOf(enumText(node.get("operator"), "operator"));
        }
        catch (IllegalArgumentException invalid)
        {
            throw new IllegalArgumentException("Unsupported condition operator: " + node.get("operator"), invalid);
        }
        return predicate(path, operator, node.get("value"));
    }

    private static void requireKeys(Map<String, ?> node, Set<String> allowed)
    {
        for (String key : node.keySet())
            if (!allowed.contains(key))
                throw new IllegalArgumentException("Unsupported condition property: " + key);
    }

    private static String enumText(Object value, String name)
    {
        if (!(value instanceof String text) || text.isBlank())
            throw new IllegalArgumentException("Condition " + name + " is required");
        return text.toUpperCase(Locale.ROOT);
    }

    private static Map<String, Object> stringMap(Map<?, ?> source)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!(key instanceof String text))
                throw new IllegalArgumentException("Condition object keys must be strings");
            result.put(text, value);
        });
        return result;
    }

    private static Object copyJsonValue(Object value)
    {
        if (value instanceof Map<?, ?> map)
            return Map.copyOf(stringMap(map));
        if (value instanceof List<?> list)
            return list.stream().map(ConditionExpression::copyJsonValue).toList();
        return value;
    }
}
