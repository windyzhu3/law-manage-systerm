package com.ruoyi.system.service.lead;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.business.event.BusinessEventCommand;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.event.BusinessEventType;
import com.law.business.lead.dto.LeadTagConfirmCommand;
import com.law.business.security.BusinessActor;
import com.law.business.security.BusinessActorProvider;
import com.law.business.shared.error.BusinessErrorCode;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.mapper.BizLeadMapper;
import com.ruoyi.system.mapper.LeadFlowMapper;
import com.ruoyi.system.service.ISysDictTypeService;

@Service
public class LeadTagConfirmationService
{
    private final BizLeadMapper leads;
    private final LeadFlowMapper facts;
    private final LeadAccessPolicy access;
    private final BusinessActorProvider actors;
    private final ISysDictTypeService dictionaries;
    private final BusinessEventPublisher events;

    public LeadTagConfirmationService(BizLeadMapper leads, LeadFlowMapper facts, LeadAccessPolicy access,
            BusinessActorProvider actors, ISysDictTypeService dictionaries, BusinessEventPublisher events)
    {
        this.leads = leads;
        this.facts = facts;
        this.access = access;
        this.actors = actors;
        this.dictionaries = dictionaries;
        this.events = events;
    }

    @Transactional
    public void confirm(LeadTagConfirmCommand command)
    {
        require(command != null && command.getLeadId() != null && command.getTagRelationId() != null,
                "Tag confirmation identity is incomplete");
        BizLead lead = access.requireOperable(command.getLeadId());
        BusinessActor actor = actors.current();
        String status = trim(command.getConfirmStatus());
        List<SysDictData> options = dictionaries.selectDictDataByType("law_lead_tag_confirm_status");
        require(options != null && options.stream().anyMatch(item -> status.equals(item.getDictValue())),
                "Tag confirmation status is invalid");
        require("CONFIRMED".equals(status), "Only confirmed tag relations are accepted");
        changed(facts.confirmTagRelation(command.getTagRelationId(), lead.getLeadId(), status,
                actor.userId(), actor.userName()));
        changed(leads.confirmLeadTags(lead.getLeadId(), lead.getStatus(), actor.userId(),
                lead.getRowVersion(), actor.userName()));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", 1);
        payload.put("leadId", lead.getLeadId());
        payload.put("tagRelationId", command.getTagRelationId());
        payload.put("confirmStatus", status);
        payload.put("operatorId", actor.userId());
        events.publish(new BusinessEventCommand(BusinessEventType.LEAD_TAG_CONFIRMED, "LEAD",
                lead.getLeadId(), lead.getLeadNo(), "LEAD_TAG_CONFIRMED:" + lead.getLeadId() + ":"
                        + command.getTagRelationId(), payload),actor);
    }

    private void changed(int rows)
    {
        if (rows != 1) throw new ServiceException("Tag confirmation state changed",
                BusinessErrorCode.CONCURRENT_MODIFICATION.name());
    }
    private void require(boolean condition, String message)
    {
        if (!condition) throw new ServiceException(message, BusinessErrorCode.VALIDATION_FAILED.name());
    }
    private String trim(String value) { return value == null ? null : value.trim(); }
}
