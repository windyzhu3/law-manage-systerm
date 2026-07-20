package com.law.todo.expression;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntPredicate;

import com.law.todo.expression.ConditionExpression.GroupCondition;
import com.law.todo.expression.ConditionExpression.GroupOperator;
import com.law.todo.expression.ConditionExpression.NotCondition;
import com.law.todo.expression.ConditionExpression.PredicateCondition;
import org.springframework.stereotype.Component;

@Component
public final class ConditionEvaluator
{
    public boolean evaluate(ConditionExpression expression, Map<String, Object> payload)
    {
        Objects.requireNonNull(expression, "expression");
        Objects.requireNonNull(payload, "payload");
        if (expression instanceof GroupCondition group)
            return evaluateGroup(group, payload);
        if (expression instanceof NotCondition not)
            return !evaluate(not.condition(), payload);
        if (expression instanceof PredicateCondition predicate)
            return evaluatePredicate(predicate, payload);
        throw new IllegalArgumentException("Unsupported condition expression");
    }

    private boolean evaluateGroup(GroupCondition group, Map<String, Object> payload)
    {
        if (group.operator() == GroupOperator.AND)
            return group.conditions().stream().allMatch(condition -> evaluate(condition, payload));
        return group.conditions().stream().anyMatch(condition -> evaluate(condition, payload));
    }

    private boolean evaluatePredicate(PredicateCondition predicate, Map<String, Object> payload)
    {
        PathValue path = resolve(payload, predicate.field());
        return switch (predicate.operator())
        {
            case EXISTS -> path.present();
            case NOT_EXISTS -> !path.present();
            case EMPTY -> path.present() && empty(path.value());
            case NOT_EMPTY -> path.present() && !empty(path.value());
            case EQ -> path.present() && equal(path.value(), predicate.value());
            case NE -> path.present() && !equal(path.value(), predicate.value());
            case IN -> path.present() && contains(predicate.value(), path.value());
            case NOT_IN -> path.present() && !contains(predicate.value(), path.value());
            case GT -> path.present() && ordered(path.value(), predicate.value(), result -> result > 0);
            case GTE -> path.present() && ordered(path.value(), predicate.value(), result -> result >= 0);
            case LT -> path.present() && ordered(path.value(), predicate.value(), result -> result < 0);
            case LTE -> path.present() && ordered(path.value(), predicate.value(), result -> result <= 0);
        };
    }

    private static PathValue resolve(Map<String, Object> payload, String path)
    {
        Object current = payload;
        for (String segment : path.split("\\."))
        {
            if (!(current instanceof Map<?, ?> map) || !map.containsKey(segment))
                return new PathValue(false, null);
            current = map.get(segment);
        }
        return new PathValue(true, current);
    }

    private static boolean equal(Object left, Object right)
    {
        if (left instanceof Number leftNumber && right instanceof Number rightNumber)
            return decimal(leftNumber).compareTo(decimal(rightNumber)) == 0;
        return Objects.equals(left, right);
    }

    private static boolean contains(Object expectedValues, Object actual)
    {
        if (!(expectedValues instanceof Collection<?> values))
            return false;
        return values.stream().anyMatch(expected -> equal(actual, expected));
    }

    private static boolean ordered(Object left, Object right, IntPredicate predicate)
    {
        Integer result = compare(left, right);
        return result != null && predicate.test(result);
    }

    private static Integer compare(Object left, Object right)
    {
        if (left instanceof Number leftNumber && right instanceof Number rightNumber)
            return decimal(leftNumber).compareTo(decimal(rightNumber));
        if (left instanceof String leftText && right instanceof String rightText)
            return leftText.compareTo(rightText);
        return null;
    }

    private static BigDecimal decimal(Number number)
    {
        try
        {
            return number instanceof BigDecimal decimal ? decimal
                    : new BigDecimal(number.toString());
        }
        catch (NumberFormatException invalid)
        {
            throw new IllegalArgumentException("Non-finite numbers are not supported in conditions", invalid);
        }
    }

    private static boolean empty(Object value)
    {
        return value == null
                || value instanceof CharSequence text && text.length() == 0
                || value instanceof Collection<?> collection && collection.isEmpty()
                || value instanceof Map<?, ?> map && map.isEmpty();
    }

    private record PathValue(boolean present, Object value) {}
}
