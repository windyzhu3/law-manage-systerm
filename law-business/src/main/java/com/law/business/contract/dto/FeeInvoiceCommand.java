package com.law.business.contract.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
public class FeeInvoiceCommand {
    @NotNull(message="请选择收费计划") private Long planId;
    @NotBlank(message="请选择开票状态") private String invoiceStatus;
    public Long getPlanId(){return planId;} public void setPlanId(Long value){planId=value;}
    public String getInvoiceStatus(){return invoiceStatus;} public void setInvoiceStatus(String value){invoiceStatus=value;}
}
