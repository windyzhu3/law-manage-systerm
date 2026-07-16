package com.law.todo.assignment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiFunction;

import com.law.todo.definition.model.TodoDefinitionDocument.OwnerRule;
import com.law.todo.domain.TodoException;
import com.law.todo.spi.TodoOrganizationPort;

/** Resolves typed owner rules and composes availability, delegation, cc and fallback behavior. */
public final class CompositeOwnerResolver
{
    private static final int MAX_DEPTH = 8;
    private static final String ROUND_ROBIN_SOURCE = "_resolvedSourceCandidates";

    private final TodoOrganizationPort organization;
    private final Map<String, OwnerStrategy> strategies;

    public CompositeOwnerResolver(TodoOrganizationPort organization)
    {
        this(organization, List.of());
    }

    public CompositeOwnerResolver(TodoOrganizationPort organization, List<OwnerStrategy> additionalStrategies)
    {
        this.organization = Objects.requireNonNull(organization, "organization");
        Map<String, OwnerStrategy> registered = new LinkedHashMap<>();
        builtInStrategies().forEach(strategy -> registered.put(strategy.type(), strategy));
        if (additionalStrategies != null)
            additionalStrategies.forEach(strategy -> registered.put(normalized(strategy.type()), strategy));
        strategies = Collections.unmodifiableMap(registered);
    }

    public OwnerResolutionResult resolve(OwnerRule rule, OwnerResolutionContext context)
    {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(context, "context");
        Set<Object> active = Collections.newSetFromMap(new IdentityHashMap<>());
        return resolve(rule, context, 1, active);
    }

    private OwnerResolutionResult resolve(OwnerRule rule, OwnerResolutionContext context,
            int depth, Set<Object> active)
    {
        if (depth > MAX_DEPTH)
            throw error("TODO_OWNER_RULE_DEPTH_EXCEEDED", "Owner rule nesting exceeds " + MAX_DEPTH);
        Map<String, Object> config = rule.config();
        if (active.contains(rule) || active.contains(config))
            throw error("TODO_OWNER_RULE_CYCLE", "Owner rule contains a recursive reference");
        active.add(rule);
        active.add(config);
        try
        {
            String type = normalized(config.get("type"));
            OwnerStrategy strategy = strategies.get(type);
            OwnerResolutionResult raw = strategy == null
                    ? unsupported(type)
                    : raw(strategy, rule, context, depth, active);
            return compose(raw, config, context, depth, active);
        }
        finally
        {
            active.remove(config);
            active.remove(rule);
        }
    }

    private OwnerResolutionResult raw(OwnerStrategy strategy, OwnerRule rule,
            OwnerResolutionContext context, int depth, Set<Object> active)
    {
        if (!"ROUND_ROBIN".equals(strategy.type()))
            return safe(strategy.resolve(rule, context));

        OwnerRule source = nestedRule(rule.config().get("source"), active);
        OwnerResolutionResult sourceResult = source == null
                ? OwnerResolutionResult.empty()
                : resolve(source, context, depth + 1, active);
        List<Long> pool = new ArrayList<>(sourceResult.candidateUserIds());
        if (sourceResult.ownerId() != null) pool.add(sourceResult.ownerId());
        pool = available(pool, true, context);
        Map<String, Object> enriched = new LinkedHashMap<>(rule.config());
        enriched.put(ROUND_ROBIN_SOURCE, pool);
        OwnerResolutionResult selected = safe(strategy.resolve(new OwnerRule(enriched), context));
        return result(selected.ownerId(), selected.candidateUserIds(), selected.ccUserIds(),
                selected.fallbackUsed(), concat(sourceResult.trace(), selected.trace()));
    }

    private OwnerResolutionResult compose(OwnerResolutionResult raw, Map<String, Object> config,
            OwnerResolutionContext context, int depth, Set<Object> active)
    {
        boolean skipUnavailable = bool(config.get("skipUnavailable"), true);
        boolean useDelegation = bool(config.get("useDelegation"), true);
        List<String> trace = new ArrayList<>(raw.trace());
        Long owner = availablePrimary(raw.ownerId(), skipUnavailable, useDelegation, context, trace);
        List<Long> candidates = new ArrayList<>(available(raw.candidateUserIds(), skipUnavailable, context));
        List<Long> cc = new ArrayList<>(available(raw.ccUserIds(), skipUnavailable, context));

        for (OwnerRule candidateRule : nestedRules(config.get("candidates"), active))
        {
            OwnerResolutionResult nested = resolve(candidateRule, context, depth + 1, active);
            if (nested.ownerId() != null) candidates.add(nested.ownerId());
            candidates.addAll(nested.candidateUserIds());
            trace.addAll(nested.trace());
        }
        for (OwnerRule ccRule : nestedRules(config.get("cc"), active))
        {
            OwnerResolutionResult nested = resolve(ccRule, context, depth + 1, active);
            if (nested.ownerId() != null) cc.add(nested.ownerId());
            cc.addAll(nested.candidateUserIds());
            cc.addAll(nested.ccUserIds());
            trace.addAll(nested.trace());
        }
        candidates = available(candidates, skipUnavailable, context);
        cc = new ArrayList<>(available(cc, skipUnavailable, context));

        if (owner == null && candidates.isEmpty())
        {
            OwnerRule fallback = nestedRule(config.get("fallback"), active);
            if (fallback != null)
            {
                OwnerResolutionResult nested = resolve(fallback, context, depth + 1, active);
                trace.add("fallback:applied");
                trace.addAll(nested.trace());
                cc.addAll(nested.ccUserIds());
                return result(nested.ownerId(), nested.candidateUserIds(), sorted(cc), true, trace);
            }
        }
        return result(owner, candidates, cc, raw.fallbackUsed(), trace);
    }

