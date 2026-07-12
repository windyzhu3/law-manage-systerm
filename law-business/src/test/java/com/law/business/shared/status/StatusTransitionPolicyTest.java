package com.law.business.shared.status;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class StatusTransitionPolicyTest
{
    @Test void allowsCaseLifecycleTransitions()
    {
        assertTrue(CaseStatusTransitions.canTransition(CaseStatus.PROCESSING, CaseStatus.CLOSING));
        assertTrue(CaseStatusTransitions.canTransition(CaseStatus.CLOSING, CaseStatus.CLOSED));
        assertTrue(CaseStatusTransitions.canTransition(CaseStatus.CLOSED, CaseStatus.ARCHIVED));
    }

    @Test void rejectsReopeningArchivedCase()
    {
        assertFalse(CaseStatusTransitions.canTransition(CaseStatus.ARCHIVED, CaseStatus.PROCESSING));
        assertThrows(IllegalStateException.class,
                () -> CaseStatusTransitions.requireAllowed(CaseStatus.ARCHIVED, CaseStatus.PROCESSING));
    }

    @Test void allowsContractLifecycleTransitions()
    {
        assertTrue(ContractStatusTransitions.canTransition(ContractStatus.DRAFT, ContractStatus.PERFORMING));
        assertTrue(ContractStatusTransitions.canTransition(ContractStatus.PERFORMING, ContractStatus.ARCHIVED));
    }

    @Test void rejectsChangingTerminalContract()
    {
        assertFalse(ContractStatusTransitions.canTransition(ContractStatus.ARCHIVED, ContractStatus.PERFORMING));
    }
}
