package com.ruoyi.system.service.lead;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
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
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.mapper.BizLeadMapper;
import com.ruoyi.system.mapper.SysUserMapper;

@Service
public class LeadAssignmentService
{
    private final BizLeadMapper mapper;
    private final LeadAccessPolicy access;
    private final BusinessActorProvider actors;
    private final BusinessEventPublisher events;
    private final SysUserMapper users;

    @Autowired
    public LeadAssignmentService(BizLeadMapper mapper, LeadAccessPolicy access,
            BusinessActorProvider actors, BusinessEventPublisher events, SysUserMapper users)
    {
        this.mapper = mapper;
        this.access = access;
        this.actors = actors;
        this.events = events;
        this.users = users;
    }

    @Transactional
    public int assign(Long leadId, Long ownerId, String reason)
    {
        if (ownerId == null) throw error(BusinessErrorCode.VALIDATION_FAILED, "负责人不能为空");
        BizLead lead = access.requireReadable(leadId, false, true);
        requireActive(lead, "分配");
        SysUser owner = requireAssignableOwner(ownerId);
        BusinessActor actor = actors.current();
        int rows = mapper.assignLead(leadId, ownerId, actor.userName(), lead.getStatus(), lead.getRowVersion());
        changed(rows, "线索状态已变化，请刷新后重试");
        Long logId = insertLog(leadId, lead.getOwnerId(), ownerId, "assign", reason, actor.userName());
        Map<String, Object> payload = payload(actor);
        payload.put("fromOwnerId", lead.getOwnerId() == null ? "" : lead.getOwnerId());
        payload.put("assignmentId", logId);
        payload.put("ownerId", ownerId);
        payload.put("ownerDeptId", owner.getDeptId());
        publish(BusinessEventType.LEAD_ASSIGNED, lead, "LEAD_ASSIGNED:" + leadId + ":" + logId, payload);
        return rows;
    }

    private SysUser requireAssignableOwner(Long ownerId)
    {
        SysUser owner = users == null ? null : users.selectUserById(ownerId);
        if (owner == null)
            throw error(BusinessErrorCode.PRECONDITION_FAILED, "目标负责人不存在");
        if (!"0".equals(owner.getStatus()) || !"0".equals(owner.getDelFlag()))
            throw error(BusinessErrorCode.PRECONDITION_FAILED, "目标负责人已停用或删除");
        if (owner.getDeptId() == null)
            throw error(BusinessErrorCode.PRECONDITION_FAILED, "目标负责人未配置部门");
        return owner;
    }

    private Long insertLog(Long leadId, Long fromOwnerId, Long toOwnerId, String actionType,
            String reason, String createBy)
    {
        Map<String, Object> log = new HashMap<>();
        log.put("leadId", leadId); log.put("fromOwnerId", fromOwnerId); log.put("toOwnerId", toOwnerId);
        log.put("actionType", actionType); log.put("reason", reason); log.put("createBy", createBy);
        changed(mapper.insertAssignmentLog(log), "线索分配日志写入失败");
        Object id = log.get("logId");
        if (id == null) throw error(BusinessErrorCode.CONCURRENT_MODIFICATION, "线索分配日志编号生成失败");
        return Long.valueOf(String.valueOf(id));
    }

    private Map<String, Object> payload(BusinessActor actor)
    {
        Map<String, Object> value = new HashMap<>();
        value.put("schemaVersion", 1); value.put("operatorId", actor.userId());
        return value;
    }

    private void publish(BusinessEventType type, BizLead lead, String key, Map<String, Object> payload)
    {
        events.publish(new BusinessEventCommand(type, "LEAD", lead.getLeadId(), lead.getLeadNo(), key, payload));
    }

    private void requireActive(BizLead lead, String action)
    {
        if (LeadStatus.CONVERTED.code().equals(lead.getStatus()) || LeadStatus.INVALID.code().equals(lead.getStatus())
                || LeadStatus.CLOSED.code().equals(lead.getStatus()))
            throw error(BusinessErrorCode.STATE_CONFLICT, "当前线索状态不能" + action);
    }

    private void changed(int rows, String message)
    {
        if (rows <= 0) throw error(BusinessErrorCode.CONCURRENT_MODIFICATION, message);
    }

    private ServiceException error(BusinessErrorCode code, String message)
    {
        return new ServiceException(message, code.name());
    }
}
