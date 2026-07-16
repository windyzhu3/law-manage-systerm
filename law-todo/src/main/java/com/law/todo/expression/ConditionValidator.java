package com.law.todo.expression;

import java.util.List;
import java.util.Map;

import com.law.todo.definition.compiler.DefinitionValidationReport.ValidationIssue;
import com.law.todo.expression.ConditionExpression.DecodedCondition;
import com.law.todo.expression.ConditionTypeChecker.JsonSchema;
import org.springframework.stereotype.Component;

/** Shared decode and schema-validation boundary for compiler, management, and runtime. */
@Component
public final class ConditionValidator
{
    private final ConditionTypeChecker typeChecker = new ConditionTypeChecker();

    public ValidationResult validate(String json, String schemaJson,
            boolean allowLegacyWithoutSchema)
    {
        try
        {
            return validate(ConditionExpression.decodeJson(json), schemaJson,
                    allowLegacyWithoutSchema);
        }
        catch (IllegalArgumentException invalid)
        {
            return invalid(invalid.getMessage());
        }
    }

    public ValidationResult validate(Map<String, ?> document, String schemaJson,
            boolean allowLegacyWithoutSchema)
    {
        try
        {
            return validate(ConditionExpression.decodeMap(document), schemaJson,
                    allowLegacyWithoutSchema);
        }
        catch (IllegalArgumentException invalid)
        {
            return invalid(invalid.getMessage());
        }
    }

    /** Runtime decode for a condition already schema-checked as part of an immutable definition. */
    public ConditionExpression decodeCanonical(Map<String, ?> document)
    {
        DecodedCondition decoded = ConditionExpression.decodeMap(document);
        if (!decoded.canonical())
            throw new IllegalArgumentException("Runtime route conditions must use the canonical $expression envelope");
        return decoded.expression();
    }

    private ValidationResult validate(DecodedCondition decoded, String schemaJson,
            boolean allowLegacyWithoutSchema)
    {
        if (schemaJson == null || schemaJson.isBlank())
        {
            if (!decoded.canonical() && allowLegacyWithoutSchema)
                return new ValidationResult(decoded.expression(), false, List.of());
            return new ValidationResult(decoded.expression(), decoded.canonical(), List.of(
                    issue("TODO_CONDITION_SCHEMA_REQUIRED", "event",
                            "Active event payload schema is required")));
        }
        try
        {
            return new ValidationResult(decoded.expression(), decoded.canonical(),
                    typeChecker.check(decoded.expression(), JsonSchema.parse(schemaJson)));
        }
        catch (IllegalArgumentException invalidSchema)
        {
            return new ValidationResult(decoded.expression(), decoded.canonical(), List.of(
                    issue("TODO_CONDITION_SCHEMA_INVALID", "event", invalidSchema.getMessage())));
        }
    }

    private static ValidationResult invalid(String message)
    {
        return new ValidationResult(null, true, List.of(
                issue("TODO_CONDITION_INVALID", "event.condition", message)));
    }

    private static ValidationIssue issue(String code, String path, String message)
    {
        return new ValidationIssue(code, path, message);
    }

    public record ValidationResult(ConditionExpression expression, boolean canonical,
            List<ValidationIssue> issues)
    {
        public ValidationResult
        {
            issues = issues == null ? List.of() : List.copyOf(issues);
        }

        public boolean valid()
        {
            return expression != null && issues.isEmpty();
        }
    }
}
