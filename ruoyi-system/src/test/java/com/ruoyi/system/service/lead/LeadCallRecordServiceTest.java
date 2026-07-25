package com.ruoyi.system.service.lead;

import static com.ruoyi.system.support.BusinessFixtures.actor;
import static com.ruoyi.system.support.BusinessFixtures.administrator;
import static com.ruoyi.system.support.BusinessFixtures.lead;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.business.lead.dto.LeadCallRecordCommand;
import com.law.business.lead.outbound.LeadOutboundCallPort;
import com.law.business.lead.outbound.OutboundCallCallbackCommand;
import com.law.business.lead.outbound.VerifiedLeadCall;
import com.law.business.security.LeadPermissions;
import com.law.business.security.BusinessActorProvider;
import com.law.file.security.FileAccessPolicy;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.domain.BizLeadCallRecord;
import com.ruoyi.system.mapper.BizLeadMapper;
import com.ruoyi.system.mapper.LeadFlowMapper;
import com.ruoyi.system.service.ISysDictTypeService;

@ExtendWith(MockitoExtension.class)
class LeadCallRecordServiceTest
{
    @Mock private LeadFlowMapper mapper;
    @Mock private LeadAccessPolicy access;
    @Mock private BusinessActorProvider actors;
    @Mock private ISysDictTypeService dictionaries;
    @Mock private FileAccessPolicy files;
    @Mock private BizLeadMapper leads;
    @Mock private LeadPermissionPolicy permissions;
    @Mock private LeadOutboundCallPort outbound;

    @Test
    void duplicate_call_external_id_is_idempotent()
    {
        LeadCallRecordService service = service();
        BizLead stored = lead(7L, "1", "0");
        stored.setOwnerId(8L);
        when(access.requireOperable(7L)).thenReturn(stored);
        when(actors.current()).thenReturn(actor());
        SysDictData manual = new SysDictData();
        manual.setDictValue("MANUAL");
        when(dictionaries.selectDictDataByType("law_call_channel")).thenReturn(List.of(manual));
        when(mapper.insertCallRecordIfAbsent(any())).thenAnswer(invocation -> {
            BizLeadCallRecord value = invocation.getArgument(0);
            value.setCallRecordId(41L);
            return 1;
        }).thenReturn(0);
        BizLeadCallRecord persisted = new BizLeadCallRecord();
        persisted.setCallRecordId(41L);
        persisted.setLeadId(7L);
        persisted.setTodoId(21L);
        persisted.setCallChannel("MANUAL");
        persisted.setExternalCallId("external-1");
        persisted.setStartedAt(LocalDateTime.of(2026,7,25,9,0));
        persisted.setEndedAt(LocalDateTime.of(2026,7,25,9,2));
        persisted.setDurationSeconds(120);
        persisted.setCallResult("CONNECTED");
        persisted.setIdempotencyKey("LEAD_CALL:MANUAL:external-1");
        when(mapper.selectCallRecordByIdempotencyKey("LEAD_CALL:MANUAL:external-1")).thenReturn(persisted);

        LeadCallRecordService.CallRecordOutcome first = service.record(command(7L, 21L, "external-1"));
        LeadCallRecordService.CallRecordOutcome replay = service.record(command(7L, 21L, "external-1"));

        assertEquals(first.callRecordId(), replay.callRecordId());
        assertEquals(true, replay.replayed());
        verify(mapper, times(2)).insertCallRecordIfAbsent(any());
        verify(permissions,times(2)).require(LeadPermissions.CALL_RECORD_ADD);
    }

    @Test
    void ordinary_command_cannot_forge_outbound_channel()
    {
        BizLead stored=lead(7L,"1","0");stored.setOwnerId(8L);
        LeadCallRecordCommand command=command(7L,21L,"external-1");
        command.setCallChannel("OUTBOUND_SYSTEM");
        when(access.requireOperable(7L)).thenReturn(stored);
        when(actors.current()).thenReturn(actor());

        ServiceException error=assertThrows(ServiceException.class,()->service().record(command));

        assertEquals("ACCESS_DENIED",error.getBusinessCode());
        verify(mapper,org.mockito.Mockito.never()).insertCallRecordIfAbsent(any());
    }

    @Test
    void direct_record_requires_owner_even_with_data_scope_but_admin_is_allowed()
    {
        BizLead stored=lead(7L,"1","0");stored.setOwnerId(99L);
        when(access.requireOperable(7L)).thenReturn(stored);
        when(actors.current()).thenReturn(actor(),administrator());
        manualDictionary();

        assertThrows(ServiceException.class,()->service().record(command(7L,21L,"owner-denied")));
        when(mapper.insertCallRecordIfAbsent(any())).thenAnswer(invocation->{
            invocation.<BizLeadCallRecord>getArgument(0).setCallRecordId(42L);return 1;
        });
        assertEquals(42L,service().record(command(7L,21L,"admin-allowed")).callRecordId());
        verify(permissions,times(2)).require(LeadPermissions.CALL_RECORD_ADD);
    }

