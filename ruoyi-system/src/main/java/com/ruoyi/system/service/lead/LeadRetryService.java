package com.ruoyi.system.service.lead;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.business.event.BusinessEventCommand;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.event.BusinessEventType;
import com.law.business.lead.dto.LeadRetryCompleteCommand;
import com.law.business.security.BusinessActor;
import com.law.business.security.BusinessActorProvider;
import com.law.business.shared.error.BusinessErrorCode;
import com.law.todo.schedule.TodoScheduleService;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.domain.BizLeadRetryRecord;
import com.ruoyi.system.mapper.BizLeadMapper;
import com.ruoyi.system.mapper.LeadFlowMapper;
import com.ruoyi.system.service.ISysDictTypeService;

@Service
public class LeadRetryService
{
    private final BizLeadMapper leads;
    private final LeadFlowMapper facts;
    private final LeadAccessPolicy access;
    private final LeadCallRecordService calls;
    private final BusinessActorProvider actors;
    private final ISysDictTypeService dictionaries;
    private final TodoScheduleService schedules;
    private final LeadPoolService pool;
    private final BusinessEventPublisher events;

    public LeadRetryService(BizLeadMapper leads, LeadFlowMapper facts, LeadAccessPolicy access,
            LeadCallRecordService calls, BusinessActorProvider actors, ISysDictTypeService dictionaries,
            TodoScheduleService schedules, LeadPoolService pool, BusinessEventPublisher events)
    {
        this.leads = leads;
        this.facts = facts;
        this.access = access;
        this.calls = calls;
        this.actors = actors;
        this.dictionaries = dictionaries;
        this.schedules = schedules;
        this.pool = pool;
        this.events = events;
    }

    @Transactional
    public RetryOutcome completeWindow(LeadRetryCompleteCommand command)
    {
        validateIdentity(command);
        BizLead lead = access.requireReadable(command.getLeadId(), false, true);
        BusinessActor actor = actors.current();
        TodoScheduleService.ScheduleOccurrenceContext context =
                schedules.lockOccurrenceContext(command.getOccurrenceId());
        validateContext(command, context);
        String occurrenceKey = "LEAD_RETRY_OCCURRENCE:" + command.getOccurrenceId();
        BizLeadRetryRecord prior = facts.selectRetryRecordByIdempotencyKey(occurrenceKey);
        if (prior != null) return replay(command, context, prior);
        require(actor.administrator() || actor.userId().equals(lead.getOwnerId()),
                BusinessErrorCode.ACCESS_DENIED, "Only the lead owner may complete a retry window");

        require("ACTIVE".equals(lead.getDisposition()) && "UNREACHABLE".equals(lead.getFirstContactResult())
                && context.windowCode().equals(lead.getRetryStage()),
                BusinessErrorCode.STATE_CONFLICT, "LEAD_RETRY_STATE_INVALID");
        String result = trim(command.getResult());
        requireDict("law_retry_result", result);
        validateBranch(command, result);
        LeadCallRecordService.CallRecordOutcome call =
                calls.recordForLead(command.getCallRecord(), lead, actor, context.todoId());
        LocalDateTime completedAt = LocalDateTime.now();
        TodoScheduleService.ScheduleCompletion schedule = schedules.completeOccurrence(
                context, result, completedAt);
        require(context.planId().equals(schedule.planId())
                && context.windowCode().equals(schedule.windowCode())
                && context.businessId().equals(schedule.businessId())
                && context.todoId().equals(schedule.todoId()),
                BusinessErrorCode.STATE_CONFLICT, "Retry command does not match its schedule occurrence");

        String nextStage = "EXHAUSTED".equals(result) ? "EXHAUSTED" : schedule.nextWindowCode();
        BizLeadRetryRecord retry = insertRetry(command, context, call.callRecordId(), actor,
                occurrenceKey, nextStage);
        if ("NEXT_WINDOW".equals(result))
        {
            Date nextRetry = Date.from(schedule.nextStartAt().atZone(ZoneId.of(context.timezone())).toInstant());
            changed(leads.advanceRetryStage(lead.getLeadId(), lead.getRetryStage(), nextStage,
                    context.occurrenceNo(), nextRetry, lead.getRowVersion(), actor.userName()));
        }
        else if ("CONNECTED".equals(result))
        {
            changed(leads.completeRetryConnected(lead.getLeadId(), lead.getRetryStage(),
                    trim(command.getContactName()), trim(command.getCity()), trim(command.getLegalDemand()),
                    trim(command.getVisited()), lead.getRowVersion(), actor.userName()));
            publishConnected(lead, retry, command, actor);
        }
        else
        {
            schedules.cancelPlan(context.planId(), "RETRY_EXHAUSTED", completedAt);
            changed(leads.advanceRetryStage(lead.getLeadId(), lead.getRetryStage(), "EXHAUSTED",
                    context.occurrenceNo(), null, lead.getRowVersion(), actor.userName()));
            pool.moveToPoolBySystem(lead, lead.getRowVersion() + 1, "RETRY_EXHAUSTED");
            publishExhausted(lead, context, actor);
        }
        return new RetryOutcome(result, retry.getRetryRecordId(), nextStage, false);
    }

