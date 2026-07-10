package com.law.business.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class PaymentConfirmCommand
{
    @NotNull(message = "请选择收费计划")
    private Long planId;

    @NotBlank(message = "请输入本次回款金额")
    @DecimalMin(value = "0.01", message = "回款金额必须大于0")
    private String receivedAmount;

    @NotBlank(message = "请选择付款方式")
    private String paymentMethod;

    private String reason;

    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public String getReceivedAmount() { return receivedAmount; }
    public void setReceivedAmount(String receivedAmount) { this.receivedAmount = receivedAmount; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
