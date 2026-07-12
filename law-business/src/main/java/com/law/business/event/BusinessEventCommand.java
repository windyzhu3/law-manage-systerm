package com.law.business.event;

import java.util.Collections;
import java.util.Map;

public class BusinessEventCommand
{
    private final BusinessEventType eventType;
    private final String aggregateType;
    private final Long aggregateId;
    private final String aggregateNo;
    private final String idempotencyKey;
    private final Map<String, Object> payload;

    public BusinessEventCommand(BusinessEventType eventType, String aggregateType, Long aggregateId,
            String aggregateNo, String idempotencyKey, Map<String, Object> payload)
    {
        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.aggregateNo = aggregateNo;
        this.idempotencyKey = idempotencyKey;
        this.payload = payload == null ? Collections.emptyMap() : Collections.unmodifiableMap(payload);
    }

    public BusinessEventType getEventType() { return eventType; }
    public String getAggregateType() { return aggregateType; }
    public Long getAggregateId() { return aggregateId; }
    public String getAggregateNo() { return aggregateNo; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Map<String, Object> getPayload() { return payload; }
}
