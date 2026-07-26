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
    private static final String DEAD_POOL = "DEAD_POOL";
    private static final String PUBLIC_POOL = "PUBLIC_POOL";

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

    /**
     * A review assignment is an explicit, occurrence-scoped access grant. It
     * lets only the exact TD-002 reviewer inspect the source lead even when the
     * sales and reviewer departments are siblings. Peers still fall through to
     * the ordinary lead data-scope policy.
     */
    public BizLead requireReviewable(Long leadId, Long reviewerId)
    {
        BizLead lead = mapper.selectLeadById(leadId);
        if (lead == null || DELETED.equals(lead.getDelFlag()))
        {
            throw error(BusinessErrorCode.DATA_NOT_FOUND, "线索不存在或已删除");
        }
        BusinessActor actor = actors.current();
        if (actor.administrator() || (reviewerId != null && reviewerId.equals(actor.userId())))
        {
            return lead;
        }
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
                && PUBLIC_POOL.equals(lead.getDisposition()))
        {
            return lead;
        }
        if (DEAD_POOL.equals(lead.getDisposition()))
        {
            if (mapper.countDeadPoolInDataScope(leadId, actor.userId(), actor.deptId()) == 0)
            {
                throw error(BusinessErrorCode.ACCESS_DENIED, "LEAD_DEAD_POOL_ACCESS_DENIED");
            }
            return lead;
        }
        if (mapper.countLeadInDataScope(leadId, actor.userId(), actor.deptId(), includeDeleted) == 0)
        {
            throw error(BusinessErrorCode.ACCESS_DENIED, "无权访问该线索");
        }
        return lead;
    }

    public BizLead requireDeadPoolRestorable(Long leadId)
    {
        BizLead lead = mapper.selectLeadById(leadId);
        if (lead == null || DELETED.equals(lead.getDelFlag()))
        {
            throw error(BusinessErrorCode.DATA_NOT_FOUND, "线索不存在或已删除");
        }
        if (!DEAD_POOL.equals(lead.getDisposition()))
        {
            throw error(BusinessErrorCode.STATE_CONFLICT, "LEAD_DEAD_POOL_STATE_INVALID");
        }
        BusinessActor actor = actors.current();
        if (!actor.administrator()
                && mapper.countDeadPoolInDataScope(leadId, actor.userId(), actor.deptId()) == 0)
        {
            throw error(BusinessErrorCode.ACCESS_DENIED, "LEAD_DEAD_POOL_ACCESS_DENIED");
        }
        return lead;
    }

    /**
     * Locks the lead and its immutable TRUE_INVALID/ENTER provenance before a
     * restore attempt is inspected. PUBLIC_POOL is accepted only so an exact
     * retry can be verified after the first transaction has committed.
     */
    public BizLead requireDeadPoolOriginForRestore(Long leadId, BusinessActor actor)
    {
        BizLead lead = mapper.selectDeadPoolOriginLeadForUpdate(leadId);
        if (lead == null || DELETED.equals(lead.getDelFlag()))
        {
            throw error(BusinessErrorCode.DATA_NOT_FOUND, "LEAD_DEAD_POOL_ORIGIN_NOT_FOUND");
        }
        if (!DEAD_POOL.equals(lead.getDisposition()) && !PUBLIC_POOL.equals(lead.getDisposition()))
        {
            throw error(BusinessErrorCode.STATE_CONFLICT, "LEAD_DEAD_POOL_STATE_INVALID");
        }
        if (!actor.administrator()
                && mapper.countDeadPoolInDataScope(leadId, actor.userId(), actor.deptId()) == 0)
        {
            throw error(BusinessErrorCode.ACCESS_DENIED, "LEAD_DEAD_POOL_ACCESS_DENIED");
        }
        return lead;
    }

    public void requireDepartmentAdministerable(Long deptId)
    {
        BusinessActor actor = actors.current();
        if (deptId == null || (!actor.administrator()
                && mapper.countDepartmentInDataScope(deptId, actor.userId(), actor.deptId()) == 0))
        {
            throw error(BusinessErrorCode.ACCESS_DENIED, "Assignment policy department is outside data scope");
        }
    }

    private ServiceException error(BusinessErrorCode code, String message)
    {
        return new ServiceException(message, code.name());
    }
}
