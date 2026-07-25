package com.ruoyi.system.service.lead;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.business.event.BusinessEventCommand;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.event.BusinessEventType;
import com.law.business.security.BusinessActor;
import com.law.business.security.BusinessActorProvider;
import com.law.business.shared.error.BusinessErrorCode;
import com.law.todo.schedule.TodoScheduleService;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.domain.BizLeadDeadPoolLog;
import com.ruoyi.system.mapper.BizLeadMapper;
import com.ruoyi.system.mapper.LeadFlowMapper;

@Service
public class LeadDeadPoolService
{
    private final BizLeadMapper leads;
    private final LeadFlowMapper facts;
    private final LeadAccessPolicy access;
    private final BusinessActorProvider actors;
    private final TodoScheduleService schedules;
    private final BusinessEventPublisher events;

    public LeadDeadPoolService(BizLeadMapper leads, LeadFlowMapper facts, LeadAccessPolicy access,
            BusinessActorProvider actors, TodoScheduleService schedules, BusinessEventPublisher events)
    {
        this.leads = leads;
        this.facts = facts;
        this.access = access;
        this.actors = actors;
        this.schedules = schedules;
        this.events = events;
    }

    @Transactional
    public DeadPoolOutcome enterConfirmedInvalid(Long leadId, Long sourceTodoId, Long reviewId,
            String reasonCode, String detail)
    {
        BizLead lead = access.requireOperable(leadId);
        return enterConfirmedInvalid(lead, lead.getRowVersion(), sourceTodoId, reviewId, reasonCode, detail,
                actors.current());
    }

    DeadPoolOutcome enterConfirmedInvalid(BizLead lead, int expectedRowVersion, Long sourceTodoId, Long reviewId,
            String reasonCode, String detail, BusinessActor actor)
    {
        require(lead != null && "ACTIVE".equals(lead.getDisposition()), "LEAD_INVALID_REVIEW_STATE_INVALID");
        changed(leads.moveToDeadPool(lead.getLeadId(), reasonCode, expectedRowVersion, actor.userName()));
        cancelRemaining(lead.getLeadId(), "TRUE_INVALID");
        String key = "LEAD_DEAD_POOL:" + lead.getLeadId() + ":" + reviewId;
        BizLeadDeadPoolLog log = new BizLeadDeadPoolLog();
        log.setLeadId(lead.getLeadId());
        log.setActionType("ENTER");
        log.setReasonCode(reasonCode);
        log.setReasonDetail(detail);
        log.setFromDisposition("ACTIVE");
        log.setToDisposition("DEAD_POOL");
        log.setOperatorId(actor.userId());
        log.setSourceTodoId(sourceTodoId);
        log.setIdempotencyKey(key);
        log.setCreateBy(actor.userName());
        int inserted = facts.insertDeadPoolLogIfAbsent(log);
        if (inserted == 0) log = facts.selectDeadPoolLogByIdempotencyKey(key);
        require(log != null && log.getDeadPoolLogId() != null, "Dead-Pool audit identity was not generated");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", 1);
        payload.put("leadId", lead.getLeadId());
        payload.put("deadPoolLogId", log.getDeadPoolLogId());
        payload.put("reasonCode", reasonCode);
        payload.put("operatorId", actor.userId());
        events.publish(new BusinessEventCommand(BusinessEventType.LEAD_MOVED_TO_DEAD_POOL, "LEAD",
                lead.getLeadId(), lead.getLeadNo(), "LEAD_MOVED_TO_DEAD_POOL:" + lead.getLeadId() + ":"
                        + log.getDeadPoolLogId(), payload));
        return new DeadPoolOutcome(log.getDeadPoolLogId(), inserted == 0);
    }

    @Transactional
    public void cancelRemaining(Long leadId, String reason)
    {
        Long planId = facts.selectActiveRetryPlanId(leadId);
        if (planId != null) schedules.cancelPlan(planId, reason, LocalDateTime.now());
    }

    private void changed(int rows)
    {
        if (rows != 1) throw new ServiceException("LEAD_INVALID_REVIEW_STATE_INVALID",
                BusinessErrorCode.CONCURRENT_MODIFICATION.name());
    }
    private void require(boolean condition, String message)
    {
        if (!condition) throw new ServiceException(message, BusinessErrorCode.STATE_CONFLICT.name());
    }

    public record DeadPoolOutcome(Long deadPoolLogId, boolean replayed) { }
}
