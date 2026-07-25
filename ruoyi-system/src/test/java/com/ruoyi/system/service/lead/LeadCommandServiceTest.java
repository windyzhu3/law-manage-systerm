package com.ruoyi.system.service.lead;

import static com.ruoyi.system.support.BusinessFixtures.actor;
import static com.ruoyi.system.support.BusinessFixtures.lead;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.security.BusinessActorProvider;
import com.law.business.shared.status.LeadStatus;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.domain.BizLeadSetting;
import com.ruoyi.system.mapper.BizLeadMapper;
import com.ruoyi.system.service.ISysDictTypeService;

@ExtendWith(MockitoExtension.class)
class LeadCommandServiceTest
{
    @Mock private BizLeadMapper mapper;
    @Mock private ISysDictTypeService dictionaries;
    @Mock private BusinessEventPublisher events;
    @Mock private BusinessActorProvider actors;
    @Mock private LeadAccessPolicy access;
    private LeadCommandService service;

    @BeforeEach
    void setUp()
    {
        service = new LeadCommandService(mapper, dictionaries, events, actors, access);
    }

    @Test
    void createPublishesStableEventKey()
    {
        BizLead input = validLead();
        when(actors.current()).thenReturn(actor());
        when(mapper.selectSettingList(argThat(s -> "source".equals(s.getSettingType()) && "web".equals(s.getSettingCode()))))
                .thenReturn(List.of(setting("web")));
        when(dictionaries.selectDictDataByType("law_lead_priority")).thenReturn(List.of(dict("1")));
        when(mapper.insertLead(input)).thenAnswer(invocation -> { input.setLeadId(11L); return 1; });
        when(mapper.insertLeadSourceTagRelationIfAbsent(11L, "web", "alice")).thenReturn(1);

        assertEquals(1, service.create(input));

        InOrder persisted = inOrder(mapper, events);
        persisted.verify(mapper).insertLead(input);
        persisted.verify(mapper).insertSourceBusinessTagIfAbsent("web", "alice");
        persisted.verify(mapper).insertLeadSourceTagRelationIfAbsent(11L, "web", "alice");
        persisted.verify(events).publish(argThat(event -> "LEAD_CREATED:11".equals(event.getIdempotencyKey())));
        verify(events).publish(argThat(event -> "LEAD_CREATED:11".equals(event.getIdempotencyKey())
                && Integer.valueOf(1).equals(event.getPayload().get("schemaVersion"))
                && Long.valueOf(8L).equals(event.getPayload().get("operatorId"))));
    }

    @Test
    void convertedLeadCannotBeDeleted()
    {
        BizLead converted = lead(11L, LeadStatus.CONVERTED.code(), "0");
        when(access.requireOperable(11L)).thenReturn(converted);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.softDelete(new Long[] { 11L }));

        assertEquals("STATE_CONFLICT", exception.getBusinessCode());
        verify(mapper, never()).softDeleteLead(new Long[] { 11L }, "alice");
    }

    @Test
    void craftedUpdateCannotChangeImmutableSource()
    {
        BizLead persisted=validLead();persisted.setLeadId(11L);persisted.setSourceCode("web");
        BizLead crafted=validLead();crafted.setLeadId(11L);crafted.setSourceCode("phone");
        when(access.requireOperable(11L)).thenReturn(persisted);

        ServiceException error=assertThrows(ServiceException.class,()->service.update(crafted));

        assertEquals("STATE_CONFLICT",error.getBusinessCode());
        verify(mapper,never()).updateLead(crafted);
    }

    @Test
    void updateWithoutSourcePreservesPersistedImmutableSource()
    {
        BizLead persisted = validLead();
        persisted.setLeadId(11L);
        BizLead update = validLead();
        update.setLeadId(11L);
        update.setSourceCode(null);
        when(access.requireOperable(11L)).thenReturn(persisted);
        when(mapper.selectSettingList(argThat(s -> "source".equals(s.getSettingType())
                && "web".equals(s.getSettingCode())))).thenReturn(List.of(setting("web")));
        when(dictionaries.selectDictDataByType("law_lead_priority")).thenReturn(List.of(dict("1")));
        when(actors.current()).thenReturn(actor());
        when(mapper.updateLead(update)).thenReturn(1);

        assertEquals(1, service.update(update));

        assertEquals("web", update.getSourceCode());
        verify(mapper).updateLead(update);
    }

    @Test
    void purgeDeletesEveryLeadOwnedFactBeforeTheLead()
    {
        Long[] leadIds = { 11L, 12L };
        BizLead first = lead(11L, LeadStatus.INVALID.code(), "2");
        BizLead second = lead(12L, LeadStatus.CLOSED.code(), "2");
        when(access.requireReadable(11L, true, false)).thenReturn(first);
        when(access.requireReadable(12L, true, false)).thenReturn(second);
        when(mapper.purgeLead(leadIds)).thenReturn(2);

        assertEquals(2, service.purge(leadIds));

        InOrder order = inOrder(mapper);
        order.verify(mapper).purgeLeadCallRecords(leadIds);
        order.verify(mapper).purgeLeadInvalidReviews(leadIds);
        order.verify(mapper).purgeLeadRetryRecords(leadIds);
        order.verify(mapper).purgeLeadQualityRecords(leadIds);
        order.verify(mapper).purgeLeadDeadPoolLogs(leadIds);
        order.verify(mapper).purgeLeadTagRelations(leadIds);
        order.verify(mapper).purgeLeadFollowups(leadIds);
        order.verify(mapper).purgeLeadAssignmentLogs(leadIds);
        order.verify(mapper).purgeLead(leadIds);
        order.verifyNoMoreInteractions();
    }

    private BizLead validLead()
    {
        BizLead value = new BizLead();
        value.setLeadName("张三咨询");
        value.setContactName("张三");
        value.setMobile("13800000000");
        value.setSourceCode("web");
        value.setPriority("1");
        value.setLegalDemand("合同纠纷");
        return value;
    }

    private BizLeadSetting setting(String code)
    {
        BizLeadSetting value = new BizLeadSetting();
        value.setSettingCode(code);
        value.setStatus("0");
        return value;
    }

    private SysDictData dict(String value)
    {
        SysDictData item = new SysDictData();
        item.setDictValue(value);
        return item;
    }
}
