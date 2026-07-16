package com.law.todo.definition.compiler;

import java.util.List;

public record DefinitionValidationReport(
        List<ValidationIssue> errors,
        List<ValidationIssue> warnings,
        String compiledJson,
        String definitionHash)
{
    public DefinitionValidationReport
    {
        errors = errors == null ? List.of() : List.copyOf(errors);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public boolean publishable()
    {
        return errors.isEmpty();
    }

    public record ValidationIssue(String code, String path, String message) {}
}
