package com.ruoyi.system.service.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;
import com.law.business.event.BusinessEventCommand;
import com.law.business.event.BusinessEventType;
import com.law.business.security.BusinessActor;
import com.ruoyi.system.domain.BusinessEventRecord;
import com.ruoyi.system.mapper.BusinessEventMapper;

class OutboxBusinessEventPublisherTest
{
    @Test
    void explicit_system_actor_writes_outbox_without_web_principal()
    {
        SecurityContextHolder.clearContext();
        BusinessEventMapper mapper=org.mockito.Mockito.mock(BusinessEventMapper.class);
        when(mapper.insertBusinessEvent(any())).thenReturn(1);
        OutboxBusinessEventPublisher publisher=new OutboxBusinessEventPublisher(mapper);

        publisher.publish(new BusinessEventCommand(BusinessEventType.LEAD_INVALID_REVIEW_CONFIRMED,
                "LEAD",7L,"L-7","LEAD_INVALID_REVIEW_CONFIRMED:7:61",
                Map.of("schemaVersion",1)),new BusinessActor(0L,"system","system",null,false));

        ArgumentCaptor<BusinessEventRecord> event=ArgumentCaptor.forClass(BusinessEventRecord.class);
        verify(mapper).insertBusinessEvent(event.capture());
        assertEquals("system",event.getValue().getCreateBy());
    }
}
