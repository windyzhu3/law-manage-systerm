package com.ruoyi.system.service.lead;

import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.business.event.BusinessEventCommand;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.event.BusinessEventType;
import com.law.business.security.BusinessActor;
import com.law.business.security.BusinessActorProvider;
import com.law.business.shared.error.BusinessErrorCode;
import com.law.business.shared.status.LeadStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.BizCustomer;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.mapper.BizLeadMapper;
import com.ruoyi.system.service.IBizCustomerService;

@Service
public class LeadConversionService
{
    private static final String IN_POOL = "1";

    private final BizLeadMapper mapper;
    private final LeadAccessPolicy access;
    private final BusinessActorProvider actors;
    private final BusinessEventPublisher events;
    private final IBizCustomerService customers;

    public LeadConversionService(BizLeadMapper mapper, LeadAccessPolicy access,
            BusinessActorProvider actors, BusinessEventPublisher events, IBizCustomerService customers)
    {
        this.mapper = mapper;
        this.access = access;
        this.actors = actors;
        this.events = events;
        this.customers = customers;
    }

    @Transactional
    public Long convert(Long leadId, boolean ownerOnly)
    {
        BizLead lead = access.requireOperable(leadId);
        if (LeadStatus.CONVERTED.code().equals(lead.getStatus()) && lead.getCustomerId() != null)
        {
            return lead.getCustomerId();
        }
        requireConvertible(lead);
        BusinessActor actor = actors.current();
        if (ownerOnly && !actor.userId().equals(lead.getOwnerId()))
        {
            throw error(BusinessErrorCode.ACCESS_DENIED, "只有当前负责人可以转化该线索");
        }

        BizCustomer customer = customers.convertLeadToCustomer(lead);
        if (customer == null || customer.getCustomerId() == null)
        {
            throw error(BusinessErrorCode.PRECONDITION_FAILED, "客户创建失败");
        }
        Long customerId = customer.getCustomerId();
        int rows = mapper.bindCustomerConditionally(leadId, customerId, actor.userName(), lead.getStatus());
        if (rows <= 0)
        {
            throw error(BusinessErrorCode.CONCURRENT_MODIFICATION, "线索状态已变化，请刷新后重试");
        }
        events.publish(convertedEvent(lead, customerId, actor));
        return customerId;
    }

    private void requireConvertible(BizLead lead)
    {
        boolean following = LeadStatus.WAIT_FOLLOW.code().equals(lead.getStatus())
                || LeadStatus.FOLLOWING.code().equals(lead.getStatus());
        if (!following)
        {
            throw error(BusinessErrorCode.STATE_CONFLICT, "只有待跟进或跟进中的线索可以转化");
        }
        if (IN_POOL.equals(lead.getPoolStatus()) || lead.getOwnerId() == null)
        {
            throw error(BusinessErrorCode.PRECONDITION_FAILED, "线索需先分配或领取后才能转化");
        }
    }

    private BusinessEventCommand convertedEvent(BizLead lead, Long customerId, BusinessActor actor)
    {
        Map<String, Object> payload = new HashMap<>();
        payload.put("schemaVersion", 1);
        payload.put("operatorId", actor.userId());
        payload.put("ownerId", lead.getOwnerId());
        payload.put("customerId", customerId);
        return new BusinessEventCommand(BusinessEventType.LEAD_CONVERTED, "LEAD", lead.getLeadId(),
                lead.getLeadNo(), "LEAD_CONVERTED:" + lead.getLeadId() + ":" + customerId, payload);
    }

    private ServiceException error(BusinessErrorCode code, String message)
    {
        return new ServiceException(message, code.name());
    }
}
