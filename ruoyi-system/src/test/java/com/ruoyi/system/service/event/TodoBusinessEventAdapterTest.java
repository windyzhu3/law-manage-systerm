package com.ruoyi.system.service.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.law.todo.integration.TodoEvent;
import com.law.todo.integration.TodoEventService;
import com.ruoyi.system.domain.BusinessEventRecord;

@ExtendWith(MockitoExtension.class)
class TodoBusinessEventAdapterTest
{
    @Mock TodoEventService service;

    @Test void propagatesProducerPayloadVersionToTodoRuntime()
    {
        BusinessEventRecord record=new BusinessEventRecord();record.setEventId(7L);record.setEventType("LEAD_CREATED");
        record.setAggregateType("LEAD");record.setAggregateId(9L);record.setAggregateNo("L-9");
        record.setPayload("{\"ownerId\":8}");record.setPayloadVersion(3);

        new TodoBusinessEventAdapter(service).handle(record);

        ArgumentCaptor<TodoEvent> captured=ArgumentCaptor.forClass(TodoEvent.class);
        verify(service).handle(captured.capture());
        assertEquals(3,captured.getValue().payloadVersion());
        assertEquals(8,captured.getValue().payload().get("ownerId"));
    }
}
