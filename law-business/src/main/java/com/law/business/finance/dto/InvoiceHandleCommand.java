package com.law.business.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class InvoiceHandleCommand
{
    @NotNull(message = "请选择收费计划")
    private Long planId;

    @NotBlank(message = "请选择开票状态")
    private String invoiceStatus;

    @NotBlank(message = "请选择发票类型")
    private String invoiceType;

    private String reason;

    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public String getInvoiceStatus() { return invoiceStatus; }
    public void setInvoiceStatus(String invoiceStatus) { this.invoiceStatus = invoiceStatus; }
    public String getInvoiceType() { return invoiceType; }
    public void setInvoiceType(String invoiceType) { this.invoiceType = invoiceType; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