    private Long availablePrimary(Long primary, boolean skipUnavailable, boolean useDelegation,
            OwnerResolutionContext context, List<String> trace)
    {
        if (primary == null) return null;
        if (!skipUnavailable || organization.isAvailable(primary, context.effectiveAt()))
        {
            trace.add("primary:" + primary + ":selected");
            return primary;
        }

        trace.add("primary:" + primary + ":unavailable");
        if (!useDelegation) return null;
        Optional<Long> delegate = organization.delegateFor(primary, context.effectiveAt());
        if (delegate.isEmpty()) return null;
        long delegateId = delegate.get();
        if (organization.isAvailable(delegateId, context.effectiveAt()))
        {
            trace.add("delegate:" + delegateId + ":selected");
            return delegateId;
        }
        trace.add("delegate:" + delegateId + ":unavailable");
        return null;
    }

    private List<Long> available(Collection<Long> users, boolean skipUnavailable,
            OwnerResolutionContext context)
    {
        TreeSet<Long> result = new TreeSet<>();
        if (users != null)
            for (Long user : users)
                if (user != null && (!skipUnavailable || organization.isAvailable(user, context.effectiveAt())))
                    result.add(user);
        return List.copyOf(result);
    }

    private List<OwnerStrategy> builtInStrategies()
    {
        return List.of(
                strategy("USER", this::user),
                strategy("ROLE", this::role),
                strategy("DEPT", this::department),
                strategy("POST", this::post),
                strategy("PAYLOAD", this::payload),
                strategy("BUSINESS_OWNER", this::businessOwner),
                strategy("SUPERVISOR", this::supervisor),
                strategy("ROUND_ROBIN", this::roundRobin),
                strategy("ASSIGNMENT_LEVEL", this::assignmentLevel));
    }

    private OwnerResolutionResult user(OwnerRule rule, OwnerResolutionContext context)
    {
        return owner(longOperand(rule));
    }

    private OwnerResolutionResult role(OwnerRule rule, OwnerResolutionContext context)
    {
        Long roleId = longOperand(rule);
        return roleId == null ? OwnerResolutionResult.empty()
                : candidates(organization.usersForRole(roleId));
    }

    private OwnerResolutionResult department(OwnerRule rule, OwnerResolutionContext context)
    {
        Long departmentId = longOperand(rule);
        return departmentId == null ? OwnerResolutionResult.empty()
                : candidates(organization.usersForDepartment(departmentId));
    }

    private OwnerResolutionResult post(OwnerRule rule, OwnerResolutionContext context)
    {
        Long postId = longOperand(rule);
        return postId == null ? OwnerResolutionResult.empty()
                : candidates(organization.usersForPost(postId));
    }

    private OwnerResolutionResult payload(OwnerRule rule, OwnerResolutionContext context)
    {
        String field = text(first(rule.config(), "field", "operand", "value"));
        return owner(longValue(field == null ? null : context.payload().get(field)));
    }

    private OwnerResolutionResult businessOwner(OwnerRule rule, OwnerResolutionContext context)
    {
        return owner(organization.businessOwner(context.businessType(), context.businessId()).orElse(null));
    }

    private OwnerResolutionResult supervisor(OwnerRule rule, OwnerResolutionContext context)
    {
        String sourceField = text(rule.config().getOrDefault("sourceField", "ownerId"));
        Long source = longValue(context.payload().get(sourceField));
        int levels = intValue(rule.config().get("levels"), 1);
        if (source == null || levels < 1) return OwnerResolutionResult.empty();
        return owner(organization.supervisor(source, levels).orElse(null));
    }

