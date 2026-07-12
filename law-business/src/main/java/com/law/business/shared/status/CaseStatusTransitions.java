package com.law.business.shared.status;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class CaseStatusTransitions
{
    private static final Map<CaseStatus, Set<CaseStatus>> ALLOWED = new EnumMap<>(CaseStatus.class);

    static
    {
        allow(CaseStatus.PENDING, CaseStatus.CONFIRMING, CaseStatus.TERMINATED);
        allow(CaseStatus.CONFIRMING, CaseStatus.PENDING, CaseStatus.PROCESSING, CaseStatus.TERMINATED);
        allow(CaseStatus.PROCESSING, CaseStatus.TRANSFERRING, CaseStatus.CLOSING, CaseStatus.TERMINATED);
        allow(CaseStatus.TRANSFERRING, CaseStatus.PROCESSING, CaseStatus.CONFIRMING, CaseStatus.TERMINATED);
        allow(CaseStatus.CLOSING, CaseStatus.PROCESSING, CaseStatus.CLOSED);
        allow(CaseStatus.CLOSED, CaseStatus.ARCHIVED);
    }

    private CaseStatusTransitions() { }

    public static boolean canTransition(CaseStatus from, CaseStatus to)
    {
        return from == to || ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static void requireAllowed(CaseStatus from, CaseStatus to)
    {
        if (!canTransition(from, to)) throw new IllegalStateException("Illegal case status transition: " + from + " -> " + to);
    }

    private static void allow(CaseStatus from, CaseStatus... targets)
    {
        ALLOWED.put(from, EnumSet.of(targets[0], targets));
    }
}
