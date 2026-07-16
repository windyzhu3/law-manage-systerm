package com.law.todo.assignment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.law.todo.application.TodoAssignmentResolver;
import com.law.todo.definition.model.TodoDefinitionDocument.OwnerRule;
import com.law.todo.domain.TodoException;
import com.law.todo.spi.TodoOrganizationPort;

class CompositeOwnerResolverTest
{
    private static final LocalDateTime EFFECTIVE_AT = LocalDateTime.of(2026, 7, 17, 9, 30);

    private final OrganizationStub organization = new OrganizationStub();
    private final CompositeOwnerResolver resolver = new CompositeOwnerResolver(organization);

    @Test
    void fixedAndPayloadRulesSelectPrimaryUsers()
    {
        assertEquals(11L, resolve(rule("USER", "value", 11L)).ownerId());
        assertEquals(12L, resolve(rule("PAYLOAD", "field", "assigneeId")).ownerId());
    }

    @Test
    void taskOneOperandConfigurationRemainsExecutable()
    {
        assertEquals(15L, resolve(rule("USER", "operand", "15")).ownerId());
        assertEquals(12L, resolve(rule("PAYLOAD", "operand", "assigneeId")).ownerId());
    }

    @Test
    void missingRequiredValueDoesNotCreateSyntheticZeroOwner()
    {
        OwnerResolutionResult result = resolve(new OwnerRule(Map.of("type", "USER")));

        assertEquals(null, result.ownerId());
        assertEquals(List.of(), result.candidateUserIds());
    }

    @Test
    void additionalOwnerStrategyComposesWithCommonAvailabilityBehavior()
    {
        OwnerStrategy custom = new OwnerStrategy()
        {
            @Override public String type() { return "CUSTOM"; }
            @Override public OwnerResolutionResult resolve(OwnerRule rule, OwnerResolutionContext context)
            {
                return new OwnerResolutionResult(61L, List.of(), List.of(), false, List.of("custom:resolved"));
            }
        };

        OwnerResolutionResult result = new CompositeOwnerResolver(organization, List.of(custom))
                .resolve(new OwnerRule(Map.of("type", "CUSTOM")), context());

        assertEquals(61L, result.ownerId());
        assertEquals(List.of("custom:resolved", "primary:61:selected"), result.trace());
    }

    @Test
    void roleDepartmentAndPostRulesReturnSortedAvailableCandidates()
    {
        organization.roleUsers = Arrays.asList(3L, null, 2L, 3L, 1L);
        organization.deptUsers = List.of(8L, 7L);
        organization.postUsers = List.of(10L, 9L);
        organization.unavailable.add(2L);

        assertEquals(List.of(1L, 3L), resolve(rule("ROLE", "value", 5L)).candidateUserIds());
        assertEquals(List.of(7L, 8L), resolve(rule("DEPT", "value", 6L)).candidateUserIds());
        assertEquals(List.of(9L, 10L), resolve(rule("POST", "value", 7L)).candidateUserIds());
    }

    @Test
    void businessOwnerUsesContextBusinessIdentity()
    {
        organization.businessOwner = 31L;

        OwnerResolutionResult result = resolve(new OwnerRule(Map.of("type", "BUSINESS_OWNER")));

        assertEquals(31L, result.ownerId());
        assertEquals("CASE", organization.businessType);
        assertEquals(77L, organization.businessId);
    }

    @Test
    void supervisorUsesPayloadSourceAndExactConfiguredLevel()
    {
        organization.supervisor = 44L;

        OwnerResolutionResult result = resolve(new OwnerRule(Map.of(
                "type", "SUPERVISOR", "sourceField", "employeeId", "levels", 2)));

        assertEquals(44L, result.ownerId());
        assertEquals(13L, organization.supervisorSource);
        assertEquals(2, organization.supervisorLevels);
    }

    @Test
    void roundRobinReceivesSortedAvailablePoolAndSelectsOnlyReturnedMember()
    {
        organization.roleUsers = List.of(9L, 7L, 8L, 7L);
        organization.unavailable.add(8L);
        organization.roundRobin = 9L;
        OwnerRule rule = new OwnerRule(Map.of(
                "type", "ROUND_ROBIN",
                "strategyKey", "case-managers",
                "source", Map.of("type", "ROLE", "value", 5L)));

        OwnerResolutionResult result = resolve(rule);

        assertEquals(9L, result.ownerId());
        assertEquals(List.of(7L, 9L), organization.roundRobinCandidates);
        assertTrue(result.trace().contains("round-robin:case-managers:9:selected"));
    }

