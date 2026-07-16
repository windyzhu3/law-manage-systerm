package com.law.todo.spi;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * The only organization and availability boundary used by {@code law-todo}.
 * Implementations own persistent round-robin cursor updates atomically.
 */
public interface TodoOrganizationPort
{
    /** Returns deterministic, unique user IDs; the resolver also normalizes defensively. */
    List<Long> usersForRole(long roleId);

    /** Returns deterministic, unique user IDs; the resolver also normalizes defensively. */
    List<Long> usersForDepartment(long departmentId);

    /** Returns deterministic, unique user IDs; the resolver also normalizes defensively. */
    List<Long> usersForPost(long postId);

    /** Returns the business object's current stable owner. */
    Optional<Long> businessOwner(String businessType, Long businessId);

    /** Returns the exact Nth-level supervisor, or empty when the chain breaks. */
    Optional<Long> supervisor(long userId, int levels);

    /** Atomically advances a persistent cursor and returns a member of the supplied pool. */
    Optional<Long> roundRobin(String strategyKey, List<Long> sortedAvailableCandidates);

    /** Covers enabled account, active employment, and absence at the effective time. */
    boolean isAvailable(long userId, LocalDateTime effectiveAt);

    /** Returns only a delegation active at the effective time. */
    Optional<Long> delegateFor(long userId, LocalDateTime effectiveAt);

    /** Returns deterministic candidates for the configured business assignment level. */
    List<Long> assignmentLevel(int level, String businessType, Long businessId);

    /** Compatibility port for legacy scalar rules that do not require organization lookup. */
    static TodoOrganizationPort legacyCompatible()
    {
        return new TodoOrganizationPort()
        {
            @Override public List<Long> usersForRole(long roleId) { return List.of(); }
            @Override public List<Long> usersForDepartment(long departmentId) { return List.of(); }
            @Override public List<Long> usersForPost(long postId) { return List.of(); }
            @Override public Optional<Long> businessOwner(String businessType, Long businessId) { return Optional.empty(); }
            @Override public Optional<Long> supervisor(long userId, int levels) { return Optional.empty(); }
            @Override public Optional<Long> roundRobin(String strategyKey, List<Long> candidates) { return Optional.empty(); }
            @Override public boolean isAvailable(long userId, LocalDateTime effectiveAt) { return true; }
            @Override public Optional<Long> delegateFor(long userId, LocalDateTime effectiveAt) { return Optional.empty(); }
            @Override public List<Long> assignmentLevel(int level, String businessType, Long businessId) { return List.of(); }
        };
    }
}
