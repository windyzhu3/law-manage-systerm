package com.ruoyi.system.domain;

public class BizLeadQualityRecord
{
    private Long qualityRecordId;
    private Long leadId;
    private Long salesUserId;
    private String reasonCode;
    private Long reviewerId;
    private Long sourceTodoId;
    private Long sourceReviewId;
    private String qualityType;
    private String idempotencyKey;
    private String createBy;

    public Long getQualityRecordId() { return qualityRecordId; }
    public void setQualityRecordId(Long value) { qualityRecordId = value; }
    public Long getLeadId() { return leadId; }
    public void setLeadId(Long value) { leadId = value; }
    public Long getSalesUserId() { return salesUserId; }
    public void setSalesUserId(Long value) { salesUserId = value; }
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String value) { reasonCode = value; }
    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long value) { reviewerId = value; }
    public Long getSourceTodoId() { return sourceTodoId; }
    public void setSourceTodoId(Long value) { sourceTodoId = value; }
    public Long getSourceReviewId() { return sourceReviewId; }
    public void setSourceReviewId(Long value) { sourceReviewId = value; }
    public String getQualityType() { return qualityType; }
    public void setQualityType(String value) { qualityType = value; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String value) { idempotencyKey = value; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String value) { createBy = value; }
}
