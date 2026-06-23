package com.ruoyi.system.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.annotation.Excel.Type;
import com.ruoyi.common.core.domain.BaseEntity;

public class BizContract extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long contractId;
    @Excel(name = "合同编号", type = Type.EXPORT)
    private String contractNo;
    @Excel(name = "合同名称")
    private String contractName;
    private Long customerId;
    @Excel(name = "客户名称")
    private String customerName;
    @Excel(name = "案件类型", dictType = "law_contract_case_type", comboReadDict = true)
    private String caseType;
    private Long lawyerId;
    @Excel(name = "承办律师")
    private String lawyerName;
    private Long ownerId;
    @Excel(name = "负责人", type = Type.EXPORT)
    private String ownerName;
    private Long deptId;
    @Excel(name = "所属部门", type = Type.EXPORT)
    private String deptName;
    @Excel(name = "签约金额")
    private BigDecimal signAmount;
    @Excel(name = "收费方式", dictType = "law_contract_fee_type", comboReadDict = true)
    private String feeType;
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "签订日期", width = 30, dateFormat = "yyyy-MM-dd")
    private Date signDate;
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "生效日期", width = 30, dateFormat = "yyyy-MM-dd")
    private Date effectiveDate;
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "到期日期", width = 30, dateFormat = "yyyy-MM-dd")
    private Date expireDate;
    @Excel(name = "签订方式", dictType = "law_contract_sign_method", comboReadDict = true)
    private String signMethod;
    @Excel(name = "签订状态", dictType = "law_contract_sign_status", comboReadDict = true, type = Type.EXPORT)
    private String signStatus;
    @Excel(name = "审核状态", dictType = "law_contract_audit_status", comboReadDict = true, type = Type.EXPORT)
    private String auditStatus;
    @Excel(name = "合同状态", dictType = "law_contract_status", comboReadDict = true, type = Type.EXPORT)
    private String contractStatus;
    @Excel(name = "风险等级", dictType = "law_contract_risk_level", comboReadDict = true)
    private String riskLevel;
    private String delFlag;
    private Long currentUserId;
    private Long currentDeptId;
    private Boolean dataScope;
    /** 列表统一搜索关键字（仅查询条件，不入库） */
    private String keyword;

    public Long getContractId() { return contractId; }
    public void setContractId(Long contractId) { this.contractId = contractId; }
    public String getContractNo() { return contractNo; }
    public void setContractNo(String contractNo) { this.contractNo = contractNo; }
    public String getContractName() { return contractName; }
    public void setContractName(String contractName) { this.contractName = contractName; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCaseType() { return caseType; }
    public void setCaseType(String caseType) { this.caseType = caseType; }
    public Long getLawyerId() { return lawyerId; }
    public void setLawyerId(Long lawyerId) { this.lawyerId = lawyerId; }
    public String getLawyerName() { return lawyerName; }
    public void setLawyerName(String lawyerName) { this.lawyerName = lawyerName; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public Long getDeptId() { return deptId; }
    public void setDeptId(Long deptId) { this.deptId = deptId; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
    public BigDecimal getSignAmount() { return signAmount; }
    public void setSignAmount(BigDecimal signAmount) { this.signAmount = signAmount; }
    public String getFeeType() { return feeType; }
    public void setFeeType(String feeType) { this.feeType = feeType; }
    public Date getSignDate() { return signDate; }
    public void setSignDate(Date signDate) { this.signDate = signDate; }
    public Date getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(Date effectiveDate) { this.effectiveDate = effectiveDate; }
    public Date getExpireDate() { return expireDate; }
    public void setExpireDate(Date expireDate) { this.expireDate = expireDate; }
    public String getSignMethod() { return signMethod; }
    public void setSignMethod(String signMethod) { this.signMethod = signMethod; }
    public String getSignStatus() { return signStatus; }
    public void setSignStatus(String signStatus) { this.signStatus = signStatus; }
    public String getAuditStatus() { return auditStatus; }
    public void setAuditStatus(String auditStatus) { this.auditStatus = auditStatus; }
    public String getContractStatus() { return contractStatus; }
    public void setContractStatus(String contractStatus) { this.contractStatus = contractStatus; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }
    public Long getCurrentUserId() { return currentUserId; }
    public void setCurrentUserId(Long currentUserId) { this.currentUserId = currentUserId; }
    public Long getCurrentDeptId() { return currentDeptId; }
    public void setCurrentDeptId(Long currentDeptId) { this.currentDeptId = currentDeptId; }
    public Boolean getDataScope() { return dataScope; }
    public void setDataScope(Boolean dataScope) { this.dataScope = dataScope; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
}