    @Test
    void roundRobinRejectsPortSelectionOutsideCandidatePool()
    {
        organization.roleUsers = List.of(7L, 9L);
        organization.roundRobin = 99L;

        OwnerResolutionResult result = resolve(new OwnerRule(Map.of(
                "type", "ROUND_ROBIN", "strategyKey", "case-managers",
                "source", Map.of("type", "ROLE", "value", 5L))));

        assertEquals(null, result.ownerId());
        assertEquals(List.of(7L, 9L), result.candidateUserIds());
        assertTrue(result.trace().contains("round-robin:case-managers:99:rejected"));
    }

    @Test
    void roundRobinAlwaysExcludesUnavailableSourceUsers()
    {
        organization.roleUsers = List.of(7L, 8L);
        organization.unavailable.add(8L);
        organization.roundRobin = 7L;

        resolve(new OwnerRule(Map.of(
                "type", "ROUND_ROBIN", "strategyKey", "case-managers",
                "source", Map.of("type", "ROLE", "value", 5L, "skipUnavailable", false))));

        assertEquals(List.of(7L), organization.roundRobinCandidates);
    }

    @Test
    void assignmentLevelReturnsDeterministicAvailableCandidates()
    {
        organization.assignmentUsers = List.of(52L, 51L, 52L);

        OwnerResolutionResult result = resolve(rule("ASSIGNMENT_LEVEL", "level", 3));

        assertEquals(List.of(51L, 52L), result.candidateUserIds());
        assertEquals(3, organization.assignmentLevel);
        assertEquals("CASE", organization.businessType);
        assertEquals(77L, organization.businessId);
    }

    @Test
    void unavailablePrimaryUsesAvailableDelegateBeforeFallback()
    {
        organization.unavailable.add(11L);
        organization.delegate = 22L;

        OwnerResolutionResult result = resolve(rule("USER", "value", 11L));

        assertEquals(22L, result.ownerId());
        assertEquals(List.of("primary:11:unavailable", "delegate:22:selected"), result.trace());
        assertFalse(result.fallbackUsed());
    }

    @Test
    void unavailableDelegateUsesNestedFallback()
    {
        organization.unavailable.addAll(List.of(11L, 22L));
        organization.delegate = 22L;
        OwnerRule rule = new OwnerRule(Map.of(
                "type", "USER", "value", 11L,
                "fallback", Map.of("type", "USER", "value", 33L)));

        OwnerResolutionResult result = resolve(rule);

        assertEquals(33L, result.ownerId());
        assertTrue(result.fallbackUsed());
        assertTrue(result.trace().contains("delegate:22:unavailable"));
        assertTrue(result.trace().contains("fallback:applied"));
    }

    @Test
    void usableCandidatesPreventFallbackAndCcUsersAreNeverPromoted()
    {
        organization.unavailable.add(11L);
        OwnerRule rule = new OwnerRule(Map.of(
                "type", "USER", "value", 11L,
                "useDelegation", false,
                "candidates", List.of(Map.of("type", "USER", "value", 41L)),
                "cc", List.of(Map.of("type", "USER", "value", 42L)),
                "fallback", Map.of("type", "USER", "value", 43L)));

        OwnerResolutionResult result = resolve(rule);

        assertEquals(null, result.ownerId());
        assertEquals(List.of(41L), result.candidateUserIds());
        assertEquals(List.of(42L), result.ccUserIds());
        assertFalse(result.fallbackUsed());
    }

    @Test
    void disablingAvailabilityFilteringKeepsUnavailablePrimary()
    {
        organization.unavailable.add(11L);

        OwnerResolutionResult result = resolve(new OwnerRule(Map.of(
                "type", "USER", "value", 11L, "skipUnavailable", false)));

        assertEquals(11L, result.ownerId());
        assertEquals(List.of("primary:11:selected"), result.trace());
    }

    @Test
    void recursiveRuleObjectFailsWithStableCycleCode()
    {
        OwnerRule cyclic = mock(OwnerRule.class);
        Map<String, Object> config = new HashMap<>();
        config.put("type", "ROUND_ROBIN");
        config.put("strategyKey", "cycle");
        config.put("source", cyclic);
        when(cyclic.config()).thenReturn(config);

        TodoException error = assertThrows(TodoException.class, () -> resolver.resolve(cyclic, context()));

        assertEquals("TODO_OWNER_RULE_CYCLE", error.getBusinessCode());
    }

