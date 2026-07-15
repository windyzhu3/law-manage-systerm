package com.ruoyi.system.service.contract;

import java.math.BigDecimal;

public record ContractFeePlanContext(
        Long planId,
        Long contractId,
        String contractNo,
        String confirmStatus,
        String invoiceStatus,
        String contractStatus,
        BigDecimal receivableAmount,
        BigDecimal receivedAmount)
{
}
