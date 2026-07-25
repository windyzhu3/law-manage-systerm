package com.ruoyi.system.service.lead;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.business.event.BusinessEventCommand;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.event.BusinessEventType;
import com.law.business.lead.dto.LeadFirstContactCommand;
import com.law.business.security.BusinessActor;
import com.law.business.security.BusinessActorProvider;
import com.law.business.shared.error.BusinessErrorCode;
import com.law.todo.schedule.TodoScheduleService;
import com.law.todo.spi.TodoOrganizationPort;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.domain.BizLeadFollowup;
import com.ruoyi.system.domain.BizLeadInvalidReview;
import com.ruoyi.system.mapper.BizLeadMapper;
import com.ruoyi.system.mapper.LeadFlowMapper;
import com.ruoyi.system.service.ISysDictTypeService;

@Service
public class LeadFirstContactService
{
    private final BizLeadMapper leads;
    private final LeadFlowMapper facts;
    private final LeadAccessPolicy access;
    private final LeadCallRecordService calls;
    private final BusinessActorProvider actors;
    private final ISysDictTypeService dictionaries;
    private final TodoOrganizationPort organization;
    private final TodoScheduleService schedules;
    private final BusinessEventPublisher events;

    public LeadFirstContactService(BizLeadMapper leads, LeadFlowMapper facts, LeadAccessPolicy access,
            LeadCallRecordService calls, BusinessActorProvider actors, ISysDictTypeService dictionaries,
            TodoOrganizationPort organization, TodoScheduleService schedules, BusinessEventPublisher events)
    {
        this.leads = leads;
        this.facts = facts;
        this.access = access;
        this.calls = calls;
        this.actors = actors;
        this.dictionaries = dictionaries;
        this.organization = organization;
        this.schedules = schedules;
        this.events = events;
    }

    @Transactional
    public FirstContactOutcome complete(LeadFirstContactCommand command)
    {
        require(command != null && command.getLeadId() != null && command.getTodoId() != null,
                "First-contact identity is incomplete");
        BizLead lead = access.requireOperable(command.getLeadId());
        BusinessActor actor = actors.current();
        requireOwner(lead, actor);
        require("ACTIVE".equals(lead.getDisposition())
                && (lead.getFirstContactStatus() == null || "PENDING".equals(lead.getFirstContactStatus())),
                BusinessErrorCode.STATE_CONFLICT, "LEAD_FIRST_CONTACT_STATE_INVALID");
        String result = trim(command.getContactResult());
        requireDict("law_first_contact_result", result);
        validateBranch(command, result);
        LeadCallRecordService.CallRecordOutcome call = calls.recordForLead(command.getCallRecord(), lead, actor);
        Long followupId = insertFollowup(command, actor, result);

        Long reviewId = null;
        Long planId = null;
        if ("SUSPECT_INVALID".equals(result)) reviewId = insertReview(command, lead, actor);
        if ("UNREACHABLE".equals(result)) planId = createRetryPlan(command);

        int rows = leads.completeFirstContact(lead.getLeadId(), lead.getStatus(), result,
                trim(command.getContactName()), trim(command.getCity()), trim(command.getLegalDemand()),
                trim(command.getVisited()), trim(command.getInvalidReasonCode()),
                "SUSPECT_INVALID".equals(result) ? "TD-001" : null, lead.getRowVersion(), actor.userName());
        changed(rows, "LEAD_FIRST_CONTACT_STATE_INVALID");

        BusinessEventType type;
        String key;
        Long businessFactId;
        Map<String, Object> payload = basePayload(lead, actor);
        if ("VALID".equals(result))
        {
            type = BusinessEventType.LEAD_FIRST_CONTACT_VALID;
            key = "LEAD_FIRST_CONTACT_VALID:" + lead.getLeadId() + ":" + followupId;
            businessFactId = followupId;
            payload.put("followupId", followupId);
            payload.put("ownerId", lead.getOwnerId());
            payload.put("contactResult", "VALID");
        }
        else if ("SUSPECT_INVALID".equals(result))
        {
            type = BusinessEventType.LEAD_SUSPECT_INVALID_MARKED;
            key = "LEAD_SUSPECT_INVALID_MARKED:" + lead.getLeadId() + ":" + reviewId;
            businessFactId = reviewId;
            payload.put("reviewId", reviewId);
            payload.put("ownerId", lead.getOwnerId());
            payload.put("reviewerId", organization.supervisor(lead.getOwnerId(), 1)
                    .orElseThrow(() -> new ServiceException("TODO_OWNER_UNRESOLVED",
                            BusinessErrorCode.PRECONDITION_FAILED.name())));
            payload.put("reasonCode", trim(command.getInvalidReasonCode()));
        }
        else
        {
            type = BusinessEventType.LEAD_FIRST_CONTACT_UNREACHABLE;
            key = "LEAD_FIRST_CONTACT_UNREACHABLE:" + lead.getLeadId() + ":" + planId;
            businessFactId = planId;
            payload.put("planId", planId);
            payload.put("ownerId", lead.getOwnerId());
            payload.put("attempts", 0);
            payload.put("nextContactAt", LocalDateTime.now().toString());
        }
        events.publish(new BusinessEventCommand(type, "LEAD", lead.getLeadId(), lead.getLeadNo(), key, payload));
        return new FirstContactOutcome(result, businessFactId, followupId, call.callRecordId(), reviewId, planId);
    }

