package com.law.todo.integration;

import java.util.Map;

public record TodoEvent(String eventId,String eventType,String aggregateType,Long aggregateId,String aggregateNo,Map<String,Object> payload,int payloadVersion)
{
    public TodoEvent { if(payloadVersion<1)throw new IllegalArgumentException("payloadVersion must be positive");payload=payload==null?Map.of():Map.copyOf(payload); }
    public TodoEvent(String eventId,String eventType,String aggregateType,Long aggregateId,String aggregateNo,Map<String,Object> payload)
    {this(eventId,eventType,aggregateType,aggregateId,aggregateNo,payload,1);}
}
