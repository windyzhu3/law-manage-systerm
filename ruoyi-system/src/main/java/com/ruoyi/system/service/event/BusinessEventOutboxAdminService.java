package com.ruoyi.system.service.event;

import java.util.List;
import org.springframework.stereotype.Service;
import com.ruoyi.system.domain.BusinessEventRecord;
import com.ruoyi.system.mapper.BusinessEventMapper;

@Service
public class BusinessEventOutboxAdminService
{
    private final BusinessEventMapper mapper;
    private final BusinessEventOutboxProcessor processor;

    public BusinessEventOutboxAdminService(BusinessEventMapper mapper, BusinessEventOutboxProcessor processor)
    {
        this.mapper = mapper;
        this.processor = processor;
    }

    public List<BusinessEventRecord> list(BusinessEventRecord query)
    {
        return mapper.selectEventList(query == null ? new BusinessEventRecord() : query);
    }

    public boolean requeueDead(Long eventId)
    {
        return processor.requeueDead(eventId);
    }
}
