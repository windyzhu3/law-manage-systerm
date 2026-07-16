package com.law.todo.routing;

import java.util.Objects;

/** Immutable branch position carried between route nodes. */
public record RouteToken(Long rootTodoId, String nodeKey, String branchKey,
        int occurrence, RouteTokenStatus status)
{
    public RouteToken
    {
        Objects.requireNonNull(rootTodoId, "rootTodoId");
        if (nodeKey == null || nodeKey.isBlank()) throw new IllegalArgumentException("nodeKey is required");
        if (occurrence < 0) throw new IllegalArgumentException("occurrence cannot be negative");
        Objects.requireNonNull(status, "status");
    }
}
