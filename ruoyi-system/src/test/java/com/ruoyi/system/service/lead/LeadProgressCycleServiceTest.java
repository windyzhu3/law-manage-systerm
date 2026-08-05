package com.ruoyi.system.service.lead;

import static com.ruoyi.system.support.BusinessFixtures.actor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.law.business.lead.dto.LeadProgressCompleteCommand;
import com.law.business.security.BusinessActorProvider;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.schedule.TodoScheduleService;
import com.law.todo.schedule.TodoScheduleService.CreateSchedulePlanCommand;
import com.law.todo.schedule.TodoScheduleService.SchedulePurpose;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.domain.BizLeadFollowup;
import com.ruoyi.system.mapper.BizLeadMapper;
import com.ruoyi.system.service.ISysDictTypeService;

@ExtendWith(MockitoExtension.class)
class LeadProgressCycleServiceTest
{
    private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    private static final LocalDateTime NOW=LocalDateTime.of(2026,7,31,10,0);
    private static final Clock CLOCK=Clock.fixed(NOW.atZone(ZONE).toInstant(),ZONE);

    @Mock private BizLeadMapper leads;
    @Mock private BusinessActorProvider actors;
    @Mock private ISysDictTypeService dictionaries;
    @Mock private TodoMapper todos;
    @Mock private TodoScheduleService schedules;
    @Mock private LeadAssignmentPolicyService policies;
    private LeadProgressCycleService service;
    private BizLead stored;
    private TodoInstance source;
    private AtomicReference<BizLeadFollowup> inserted;

    @BeforeEach
    void setUp()
    {
        service=new LeadProgressCycleService(leads,actors,dictionaries,todos,schedules,policies,CLOCK);
        stored=new BizLead();stored.setLeadId(91L);stored.setLeadNo("L-91");stored.setStatus("2");
        stored.setDelFlag("0");stored.setPoolStatus("0");stored.setDisposition("ACTIVE");
        stored.setOwnerId(8L);stored.setDeptId(3L);stored.setSourceCode("WEB");
        source=todo(7001L,88L);
        inserted=new AtomicReference<>();
        lenient().when(actors.current()).thenReturn(actor());
        lenient().when(leads.selectLeadForProgressCycleForUpdate(91L)).thenReturn(stored);
        lenient().when(dictionaries.selectDictDataByType("law_lead_progress_type")).thenReturn(dict("PHONE"));
        lenient().when(todos.selectAttachmentTypes(7001L)).thenReturn(List.of("FOLLOWUP_PROOF"));
        lenient().when(policies.resolveProgressSchedule(stored)).thenReturn(
                new LeadAssignmentPolicyService.ProgressSchedulePolicy(11L,2,501L,"Asia/Shanghai"));
        lenient().when(leads.insertProgressFollowupIfAbsent(any())).thenAnswer(invocation->{
            BizLeadFollowup value=invocation.getArgument(0);value.setFollowupId(90L);
            inserted.set(value);return 1;
        });
        lenient().when(leads.selectProgressFollowupByIdempotencyKeyForUpdate("LEAD_PROGRESS:7001"))
                .thenAnswer(invocation->inserted.get());
        lenient().when(schedules.createPlan(any())).thenReturn(81L);
        lenient().when(leads.linkProgressFollowupSchedule(90L,81L,"alice")).thenAnswer(invocation->{
            inserted.get().setSchedulePlanId(81L);return 1;
        });
    }

