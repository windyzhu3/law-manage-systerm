package com.law.business.contract.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ContractCreateCommand
{
    @NotBlank(message = "合同名称不能为空") @Size(max = 200, message = "合同名称不能超过200个字符")
    private String contractName;
    @NotNull(message = "请选择客户")
    private Long customerId;
    @NotBlank(message = "请选择案件类型") @Size(max = 50, message = "案件类型不能超过50个字符")
    private String caseType;
    private Long lawyerId;
    @Size(max = 80, message = "律师名称不能超过80个字符")
    private String lawyerName;
    private Long ownerId;
    private Long deptId;
    @NotNull(message = "请输入签约金额") @DecimalMin(value = "0.01", message = "签约金额必须大于0")
    private BigDecimal signAmount;
    @Size(max = 30, message = "收费方式不能超过30个字符")
    private String feeType;
    private LocalDate signDate;
    private LocalDate effectiveDate;
    private LocalDate expireDate;
    @Size(max = 30, message = "签订方式不能超过30个字符")
    private String signMethod;
    @Size(max = 30, message = "风险等级不能超过30个字符")
    private String riskLevel;
    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;

    public String getContractName() { return contractName; }
    public void setContractName(String value) { contractName = value; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long value) { customerId = value; }
    public String getCaseType() { return caseType; }
    public void setCaseType(String value) { caseType = value; }
    public Long getLawyerId() { return lawyerId; }
    public void setLawyerId(Long value) { lawyerId = value; }
    public String getLawyerName() { return lawyerName; }
    public void setLawyerName(String value) { lawyerName = value; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long value) { ownerId = value; }
    public Long getDeptId() { return deptId; }
    public void setDeptId(Long value) { deptId = value; }
    public BigDecimal getSignAmount() { return signAmount; }
    public void setSignAmount(BigDecimal value) { signAmount = value; }
    public String getFeeType() { return feeType; }
    public void setFeeType(String value) { feeType = value; }
    public LocalDate getSignDate() { return signDate; }
    public void setSignDate(LocalDate value) { signDate = value; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate value) { effectiveDate = value; }
    public LocalDate getExpireDate() { return expireDate; }
    public void setExpireDate(LocalDate value) { expireDate = value; }
    public String getSignMethod() { return signMethod; }
    public void setSignMethod(String value) { signMethod = value; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String value) { riskLevel = value; }
    public String getRemark() { return remark; }
    public void setRemark(String value) { remark = value; }
}
