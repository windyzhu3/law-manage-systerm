package com.law.todo.expression;

import static com.law.todo.expression.ConditionExpression.ConditionOperator.IN;
import static com.law.todo.expression.ConditionExpression.ConditionOperator.NE;
import static com.law.todo.expression.ConditionExpression.ConditionOperator.NOT_EMPTY;
import static com.law.todo.expression.ConditionExpression.predicate;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.law.todo.expression.ConditionTypeChecker.JsonSchema;

class ConditionTypeCheckerTest
{
    private final ConditionTypeChecker checker=new ConditionTypeChecker();
    private final JsonSchema schema=JsonSchema.parse("""
            {"type":"object","properties":{"assignmentId":{"type":"integer"}}}
            """);

    @Test void valueOperatorRequiresAComparisonValueBeforeTypeChecking()
    {
        var issues=checker.check(predicate("assignmentId",NE,null),schema);

        assertThat(issues).singleElement().satisfies(issue->{
            assertThat(issue.code()).isEqualTo("TODO_CONDITION_VALUE_REQUIRED");
            assertThat(issue.path()).isEqualTo("event.condition.assignmentId");
        });
    }

    @Test void setOperatorRequiresAtLeastOneComparisonValue()
    {
        assertThat(checker.check(predicate("assignmentId",IN,List.of()),schema))
                .extracting(issue->issue.code())
                .containsExactly("TODO_CONDITION_VALUE_REQUIRED");
    }

    @Test void noValueOperatorAcceptsNull()
    {
        assertThat(checker.check(predicate("assignmentId",NOT_EMPTY,null),schema)).isEmpty();
    }
}
