package com.ruoyi.system.domain;

public class BizLeadAssignmentPolicy
{
    private Long policyId;
    private String policyCode;
    private String policyName;
    private Long salesDeptId;
    private String sourceCode;
    private String businessType;
    private String retryRuleJson;
    private String status;
    private Integer rowVersion;
    private String createBy;

    public Long getPolicyId() { return policyId; }
    public void setPolicyId(Long value) { policyId = value; }
    public String getPolicyCode() { return policyCode; }
    public void setPolicyCode(String value) { policyCode = value; }
    public String getPolicyName() { return policyName; }
    public void setPolicyName(String value) { policyName = value; }
    public Long getSalesDeptId() { return salesDeptId; }
    public void setSalesDeptId(Long value) { salesDeptId = value; }
    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String value) { sourceCode = value; }
    public String getBusinessType() { return businessType; }
    public void setBusinessType(String value) { businessType = value; }
    public String getRetryRuleJson() { return retryRuleJson; }
    public void setRetryRuleJson(String value) { retryRuleJson = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public Integer getRowVersion() { return rowVersion; }
    public void setRowVersion(Integer value) { rowVersion = value; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String value) { createBy = value; }
}