    @Test
    void nestingBeyondEightRulesFailsWithStableDepthCode()
    {
        Map<String, Object> nested = Map.of("type", "USER", "value", 1L);
        for (int index = 0; index < 9; index++)
            nested = Map.of("type", "USER", "value", 100L + index, "fallback", nested);
        OwnerRule tooDeep = new OwnerRule(nested);
        organization.unavailable.addAll(ids(1L, 100L, 9));

        TodoException error = assertThrows(TodoException.class, () -> resolver.resolve(tooDeep, context()));

        assertEquals("TODO_OWNER_RULE_DEPTH_EXCEEDED", error.getBusinessCode());
    }

    @Test
    void compatibilityFacadePreservesEveryLegacyOwnerRule()
    {
        TodoAssignmentResolver facade = new TodoAssignmentResolver();

        assertEquals(new TodoAssignmentResolver.Assignment(8L, "USER", 8L),
                facade.resolve("\"USER:8\"", Map.of()));
        assertEquals(new TodoAssignmentResolver.Assignment(null, "ROLE", 5L),
                facade.resolve("ROLE:5", Map.of()));
        assertEquals(new TodoAssignmentResolver.Assignment(null, "DEPT", 6L),
                facade.resolve("DEPT:6", Map.of()));
        assertEquals(new TodoAssignmentResolver.Assignment(null, "POST", 7L),
                facade.resolve("POST:7", Map.of()));
        assertEquals(new TodoAssignmentResolver.Assignment(12L, "USER", 12L),
                facade.resolve("PAYLOAD:assigneeId", context().payload()));
        assertEquals(new TodoAssignmentResolver.Assignment(14L, "USER", 14L),
                facade.resolve("OWNER", Map.of("ownerId", 14L)));
    }

    private OwnerResolutionResult resolve(OwnerRule rule)
    {
        return resolver.resolve(rule, context());
    }

    private OwnerResolutionContext context()
    {
        return new OwnerResolutionContext(Map.of("assigneeId", 12L, "employeeId", 13L),
                "CASE", 77L, EFFECTIVE_AT);
    }

    private static OwnerRule rule(String type, String key, Object value)
    {
        return new OwnerRule(Map.of("type", type, key, value));
    }

    private static List<Long> ids(long tail, long start, int count)
    {
        List<Long> result = new ArrayList<>();
        result.add(tail);
        for (int index = 0; index < count; index++) result.add(start + index);
        return result;
    }

    private static final class OrganizationStub implements TodoOrganizationPort
    {
        private List<Long> roleUsers = List.of();
        private List<Long> deptUsers = List.of();
        private List<Long> postUsers = List.of();
        private List<Long> assignmentUsers = List.of();
        private final Set<Long> unavailable = new HashSet<>();
        private Long businessOwner;
        private Long supervisor;
        private Long supervisorSource;
        private int supervisorLevels;
        private Long roundRobin;
        private List<Long> roundRobinCandidates = List.of();
        private Long delegate;
        private int assignmentLevel;
        private String businessType;
        private Long businessId;

        @Override public List<Long> usersForRole(long roleId) { return roleUsers; }
        @Override public List<Long> usersForDepartment(long departmentId) { return deptUsers; }
        @Override public List<Long> usersForPost(long postId) { return postUsers; }

        @Override
        public Optional<Long> businessOwner(String type, Long id)
        {
            businessType = type; businessId = id; return Optional.ofNullable(businessOwner);
        }

        @Override
        public Optional<Long> supervisor(long userId, int levels)
        {
            supervisorSource = userId; supervisorLevels = levels; return Optional.ofNullable(supervisor);
        }

        @Override
        public Optional<Long> roundRobin(String strategyKey, List<Long> candidates)
        {
            roundRobinCandidates = List.copyOf(candidates); return Optional.ofNullable(roundRobin);
        }

        @Override
        public boolean isAvailable(long userId, LocalDateTime effectiveAt)
        {
            assertEquals(EFFECTIVE_AT, effectiveAt);
            return !unavailable.contains(userId);
        }

        @Override
        public Optional<Long> delegateFor(long userId, LocalDateTime effectiveAt)
        {
            assertEquals(EFFECTIVE_AT, effectiveAt);
            return Optional.ofNullable(delegate);
        }

        @Override
        public List<Long> assignmentLevel(int level, String type, Long id)
        {
            assignmentLevel = level; businessType = type; businessId = id; return assignmentUsers;
        }
    }
}
