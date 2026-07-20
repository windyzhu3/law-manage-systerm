package com.law.todo.expression;

import static com.law.todo.expression.ConditionExpression.ConditionOperator.EMPTY;
import static com.law.todo.expression.ConditionExpression.ConditionOperator.EXISTS;
import static com.law.todo.expression.ConditionExpression.ConditionOperator.GT;
import static com.law.todo.expression.ConditionExpression.ConditionOperator.GTE;
import static com.law.todo.expression.ConditionExpression.ConditionOperator.IN;
import static com.law.todo.expression.ConditionExpression.ConditionOperator.LT;
import static com.law.todo.expression.ConditionExpression.ConditionOperator.LTE;
import static com.law.todo.expression.ConditionExpression.ConditionOperator.NE;
import static com.law.todo.expression.ConditionExpression.ConditionOperator.NOT_EMPTY;
import static com.law.todo.expression.ConditionExpression.ConditionOperator.NOT_EXISTS;
import static com.law.todo.expression.ConditionExpression.ConditionOperator.NOT_IN;
import static com.law.todo.expression.ConditionExpression.and;
import static com.law.todo.expression.ConditionExpression.eq;
import static com.law.todo.expression.ConditionExpression.not;
import static com.law.todo.expression.ConditionExpression.or;
import static com.law.todo.expression.ConditionExpression.predicate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.law.todo.definition.compiler.DefinitionValidationReport.ValidationIssue;
import com.law.todo.expression.ConditionTypeChecker.JsonSchema;

class ConditionEvaluatorTest
{
    private final ConditionEvaluator evaluator = new ConditionEvaluator();
    private final ConditionTypeChecker checker = new ConditionTypeChecker();

    @Test
    void nestedAndOrIsEvaluated()
    {
        ConditionExpression expression = and(eq("amount", 100),
                or(eq("type", "A"), eq("type", "B")));

        assertTrue(evaluator.evaluate(expression, payload()));
        assertFalse(evaluator.evaluate(expression,
                Map.of("amount", 100, "type", "C")));
    }

    @Test
    void typedOperatorsAreEvaluatedWithoutStringCoercion()
    {
        assertTrue(evaluator.evaluate(predicate("amount", GTE, 100), payload()));
        assertTrue(evaluator.evaluate(predicate("amount", LT, 101), payload()));
        assertTrue(evaluator.evaluate(predicate("amount", LTE, 100L), payload()));
        assertTrue(evaluator.evaluate(predicate("amount", GT, 99.5), payload()));
        assertTrue(evaluator.evaluate(predicate("type", NE, "B"), payload()));
        assertTrue(evaluator.evaluate(predicate("type", IN, List.of("A", "B")), payload()));
        assertTrue(evaluator.evaluate(predicate("type", NOT_IN, List.of("B", "C")), payload()));
        assertFalse(evaluator.evaluate(eq("amount", "100"), payload()));
        assertFalse(evaluator.evaluate(predicate("active", LT, true),
                Map.of("active", true)));
    }

    @Test
    void presenceAndEmptyOperatorsDistinguishMissingValues()
    {
        Map<String, Object> value = Map.of("blank", "", "items", List.of(), "name", "lead");

        assertTrue(evaluator.evaluate(predicate("blank", EXISTS, null), value));
        assertTrue(evaluator.evaluate(predicate("blank", EMPTY, null), value));
        assertTrue(evaluator.evaluate(predicate("items", EMPTY, null), value));
        assertTrue(evaluator.evaluate(predicate("name", NOT_EMPTY, null), value));
        assertFalse(evaluator.evaluate(predicate("missing", EXISTS, null), value));
        assertFalse(evaluator.evaluate(predicate("missing", NE, "x"), value));
    }

    @Test
    void notExistsMatchesOnlyAPathThatIsAbsentAndAcceptsNoValue()
    {
        Map<String,Object> presentNull=new java.util.HashMap<>();presentNull.put("field",null);

        assertTrue(evaluator.evaluate(predicate("missing",NOT_EXISTS,null),Map.of("field","value")));
        assertFalse(evaluator.evaluate(predicate("field",NOT_EXISTS,null),Map.of("field","value")));
        assertFalse(evaluator.evaluate(predicate("field",NOT_EXISTS,null),presentNull));
        assertTrue(checker.check(predicate("type",NOT_EXISTS,null),schema()).isEmpty());
        assertEquals("TODO_CONDITION_VALUE_NOT_ALLOWED",
                checker.check(predicate("type",NOT_EXISTS,"unexpected"),schema()).get(0).code());
        assertTrue(evaluator.evaluate(ConditionExpression.fromJson("{\"$expression\":{\"version\":1,\"root\":{\"field\":\"missing\",\"operator\":\"NOT_EXISTS\",\"value\":null}}}"),Map.of()));
    }

    @Test
    void notAndMapOnlyNestedPathsAreSafe()
    {
        ConditionExpression expression = not(eq("customer.level", "B"));

        assertTrue(evaluator.evaluate(expression,
                Map.of("customer", Map.of("level", "A"))));
        assertFalse(evaluator.evaluate(eq("class.classLoader", "x"), payload()));
    }

