package com.law.business.shared.status;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class FeeStatusTest
{
    @Test void invoiceTransitionsFollowDatabaseProtocol()
    {
        assertTrue(FeeInvoiceStatus.NONE.canTransitionTo(FeeInvoiceStatus.PARTIAL));
        assertTrue(FeeInvoiceStatus.PARTIAL.canTransitionTo(FeeInvoiceStatus.INVOICED));
        assertFalse(FeeInvoiceStatus.PARTIAL.canTransitionTo(FeeInvoiceStatus.PARTIAL));
        assertFalse(FeeInvoiceStatus.INVOICED.canTransitionTo(FeeInvoiceStatus.INVOICED));
    }

    @Test void paymentTransitionsCoverConfirmRejectAndCorrection()
    {
        assertTrue(FeePaymentStatus.PENDING.canTransitionTo(FeePaymentStatus.CONFIRMED));
        assertTrue(FeePaymentStatus.PENDING.canTransitionTo(FeePaymentStatus.REJECTED));
        assertTrue(FeePaymentStatus.REJECTED.canTransitionTo(FeePaymentStatus.PENDING));
        assertFalse(FeePaymentStatus.REJECTED.canTransitionTo(FeePaymentStatus.CONFIRMED));
    }
}
