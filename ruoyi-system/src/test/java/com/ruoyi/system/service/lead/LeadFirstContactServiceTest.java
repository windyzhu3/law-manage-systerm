package com.ruoyi.system.service.lead;

import static com.ruoyi.system.support.BusinessFixtures.actor;
import static com.ruoyi.system.support.BusinessFixtures.lead;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.business.event.BusinessEventCommand;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.lead.dto.LeadFirstContactCommand;
import com.law.business.security.BusinessActorProvider;
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

@ExtendWith(MockitoExtension.class)
class LeadFirstContactServiceTest
{
    @Mock private BizLeadMapper leads;
    @Mock private LeadFlowMapper facts;
    @Mock private LeadAccessPolicy access;
    @Mock private LeadCallRecordService calls;
    @Mock private BusinessActorProvider actors;
    @Mock private ISysDictTypeService dictionaries;
    @Mock private TodoOrganizationPort organization;
    @Mock private TodoScheduleService schedules;
    @Mock private LeadAssignmentPolicyService policies;
    @Mock private BusinessEventPublisher events;
    private LeadFirstContactService service;
    private BizLead stored;

    @BeforeEach
    void setUp()
    {
        service = new LeadFirstContactService(leads, facts, access, calls, actors, dictionaries,
                organization, schedules, policies, events);
        stored = lead(7L, "1", "0");
        stored.setLeadNo("L-7");
        stored.setOwnerId(8L);
        stored.setDisposition("ACTIVE");
        stored.setFirstContactStatus("PENDING");
        stored.setRowVersion(4);
        when(organization.isAvailable(anyLong(),any())).thenReturn(true);
    }

    @Test
    void valid_contact_persists_four_fields_and_publishes_one_event()
    {
        LeadFirstContactCommand command = command("VALID");
        command.setContactName("Client");
        command.setCity("Shanghai");
        command.setLegalDemand("Contract dispute");
        command.setVisited("1");
        when(access.requireOperable(7L)).thenReturn(stored);
        when(actors.current()).thenReturn(actor());
        when(dictionaries.selectDictDataByType("law_first_contact_result")).thenReturn(dict("VALID"));
        when(calls.recordForLead(command.getCallRecord(), stored, actor(),21L)).thenReturn(
                new LeadCallRecordService.CallRecordOutcome(31L, false));
        generatedFollowup(51L);
        when(leads.completeFirstContact(7L, "1", "VALID", "Client", "Shanghai",
                "Contract dispute", "1", null, null, 4, "alice")).thenReturn(1);

        LeadFirstContactService.FirstContactOutcome outcome = service.complete(command);

        assertEquals("VALID", outcome.result());
        assertEquals(51L, outcome.businessFactId());
        assertEquals(31L, outcome.callRecordId());
        ArgumentCaptor<BusinessEventCommand> event = ArgumentCaptor.forClass(BusinessEventCommand.class);
        verify(events).publish(event.capture(),org.mockito.ArgumentMatchers.eq(actor()));
        assertEquals("LEAD_FIRST_CONTACT_VALID:7:51", event.getValue().getIdempotencyKey());
        assertEquals(51L, event.getValue().getPayload().get("followupId"));
    }

    @Test
    void suspect_invalid_requires_one_of_four_reason_codes()
    {
        LeadFirstContactCommand command = command("SUSPECT_INVALID");
        command.setInvalidReasonCode("FREE_TEXT");
        when(access.requireOperable(7L)).thenReturn(stored);
        when(actors.current()).thenReturn(actor());
        when(dictionaries.selectDictDataByType("law_first_contact_result")).thenReturn(dict("SUSPECT_INVALID"));
        when(dictionaries.selectDictDataByType("law_lead_invalid_reason")).thenReturn(dict(
                "NO_DEMAND", "DENY_SUBMISSION", "COMPETITOR_INTERFERENCE", "OTHER"));

        ServiceException error = assertThrows(ServiceException.class, () -> service.complete(command));

        assertEquals("VALIDATION_FAILED", error.getBusinessCode());
        verify(leads, never()).completeFirstContact(any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any());
        verify(events, never()).publish(any(),any());
    }

