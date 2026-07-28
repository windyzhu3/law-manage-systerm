package com.ruoyi.system.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.business.lead.dto.LeadFirstContactCommand;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.application.CompletionContext;
import com.law.todo.spi.TodoCompletionHandler.CompletionResult;
import com.ruoyi.system.service.event.LeadFirstContactHandler;
import com.ruoyi.system.service.lead.LeadFirstContactService;

@ExtendWith(MockitoExtension.class)
class LeadFirstContactHandlerTest
{
    @Mock LeadFirstContactService firstContacts;

    @Test
    void supports_governed_and_legacy_codes_without_title_coupling()
    {
        LeadFirstContactHandler handler = new LeadFirstContactHandler(firstContacts);

        assertTrue(handler.supports(todo("TD-001", "任意标题")));
        assertTrue(handler.supports(todo("LEAD_FIRST_CONTACT", "历史首联")));
        assertEquals("TD-001_COMPLETE", handler.catalogCode());
    }

    @Test
    void maps_todo_identity_and_manual_call_payload_to_typed_command()
    {
        LeadFirstContactHandler handler = new LeadFirstContactHandler(firstContacts);
        Map<String,Object> call = Map.of(
                "callChannel", "MANUAL",
                "businessOccurrenceKey", "td001-call-1",
                "startedAt", "2026-07-26T09:00:00",
                "endedAt", "2026-07-26T09:02:00",
                "durationSeconds", 120,
                "callResult", "CONNECTED",
                "manualNotes", "客户确认有需求",
                "providerSummaryHash", "forged-client-summary");
        Map<String,Object> payload = Map.of(
                "contactResult", "VALID",
                "contactName", "张三",
                "city", "上海",
                "legalDemand", "合同争议",
                "visited", "0",
                "callRecord", call);

        handler.complete(todo("TD-001", "不依赖标题"), payload, 8L, "alice");

        ArgumentCaptor<LeadFirstContactCommand> captured =
                ArgumentCaptor.forClass(LeadFirstContactCommand.class);
        verify(firstContacts).complete(captured.capture());
        LeadFirstContactCommand command = captured.getValue();
        assertEquals(7L, command.getLeadId());
        assertEquals(21L, command.getTodoId());
        assertEquals("VALID", command.getContactResult());
        assertEquals("张三", command.getContactName());
        assertEquals("合同争议", command.getLegalDemand());
        assertEquals("MANUAL", command.getCallRecord().getCallChannel());
        assertEquals(7L, command.getCallRecord().getLeadId());
        assertEquals(21L, command.getCallRecord().getTodoId());
        assertEquals(LocalDateTime.of(2026,7,26,9,0), command.getCallRecord().getStartedAt());
        assertNull(command.getCallRecord().getProviderSummaryHash());
    }

    @Test
    void legacy_field_aliases_are_preserved_for_existing_instances()
    {
        LeadFirstContactHandler handler = new LeadFirstContactHandler(firstContacts);
        Map<String,Object> payload = new java.util.LinkedHashMap<>();
        payload.put("contactResult", "VALID");
        payload.put("name", "李四");
        payload.put("demand", "劳动争议");
        payload.put("city", "苏州");
        payload.put("visited", "1");
        payload.put("callRecord", Map.of(
                "callChannel", "MANUAL",
                "businessOccurrenceKey", "legacy-call",
                "startedAt", "2026-07-26T10:00:00"));

        handler.complete(todo("LEAD_FIRST_CONTACT", "任意标题"), payload, 8L, "alice");

        ArgumentCaptor<LeadFirstContactCommand> captured =
                ArgumentCaptor.forClass(LeadFirstContactCommand.class);
        verify(firstContacts).complete(captured.capture());
        assertEquals("李四", captured.getValue().getContactName());
        assertEquals("劳动争议", captured.getValue().getLegalDemand());
    }

    @Test
    void existing_dynamic_form_contacted_at_becomes_stable_manual_call_evidence()
    {
        LeadFirstContactHandler handler=new LeadFirstContactHandler(firstContacts);
        Map<String,Object> payload=Map.of(
                "contactResult","VALID","contactedAt","2026-07-26T12:00:00",
                "name","赵六","city","杭州","demand","侵权争议","visited","0");

        handler.complete(todo("TD-001","任意标题"),payload,8L,"alice");

        ArgumentCaptor<LeadFirstContactCommand> captured=
                ArgumentCaptor.forClass(LeadFirstContactCommand.class);
        verify(firstContacts).complete(captured.capture());
        assertEquals(LocalDateTime.of(2026,7,26,12,0),
                captured.getValue().getCallRecord().getStartedAt());
        assertEquals("TD-001:21:FIRST_CONTACT",
                captured.getValue().getCallRecord().getBusinessOccurrenceKey());
        assertEquals("VALID",captured.getValue().getCallRecord().getCallResult());
    }

    @Test
    void routingUsesOnlyPersistedLeadOwnerAndReviewerReturnedByBusinessService()
    {
        LeadFirstContactHandler handler=new LeadFirstContactHandler(firstContacts);
        TodoInstance todo=todo("TD-001","First contact");
        when(firstContacts.complete(any())).thenReturn(
                new LeadFirstContactService.FirstContactOutcome("SUSPECT_INVALID",51L,52L,53L,
                        61L,null,8L,91L));

        CompletionResult result=handler.handle(CompletionContext.human(todo,Map.of(
                "contactResult","SUSPECT_INVALID","ownerId",999L,"reviewerId",998L,
                "invalidReasonCode","NO_NEED","salesExplanation","client"),
                8L,"alice"));

        assertEquals(8L,result.routingPayload().get("ownerId"));
        assertEquals(91L,result.routingPayload().get("reviewerId"));
        assertEquals(61L,result.routingPayload().get("reviewId"));
    }

    @Test
    void dryRunReportsDeferredRetryTodoWithoutMutatingLeadData()
    {
        LeadFirstContactHandler handler=new LeadFirstContactHandler(firstContacts);

        var result=handler.simulate(todo("TD-001","First contact"),
                Map.of("contactResult","UNREACHABLE","contactedAt","2026-07-28T09:00:00"));

        assertTrue(handler.supportsSimulation());
        assertEquals(Map.of("contactResult","UNREACHABLE"),result.routingPayload());
        assertEquals(java.util.List.of("TD-003"),result.producedTemplateCodes());
        verifyNoInteractions(firstContacts);
    }

    private TodoInstance todo(String code,String title)
    {
        TodoInstance todo = new TodoInstance();
        todo.setTodoId(21L);
        todo.setTemplateCode(code);
        todo.setTitle(title);
        todo.setBusinessType("LEAD");
        todo.setBusinessId(7L);
        todo.setOwnerId(8L);
        todo.setOwnerDeptId(3L);
        return todo;
    }
}
