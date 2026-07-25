package com.ruoyi.system.service.lead;

import static com.ruoyi.system.support.BusinessFixtures.actor;
import static com.ruoyi.system.support.BusinessFixtures.lead;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.business.event.BusinessEventCommand;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.lead.dto.LeadTagConfirmCommand;
import com.law.business.security.BusinessActorProvider;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.mapper.BizLeadMapper;
import com.ruoyi.system.mapper.LeadFlowMapper;
import com.ruoyi.system.service.ISysDictTypeService;

@ExtendWith(MockitoExtension.class)
class LeadTagConfirmationServiceTest
{
    @Mock private BizLeadMapper leads;
    @Mock private LeadFlowMapper facts;
    @Mock private LeadAccessPolicy access;
    @Mock private BusinessActorProvider actors;
    @Mock private ISysDictTypeService dictionaries;
    @Mock private BusinessEventPublisher events;

    @Test
    void confirmation_uses_current_actor_and_stable_relation_event_key()
    {
        LeadTagConfirmationService service = new LeadTagConfirmationService(
                leads, facts, access, actors, dictionaries, events);
        BizLead stored = lead(7L, "1", "0");
        stored.setLeadNo("L-7");
        stored.setTagConfirmStatus("PENDING");
        stored.setRowVersion(3);
        LeadTagConfirmCommand command = new LeadTagConfirmCommand();
        command.setLeadId(7L);
        command.setTagRelationId(81L);
        command.setConfirmStatus("CONFIRMED");
        SysDictData confirmed = new SysDictData();
        confirmed.setDictValue("CONFIRMED");
        when(access.requireOperable(7L)).thenReturn(stored);
        when(actors.current()).thenReturn(actor());
        when(dictionaries.selectDictDataByType("law_lead_tag_confirm_status")).thenReturn(List.of(confirmed));
        when(facts.confirmTagRelation(81L, 7L, "CONFIRMED", 8L, "alice")).thenReturn(1);
        when(leads.confirmLeadTags(7L, "1", 8L, 3, "alice")).thenReturn(1);

        service.confirm(command);

        ArgumentCaptor<BusinessEventCommand> event = ArgumentCaptor.forClass(BusinessEventCommand.class);
        verify(events).publish(event.capture());
        assertEquals("LEAD_TAG_CONFIRMED:7:81", event.getValue().getIdempotencyKey());
        verify(leads).confirmLeadTags(7L, "1", 8L, 3, "alice");
        verify(facts).confirmTagRelation(any(), any(), any(), any(), any());
    }
}