    @Test
    void suspect_invalid_event_uses_review_id_not_call_or_followup_id()
    {
        LeadFirstContactCommand command = command("SUSPECT_INVALID");
        command.setInvalidReasonCode("NO_DEMAND");
        when(access.requireOperable(7L)).thenReturn(stored);
        when(actors.current()).thenReturn(actor());
        when(dictionaries.selectDictDataByType("law_first_contact_result")).thenReturn(dict("SUSPECT_INVALID"));
        when(dictionaries.selectDictDataByType("law_lead_invalid_reason")).thenReturn(dict("NO_DEMAND"));
        when(calls.recordForLead(command.getCallRecord(), stored, actor(),21L)).thenReturn(
                new LeadCallRecordService.CallRecordOutcome(33L, false));
        generatedFollowup(53L);
        when(facts.insertInvalidReviewIfAbsent(any())).thenAnswer(invocation -> {
            BizLeadInvalidReview value = invocation.getArgument(0);
            assertEquals(9L,value.getReviewerId());
            value.setReviewId(63L);
            return 1;
        });
        when(leads.completeFirstContact(7L, "1", "SUSPECT_INVALID", null, null, null, null,
                "NO_DEMAND", "TD-001", 4, "alice")).thenReturn(1);
        when(organization.supervisor(8L, 1)).thenReturn(java.util.Optional.of(9L));

        LeadFirstContactService.FirstContactOutcome outcome = service.complete(command);

        assertEquals(63L, outcome.businessFactId());
        assertEquals(53L, outcome.followupId());
        assertEquals(33L, outcome.callRecordId());
        ArgumentCaptor<BusinessEventCommand> event = ArgumentCaptor.forClass(BusinessEventCommand.class);
        verify(events).publish(event.capture(),org.mockito.ArgumentMatchers.eq(actor()));
        assertEquals("LEAD_SUSPECT_INVALID_MARKED:7:63", event.getValue().getIdempotencyKey());
        assertEquals(63L, event.getValue().getPayload().get("reviewId"));
        assertEquals(9L, event.getValue().getPayload().get("reviewerId"));
    }

    @Test
    void unreachable_creates_default_retry_plan()
    {
        LeadFirstContactCommand command = command("UNREACHABLE");
        stored.setDeptId(3L);
        stored.setSourceCode("WEB");
        when(access.requireOperable(7L)).thenReturn(stored);
        when(actors.current()).thenReturn(actor());
        when(dictionaries.selectDictDataByType("law_first_contact_result")).thenReturn(dict("UNREACHABLE"));
        when(calls.recordForLead(command.getCallRecord(), stored, actor(),21L)).thenReturn(
                new LeadCallRecordService.CallRecordOutcome(32L, false));
        generatedFollowup(52L);
        when(leads.completeFirstContact(7L, "1", "UNREACHABLE", null, null, null, null,
                null, null, 4, "alice")).thenReturn(1);
        var windows=List.of(new TodoScheduleService.ScheduleWindowRule(
                "T0",0,0,null,null,0,120,3,1));
        when(policies.resolveRetrySchedule(stored)).thenReturn(
                new LeadAssignmentPolicyService.RetrySchedulePolicy(11L,2,99L,501L,
                        "Asia/Shanghai",windows));
        when(schedules.createPlan(any())).thenReturn(81L);

        LeadFirstContactService.FirstContactOutcome outcome = service.complete(command);

        assertEquals(81L, outcome.schedulePlanId());
        assertEquals(52L, outcome.followupId());
        assertEquals(32L, outcome.callRecordId());
        ArgumentCaptor<TodoScheduleService.CreateSchedulePlanCommand> plan=
                ArgumentCaptor.forClass(TodoScheduleService.CreateSchedulePlanCommand.class);
        verify(schedules).createPlan(plan.capture());
        assertEquals(99L,plan.getValue().templateVersionId());
        assertEquals(501L,plan.getValue().ruleVersionId());
        assertEquals(11L,plan.getValue().assignmentPolicyId());
        assertEquals(2,plan.getValue().assignmentPolicyVersion());
        assertEquals("Asia/Shanghai",plan.getValue().timezone());
        assertEquals(windows,plan.getValue().windows());
        ArgumentCaptor<BusinessEventCommand> event = ArgumentCaptor.forClass(BusinessEventCommand.class);
        verify(events).publish(event.capture(),org.mockito.ArgumentMatchers.eq(actor()));
        assertEquals("LEAD_FIRST_CONTACT_UNREACHABLE:7:81", event.getValue().getIdempotencyKey());
        assertEquals(81L, event.getValue().getPayload().get("planId"));
    }

    private LeadFirstContactCommand command(String result)
    {
        LeadFirstContactCommand value = new LeadFirstContactCommand();
        value.setLeadId(7L);
        value.setTodoId(21L);
        value.setContactResult(result);
        value.setCallRecord(LeadCallRecordServiceTest.command(7L, 21L, "call-1"));
        return value;
    }

    private List<SysDictData> dict(String... values)
    {
        return java.util.Arrays.stream(values).map(value -> {
            SysDictData item = new SysDictData();
            item.setDictValue(value);
            return item;
        }).toList();
    }

    private void generatedFollowup(Long followupId)
    {
        when(leads.insertFollowup(any(BizLeadFollowup.class))).thenAnswer(invocation -> {
            BizLeadFollowup value = invocation.getArgument(0);
            value.setFollowupId(followupId);
            return 1;
        });
    }
}