    @SuppressWarnings("unchecked")
    private OwnerResolutionResult roundRobin(OwnerRule rule, OwnerResolutionContext context)
    {
        String strategyKey = text(rule.config().get("strategyKey"));
        List<Long> pool = sorted((Collection<Long>) rule.config().get(ROUND_ROBIN_SOURCE));
        Optional<Long> selected = strategyKey == null || pool.isEmpty()
                ? Optional.empty()
                : organization.roundRobin(strategyKey, pool);
        List<String> trace = new ArrayList<>();
        Long owner = null;
        if (selected.isPresent())
        {
            long selectedId = selected.get();
            if (pool.contains(selectedId))
            {
                owner = selectedId;
                trace.add("round-robin:" + strategyKey + ":" + selectedId + ":selected");
            }
            else
                trace.add("round-robin:" + strategyKey + ":" + selectedId + ":rejected");
        }
        return result(owner, pool, List.of(), false, trace);
    }

    private OwnerResolutionResult assignmentLevel(OwnerRule rule, OwnerResolutionContext context)
    {
        int level = intValue(rule.config().get("level"), 0);
        if (level < 1) return OwnerResolutionResult.empty();
        return candidates(organization.assignmentLevel(level, context.businessType(), context.businessId()));
    }

    private static OwnerStrategy strategy(String type,
            BiFunction<OwnerRule, OwnerResolutionContext, OwnerResolutionResult> resolver)
    {
        return new OwnerStrategy()
        {
            @Override public String type() { return type; }
            @Override public OwnerResolutionResult resolve(OwnerRule rule, OwnerResolutionContext context)
            {
                return resolver.apply(rule, context);
            }
        };
    }

    private static OwnerResolutionResult owner(Long owner)
    {
        return result(owner, List.of(), List.of(), false, List.of());
    }

    private static OwnerResolutionResult candidates(Collection<Long> candidates)
    {
        return result(null, sorted(candidates), List.of(), false, List.of());
    }

    private static OwnerResolutionResult unsupported(String type)
    {
        return result(null, List.of(), List.of(), false,
                type.isEmpty() ? List.of() : List.of("strategy:" + type + ":unsupported"));
    }

    private static OwnerResolutionResult safe(OwnerResolutionResult result)
    {
        return result == null ? OwnerResolutionResult.empty() : result;
    }

    private static OwnerResolutionResult result(Long owner, Collection<Long> candidates,
            Collection<Long> cc, boolean fallback, Collection<String> trace)
    {
        return new OwnerResolutionResult(owner, sorted(candidates), sorted(cc), fallback,
                trace == null ? List.of() : List.copyOf(trace));
    }

    private static List<Long> sorted(Collection<Long> values)
    {
        if (values == null || values.isEmpty()) return List.of();
        TreeSet<Long> sorted = new TreeSet<>();
        for (Long value : values) if (value != null) sorted.add(value);
        return List.copyOf(sorted);
    }

    private static List<String> concat(List<String> first, List<String> second)
    {
        List<String> result = new ArrayList<>(first);
        result.addAll(second);
        return result;
    }

    private static List<OwnerRule> nestedRules(Object value, Set<Object> active)
    {
        if (value == null) return List.of();
        Collection<?> entries = value instanceof Collection<?> collection
                ? collection : List.of(value);
        List<OwnerRule> result = new ArrayList<>();
        for (Object entry : entries)
        {
            OwnerRule rule = nestedRule(entry, active);
            if (rule != null) result.add(rule);
        }
        return result;
    }

    private static OwnerRule nestedRule(Object value, Set<Object> active)
    {
        if (value == null) return null;
        if (active.contains(value))
            throw error("TODO_OWNER_RULE_CYCLE", "Owner rule contains a recursive reference");
        if (value instanceof OwnerRule rule) return rule;
        if (value instanceof Map<?, ?> map)
        {
            Map<String, Object> config = new LinkedHashMap<>();
            map.forEach((key, entry) -> config.put(String.valueOf(key), entry));
            return new OwnerRule(config);
        }
        return null;
    }

    private static Long longOperand(OwnerRule rule)
    {
        return longValue(first(rule.config(), "value", "operand"));
    }

    private static Object first(Map<String, Object> config, String... keys)
    {
        for (String key : keys) if (config.containsKey(key)) return config.get(key);
        return null;
    }

    private static Long longValue(Object value)
    {
        if (value == null) return null;
        return value instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(value));
    }

    private static int intValue(Object value, int defaultValue)
    {
        if (value == null) return defaultValue;
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }

    private static boolean bool(Object value, boolean defaultValue)
    {
        return value == null ? defaultValue : Boolean.parseBoolean(String.valueOf(value));
    }

    private static String text(Object value)
    {
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
    }

    private static String normalized(Object type)
    {
        String value = text(type);
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static TodoException error(String code, String message)
    {
        return new TodoException(code, message);
    }
}
