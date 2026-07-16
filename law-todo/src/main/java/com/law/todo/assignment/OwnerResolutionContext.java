package com.law.todo.assignment;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable runtime inputs that owner strategies may inspect. */
public record OwnerResolutionContext(
        Map<String, Object> payload,
        String businessType,
        Long businessId,
        LocalDateTime effectiveAt)
{
    public OwnerResolutionContext
    {
        payload = payload == null || payload.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
        effectiveAt = Objects.requireNonNull(effectiveAt, "effectiveAt");
    }
}
