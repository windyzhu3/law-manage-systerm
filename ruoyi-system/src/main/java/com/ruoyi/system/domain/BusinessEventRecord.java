package com.ruoyi.system.domain;

import java.util.Date;

public class BusinessEventRecord
{
    private Long eventId;
    private String eventType;
    private String aggregateType;
    private Long aggregateId;
    private String aggregateNo;
    private String idempotencyKey;
    private String payload;
    private String eventStatus;
    private Integer retryCount;
    private Date nextRetryTime;
    private String createBy;

    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getAggregateType() { return aggregateType; }
    public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }
    public Long getAggregateId() { return aggregateId; }
    public void setAggregateId(Long aggregateId) { this.aggregateId = aggregateId; }
    public String getAggregateNo() { return aggregateNo; }
    public void setAggregateNo(String aggregateNo) { this.aggregateNo = aggregateNo; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public String getEventStatus() { return eventStatus; }
    public void setEventStatus(String eventStatus) { this.eventStatus = eventStatus; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public Date getNextRetryTime() { return nextRetryTime; }
    public void setNextRetryTime(Date nextRetryTime) { this.nextRetryTime = nextRetryTime; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }
}
