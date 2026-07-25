package com.ruoyi.system.domain;

import java.time.LocalDateTime;

public class BizLeadInvalidReview
{
    private Long reviewId;
    private Long leadId;
    private String reasonCode;
    private String salesExplanation;
    private Long submittedBy;
    private LocalDateTime submittedAt;
    private Long reviewerId;
    private String reviewResult;
    private String reviewComment;
    private LocalDateTime reviewedAt;
    private String systemDefault;
    private Long todoId;
    private String status;
    private String idempotencyKey;
    private Integer rowVersion;
    private String createBy;
    private String updateBy;

    public Long getReviewId() { return reviewId; }
    public void setReviewId(Long value) { reviewId = value; }
    public Long getLeadId() { return leadId; }
    public void setLeadId(Long value) { leadId = value; }
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String value) { reasonCode = value; }
    public String getSalesExplanation() { return salesExplanation; }
    public void setSalesExplanation(String value) { salesExplanation = value; }
    public Long getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(Long value) { submittedBy = value; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime value) { submittedAt = value; }
    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long value) { reviewerId = value; }
    public String getReviewResult() { return reviewResult; }
    public void setReviewResult(String value) { reviewResult = value; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String value) { reviewComment = value; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime value) { reviewedAt = value; }
    public String getSystemDefault() { return systemDefault; }
    public void setSystemDefault(String value) { systemDefault = value; }
    public Long getTodoId() { return todoId; }
    public void setTodoId(Long value) { todoId = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String value) { idempotencyKey = value; }
    public Integer getRowVersion() { return rowVersion; }
    public void setRowVersion(Integer value) { rowVersion = value; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String value) { createBy = value; }
    public String getUpdateBy() { return updateBy; }
    public void setUpdateBy(String value) { updateBy = value; }
}