    private Long insertFollowup(LeadFirstContactCommand command, BusinessActor actor, String result)
    {
        BizLeadFollowup followup = new BizLeadFollowup();
        followup.setLeadId(command.getLeadId());
        followup.setFollowType("phone");
        followup.setFollowResult(result);
        String notes = command.getCallRecord() == null ? null : trim(command.getCallRecord().getManualNotes());
        followup.setContent(notes == null || notes.isBlank() ? "TD-001 first contact: " + result : notes);
        followup.setFollowUserId(actor.userId());
        followup.setFollowUserName(actor.displayName());
        followup.setTaskStatus("1");
        followup.setCreateBy(actor.userName());
        changed(leads.insertFollowup(followup), "LEAD_FIRST_CONTACT_FACT_WRITE_FAILED");
        require(followup.getFollowupId() != null, "First-contact followup identity was not generated");
        return followup.getFollowupId();
    }

    private Long insertReview(LeadFirstContactCommand command, BizLead lead, BusinessActor actor)
    {
        String key = "LEAD_INVALID_REVIEW:" + lead.getLeadId() + ":" + command.getTodoId();
        BizLeadInvalidReview review = new BizLeadInvalidReview();
        review.setLeadId(lead.getLeadId());
        review.setReasonCode(trim(command.getInvalidReasonCode()));
        review.setSalesExplanation(trim(command.getSalesExplanation()));
        review.setSubmittedBy(actor.userId());
        review.setTodoId(command.getTodoId());
        review.setIdempotencyKey(key);
        review.setCreateBy(actor.userName());
        int inserted = facts.insertInvalidReviewIfAbsent(review);
        if (inserted == 0) review = facts.selectInvalidReviewByIdempotencyKey(key);
        require(review != null && review.getReviewId() != null, "Invalid-review identity was not generated");
        return review.getReviewId();
    }

    private Long createRetryPlan(LeadFirstContactCommand command)
    {
        require(command.getRetryTemplateVersionId() != null && command.getRetryTemplateVersionId() > 0,
                "Retry template version is required");
        return schedules.createPlan(new TodoScheduleService.CreateSchedulePlanCommand(
                command.getTodoId(), command.getRetryTemplateVersionId(), "LEAD", command.getLeadId(),
                LocalDateTime.now(), command.getTimezone(), command.getRetryRuleVersionId()));
    }

    private void validateBranch(LeadFirstContactCommand command, String result)
    {
        if ("VALID".equals(result))
        {
            requireText(command.getContactName(), "Contact name is required");
            requireText(command.getCity(), "City is required");
            requireText(command.getLegalDemand(), "Legal demand is required");
            require("0".equals(command.getVisited()) || "1".equals(command.getVisited()),
                    "Visited must be controlled");
        }
        else if ("SUSPECT_INVALID".equals(result))
        {
            requireDict("law_lead_invalid_reason", trim(command.getInvalidReasonCode()));
        }
        else require("UNREACHABLE".equals(result), "Unknown first-contact result");
    }

    private Map<String, Object> basePayload(BizLead lead, BusinessActor actor)
    {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", 1);
        payload.put("leadId", lead.getLeadId());
        payload.put("operatorId", actor.userId());
        return payload;
    }

    private void requireOwner(BizLead lead, BusinessActor actor)
    {
        require(actor.administrator() || actor.userId().equals(lead.getOwnerId()),
                BusinessErrorCode.ACCESS_DENIED, "Only the current owner may complete first contact");
    }

    private void requireDict(String type, String value)
    {
        List<SysDictData> options = dictionaries.selectDictDataByType(type);
        require(options != null && options.stream().anyMatch(item -> value != null && value.equals(item.getDictValue())),
                "Controlled dictionary value is invalid: " + type);
    }

    private void requireText(String value, String message) { require(value != null && !value.trim().isEmpty(), message); }
    private void require(boolean condition, String message) { require(condition, BusinessErrorCode.VALIDATION_FAILED, message); }
    private void require(boolean condition, BusinessErrorCode code, String message)
    {
        if (!condition) throw new ServiceException(message, code.name());
    }
    private void changed(int rows, String message)
    {
        require(rows == 1, BusinessErrorCode.CONCURRENT_MODIFICATION, message);
    }
    private String trim(String value) { return value == null ? null : value.trim(); }

    public record FirstContactOutcome(String result, Long businessFactId, Long followupId, Long callRecordId,
            Long reviewId, Long schedulePlanId) { }
}
