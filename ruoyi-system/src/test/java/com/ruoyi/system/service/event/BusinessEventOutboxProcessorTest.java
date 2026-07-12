package com.ruoyi.system.service.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.system.domain.BusinessEventRecord;
import com.ruoyi.system.mapper.BusinessEventMapper;

@ExtendWith(MockitoExtension.class)
class BusinessEventOutboxProcessorTest
{
    @Mock private BusinessEventMapper mapper;
    @Mock private BusinessEventHandler handler;

    @Test
    void processesClaimedEventExactlyOnce()
    {
        BusinessEventRecord event = event(1L, "CASE_CREATED");
        when(mapper.selectPendingEvents(20)).thenReturn(List.of(event));
        when(mapper.claimEvent(1L)).thenReturn(1);
        when(handler.supports("CASE_CREATED")).thenReturn(true);
        when(mapper.markProcessed(1L)).thenReturn(1);

        int count = new BusinessEventOutboxProcessor(mapper, List.of(handler)).processBatch(20);

        assertEquals(1, count);
        verify(handler).handle(event);
        verify(mapper).markProcessed(1L);
        verify(mapper).requeueStaleProcessing(10);
    }

    @Test
    void recoversEventsLeftProcessingByCrashedWorker()
    {
        when(mapper.selectPendingEvents(20)).thenReturn(List.of());

        new BusinessEventOutboxProcessor(mapper, List.of()).processBatch(20);

        verify(mapper).requeueStaleProcessing(10);
    }

    @Test
    void skipsEventLostDuringClaimRace()
    {
        BusinessEventRecord event = event(2L, "CASE_CREATED");
        when(mapper.selectPendingEvents(20)).thenReturn(List.of(event));
        when(mapper.claimEvent(2L)).thenReturn(0);

        assertEquals(0, new BusinessEventOutboxProcessor(mapper, List.of(handler)).processBatch(20));

        verify(handler, never()).handle(event);
        verify(mapper, never()).markProcessed(2L);
    }

    @Test
    void recordsFailureWhenNoHandlerExists()
    {
        BusinessEventRecord event = event(3L, "UNKNOWN");
        when(mapper.selectPendingEvents(20)).thenReturn(List.of(event));
        when(mapper.claimEvent(3L)).thenReturn(1);

        assertEquals(0, new BusinessEventOutboxProcessor(mapper, List.of()).processBatch(20));

        verify(mapper).markFailed(3L, "未配置业务事件处理器：UNKNOWN");
    }

    @Test
    void requeuesOnlyDeadEventUpdatedByMapper()
    {
        when(mapper.requeueDead(4L)).thenReturn(1);

        assertEquals(true, new BusinessEventOutboxProcessor(mapper, List.of()).requeueDead(4L));
        assertEquals(false, new BusinessEventOutboxProcessor(mapper, List.of()).requeueDead(null));
    }

    private BusinessEventRecord event(Long id, String type)
    {
        BusinessEventRecord event = new BusinessEventRecord();
        event.setEventId(id);
        event.setEventType(type);
        return event;
    }
}
