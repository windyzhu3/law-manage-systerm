package com.law.business.shared.status;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class ContractStatusTransitions
{
    private static final Map<ContractStatus, Set<ContractStatus>> ALLOWED = new EnumMap<>(ContractStatus.class);

    static
    {
        allow(ContractStatus.DRAFT, ContractStatus.PERFORMING, ContractStatus.VOID);
        allow(ContractStatus.PERFORMING, ContractStatus.ARCHIVED, ContractStatus.TERMINATED);
    }

    private ContractStatusTransitions() { }

    public static boolean canTransition(ContractStatus from, ContractStatus to)
    {
        return from == to || ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static void requireAllowed(ContractStatus from, ContractStatus to)
    {
        if (!canTransition(from, to)) throw new IllegalStateException("Illegal contract status transition: " + from + " -> " + to);
    }

    private static void allow(ContractStatus from, ContractStatus... targets)
    {
        ALLOWED.put(from, EnumSet.of(targets[0], targets));
    }
}
