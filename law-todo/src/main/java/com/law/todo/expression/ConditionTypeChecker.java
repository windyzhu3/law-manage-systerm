package com.law.todo.expression;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.law.todo.definition.compiler.DefinitionValidationReport.ValidationIssue;
import com.law.todo.expression.ConditionExpression.GroupCondition;
import com.law.todo.expression.ConditionExpression.NotCondition;
import com.law.todo.expression.ConditionExpression.PredicateCondition;

public final class ConditionTypeChecker
{
    private static final Set<String> ORDERED_TYPES = Set.of("number", "integer", "string");

    public List<ValidationIssue> check(ConditionExpression expression, JsonSchema schema)
    {
        List<ValidationIssue> issues = new ArrayList<>();
        if (expression == null)
        {
            issues.add(issue("TODO_CONDITION_REQUIRED", "event.condition",
                    "Condition expression is required"));
            return List.copyOf(issues);
        }
        if (schema == null)
        {
            issues.add(issue("TODO_CONDITION_SCHEMA_REQUIRED", "event",
                    "Event payload schema is required"));
            return List.copyOf(issues);
        }
        inspect(expression, schema, issues);
        return List.copyOf(issues);
    }

    private void inspect(ConditionExpression expression, JsonSchema schema,
            List<ValidationIssue> issues)
    {
        if (expression instanceof GroupCondition group)
        {
            group.conditions().forEach(child -> inspect(child, schema, issues));
            return;
        }
        if (expression instanceof NotCondition not)
        {
            inspect(not.condition(), schema, issues);
            return;
        }
        PredicateCondition predicate = (PredicateCondition) expression;
        FieldSchema field = schema.field(predicate.field());
        String path = "event.condition." + predicate.field();
        if (field == null)
        {
            issues.add(issue("TODO_CONDITION_FIELD_UNKNOWN", path,
                    "Condition field is not declared by the event payload schema"));
            return;
        }
        switch (predicate.operator())
        {
            case EXISTS, NOT_EXISTS, EMPTY, NOT_EMPTY -> {
                if (predicate.value() != null)
                    issues.add(issue("TODO_CONDITION_VALUE_NOT_ALLOWED", path,
                            "This condition operator does not accept a value"));
            }
            case GT, GTE, LT, LTE -> {
                if (!field.types().isEmpty() && field.types().stream().noneMatch(ORDERED_TYPES::contains))
                    issues.add(issue("TODO_CONDITION_OPERATOR_TYPE_INVALID", path,
                            "Ordered comparison is not supported for the field type"));
                else if (!field.acceptsOrdered(predicate.value()))
                    issues.add(issue("TODO_CONDITION_VALUE_TYPE_INVALID", path,
                            "Condition value does not match the event field type"));
            }
            case IN, NOT_IN -> {
                if (!(predicate.value() instanceof Collection<?> values)
                        || values.stream().anyMatch(value -> !field.accepts(value)))
                    issues.add(issue("TODO_CONDITION_VALUE_TYPE_INVALID", path,
                            "Set condition values must match the event field type"));
            }
            case EQ, NE -> {
                if (!field.accepts(predicate.value()))
                    issues.add(issue("TODO_CONDITION_VALUE_TYPE_INVALID", path,
                            "Condition value does not match the event field type"));
            }
        }
    }

    private static ValidationIssue issue(String code, String path, String message)
    {
        return new ValidationIssue(code, path, message);
    }

    public record JsonSchema(Map<String, Object> document)
    {
        public JsonSchema
        {
            if (document == null)
                throw new IllegalArgumentException("JSON schema document is required");
            document = Collections.unmodifiableMap(new LinkedHashMap<>(document));
        }

        public static JsonSchema parse(String json)
        {
            if (json == null || json.isBlank())
                throw new IllegalArgumentException("JSON schema is required");
            Map<String, Object> document;
            try
            {
                JSONObject parsed = JSON.parseObject(json);
                document = parsed == null ? null : new LinkedHashMap<>(parsed);
            }
            catch (RuntimeException invalid)
            {
                throw new IllegalArgumentException("Event payload schema must be a JSON object", invalid);
            }
            if (document == null)
                throw new IllegalArgumentException("Event payload schema must be a JSON object");
            return new JsonSchema(document);
        }

        private FieldSchema field(String path)
        {
            Map<String, Object> current = document;
            for (String segment : path.split("\\."))
            {
                Object propertiesValue = current.get("properties");
                if (!(propertiesValue instanceof Map<?, ?> properties))
                    return null;
                Object fieldValue = properties.get(segment);
                if (!(fieldValue instanceof Map<?, ?> field))
                    return null;
                current = stringMap(field);
            }
            return new FieldSchema(types(current.get("type")));
        }

        private static Map<String, Object> stringMap(Map<?, ?> source)
        {
            Map<String, Object> result = new LinkedHashMap<>();
            source.forEach((key, value) -> {
                if (key instanceof String text)
                    result.put(text, value);
            });
            return result;
        }

        private static Set<String> types(Object value)
        {
            if (value instanceof String type)
                return Set.of(type);
            if (value instanceof Collection<?> collection)
            {
                java.util.HashSet<String> types = new java.util.HashSet<>();
                collection.forEach(type -> {
                    if (type instanceof String text)
                        types.add(text);
                });
                return Set.copyOf(types);
            }
            return Set.of();
        }
    }

    private record FieldSchema(Set<String> types)
    {
        private boolean acceptsOrdered(Object value)
        {
            if (value instanceof String)
                return types.isEmpty() || types.contains("string");
            if (value instanceof Byte || value instanceof Short || value instanceof Integer
                    || value instanceof Long || value instanceof java.math.BigInteger)
                return types.isEmpty() || types.contains("integer") || types.contains("number");
            if (value instanceof Number)
                return types.isEmpty() || types.contains("number");
            return false;
        }

        private boolean accepts(Object value)
        {
            if (types.isEmpty())
                return true;
            if (value == null)
                return types.contains("null");
            if (value instanceof String)
                return types.contains("string");
            if (value instanceof Boolean)
                return types.contains("boolean");
            if (value instanceof Byte || value instanceof Short || value instanceof Integer
                    || value instanceof Long || value instanceof java.math.BigInteger)
                return types.contains("integer") || types.contains("number");
            if (value instanceof Number)
                return types.contains("number");
            if (value instanceof Collection<?>)
                return types.contains("array");
            if (value instanceof Map<?, ?>)
                return types.contains("object");
            return false;
        }
    }
}
