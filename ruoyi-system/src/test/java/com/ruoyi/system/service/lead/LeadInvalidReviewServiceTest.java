package com.ruoyi.system.service.lead;

import static com.ruoyi.system.support.BusinessFixtures.actor;
import static com.ruoyi.system.support.BusinessFixtures.lead;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.business.event.BusinessEventCommand;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.lead.dto.LeadInvalidReviewCommand;
import com.law.business.security.BusinessActor;
import com.law.business.security.BusinessActorProvider;
import com.law.todo.application.TodoAutoActionService;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.domain.BizLeadDeadPoolLog;
import com.ruoyi.system.domain.BizLeadInvalidReview;
import com.ruoyi.system.domain.BizLeadQualityRecord;
import com.ruoyi.system.mapper.BizLeadMapper;
import com.ruoyi.system.mapper.LeadFlowMapper;
import com.ruoyi.system.service.ISysDictTypeService;

@ExtendWith(MockitoExtension.class)
class LeadInvalidReviewServiceTest
{
    @Mock private BizLeadMapper leads;
    @Mock private LeadFlowMapper facts;
    @Mock private LeadAccessPolicy access;
    @Mock private BusinessActorProvider actors;
    @Mock private ISysDictTypeService dictionaries;
    @Mock private LeadPermissionPolicy permissions;
    @Mock private LeadDeadPoolService deadPool;
    @Mock private BusinessEventPublisher events;
    private LeadInvalidReviewService service;
    private BizLead stored;
    private BizLeadInvalidReview review;

    @BeforeEach
    void setUp()
    {
        service = new LeadInvalidReviewService(leads, facts, access, actors, dictionaries, permissions,
                deadPool, events);
        stored = lead(7L, "2", "0");
        stored.setLeadNo("L-7");
        stored.setOwnerId(8L);
        stored.setDisposition("ACTIVE");
        stored.setInvalidReviewStatus("PENDING");
        stored.setRowVersion(5);
        review = new BizLeadInvalidReview();
        review.setReviewId(61L);
        review.setLeadId(7L);
        review.setReasonCode("NO_DEMAND");
        review.setReviewerId(8L);
        review.setTodoId(21L);
        review.setStatus("PENDING");
        review.setRowVersion(0);
    }

    @Test
    void confirmed_invalid_moves_to_dead_pool_not_public_pool()
    {
        LeadInvalidReviewCommand command = command("TRUE_INVALID", false);
        common(command);
        when(leads.markInvalidReviewed(7L, "PENDING", "TRUE_INVALID", 5, "alice")).thenReturn(1);
        when(facts.completeInvalidReview(61L, "TRUE_INVALID", "confirmed", 8L, "N", 0, "alice"))
                .thenReturn(1);
        when(deadPool.enterConfirmedInvalid(stored, 6, 21L, 61L, "NO_DEMAND", "confirmed", actor()))
                .thenReturn(new LeadDeadPoolService.DeadPoolOutcome(71L, false));

        LeadInvalidReviewService.InvalidReviewOutcome outcome = service.review(command);

        assertEquals("TRUE_INVALID", outcome.result());
        assertEquals(71L, outcome.deadPoolLogId());
        verify(deadPool).enterConfirmedInvalid(stored, 6, 21L, 61L, "NO_DEMAND", "confirmed", actor());
    }

    @Test
    void misjudged_valid_writes_quality_record_and_reopens_first_contact()
    {
        LeadInvalidReviewCommand command = command("MISJUDGED_VALID", false);
        common(command);
        when(leads.markInvalidReviewed(7L, "PENDING", "MISJUDGED_VALID", 5, "alice")).thenReturn(1);
        when(facts.completeInvalidReview(61L, "MISJUDGED_VALID", "wrong", 8L, "N", 0, "alice"))
                .thenReturn(1);
        when(facts.insertQualityRecordIfAbsent(any())).thenAnswer(invocation -> {
            BizLeadQualityRecord value = invocation.getArgument(0);
            value.setQualityRecordId(72L);
            return 1;
        });
        when(leads.reopenFirstContact(7L, 6, "alice")).thenReturn(1);

        LeadInvalidReviewService.InvalidReviewOutcome outcome = service.review(command);

        assertEquals(72L, outcome.qualityRecordId());
        verify(leads).reopenFirstContact(7L, 6, "alice");
        ArgumentCaptor<BusinessEventCommand> event = ArgumentCaptor.forClass(BusinessEventCommand.class);
        verify(events).publish(event.capture(),org.mockito.ArgumentMatchers.eq(actor()));
        assertEquals("LEAD_INVALID_REVIEW_MISJUDGED:7:61:72", event.getValue().getIdempotencyKey());
    }