    @Test
    void direct_record_stops_at_permission_boundary()
    {
        doThrow(new ServiceException("denied","ACCESS_DENIED"))
                .when(permissions).require(LeadPermissions.CALL_RECORD_ADD);

        ServiceException error=assertThrows(ServiceException.class,
                ()->service().record(command(7L,21L,"permission-denied")));

        assertEquals("ACCESS_DENIED",error.getBusinessCode());
        verify(access,org.mockito.Mockito.never()).requireOperable(any());
        verify(mapper,org.mockito.Mockito.never()).insertCallRecordIfAbsent(any());
    }

    @Test
    void embedded_call_must_match_parent_todo()
    {
        BizLead stored=lead(7L,"1","0");stored.setOwnerId(8L);

        assertThrows(ServiceException.class,()->service().recordForLead(
                command(7L,999L,"cross-todo"),stored,actor(),21L));

        verify(mapper,org.mockito.Mockito.never()).insertCallRecordIfAbsent(any());
    }

    @Test
    void trusted_callback_exact_replay_succeeds_but_conflicting_hash_is_rejected()
    {
        when(outbound.providerCode()).thenReturn("trusted");
        VerifiedLeadCall verified=new VerifiedLeadCall(7L,21L,"OUTBOUND_SYSTEM","provider-1",
                LocalDateTime.of(2026,7,25,9,0,0,123_000_000),
                LocalDateTime.of(2026,7,25,9,2,0,456_000_000),120,
                "CONNECTED",null,null,"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        when(outbound.verify(any())).thenReturn(verified);
        BizLead stored=lead(7L,"1","0");stored.setDisposition("ACTIVE");
        when(leads.selectLeadById(7L)).thenReturn(stored);
        SysDictData channel=new SysDictData();channel.setDictValue("OUTBOUND_SYSTEM");
        when(dictionaries.selectDictDataByType("law_call_channel")).thenReturn(List.of(channel));
        when(mapper.insertCallRecordIfAbsent(any())).thenReturn(0);
        BizLeadCallRecord persisted=new BizLeadCallRecord();
        persisted.setCallRecordId(43L);persisted.setLeadId(7L);persisted.setTodoId(21L);
        persisted.setCallChannel("OUTBOUND_SYSTEM");persisted.setExternalCallId("provider-1");
        persisted.setStartedAt(verified.startedAt().withNano(0));
        persisted.setEndedAt(verified.endedAt().withNano(0));
        persisted.setDurationSeconds(120);persisted.setCallResult("CONNECTED");
        persisted.setProviderSummaryHash(verified.providerSummaryHash());
        persisted.setIdempotencyKey("LEAD_CALL:OUTBOUND_SYSTEM:trusted:provider-1");
        when(mapper.selectCallRecordByIdempotencyKey(
                "LEAD_CALL:OUTBOUND_SYSTEM:trusted:provider-1")).thenReturn(persisted);

        assertEquals(true,service().recordTrustedCallback(
                new OutboundCallCallbackCommand("trusted","payload","signature")).replayed());

        when(outbound.verify(any())).thenReturn(new VerifiedLeadCall(7L,21L,"OUTBOUND_SYSTEM","provider-1",
                verified.startedAt(),verified.endedAt(),120,"CONNECTED",null,null,
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
        ServiceException error=assertThrows(ServiceException.class,()->service().recordTrustedCallback(
                new OutboundCallCallbackCommand("trusted","payload-2","signature-2")));
        assertEquals("LEAD_CALL_RECORD_DUPLICATE",error.getMessage());
    }

    private LeadCallRecordService service()
    {
        return new LeadCallRecordService(mapper,leads,access,actors,dictionaries,files,permissions,
                List.of(outbound));
    }

    private void manualDictionary()
    {
        SysDictData manual=new SysDictData();manual.setDictValue("MANUAL");
        when(dictionaries.selectDictDataByType("law_call_channel")).thenReturn(List.of(manual));
    }

    static LeadCallRecordCommand command(Long leadId, Long todoId, String externalId)
    {
        LeadCallRecordCommand value = new LeadCallRecordCommand();
        value.setLeadId(leadId);
        value.setTodoId(todoId);
        value.setCallChannel("MANUAL");
        value.setExternalCallId(externalId);
        value.setStartedAt(LocalDateTime.of(2026, 7, 25, 9, 0));
        value.setEndedAt(LocalDateTime.of(2026, 7, 25, 9, 2));
        value.setDurationSeconds(120);
        value.setCallResult("CONNECTED");
        return value;
    }
}
