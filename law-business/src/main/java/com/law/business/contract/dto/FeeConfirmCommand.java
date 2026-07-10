package com.law.business.contract.dto;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
public class FeeConfirmCommand {
    @NotNull(message="请选择收费计划") private Long planId;
    @NotBlank(message="请输入本次回款金额") @DecimalMin(value="0.01",message="回款金额必须大于0") private String receivedAmount;
    public Long getPlanId(){return planId;} public void setPlanId(Long value){planId=value;}
    public String getReceivedAmount(){return receivedAmount;} public void setReceivedAmount(String value){receivedAmount=value;}
}