    @Test
    void system_default_review_is_audited_as_system()
    {
        LeadInvalidReviewCommand command = command("TRUE_INVALID", true);
        review.setReviewerId(9L);
        when(leads.selectLeadById(7L)).thenReturn(stored);
        when(facts.selectInvalidReviewById(61L)).thenReturn(review);
        when(dictionaries.selectDictDataByType("law_lead_invalid_review_result"))
                .thenReturn(dict("TRUE_INVALID", "MISJUDGED_VALID"));
        when(leads.markInvalidReviewed(7L, "PENDING", "TRUE_INVALID", 5, "system")).thenReturn(1);
        when(facts.completeInvalidReview(61L, "TRUE_INVALID",
                "TD-002 overdue automatic confirmation", 9L, "Y", 0, "system"))
                .thenReturn(1);
        when(deadPool.enterConfirmedInvalid(any(), anyInt(), any(), any(), any(), any(), any()))
                .thenReturn(new LeadDeadPoolService.DeadPoolOutcome(73L, false));

        service.reviewAutomatically(7L,61L,21L,TodoAutoActionService.SERVICE_ACTOR);

        verify(facts).completeInvalidReview(61L, "TRUE_INVALID",
                "TD-002 overdue automatic confirmation", 9L, "Y", 0, "system");
        ArgumentCaptor<BusinessEventCommand> event=ArgumentCaptor.forClass(BusinessEventCommand.class);
        ArgumentCaptor<BusinessActor> publisherActor=ArgumentCaptor.forClass(BusinessActor.class);
        verify(events).publish(event.capture(),publisherActor.capture());
        assertEquals(9L,event.getValue().getPayload().get("reviewerId"));
        assertEquals(0L,event.getValue().getPayload().get("operatorId"));
        assertEquals(0L,publisherActor.getValue().userId());
        assertEquals("system",publisherActor.getValue().userName());
    }

    @Test
    void public_review_dto_has_no_system_default_switch()
    {
        long exposed=java.util.Arrays.stream(LeadInvalidReviewCommand.class.getMethods())
                .filter(method->method.getName().contains("SystemDefault"))
                .count();

        assertEquals(0L,exposed);
    }

    @Test
    void repeated_completed_review_returns_existing_dead_pool_outcome()
    {
        LeadInvalidReviewCommand command = command("TRUE_INVALID", true);
        stored.setDisposition("DEAD_POOL");
        stored.setInvalidReviewStatus("CONFIRMED");
        review.setStatus("COMPLETED");
        review.setReviewResult("TRUE_INVALID");
        BizLeadDeadPoolLog log = new BizLeadDeadPoolLog();
        log.setDeadPoolLogId(73L);
        when(leads.selectLeadById(7L)).thenReturn(stored);
        when(facts.selectInvalidReviewById(61L)).thenReturn(review);
        when(dictionaries.selectDictDataByType("law_lead_invalid_review_result"))
                .thenReturn(dict("TRUE_INVALID", "MISJUDGED_VALID"));
        when(facts.selectDeadPoolLogByIdempotencyKey("LEAD_DEAD_POOL:7:61")).thenReturn(log);

        LeadInvalidReviewService.InvalidReviewOutcome outcome =
                service.reviewAutomatically(7L,61L,21L,TodoAutoActionService.SERVICE_ACTOR);

        assertEquals(true, outcome.replayed());
        assertEquals(73L, outcome.deadPoolLogId());
        verify(leads, org.mockito.Mockito.never()).markInvalidReviewed(any(), any(), any(), any(), any());
    }

    @Test
    void human_review_rejects_wrong_source_todo()
    {
        LeadInvalidReviewCommand command = command("TRUE_INVALID", false);
        review.setReviewerId(9L);
        review.setTodoId(20L);
        when(actors.current()).thenReturn(actor());
        when(facts.selectInvalidReviewById(61L)).thenReturn(review);

        org.junit.jupiter.api.Assertions.assertThrows(com.ruoyi.common.exception.ServiceException.class,
                () -> service.review(command));

        verify(leads, org.mockito.Mockito.never()).markInvalidReviewed(any(), any(), any(), any(), any());
    }

    @Test
    void human_review_rejects_actor_who_is_not_assigned_reviewer()
    {
        LeadInvalidReviewCommand command=command("TRUE_INVALID",false);
        review.setReviewerId(9L);
        when(actors.current()).thenReturn(actor());
        when(facts.selectInvalidReviewById(61L)).thenReturn(review);

        org.junit.jupiter.api.Assertions.assertThrows(com.ruoyi.common.exception.ServiceException.class,
                ()->service.review(command));

        verify(leads,org.mockito.Mockito.never()).markInvalidReviewed(any(),any(),any(),any(),any());
    }