    private BizLeadRetryRecord insertRetry(LeadRetryCompleteCommand command,
            TodoScheduleService.ScheduleOccurrenceContext context, Long callRecordId,
            BusinessActor actor, String key, String nextWindowCode)
    {
        BizLeadRetryRecord retry = new BizLeadRetryRecord();
        retry.setLeadId(command.getLeadId());
        retry.setPlanId(context.planId());
        retry.setWindowCode(context.windowCode());
        retry.setAttemptNo(context.occurrenceNo());
        retry.setContactResult(trim(command.getResult()));
        retry.setNextWindowCode(nextWindowCode);
        retry.setTodoId(context.todoId());
        retry.setCallRecordId(callRecordId);
        retry.setIdempotencyKey(key);
        retry.setCreateBy(actor.userName());
        int inserted = facts.insertRetryRecordIfAbsent(retry);
        if (inserted == 0) retry = facts.selectRetryRecordByIdempotencyKey(key);
        require(retry != null && retry.getRetryRecordId() != null,
                BusinessErrorCode.CONCURRENT_MODIFICATION, "Retry fact identity was not generated");
        return retry;
    }

    private RetryOutcome replay(LeadRetryCompleteCommand command,
            TodoScheduleService.ScheduleOccurrenceContext context, BizLeadRetryRecord prior)
    {
        require(command.getLeadId().equals(prior.getLeadId()) && context.planId().equals(prior.getPlanId())
                && context.windowCode().equals(prior.getWindowCode())
                && Integer.valueOf(context.occurrenceNo()).equals(prior.getAttemptNo())
                && context.todoId().equals(prior.getTodoId())
                && trim(command.getResult()).equals(prior.getContactResult()),
                BusinessErrorCode.DUPLICATE_OPERATION, "Retry occurrence belongs to another outcome");
        return new RetryOutcome(prior.getContactResult(), prior.getRetryRecordId(), prior.getNextWindowCode(), true);
    }

    private void publishConnected(BizLead lead, BizLeadRetryRecord retry, LeadRetryCompleteCommand command,
            BusinessActor actor)
    {
        Map<String, Object> payload = base(lead, actor);
        payload.put("planId", retry.getPlanId());
        payload.put("retryRecordId", retry.getRetryRecordId());
        payload.put("ownerId", lead.getOwnerId());
        events.publish(new BusinessEventCommand(BusinessEventType.LEAD_RETRY_CONNECTED, "LEAD",
                lead.getLeadId(), lead.getLeadNo(), "LEAD_RETRY_CONNECTED:" + retry.getPlanId() + ":"
                        + retry.getRetryRecordId(), payload),actor);
    }

    private void publishExhausted(BizLead lead, TodoScheduleService.ScheduleOccurrenceContext context,
            BusinessActor actor)
    {
        Map<String, Object> payload = base(lead, actor);
        payload.put("planId", context.planId());
        events.publish(new BusinessEventCommand(BusinessEventType.LEAD_RETRY_EXHAUSTED, "LEAD",
                lead.getLeadId(), lead.getLeadNo(), "LEAD_RETRY_EXHAUSTED:" + context.planId(), payload),actor);
    }

    private Map<String, Object> base(BizLead lead, BusinessActor actor)
    {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", 1);
        payload.put("leadId", lead.getLeadId());
        payload.put("operatorId", actor.userId());
        return payload;
    }

    private void validateIdentity(LeadRetryCompleteCommand command)
    {
        require(command != null && command.getLeadId() != null && command.getTodoId() != null
                && command.getOccurrenceId() != null && command.getOccurrenceId() > 0
                && command.getResult() != null && !command.getResult().isBlank(),
                BusinessErrorCode.VALIDATION_FAILED, "Retry occurrence identity is incomplete");
    }

    private void validateContext(LeadRetryCompleteCommand command,
            TodoScheduleService.ScheduleOccurrenceContext context)
    {
        require(context != null && "LEAD".equals(context.businessType())
                && command.getLeadId().equals(context.businessId())
                && command.getTodoId().equals(context.todoId())
                && command.getOccurrenceId().equals(context.occurrenceId())
                && context.planId() != null && context.windowId() != null
                && context.windowCode() != null && !context.windowCode().isBlank()
                && context.occurrenceNo() > 0 && context.occurrenceNo() <= context.maxAttempts()
                && context.timezone() != null && context.templateVersionId() != null
                && context.ruleVersionId() != null
                && ("MATERIALIZED".equals(context.status()) || "COMPLETED".equals(context.status())),
                BusinessErrorCode.STATE_CONFLICT, "LEAD_RETRY_STATE_INVALID");
    }

    private void validateBranch(LeadRetryCompleteCommand command, String result)
    {
        if ("NEXT_WINDOW".equals(result))
        {
            return;
        }
        else if ("CONNECTED".equals(result))
        {
            requireText(command.getContactName(), "Contact name is required");
            requireText(command.getCity(), "City is required");
            requireText(command.getLegalDemand(), "Legal demand is required");
            require("0".equals(command.getVisited()) || "1".equals(command.getVisited()),
                    BusinessErrorCode.VALIDATION_FAILED, "Visited must be controlled");
        }
        else require("EXHAUSTED".equals(result), BusinessErrorCode.VALIDATION_FAILED, "Unknown retry result");
    }

    private void requireDict(String type, String value)
    {
        List<SysDictData> options = dictionaries.selectDictDataByType(type);
        require(options != null && options.stream().anyMatch(item -> value.equals(item.getDictValue())),
                BusinessErrorCode.VALIDATION_FAILED, "Controlled dictionary value is invalid: " + type);
    }
    private void requireText(String value, String message)
    {
        require(value != null && !value.trim().isEmpty(), BusinessErrorCode.VALIDATION_FAILED, message);
    }
    private void changed(int rows)
    {
        require(rows == 1, BusinessErrorCode.CONCURRENT_MODIFICATION, "LEAD_RETRY_STATE_INVALID");
    }
    private void require(boolean condition, BusinessErrorCode code, String message)
    {
        if (!condition) throw new ServiceException(message, code.name());
    }
    private String trim(String value) { return value == null ? null : value.trim(); }

    public record RetryOutcome(String result, Long retryRecordId, String nextStage, boolean replayed) { }
}