    @Test
    void completionWritesOneProgressFactAndOneFiveDayPlan()
    {
        LeadProgressCycleService.ProgressCycleOutcome result=service.complete(command("PHONE",NOW),source);

        assertEquals(90L,result.followupId());
        assertEquals(81L,result.schedulePlanId());
        assertEquals(NOW.plusDays(5),result.nextDueAt());
        assertFalse(result.replayed());
        verify(leads).insertProgressFollowupIfAbsent(argThat(row ->
                row.getLeadId().equals(91L)
                &&"PHONE".equals(row.getFollowType())
                &&"SUBSTANTIVE_PROGRESS".equals(row.getFollowResult())
                &&NOW.equals(row.getProgressAt())
                &&Date.from(NOW.plusDays(5).atZone(ZONE).toInstant()).equals(row.getNextFollowTime())
                &&row.getFollowUserId().equals(8L)
                &&row.getSourceTodoId().equals(7001L)
                &&"LEAD_PROGRESS:7001".equals(row.getIdempotencyKey())));
        ArgumentCaptor<CreateSchedulePlanCommand> plan=ArgumentCaptor.forClass(CreateSchedulePlanCommand.class);
        verify(schedules).createPlan(plan.capture());
        assertEquals(7001L,plan.getValue().previousTodoId());
        assertEquals(88L,plan.getValue().templateVersionId());
        assertEquals("LEAD",plan.getValue().businessType());
        assertEquals(91L,plan.getValue().businessId());
        assertEquals(NOW,plan.getValue().firstContactCompletedAt());
        assertEquals(SchedulePurpose.LEAD_PROGRESS_5D,plan.getValue().purpose());
        assertEquals("LEAD_PROGRESS_5D:91:7001",plan.getValue().idempotencyKey());
        assertEquals(1,plan.getValue().windows().size());
        assertEquals("P5D",plan.getValue().windows().get(0).windowCode());
        assertEquals(7200,plan.getValue().windows().get(0).durationMinutes());
        assertEquals(11L,plan.getValue().assignmentPolicyId());
        assertEquals(2,plan.getValue().assignmentPolicyVersion());
    }

    @Test
    void locksAndRevalidatesTheLeadBeforeAuthorizationPolicyFactsOrSchedules()
    {
        service.complete(command("PHONE",NOW),source);

        InOrder order=org.mockito.Mockito.inOrder(leads,actors,dictionaries,todos,policies,schedules);
        order.verify(leads).selectLeadForProgressCycleForUpdate(91L);
        order.verify(actors).current();
        order.verify(dictionaries).selectDictDataByType("law_lead_progress_type");
        order.verify(todos).selectAttachmentTypes(7001L);
        order.verify(leads).selectProgressFollowupByIdempotencyKey("LEAD_PROGRESS:7001");
        order.verify(leads).insertProgressFollowupIfAbsent(any());
        order.verify(leads,org.mockito.Mockito.times(2))
                .selectProgressFollowupByIdempotencyKeyForUpdate("LEAD_PROGRESS:7001");
        order.verify(policies).resolveProgressSchedule(stored);
        order.verify(schedules).createPlan(any());
        order.verify(leads).linkProgressFollowupSchedule(90L,81L,"alice");
        verify(leads,never()).selectLeadById(any());
    }

    @Test
    void replayReturnsFullyLinkedExistingFactWithoutCreatingAnotherPlan()
    {
        BizLeadFollowup existing=existing(90L,81L,"PHONE",NOW,"finished");
        when(leads.selectProgressFollowupByIdempotencyKey("LEAD_PROGRESS:7001"))
                .thenReturn(existing);
        when(todos.selectSchedulePlanForUpdate(81L)).thenReturn(plan(81L,88L,NOW));
        when(todos.selectScheduleWindowsByPlanIdForUpdate(81L)).thenReturn(windows(NOW));

        LeadProgressCycleService.ProgressCycleOutcome outcome=service.complete(
                command("PHONE",NOW,"finished"),source);

        assertTrue(outcome.replayed());
        assertEquals(90L,outcome.followupId());
        assertEquals(81L,outcome.schedulePlanId());
        assertEquals(NOW.plusDays(5),outcome.nextDueAt());
        verifyNoInteractions(schedules);
        verify(leads,never()).insertProgressFollowupIfAbsent(any());
    }