    @Test
    void undeclaredPathIsRejected()
    {
        List<ValidationIssue> issues = checker.check(
                eq("class.classLoader", "x"), schema());

        assertEquals("TODO_CONDITION_FIELD_UNKNOWN", issues.get(0).code());
    }

    @Test
    void incompatibleOperatorAndOperandAreRejected()
    {
        assertEquals("TODO_CONDITION_OPERATOR_TYPE_INVALID",
                checker.check(predicate("active", GT, true), schema()).get(0).code());
        assertEquals("TODO_CONDITION_VALUE_TYPE_INVALID",
                checker.check(predicate("amount", IN, List.of("high")), schema()).get(0).code());
    }

    @Test
    void orderedLiteralMustMatchAnOrderedMemberOfAUnionSchema()
    {
        JsonSchema union = JsonSchema.parse("""
                {"type":"object","properties":{
                  "numberOrBoolean":{"type":["number","boolean"]},
                  "numberOrNull":{"type":["number","null"]}
                }}
                """);

        assertEquals("TODO_CONDITION_VALUE_TYPE_INVALID",
                checker.check(predicate("numberOrBoolean", GT, true), union).get(0).code());
        assertEquals("TODO_CONDITION_VALUE_TYPE_INVALID",
                checker.check(predicate("numberOrNull", GT, null), union).get(0).code());
    }

    @Test
    void expressionJsonIsDecodedButUnknownShapesAreRejected()
    {
        String json = """
                {"$expression":{"version":1,"root":
                    {"type":"AND","conditions":[
                      {"field":"amount","operator":"GTE","value":100},
                      {"type":"OR","conditions":[
                        {"field":"type","operator":"EQ","value":"A"},
                        {"field":"type","operator":"EQ","value":"B"}
                      ]}
                    ]}
                  }
                }
                """;

        assertTrue(evaluator.evaluate(ConditionExpression.fromJson(json), payload()));
        assertThrows(IllegalArgumentException.class,
                () -> ConditionExpression.fromJson("{\"$expression\":{\"version\":2,\"root\":{\"field\":\"amount\",\"operator\":\"EQ\",\"value\":1}}}"));
        assertThrows(IllegalArgumentException.class,
                () -> ConditionExpression.fromJson("{\"$expression\":{\"version\":1,\"root\":{\"field\":\"amount\",\"operator\":\"EQ\",\"value\":1}},\"extra\":true}"));
        assertThrows(IllegalArgumentException.class,
                () -> ConditionExpression.fromJson("{\"$expression\":{\"version\":1}}"));
        assertThrows(IllegalArgumentException.class,
                () -> ConditionExpression.fromJson("{\"$expression\":{\"version\":1,\"root\":{\"field\":\"amount\",\"operator\":\"EQ\",\"value\":1},\"extra\":true}}"));
    }

    @Test
    void legacyFlatMapsUseAnExplicitEqualityAdapter()
    {
        ConditionExpression expression = ConditionExpression.legacy(
                Map.of("source", "ONLINE", "priority", 2));

        assertTrue(evaluator.evaluate(expression,
                Map.of("source", "ONLINE", "priority", 2L)));
        assertFalse(evaluator.evaluate(expression,
                Map.of("source", "OFFLINE", "priority", 2)));
        assertTrue(evaluator.evaluate(ConditionExpression.fromJson("{\"type\":\"AND\"}"),
                Map.of("type", "AND")));
        assertTrue(evaluator.evaluate(
                ConditionExpression.fromJson("{\"field\":\"status\",\"operator\":\"EQ\"}"),
                Map.of("field", "status", "operator", "EQ")));
        assertTrue(evaluator.evaluate(
                ConditionExpression.fromJson("{\"type\":\"AND\",\"conditions\":\"READY\"}"),
                Map.of("type", "AND", "conditions", "READY")));
        assertTrue(evaluator.evaluate(ConditionExpression.fromJson("{\"root\":\"READY\"}"),
                Map.of("root", "READY")));
        assertTrue(evaluator.evaluate(ConditionExpression.fromJson("{\"expressionVersion\":1}"),
                Map.of("expressionVersion", 1)));
        assertTrue(evaluator.evaluate(
                ConditionExpression.fromJson("{\"expressionVersion\":1,\"root\":\"READY\"}"),
                Map.of("expressionVersion", 1, "root", "READY")));
    }

    @Test
    void objectLiteralNullIsPreservedForValidation()
    {
        ConditionExpression expression = ConditionExpression.fromJson("""
                {"$expression":{"version":1,"root":
                    {"field":"amount","operator":"EQ","value":{"optional":null}}}
                }
                """);

        assertEquals("TODO_CONDITION_VALUE_TYPE_INVALID",
                checker.check(expression, schema()).get(0).code());
    }

    private Map<String, Object> payload()
    {
        return Map.of("amount", 100, "type", "A");
    }

    private JsonSchema schema()
    {
        return JsonSchema.parse("""
                {"type":"object","properties":{
                  "amount":{"type":"number"},
                  "type":{"type":"string"},
                  "active":{"type":"boolean"},
                  "customer":{"type":"object","properties":{"level":{"type":"string"}}}
                }}
                """);
    }
}
