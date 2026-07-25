package com.ruoyi.system.domain;

public class BizLeadAssignmentPolicy
{
    private Long policyId;
    private Long salesDeptId;
    private String sourceCode;
    private String businessType;
    private String retryRuleJson;
    private String status;

    public Long getPolicyId() { return policyId; }
    public void setPolicyId(Long value) { policyId = value; }
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
}
