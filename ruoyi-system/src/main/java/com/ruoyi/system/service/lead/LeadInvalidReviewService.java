package com.ruoyi.system.service.lead;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.business.event.BusinessEventCommand;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.event.BusinessEventType;
import com.law.business.lead.dto.LeadInvalidReviewCommand;
import com.law.business.security.BusinessActor;
import com.law.business.security.BusinessActorProvider;
import com.law.business.shared.error.BusinessErrorCode;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.domain.BizLeadInvalidReview;
import com.ruoyi.system.domain.BizLeadQualityRecord;
import com.ruoyi.system.mapper.BizLeadMapper;
import com.ruoyi.system.mapper.LeadFlowMapper;
import com.ruoyi.system.service.ISysDictTypeService;

@Service
public class LeadInvalidReviewService
{
    private static final BusinessActor SYSTEM = new BusinessActor(0L, "system", "system", null, false);
    private final BizLeadMapper leads;
    private final LeadFlowMapper facts;
    private final LeadAccessPolicy access;
    private final BusinessActorProvider actors;
    private final ISysDictTypeService dictionaries;
    private final LeadDeadPoolService deadPool;
    private final BusinessEventPublisher events;

    public LeadInvalidReviewService(BizLeadMapper leads, LeadFlowMapper facts, LeadAccessPolicy access,
            BusinessActorProvider actors, ISysDictTypeService dictionaries, LeadDeadPoolService deadPool,
            BusinessEventPublisher events)
    {
        this.leads = leads;
        this.facts = facts;
        this.access = access;
        this.actors = actors;
        this.dictionaries = dictionaries;
        this.deadPool = deadPool;
        this.events = events;
    }

    @Transactional
    public InvalidReviewOutcome review(LeadInvalidReviewCommand command)
    {
        require(command != null && command.getLeadId() != null && command.getReviewId() != null,
                "Review identity is incomplete");
        BusinessActor actor = command.isSystemDefault() ? SYSTEM : actors.current();
        BizLead lead = command.isSystemDefault() ? leads.selectLeadById(command.getLeadId())
                : access.requireOperable(command.getLeadId());
        BizLeadInvalidReview review = facts.selectInvalidReviewById(command.getReviewId());
        require(lead != null && review != null && lead.getLeadId().equals(review.getLeadId()),
                "Invalid-review fact not found");
        String result = trim(command.getReviewResult());
        requireDict("law_lead_invalid_review_result", result);
        if ("COMPLETED".equals(review.getStatus()))
        {
            require(result.equals(review.getReviewResult()), "Review occurrence already has another result");
            return replayOutcome(lead, review);
        }
        require("ACTIVE".equals(lead.getDisposition()) && "PENDING".equals(lead.getInvalidReviewStatus()),
                "LEAD_INVALID_REVIEW_STATE_INVALID");
        require("PENDING".equals(review.getStatus()), "LEAD_INVALID_REVIEW_STATE_INVALID");

        changed(leads.markInvalidReviewed(lead.getLeadId(), "PENDING", result, lead.getRowVersion(),
                actor.userName()));
        changed(facts.completeInvalidReview(review.getReviewId(), result, trim(command.getReviewComment()),
                actor.userId(), command.isSystemDefault() ? "Y" : "N", review.getRowVersion(), actor.userName()));

        Long qualityId = null;
        Long deadPoolLogId = null;
        if ("TRUE_INVALID".equals(result))
        {
            LeadDeadPoolService.DeadPoolOutcome moved = deadPool.enterConfirmedInvalid(lead,
                    lead.getRowVersion() + 1, command.getTodoId(), review.getReviewId(), review.getReasonCode(),
                    trim(command.getReviewComment()), actor);
            deadPoolLogId = moved.deadPoolLogId();
        }
        else if ("MISJUDGED_VALID".equals(result))
        {
            require(lead.getOwnerId() != null, "Misjudged lead must retain its sales owner");
            qualityId = insertQuality(lead, review, command, actor);
            changed(leads.reopenFirstContact(lead.getLeadId(), lead.getRowVersion() + 1, actor.userName()));
        }
        else require(false, "Unknown review result");

        publish(lead, review, result, qualityId, actor);
        return new InvalidReviewOutcome(result, review.getReviewId(), qualityId, deadPoolLogId, false);
    }

