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
    private final int payloadVersion;

    public BusinessEventCommand(BusinessEventType eventType, String aggregateType, Long aggregateId,
            String aggregateNo, String idempotencyKey, Map<String, Object> payload)
    {
        this(eventType,aggregateType,aggregateId,aggregateNo,idempotencyKey,payload,1);
    }
    public BusinessEventCommand(BusinessEventType eventType, String aggregateType, Long aggregateId,
            String aggregateNo, String idempotencyKey, Map<String, Object> payload,int payloadVersion)
    {
        if (payloadVersion < 1) throw new IllegalArgumentException("payloadVersion must be positive");
        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.aggregateNo = aggregateNo;
        this.idempotencyKey = idempotencyKey;
        this.payload = payload == null ? Collections.emptyMap() : Collections.unmodifiableMap(payload);
        this.payloadVersion = payloadVersion;
    }

    public BusinessEventType getEventType() { return eventType; }
    public String getAggregateType() { return aggregateType; }
    public Long getAggregateId() { return aggregateId; }
    public String getAggregateNo() { return aggregateNo; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Map<String, Object> getPayload() { return payload; }
    public int getPayloadVersion() { return payloadVersion; }
}