    @Test
    void replayRejectsChangedProgressDataAndLinkedTemplateOrPurpose()
    {
        BizLeadFollowup existing=existing(90L,81L,"PHONE",NOW,"finished");
        when(leads.selectProgressFollowupByIdempotencyKey("LEAD_PROGRESS:7001"))
                .thenReturn(existing);
        doReturn(dict("PHONE","WECHAT")).when(dictionaries)
                .selectDictDataByType("law_lead_progress_type");

        ServiceException changedProgress=assertThrows(ServiceException.class,
                ()->service.complete(command("WECHAT",NOW,"finished"),source));
        assertEquals("DUPLICATE_OPERATION",changedProgress.getBusinessCode());

        when(todos.selectSchedulePlanForUpdate(81L)).thenReturn(plan(81L,99L,NOW));
        ServiceException changedTemplate=assertThrows(ServiceException.class,
                ()->service.complete(command("PHONE",NOW,"finished"),source));
        assertEquals("DUPLICATE_OPERATION",changedTemplate.getBusinessCode());

        doAnswer(invocation->{
            Map<String,Object> value=new java.util.HashMap<>(plan(81L,88L,NOW));
            value.put("schedulePurpose","LEAD_RETRY");return value;
        }).when(todos).selectSchedulePlanForUpdate(81L);
        ServiceException changedPurpose=assertThrows(ServiceException.class,
                ()->service.complete(command("PHONE",NOW,"finished"),source));
        assertEquals("DUPLICATE_OPERATION",changedPurpose.getBusinessCode());
    }

    @Test
    void historicalFactWithoutPlanRecoversAndLinksTheDeterministicSchedule()
    {
        BizLeadFollowup existing=existing(90L,null,"PHONE",NOW,"finished");
        when(leads.selectProgressFollowupByIdempotencyKey("LEAD_PROGRESS:7001"))
                .thenReturn(existing);
        when(leads.selectProgressFollowupByIdempotencyKeyForUpdate("LEAD_PROGRESS:7001"))
                .thenReturn(existing);
        doAnswer(invocation->{
            existing.setSchedulePlanId(81L);return 1;
        }).when(leads).linkProgressFollowupSchedule(90L,81L,"alice");

        LeadProgressCycleService.ProgressCycleOutcome outcome=service.complete(
                command("PHONE",NOW,"finished"),source);

        assertTrue(outcome.replayed());
        assertEquals(81L,outcome.schedulePlanId());
        verify(schedules).createPlan(argThat(value ->
                "LEAD_PROGRESS_5D:91:7001".equals(value.idempotencyKey())));
    }

    @Test
    void missingProofInvalidTodoUnauthorizedInactiveInvalidDictionaryAndFutureTimeAreRejected()
    {
        when(todos.selectAttachmentTypes(7001L)).thenReturn(List.of());
        assertCode("PRECONDITION_FAILED",()->service.complete(command("PHONE",NOW),source));

        TodoInstance wrong=todo(7001L,88L);wrong.setTemplateCode("TD-003");
        assertCode("PRECONDITION_FAILED",()->service.complete(command("PHONE",NOW),wrong));

        stored.setOwnerId(9L);
        assertCode("ACCESS_DENIED",()->service.complete(command("PHONE",NOW),source));
        stored.setOwnerId(8L);stored.setDisposition("DEAD_POOL");
        assertCode("STATE_CONFLICT",()->service.complete(command("PHONE",NOW),source));
        stored.setDisposition("ACTIVE");

        when(dictionaries.selectDictDataByType("law_lead_progress_type")).thenReturn(dict("WECHAT"));
        assertCode("VALIDATION_FAILED",()->service.complete(command("PHONE",NOW),source));
        when(dictionaries.selectDictDataByType("law_lead_progress_type")).thenReturn(dict("PHONE"));

        assertCode("VALIDATION_FAILED",()->service.complete(command("PHONE",NOW.plusMinutes(6)),source));
        verify(leads,never()).linkProgressFollowupSchedule(90L,81L,"alice");
    }

    @Test
    void sourceTodoAndTypedCommandIdentityMustMatchExactly()
    {
        LeadProgressCompleteCommand missing=new LeadProgressCompleteCommand();
        assertCode("VALIDATION_FAILED",()->service.complete(missing,source));

        LeadProgressCompleteCommand todoMismatch=command("PHONE",NOW);
        todoMismatch.setTodoId(7002L);
        assertCode("PRECONDITION_FAILED",()->service.complete(todoMismatch,source));

        LeadProgressCompleteCommand leadMismatch=command("PHONE",NOW);leadMismatch.setLeadId(92L);
        assertCode("PRECONDITION_FAILED",()->service.complete(leadMismatch,source));
    }

