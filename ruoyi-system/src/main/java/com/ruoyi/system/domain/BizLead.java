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
    private String tagConfirmStatus;
    private Long tagRelationId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date tagConfirmTime;
    private Long tagConfirmBy;
    private String firstContactStatus;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date firstContactTime;
    private String firstContactResult;
    private String city;
    private String visited;
    private String status;
    private String poolStatus;
    private String disposition;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date deadPoolTime;
    private String deadPoolReason;
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
    private String invalidReasonCode;
    private String invalidSourceNode;
    private String invalidReviewStatus;
    private String retryStage;
    private Integer retryAttemptCount;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date nextRetryTime;
    private String poolReason;
    private String delFlag;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date deleteTime;
    private Long currentUserId;
    private Long currentDeptId;
    private Boolean dataScope;
    private String listMode;
    private Integer rowVersion;

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
    public String getTagConfirmStatus() { return tagConfirmStatus; }
    public void setTagConfirmStatus(String tagConfirmStatus) { this.tagConfirmStatus = tagConfirmStatus; }
    public Long getTagRelationId() { return tagRelationId; }
    public void setTagRelationId(Long tagRelationId) { this.tagRelationId = tagRelationId; }
    public Date getTagConfirmTime() { return tagConfirmTime; }
    public void setTagConfirmTime(Date tagConfirmTime) { this.tagConfirmTime = tagConfirmTime; }
    public Long getTagConfirmBy() { return tagConfirmBy; }
    public void setTagConfirmBy(Long tagConfirmBy) { this.tagConfirmBy = tagConfirmBy; }
    public String getFirstContactStatus() { return firstContactStatus; }
    public void setFirstContactStatus(String firstContactStatus) { this.firstContactStatus = firstContactStatus; }
    public Date getFirstContactTime() { return firstContactTime; }
    public void setFirstContactTime(Date firstContactTime) { this.firstContactTime = firstContactTime; }
    public String getFirstContactResult() { return firstContactResult; }
    public void setFirstContactResult(String firstContactResult) { this.firstContactResult = firstContactResult; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getVisited() { return visited; }
    public void setVisited(String visited) { this.visited = visited; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPoolStatus() { return poolStatus; }
    public void setPoolStatus(String poolStatus) { this.poolStatus = poolStatus; }
    public String getDisposition() { return disposition; }
    public void setDisposition(String disposition) { this.disposition = disposition; }
    public Date getDeadPoolTime() { return deadPoolTime; }
    public void setDeadPoolTime(Date deadPoolTime) { this.deadPoolTime = deadPoolTime; }
    public String getDeadPoolReason() { return deadPoolReason; }
    public void setDeadPoolReason(String deadPoolReason) { this.deadPoolReason = deadPoolReason; }
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
    public String getInvalidReasonCode() { return invalidReasonCode; }
    public void setInvalidReasonCode(String invalidReasonCode) { this.invalidReasonCode = invalidReasonCode; }
    public String getInvalidSourceNode() { return invalidSourceNode; }
    public void setInvalidSourceNode(String invalidSourceNode) { this.invalidSourceNode = invalidSourceNode; }
    public String getInvalidReviewStatus() { return invalidReviewStatus; }
    public void setInvalidReviewStatus(String invalidReviewStatus) { this.invalidReviewStatus = invalidReviewStatus; }
    public String getRetryStage() { return retryStage; }
    public void setRetryStage(String retryStage) { this.retryStage = retryStage; }
    public Integer getRetryAttemptCount() { return retryAttemptCount; }
    public void setRetryAttemptCount(Integer retryAttemptCount) { this.retryAttemptCount = retryAttemptCount; }
    public Date getNextRetryTime() { return nextRetryTime; }
    public void setNextRetryTime(Date nextRetryTime) { this.nextRetryTime = nextRetryTime; }
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
    public Integer getRowVersion() { return rowVersion; }
    public void setRowVersion(Integer rowVersion) { this.rowVersion = rowVersion; }
}
