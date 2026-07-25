package com.ruoyi.system.service.lead;

import org.springframework.stereotype.Service;
import com.law.business.security.BusinessActor;
import com.law.business.security.BusinessActorProvider;
import com.law.business.shared.error.BusinessErrorCode;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.mapper.BizLeadMapper;

@Service
public class LeadAccessPolicy
{
    private static final String DELETED = "2";
    private static final String IN_POOL = "1";

    private final BizLeadMapper mapper;
    private final BusinessActorProvider actors;

    public LeadAccessPolicy(BizLeadMapper mapper, BusinessActorProvider actors)
    {
        this.mapper = mapper;
        this.actors = actors;
    }

    public BizLead requireOperable(Long leadId)
    {
        return requireReadable(leadId, false, false);
    }

    public BizLead requireReadable(Long leadId, boolean includeDeleted, boolean allowPool)
    {
        BizLead lead = mapper.selectLeadById(leadId);
        if (lead == null || (!includeDeleted && DELETED.equals(lead.getDelFlag())))
        {
            throw error(BusinessErrorCode.DATA_NOT_FOUND, "线索不存在或已删除");
        }
        BusinessActor actor = actors.current();
        if (actor.administrator())
        {
            return lead;
        }
        if (allowPool && !DELETED.equals(lead.getDelFlag()) && IN_POOL.equals(lead.getPoolStatus())
                && "PUBLIC_POOL".equals(lead.getDisposition()))
        {
            return lead;
        }
        if (mapper.countLeadInDataScope(leadId, actor.userId(), actor.deptId(), includeDeleted) == 0)
        {
            throw error(BusinessErrorCode.ACCESS_DENIED, "无权访问该线索");
        }
        return lead;
    }

    private ServiceException error(BusinessErrorCode code, String message)
    {
        return new ServiceException(message, code.name());
    }
}
