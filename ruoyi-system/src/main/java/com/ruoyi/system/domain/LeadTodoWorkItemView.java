package com.ruoyi.system.domain;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Flat operational read model used by the lead Todo workbenches. Each mapper
 * query returns a complete business/fact/Todo/SLA row and never hydrates it
 * through per-row follow-up queries.
 */
public class LeadTodoWorkItemView
{
    private Long leadId;
    private String leadNo;
    private String leadName;
    private String mobile;
    private String sourceCode;
    private String disposition;
    private Long businessOwnerId;
    private String businessOwnerName;
    private Long businessDeptId;
    private String businessDeptName;

    private Long callRecordId;
    private Long reviewId;
    private Long retryRecordId;
    private Long deadPoolLogId;
    private Long planId;
    private Long windowId;
    private Long occurrenceId;
    private Integer occurrenceNo;
    private String windowCode;
    private String nextWindowCode;
    private Integer attemptNo;
    private String factStatus;
    private String result;
    private String reasonCode;
    private String detail;
    private Long reviewerId;
    private String reviewerName;
    private String callChannel;
    private String externalCallId;
    private Integer durationSeconds;
    private Long recordingFileObjectId;
    private LocalDateTime factTime;
    private LocalDateTime windowStartAt;
    private LocalDateTime windowDueAt;

    private Long todoId;
    private String todoTitle;
    private String todoStatus;
    private Long todoOwnerId;
    private String todoOwnerName;
    private LocalDateTime dueAt;
    private String slaStatus;
    private Boolean overdue;
    private Boolean escalated;
    private LocalDateTime escalatedAt;
    private List<String> allowedActions = List.of();

    public Long getLeadId() { return leadId; }
    public void setLeadId(Long value) { leadId = value; }
    public String getLeadNo() { return leadNo; }
    public void setLeadNo(String value) { leadNo = value; }
    public String getLeadName() { return leadName; }
    public void setLeadName(String value) { leadName = value; }
    public String getMobile() { return mobile; }
    public void setMobile(String value) { mobile = value; }
    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String value) { sourceCode = value; }
    public String getDisposition() { return disposition; }
    public void setDisposition(String value) { disposition = value; }
    public Long getBusinessOwnerId() { return businessOwnerId; }
    public void setBusinessOwnerId(Long value) { businessOwnerId = value; }
    public String getBusinessOwnerName() { return businessOwnerName; }
    public void setBusinessOwnerName(String value) { businessOwnerName = value; }
    public Long getBusinessDeptId() { return businessDeptId; }
    public void setBusinessDeptId(Long value) { businessDeptId = value; }
    public String getBusinessDeptName() { return businessDeptName; }
    public void setBusinessDeptName(String value) { businessDeptName = value; }
    public Long getCallRecordId() { return callRecordId; }
    public void setCallRecordId(Long value) { callRecordId = value; }
    public Long getReviewId() { return reviewId; }
    public void setReviewId(Long value) { reviewId = value; }
    public Long getRetryRecordId() { return retryRecordId; }
    public void setRetryRecordId(Long value) { retryRecordId = value; }
    public Long getDeadPoolLogId() { return deadPoolLogId; }
    public void setDeadPoolLogId(Long value) { deadPoolLogId = value; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long value) { planId = value; }
    public Long getWindowId() { return windowId; }
    public void setWindowId(Long value) { windowId = value; }
    public Long getOccurrenceId() { return occurrenceId; }
    public void setOccurrenceId(Long value) { occurrenceId = value; }
    public Integer getOccurrenceNo() { return occurrenceNo; }
    public void setOccurrenceNo(Integer value) { occurrenceNo = value; }
    public String getWindowCode() { return windowCode; }
    public void setWindowCode(String value) { windowCode = value; }
    public String getNextWindowCode() { return nextWindowCode; }
    public void setNextWindowCode(String value) { nextWindowCode = value; }
    public Integer getAttemptNo() { return attemptNo; }
    public void setAttemptNo(Integer value) { attemptNo = value; }
    public String getFactStatus() { return factStatus; }
    public void setFactStatus(String value) { factStatus = value; }
    public String getResult() { return result; }
    public void setResult(String value) { result = value; }
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String value) { reasonCode = value; }
    public String getDetail() { return detail; }
    public void setDetail(String value) { detail = value; }
    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long value) { reviewerId = value; }
    public String getReviewerName() { return reviewerName; }
    public void setReviewerName(String value) { reviewerName = value; }
    public String getCallChannel() { return callChannel; }
    public void setCallChannel(String value) { callChannel = value; }
    public String getExternalCallId() { return externalCallId; }
    public void setExternalCallId(String value) { externalCallId = value; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer value) { durationSeconds = value; }
    public Long getRecordingFileObjectId() { return recordingFileObjectId; }
    public void setRecordingFileObjectId(Long value) { recordingFileObjectId = value; }
    public LocalDateTime getFactTime() { return factTime; }
    public void setFactTime(LocalDateTime value) { factTime = value; }
    public LocalDateTime getWindowStartAt() { return windowStartAt; }
    public void setWindowStartAt(LocalDateTime value) { windowStartAt = value; }
    public LocalDateTime getWindowDueAt() { return windowDueAt; }
    public void setWindowDueAt(LocalDateTime value) { windowDueAt = value; }
    public Long getTodoId() { return todoId; }
    public void setTodoId(Long value) { todoId = value; }
    public String getTodoTitle() { return todoTitle; }
    public void setTodoTitle(String value) { todoTitle = value; }
    public String getTodoStatus() { return todoStatus; }
    public void setTodoStatus(String value) { todoStatus = value; }
    public Long getTodoOwnerId() { return todoOwnerId; }
    public void setTodoOwnerId(Long value) { todoOwnerId = value; }
    public String getTodoOwnerName() { return todoOwnerName; }
    public void setTodoOwnerName(String value) { todoOwnerName = value; }
    public LocalDateTime getDueAt() { return dueAt; }
    public void setDueAt(LocalDateTime value) { dueAt = value; }
    public String getSlaStatus() { return slaStatus; }
    public void setSlaStatus(String value) { slaStatus = value; }
    public Boolean getOverdue() { return overdue; }
    public void setOverdue(Boolean value) { overdue = value; }
    public Boolean getEscalated() { return escalated; }
    public void setEscalated(Boolean value) { escalated = value; }
    public LocalDateTime getEscalatedAt() { return escalatedAt; }
    public void setEscalatedAt(LocalDateTime value) { escalatedAt = value; }
    public List<String> getAllowedActions() { return allowedActions; }
    public void setAllowedActions(List<String> value) { allowedActions = value == null ? List.of() : List.copyOf(value); }
}
