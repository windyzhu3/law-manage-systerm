package com.ruoyi.system.domain;

import java.time.LocalDateTime;

public class BizLeadRetryRecord
{
    private Long retryRecordId;
    private Long leadId;
    private Long planId;
    private String windowCode;
    private Integer attemptNo;
    private String contactResult;
    private String nextWindowCode;
    private Long todoId;
    private Long callRecordId;
    private LocalDateTime occurredAt;
    private String idempotencyKey;
    private String createBy;

    public Long getRetryRecordId() { return retryRecordId; }
    public void setRetryRecordId(Long value) { retryRecordId = value; }
    public Long getLeadId() { return leadId; }
    public void setLeadId(Long value) { leadId = value; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long value) { planId = value; }
    public String getWindowCode() { return windowCode; }
    public void setWindowCode(String value) { windowCode = value; }
    public Integer getAttemptNo() { return attemptNo; }
    public void setAttemptNo(Integer value) { attemptNo = value; }
    public String getContactResult() { return contactResult; }
    public void setContactResult(String value) { contactResult = value; }
    public String getNextWindowCode() { return nextWindowCode; }
    public void setNextWindowCode(String value) { nextWindowCode = value; }
    public Long getTodoId() { return todoId; }
    public void setTodoId(Long value) { todoId = value; }
    public Long getCallRecordId() { return callRecordId; }
    public void setCallRecordId(Long value) { callRecordId = value; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime value) { occurredAt = value; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String value) { idempotencyKey = value; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String value) { createBy = value; }
}