    @Test
    void human_review_cannot_bypass_lead_data_scope()
    {
        LeadInvalidReviewCommand command=command("TRUE_INVALID",false);
        when(actors.current()).thenReturn(actor());
        when(facts.selectInvalidReviewById(61L)).thenReturn(review);
        when(access.requireReviewable(7L,8L)).thenThrow(new com.ruoyi.common.exception.ServiceException(
                "out of scope",com.law.business.shared.error.BusinessErrorCode.ACCESS_DENIED.name()));

        com.ruoyi.common.exception.ServiceException error=
                org.junit.jupiter.api.Assertions.assertThrows(com.ruoyi.common.exception.ServiceException.class,
                        ()->service.review(command));

        assertEquals("ACCESS_DENIED",error.getBusinessCode());
        verify(leads,org.mockito.Mockito.never()).markInvalidReviewed(any(),any(),any(),any(),any());
    }

    @Test
    void automatic_review_requires_exact_todo_service_capability_and_fixed_result()
    {
        review.setReviewerId(9L);
        review.setTodoId(21L);
        when(leads.selectLeadById(7L)).thenReturn(stored);
        when(facts.selectInvalidReviewById(61L)).thenReturn(review);
        when(dictionaries.selectDictDataByType("law_lead_invalid_review_result"))
                .thenReturn(dict("TRUE_INVALID", "MISJUDGED_VALID"));
        when(leads.markInvalidReviewed(7L, "PENDING", "TRUE_INVALID", 5, "system")).thenReturn(1);
        when(facts.completeInvalidReview(61L, "TRUE_INVALID",
                "TD-002 overdue automatic confirmation", 9L, "Y", 0, "system")).thenReturn(1);
        when(deadPool.enterConfirmedInvalid(any(), anyInt(), any(), any(), any(), any(), any()))
                .thenReturn(new LeadDeadPoolService.DeadPoolOutcome(73L, false));

        LeadInvalidReviewService.InvalidReviewOutcome outcome =
                service.reviewAutomatically(7L, 61L, 21L, TodoAutoActionService.SERVICE_ACTOR);

        assertEquals("TRUE_INVALID", outcome.result());
        verify(facts).completeInvalidReview(61L, "TRUE_INVALID",
                "TD-002 overdue automatic confirmation", 9L, "Y", 0, "system");
    }

    @Test
    void automatic_review_rejects_lookalike_actor_without_capability_identity()
    {
        org.junit.jupiter.api.Assertions.assertThrows(com.ruoyi.common.exception.ServiceException.class,
                ()->service.reviewAutomatically(7L,61L,21L,
                        new Actor(-1L,"TODO_AUTO_ACTION",null)));

        verify(leads,org.mockito.Mockito.never()).selectLeadById(any());
    }

    @Test
    void completed_review_without_companion_evidence_is_consistency_error()
    {
        LeadInvalidReviewCommand command = command("TRUE_INVALID", false);
        stored.setDisposition("DEAD_POOL");
        stored.setInvalidReviewStatus("CONFIRMED");
        review.setStatus("COMPLETED");
        review.setReviewResult("TRUE_INVALID");
        review.setReviewerId(8L);
        review.setTodoId(21L);
        when(actors.current()).thenReturn(actor());
        when(facts.selectInvalidReviewById(61L)).thenReturn(review);
        when(access.requireReviewable(7L,8L)).thenReturn(stored);
        when(dictionaries.selectDictDataByType("law_lead_invalid_review_result"))
                .thenReturn(dict("TRUE_INVALID", "MISJUDGED_VALID"));

        com.ruoyi.common.exception.ServiceException error =
                org.junit.jupiter.api.Assertions.assertThrows(com.ruoyi.common.exception.ServiceException.class,
                        () -> service.review(command));

        assertEquals("LEAD_FLOW_EVIDENCE_MISSING", error.getMessage());
    }

    private void common(LeadInvalidReviewCommand command)
    {
        when(actors.current()).thenReturn(actor());
        when(facts.selectInvalidReviewById(61L)).thenReturn(review);
        when(access.requireReviewable(7L,8L)).thenReturn(stored);
        when(dictionaries.selectDictDataByType("law_lead_invalid_review_result"))
                .thenReturn(dict("TRUE_INVALID", "MISJUDGED_VALID"));
    }

    private LeadInvalidReviewCommand command(String result, boolean system)
    {
        LeadInvalidReviewCommand value = new LeadInvalidReviewCommand();
        value.setLeadId(7L);
        value.setReviewId(61L);
        value.setTodoId(21L);
        value.setReviewResult(result);
        value.setReviewComment("TRUE_INVALID".equals(result) ? "confirmed" : "wrong");
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
}
