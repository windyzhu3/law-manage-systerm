package com.ruoyi.system.service.event;

import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;
import com.ruoyi.system.domain.BusinessEventRecord;
import com.ruoyi.system.mapper.BusinessEventMapper;

@Service
public class BusinessEventOutboxProcessor
{
    private final BusinessEventMapper mapper;
    private final List<BusinessEventHandler> handlers;

    public BusinessEventOutboxProcessor(BusinessEventMapper mapper, List<BusinessEventHandler> handlers)
    {
        this.mapper = mapper;
        this.handlers = handlers == null ? Collections.emptyList() : handlers;
    }

    public int processBatch(int requestedLimit)
    {
        int limit = Math.max(1, Math.min(requestedLimit, 200));
        List<BusinessEventRecord> events = mapper.selectPendingEvents(limit);
        if (events == null || events.isEmpty()) return 0;
        int processed = 0;
        for (BusinessEventRecord event : events)
        {
            if (event.getEventId() == null || mapper.claimEvent(event.getEventId()) <= 0) continue;
            try
            {
                handler(event).handle(event);
                if (mapper.markProcessed(event.getEventId()) > 0) processed++;
            }
            catch (RuntimeException exception)
            {
                mapper.markFailed(event.getEventId(), errorMessage(exception));
            }
        }
        return processed;
    }

    public boolean requeueDead(Long eventId)
    {
        return eventId != null && mapper.requeueDead(eventId) > 0;
    }

    private BusinessEventHandler handler(BusinessEventRecord event)
    {
        for (BusinessEventHandler handler : handlers)
            if (handler.supports(event.getEventType())) return handler;
        throw new IllegalStateException("未配置业务事件处理器：" + event.getEventType());
    }

    private String errorMessage(RuntimeException exception)
    {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) message = exception.getClass().getSimpleName();
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