    @Test
    void aFailedScheduleLinkNeverReturnsAReplayWithANullPlan()
    {
        doReturn(0).when(leads).linkProgressFollowupSchedule(90L,81L,"alice");
        when(leads.selectProgressFollowupByIdempotencyKeyForUpdate("LEAD_PROGRESS:7001"))
                .thenAnswer(invocation->inserted.get());

        ServiceException error=assertThrows(ServiceException.class,
                ()->service.complete(command("PHONE",NOW),source));

        assertEquals("CONCURRENT_MODIFICATION",error.getBusinessCode());
    }

    private LeadProgressCompleteCommand command(String progressType,LocalDateTime progressAt)
    {return command(progressType,progressAt,null);}

    private LeadProgressCompleteCommand command(String progressType,LocalDateTime progressAt,String remark)
    {
        LeadProgressCompleteCommand value=new LeadProgressCompleteCommand();
        value.setLeadId(91L);value.setTodoId(7001L);value.setProgressType(progressType);
        value.setProgressAt(progressAt);value.setRemark(remark);return value;
    }

    private TodoInstance todo(Long todoId,Long versionId)
    {
        TodoInstance value=new TodoInstance();value.setTodoId(todoId);value.setTemplateCode("TD-004");
        value.setTemplateVersionId(versionId);value.setBusinessType("LEAD");value.setBusinessId(91L);
        value.setOwnerId(8L);value.setOwnerDeptId(3L);return value;
    }

    private BizLeadFollowup existing(Long id,Long planId,String type,LocalDateTime at,String remark)
    {
        BizLeadFollowup value=new BizLeadFollowup();value.setFollowupId(id);value.setLeadId(91L);
        value.setFollowType(type);value.setFollowResult("SUBSTANTIVE_PROGRESS");value.setProgressAt(at);
        value.setContent(remark);value.setNextFollowTime(Date.from(at.plusDays(5).atZone(ZONE).toInstant()));
        value.setFollowUserId(8L);value.setSourceTodoId(7001L);value.setSchedulePlanId(planId);
        value.setIdempotencyKey("LEAD_PROGRESS:7001");return value;
    }

    private Map<String,Object> plan(Long id,Long versionId,LocalDateTime at)
    {
        return Map.ofEntries(Map.entry("planId",id),Map.entry("previousTodoId",7001L),
                Map.entry("templateVersionId",versionId),Map.entry("businessType","LEAD"),
                Map.entry("businessId",91L),Map.entry("schedulePurpose","LEAD_PROGRESS_5D"),
                Map.entry("idempotencyKey","LEAD_PROGRESS_5D:91:7001"),
                Map.entry("firstContactAt",at),Map.entry("timezone","Asia/Shanghai"),
                Map.entry("ruleVersionId",501L),Map.entry("assignmentPolicyId",11L),
                Map.entry("assignmentPolicyVersion",2),
                Map.entry("assignmentPolicySnapshotSource","RESOLVED_POLICY"));
    }

    private List<Map<String,Object>> windows(LocalDateTime at)
    {
        return List.of(Map.of("windowCode","P5D","windowOrder",0,"dayOffset",0,
                "startTime",at.toLocalTime(),"endTime",at.plusDays(5).toLocalTime(),
                "materializeAt",at,"dueAt",at.plusDays(5),"maxAttempts",1,"occurrenceNo",1));
    }

    private List<SysDictData> dict(String... values)
    {
        return java.util.Arrays.stream(values).map(value->{
            SysDictData item=new SysDictData();item.setDictValue(value);return item;
        }).toList();
    }

    private void assertCode(String code,org.junit.jupiter.api.function.Executable executable)
    {
        ServiceException error=assertThrows(ServiceException.class,executable);
        assertEquals(code,error.getBusinessCode());
    }
}
