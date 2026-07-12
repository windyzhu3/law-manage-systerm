package com.law.todo.integration;

import java.util.Map;

public record TodoEvent(String eventId,String eventType,String aggregateType,Long aggregateId,String aggregateNo,Map<String,Object> payload)
{
    public TodoEvent { payload=payload==null?Map.of():Map.copyOf(payload); }
}
