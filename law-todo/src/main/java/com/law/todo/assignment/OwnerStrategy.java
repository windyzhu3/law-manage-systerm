package com.law.todo.assignment;

import com.law.todo.definition.model.TodoDefinitionDocument.OwnerRule;

/** One pluggable owner-rule strategy. */
public interface OwnerStrategy
{
    String type();

    OwnerResolutionResult resolve(OwnerRule rule, OwnerResolutionContext context);
}
