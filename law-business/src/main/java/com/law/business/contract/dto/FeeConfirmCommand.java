package com.law.business.contract.dto;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
public class FeeConfirmCommand {
    @NotNull(message="请选择收费计划") private Long planId;
    @NotBlank(message="请输入本次回款金额") @DecimalMin(value="0.01",message="回款金额必须大于0") private String receivedAmount;
    @Size(max=500,message="备注不能超过500个字符") private String remark;
    @Size(max=30,message="付款方式不能超过30个字符") private String paymentMethod;
    @Size(max=500,message="付款凭证地址不能超过500个字符") private String voucherUrl;
    public Long getPlanId(){return planId;} public void setPlanId(Long value){planId=value;}
    public String getReceivedAmount(){return receivedAmount;} public void setReceivedAmount(String value){receivedAmount=value;}
    public String getRemark(){return remark;} public void setRemark(String value){remark=value;}
    public String getPaymentMethod(){return paymentMethod;} public void setPaymentMethod(String value){paymentMethod=value;}
    public String getVoucherUrl(){return voucherUrl;} public void setVoucherUrl(String value){voucherUrl=value;}
}
