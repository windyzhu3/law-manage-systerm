package com.ruoyi.system.service.event;

import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSON;
import com.law.business.event.BusinessEventCommand;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.security.BusinessActor;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.domain.BusinessEventRecord;
import com.ruoyi.system.mapper.BusinessEventMapper;

@Service
public class OutboxBusinessEventPublisher implements BusinessEventPublisher
{
    private final BusinessEventMapper eventMapper;

    public OutboxBusinessEventPublisher(BusinessEventMapper eventMapper)
    {
        this.eventMapper=eventMapper;
    }

    @Override
    public void publish(BusinessEventCommand command)
    {
        persist(command,SecurityUtils.getUsername());
    }

    @Override
    public void publish(BusinessEventCommand command,BusinessActor actor)
    {
        if(actor==null)throw new ServiceException("Business event actor is required");
        persist(command,actor.userName());
    }

    private void persist(BusinessEventCommand command,String createBy)
    {
        BusinessEventRecord event = new BusinessEventRecord();
        event.setEventType(command.getEventType().name());
        event.setPayloadVersion(command.getPayloadVersion());
        event.setAggregateType(command.getAggregateType());
        event.setAggregateId(command.getAggregateId());
        event.setAggregateNo(command.getAggregateNo());
        event.setIdempotencyKey(command.getIdempotencyKey());
        event.setPayload(JSON.toJSONString(command.getPayload()));
        event.setEventStatus("PENDING");
        event.setRetryCount(0);
        event.setCreateBy(createBy);
        if (eventMapper.insertBusinessEvent(event) <= 0)
        {
            throw new ServiceException("业务事件记录失败");
        }
    }
}
