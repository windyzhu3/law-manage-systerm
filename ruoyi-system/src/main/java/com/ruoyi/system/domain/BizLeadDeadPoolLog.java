package com.ruoyi.system.domain;

import java.time.LocalDateTime;

public class BizLeadDeadPoolLog
{
    private Long deadPoolLogId;
    private Long leadId;
    private String actionType;
    private String reasonCode;
    private String reasonDetail;
    private String fromDisposition;
    private String toDisposition;
    private Long operatorId;
    private Long sourceTodoId;
    private LocalDateTime actionTime;
    private String idempotencyKey;
    private String createBy;

    public Long getDeadPoolLogId() { return deadPoolLogId; }
    public void setDeadPoolLogId(Long value) { deadPoolLogId = value; }
    public Long getLeadId() { return leadId; }
    public void setLeadId(Long value) { leadId = value; }
    public String getActionType() { return actionType; }
    public void setActionType(String value) { actionType = value; }
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String value) { reasonCode = value; }
    public String getReasonDetail() { return reasonDetail; }
    public void setReasonDetail(String value) { reasonDetail = value; }
    public String getFromDisposition() { return fromDisposition; }
    public void setFromDisposition(String value) { fromDisposition = value; }
    public String getToDisposition() { return toDisposition; }
    public void setToDisposition(String value) { toDisposition = value; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long value) { operatorId = value; }
    public Long getSourceTodoId() { return sourceTodoId; }
    public void setSourceTodoId(Long value) { sourceTodoId = value; }
    public LocalDateTime getActionTime() { return actionTime; }
    public void setActionTime(LocalDateTime value) { actionTime = value; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String value) { idempotencyKey = value; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String value) { createBy = value; }
}
