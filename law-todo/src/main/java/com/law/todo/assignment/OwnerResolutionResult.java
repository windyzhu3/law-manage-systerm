package com.law.todo.assignment;

import java.util.List;

/** Deterministic owner resolution output used by runtime execution and simulation. */
public record OwnerResolutionResult(
        Long ownerId,
        List<Long> candidateUserIds,
        List<Long> ccUserIds,
        boolean fallbackUsed,
        List<String> trace)
{
    public OwnerResolutionResult
    {
        candidateUserIds = immutable(candidateUserIds);
        ccUserIds = immutable(ccUserIds);
        trace = immutable(trace);
    }

    public static OwnerResolutionResult empty()
    {
        return new OwnerResolutionResult(null, List.of(), List.of(), false, List.of());
    }

    private static <T> List<T> immutable(List<T> values)
    {
        return values == null || values.isEmpty() ? List.of() : List.copyOf(values);
    }
}
