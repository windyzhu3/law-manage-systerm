package com.ruoyi.system.service.lead;

import static com.ruoyi.system.support.BusinessFixtures.actor;
import static com.ruoyi.system.support.BusinessFixtures.lead;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.business.lead.dto.LeadCallRecordCommand;
import com.law.business.security.BusinessActorProvider;
import com.law.file.security.FileAccessPolicy;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.domain.BizLeadCallRecord;
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

    @Test
    void duplicate_call_external_id_is_idempotent()
    {
        LeadCallRecordService service = new LeadCallRecordService(mapper, access, actors, dictionaries, files);
        BizLead stored = lead(7L, "1", "0");
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
        persisted.setCallChannel("MANUAL");
        persisted.setExternalCallId("external-1");
        when(mapper.selectCallRecordByIdempotencyKey("LEAD_CALL:MANUAL:external-1")).thenReturn(persisted);

        LeadCallRecordService.CallRecordOutcome first = service.record(command(7L, 21L, "external-1"));
        LeadCallRecordService.CallRecordOutcome replay = service.record(command(7L, 21L, "external-1"));

        assertEquals(first.callRecordId(), replay.callRecordId());
        assertEquals(true, replay.replayed());
        verify(mapper, times(2)).insertCallRecordIfAbsent(any());
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
