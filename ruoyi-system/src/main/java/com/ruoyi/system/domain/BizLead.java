package com.ruoyi.system.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * Law firm lead.
 */
public class BizLead extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long leadId;
    private String leadNo;
    private String leadName;
    private String contactName;
    private String mobile;
    private String wechat;
    private String companyName;
    private String sourceCode;
    private String status;
    private String poolStatus;
    private String priority;
    private String legalDemand;
    private BigDecimal estimatedAmount;
    private Long ownerId;
    private String ownerName;
    private Long deptId;
    private String deptName;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date nextFollowTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastFollowTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date convertedTime;
    private Long customerId;
    private String invalidReason;
    private String poolReason;
    private String delFlag;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date deleteTime;
    private Long currentUserId;
    private Long currentDeptId;
    private Boolean dataScope;
    private String listMode;

    public Long getLeadId() { return leadId; }
    public void setLeadId(Long leadId) { this.leadId = leadId; }
    public String getLeadNo() { return leadNo; }
    public void setLeadNo(String leadNo) { this.leadNo = leadNo; }
    public String getLeadName() { return leadName; }
    public void setLeadName(String leadName) { this.leadName = leadName; }
    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public String getWechat() { return wechat; }
    public void setWechat(String wechat) { this.wechat = wechat; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPoolStatus() { return poolStatus; }
    public void setPoolStatus(String poolStatus) { this.poolStatus = poolStatus; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getLegalDemand() { return legalDemand; }
    public void setLegalDemand(String legalDemand) { this.legalDemand = legalDemand; }
    public BigDecimal getEstimatedAmount() { return estimatedAmount; }
    public void setEstimatedAmount(BigDecimal estimatedAmount) { this.estimatedAmount = estimatedAmount; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public Long getDeptId() { return deptId; }
    public void setDeptId(Long deptId) { this.deptId = deptId; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
    public Date getNextFollowTime() { return nextFollowTime; }
    public void setNextFollowTime(Date nextFollowTime) { this.nextFollowTime = nextFollowTime; }
    public Date getLastFollowTime() { return lastFollowTime; }
    public void setLastFollowTime(Date lastFollowTime) { this.lastFollowTime = lastFollowTime; }
    public Date getConvertedTime() { return convertedTime; }
    public void setConvertedTime(Date convertedTime) { this.convertedTime = convertedTime; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getInvalidReason() { return invalidReason; }
    public void setInvalidReason(String invalidReason) { this.invalidReason = invalidReason; }
    public String getPoolReason() { return poolReason; }
    public void setPoolReason(String poolReason) { this.poolReason = poolReason; }
    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }
    public Date getDeleteTime() { return deleteTime; }
    public void setDeleteTime(Date deleteTime) { this.deleteTime = deleteTime; }
    public Long getCurrentUserId() { return currentUserId; }
    public void setCurrentUserId(Long currentUserId) { this.currentUserId = currentUserId; }
    public Long getCurrentDeptId() { return currentDeptId; }
    public void setCurrentDeptId(Long currentDeptId) { this.currentDeptId = currentDeptId; }
    public Boolean getDataScope() { return dataScope; }
    public void setDataScope(Boolean dataScope) { this.dataScope = dataScope; }
    public String getListMode() { return listMode; }
    public void setListMode(String listMode) { this.listMode = listMode; }
}