    private Long insertQuality(BizLead lead, BizLeadInvalidReview review, LeadInvalidReviewCommand command,
            BusinessActor actor)
    {
        String key = "LEAD_QUALITY:INVALID_MISJUDGMENT:" + review.getReviewId();
        BizLeadQualityRecord quality = new BizLeadQualityRecord();
        quality.setLeadId(lead.getLeadId());
        quality.setSalesUserId(lead.getOwnerId());
        quality.setReasonCode(review.getReasonCode());
        quality.setReviewerId(actor.userId());
        quality.setSourceTodoId(command.getTodoId());
        quality.setSourceReviewId(review.getReviewId());
        quality.setQualityType("INVALID_MISJUDGMENT");
        quality.setIdempotencyKey(key);
        quality.setCreateBy(actor.userName());
        int inserted = facts.insertQualityRecordIfAbsent(quality);
        if (inserted == 0) quality = facts.selectQualityRecordByIdempotencyKey(key);
        require(quality != null && quality.getQualityRecordId() != null,
                "Quality evidence identity was not generated");
        return quality.getQualityRecordId();
    }

    private void publish(BizLead lead, BizLeadInvalidReview review, String result, Long qualityId,
            BusinessActor actor)
    {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", 1);
        payload.put("leadId", lead.getLeadId());
        payload.put("reviewId", review.getReviewId());
        payload.put("reviewerId", actor.userId());
        payload.put("reviewResult", result);
        payload.put("operatorId", actor.userId());
        BusinessEventType type;
        String key;
        if ("TRUE_INVALID".equals(result))
        {
            type = BusinessEventType.LEAD_INVALID_REVIEW_CONFIRMED;
            key = "LEAD_INVALID_REVIEW_CONFIRMED:" + lead.getLeadId() + ":" + review.getReviewId();
        }
        else
        {
            type = BusinessEventType.LEAD_INVALID_REVIEW_MISJUDGED;
            key = "LEAD_INVALID_REVIEW_MISJUDGED:" + lead.getLeadId() + ":" + review.getReviewId() + ":"
                    + qualityId;
            payload.put("ownerId", lead.getOwnerId());
        }
        events.publish(new BusinessEventCommand(type, "LEAD", lead.getLeadId(), lead.getLeadNo(), key, payload));
    }

    private InvalidReviewOutcome replayOutcome(BizLead lead, BizLeadInvalidReview review)
    {
        Long qualityId = null;
        Long deadPoolLogId = null;
        if ("MISJUDGED_VALID".equals(review.getReviewResult()))
        {
            BizLeadQualityRecord quality = facts.selectQualityRecordByIdempotencyKey(
                    "LEAD_QUALITY:INVALID_MISJUDGMENT:" + review.getReviewId());
            qualityId = quality == null ? null : quality.getQualityRecordId();
        }
        else if ("TRUE_INVALID".equals(review.getReviewResult()))
        {
            var log = facts.selectDeadPoolLogByIdempotencyKey(
                    "LEAD_DEAD_POOL:" + lead.getLeadId() + ":" + review.getReviewId());
            deadPoolLogId = log == null ? null : log.getDeadPoolLogId();
        }
        return new InvalidReviewOutcome(review.getReviewResult(), review.getReviewId(), qualityId,
                deadPoolLogId, true);
    }

    private void requireDict(String type, String value)
    {
        List<SysDictData> options = dictionaries.selectDictDataByType(type);
        require(options != null && options.stream().anyMatch(item -> value != null && value.equals(item.getDictValue())),
                "Controlled dictionary value is invalid: " + type);
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
    private String trim(String value) { return value == null ? null : value.trim(); }

    public record InvalidReviewOutcome(String result, Long reviewId, Long qualityRecordId,
            Long deadPoolLogId, boolean replayed) { }
}
