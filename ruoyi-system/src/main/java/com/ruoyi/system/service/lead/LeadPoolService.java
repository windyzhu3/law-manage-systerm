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
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.mapper.BizLeadMapper;
import com.law.business.security.LeadPermissions;

@Service
public class LeadPoolService
{
    private static final String IN_POOL = "1";
    private static final BusinessActor SYSTEM = new BusinessActor(0L, "system", "system", null, false);
    private final BizLeadMapper mapper;
    private final LeadAccessPolicy access;
    private final BusinessActorProvider actors;
    private final BusinessEventPublisher events;

    public LeadPoolService(BizLeadMapper mapper, LeadAccessPolicy access,
            BusinessActorProvider actors, BusinessEventPublisher events)
    {
        this.mapper = mapper;
        this.access = access;
        this.actors = actors;
        this.events = events;
    }

    @Transactional
    public int moveToPool(Long leadId, String reason)
    {
        BizLead lead = access.requireOperable(leadId);
        if (!SecurityUtils.hasPermi(LeadPermissions.MOVE_POOL) && SecurityUtils.hasPermi(LeadPermissions.MOVE_MINE_POOL))
            requireOwner(lead);
        requireActive(lead);
        if (IN_POOL.equals(lead.getPoolStatus())) throw error(BusinessErrorCode.STATE_CONFLICT, "线索已在公海");
        BusinessActor actor = actors.current();
        int rows = mapper.moveToPool(leadId, reason, actor.userName(), lead.getStatus(), lead.getRowVersion());
        changed(rows);
        Long logId = insertLog(leadId, lead.getOwnerId(), null, "pool", reason, actor.userName());
        Map<String, Object> payload = payload(actor); payload.put("reason", reason == null ? "" : reason);
        publish(BusinessEventType.LEAD_MOVED_TO_POOL, lead, "LEAD_MOVED_TO_POOL:" + leadId + ":" + logId,
                payload,actor);
        return rows;
    }

    @Transactional
    public int claim(Long leadId)
    {
        BizLead lead = access.requireReadable(leadId, false, true);
        if (!"PUBLIC_POOL".equals(lead.getDisposition()))
            throw error(BusinessErrorCode.ACCESS_DENIED, "LEAD_DEAD_POOL_ACCESS_DENIED");
        requireActive(lead);
        if (!IN_POOL.equals(lead.getPoolStatus())) throw error(BusinessErrorCode.STATE_CONFLICT, "线索已被领取");
        BusinessActor actor = actors.current();
        int rows = mapper.claimLead(leadId, actor.userId(), actor.deptId(), actor.userName(), lead.getStatus(),
                lead.getRowVersion());
        changed(rows);
        Long logId = insertLog(leadId, null, actor.userId(), "claim", "claim from public pool", actor.userName());
        Map<String, Object> payload = payload(actor); payload.put("ownerId", actor.userId());
        publish(BusinessEventType.LEAD_CLAIMED, lead, "LEAD_CLAIMED:" + leadId + ":" + logId,payload,actor);
        return rows;
    }

    @Transactional
    public int moveToPoolBySystem(BizLead lead, int expectedRowVersion, String reason)
    {
        if (lead == null || !"ACTIVE".equals(lead.getDisposition()))
            throw error(BusinessErrorCode.STATE_CONFLICT, "Lead is not active");
        int rows = mapper.moveToPool(lead.getLeadId(), reason, SYSTEM.userName(), lead.getStatus(),
                expectedRowVersion);
        changed(rows);
        Long logId = insertLog(lead.getLeadId(), lead.getOwnerId(), null, "pool", reason, SYSTEM.userName());
        Map<String, Object> payload = payload(SYSTEM);
        payload.put("reason", reason == null ? "" : reason);
        publish(BusinessEventType.LEAD_MOVED_TO_POOL, lead,
                "LEAD_MOVED_TO_POOL:" + lead.getLeadId() + ":" + logId,payload,SYSTEM);
        return rows;
    }

    private Long insertLog(Long leadId, Long fromOwnerId, Long toOwnerId, String actionType,
            String reason, String createBy)
    {
        Map<String, Object> log = new HashMap<>();
        log.put("leadId", leadId); log.put("fromOwnerId", fromOwnerId); log.put("toOwnerId", toOwnerId);
        log.put("actionType", actionType); log.put("reason", reason); log.put("createBy", createBy);
        if (mapper.insertAssignmentLog(log) <= 0) throw error(BusinessErrorCode.CONCURRENT_MODIFICATION, "线索分配日志写入失败");
        Object id = log.get("logId");
        if (id == null) throw error(BusinessErrorCode.CONCURRENT_MODIFICATION, "线索分配日志编号生成失败");
        return Long.valueOf(String.valueOf(id));
    }

    private Map<String, Object> payload(BusinessActor actor)
    {
        Map<String, Object> value = new HashMap<>();value.put("schemaVersion", 1);value.put("operatorId", actor.userId());return value;
    }

    private void publish(BusinessEventType type, BizLead lead, String key, Map<String, Object> payload,
            BusinessActor actor)
    {
        events.publish(new BusinessEventCommand(type, "LEAD", lead.getLeadId(), lead.getLeadNo(), key, payload),
                actor);
    }

    private void requireActive(BizLead lead)
    {
        if (LeadStatus.CONVERTED.code().equals(lead.getStatus()) || LeadStatus.INVALID.code().equals(lead.getStatus())
                || LeadStatus.CLOSED.code().equals(lead.getStatus()))
            throw error(BusinessErrorCode.STATE_CONFLICT, "当前线索状态不能进行公海操作");
    }

    private void requireOwner(BizLead lead)
    {
        if (lead.getOwnerId() == null || !lead.getOwnerId().equals(actors.current().userId()))
            throw error(BusinessErrorCode.ACCESS_DENIED, "只有当前负责人可以移入公海");
    }

    private void changed(int rows)
    {
        if (rows <= 0) throw error(BusinessErrorCode.CONCURRENT_MODIFICATION, "线索状态已变化，请刷新后重试");
    }

    private ServiceException error(BusinessErrorCode code, String message)
    {
        return new ServiceException(message, code.name());
    }
}
