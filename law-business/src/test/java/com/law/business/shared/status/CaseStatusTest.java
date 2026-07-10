package com.law.business.shared.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class CaseStatusTest
{
    @Test
    void resolvesPersistedCode()
    {
        assertEquals(CaseStatus.PROCESSING, CaseStatus.fromCode("processing"));
    }

    @Test
    void rejectsUnknownCode()
    {
        assertThrows(IllegalArgumentException.class, () -> CaseStatus.fromCode("unknown"));
    }

    @Test
    void identifiesFinalStatuses()
    {
        assertTrue(CaseStatus.ARCHIVED.isFinalStatus());
        assertTrue(CaseStatus.TERMINATED.isFinalStatus());
    }
}
