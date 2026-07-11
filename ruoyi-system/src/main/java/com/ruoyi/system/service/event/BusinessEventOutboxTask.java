package com.ruoyi.system.service.event;

import org.springframework.stereotype.Component;

@Component("businessEventOutboxTask")
public class BusinessEventOutboxTask
{
    private final BusinessEventOutboxProcessor processor;

    public BusinessEventOutboxTask(BusinessEventOutboxProcessor processor)
    {
        this.processor = processor;
    }

    public void processPending()
    {
        processor.processBatch(100);
    }
}
