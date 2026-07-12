package com.ruoyi.system.service.event;

import com.ruoyi.system.domain.BusinessEventRecord;

public interface BusinessEventHandler
{
    boolean supports(String eventType);

    void handle(BusinessEventRecord event);
}
