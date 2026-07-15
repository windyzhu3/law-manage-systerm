package com.law.business.contract.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
public class FeeRejectCommand {
    @NotNull(message="请选择收费计划") private Long planId;
    @NotBlank(message="请填写驳回原因") @Size(max=500,message="驳回原因不能超过500个字符") private String reason;
    public Long getPlanId(){return planId;} public void setPlanId(Long value){planId=value;}
    public String getReason(){return reason;} public void setReason(String value){reason=value;}
}
