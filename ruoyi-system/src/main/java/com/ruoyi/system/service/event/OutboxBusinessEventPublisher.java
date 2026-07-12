package com.ruoyi.system.service.event;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSON;
import com.law.business.event.BusinessEventCommand;
import com.law.business.event.BusinessEventPublisher;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.domain.BusinessEventRecord;
import com.ruoyi.system.mapper.BusinessEventMapper;

@Service
public class OutboxBusinessEventPublisher implements BusinessEventPublisher
{
    @Autowired
    private BusinessEventMapper eventMapper;

    @Override
    public void publish(BusinessEventCommand command)
    {
        BusinessEventRecord event = new BusinessEventRecord();
        event.setEventType(command.getEventType().name());
        event.setAggregateType(command.getAggregateType());
        event.setAggregateId(command.getAggregateId());
        event.setAggregateNo(command.getAggregateNo());
        event.setIdempotencyKey(command.getIdempotencyKey());
        event.setPayload(JSON.toJSONString(command.getPayload()));
        event.setEventStatus("PENDING");
        event.setRetryCount(0);
        event.setCreateBy(SecurityUtils.getUsername());
        if (eventMapper.insertBusinessEvent(event) <= 0)
        {
            throw new ServiceException("业务事件记录失败");
        }
    }
}
