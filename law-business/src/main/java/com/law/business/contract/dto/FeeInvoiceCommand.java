package com.law.business.contract.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
public class FeeInvoiceCommand {
    @NotNull(message="请选择收费计划") private Long planId;
    @NotBlank(message="请选择开票状态") @Pattern(regexp="^[12]$",message="开票状态不合法") private String invoiceStatus;
    @Size(max=500,message="备注不能超过500个字符") private String remark;
    @Size(max=30,message="发票类型不能超过30个字符") private String invoiceType;
    public Long getPlanId(){return planId;} public void setPlanId(Long value){planId=value;}
    public String getInvoiceStatus(){return invoiceStatus;} public void setInvoiceStatus(String value){invoiceStatus=value;}
    public String getRemark(){return remark;} public void setRemark(String value){remark=value;}
    public String getInvoiceType(){return invoiceType;} public void setInvoiceType(String value){invoiceType=value;}
}
