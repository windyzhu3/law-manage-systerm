package com.ruoyi.system.service.event;

import java.util.Map;
import org.springframework.stereotype.Component;
import com.alibaba.fastjson2.JSON;
import com.law.todo.integration.TodoEvent;
import com.law.todo.integration.TodoEventService;
import com.ruoyi.system.domain.BusinessEventRecord;

@Component
public class TodoBusinessEventAdapter implements BusinessEventHandler
{
    private final TodoEventService service;
    public TodoBusinessEventAdapter(TodoEventService service){this.service=service;}
    public boolean supports(String eventType){return true;}
    public void handle(BusinessEventRecord event){Map<String,Object> payload=event.getPayload()==null?Map.of():JSON.parseObject(event.getPayload());service.handle(new TodoEvent(String.valueOf(event.getEventId()),event.getEventType(),event.getAggregateType(),event.getAggregateId(),event.getAggregateNo(),payload,event.getPayloadVersion()==null?1:event.getPayloadVersion()));}
}
