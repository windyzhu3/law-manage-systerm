package com.ruoyi.system.service.lead;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.system.domain.BizLeadAssignmentPolicy;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.mapper.LeadFlowMapper;
import com.law.business.lead.dto.LeadAssignmentPolicyCommand;
import com.law.business.security.BusinessActorProvider;
import com.law.business.security.LeadPermissions;
import com.law.todo.mapper.TodoMapper;
import com.ruoyi.common.exception.ServiceException;

@ExtendWith(MockitoExtension.class)
class LeadAssignmentPolicyServiceTest
{
    @Mock private LeadFlowMapper mapper;
    @Mock private BusinessActorProvider actors;
    @Mock private LeadPermissionPolicy permissions;
    @Mock private LeadAccessPolicy access;
    @Mock private TodoMapper todos;

    @Test
    void source_specific_policy_returns_stable_ordered_candidates_and_retry_rule()
    {
        BizLeadAssignmentPolicy policy = new BizLeadAssignmentPolicy();
        policy.setPolicyId(11L);
        policy.setSalesDeptId(3L);
        policy.setSourceCode("WEB");
        policy.setRetryRuleJson("{\"timezone\":\"Asia/Shanghai\"}");
        when(mapper.selectActiveAssignmentPolicy(3L, "WEB")).thenReturn(policy);
        when(mapper.selectActivePolicyCandidates(11L)).thenReturn(List.of(8L, 9L));

        LeadAssignmentPolicyService.ResolvedPolicy resolved =
                new LeadAssignmentPolicyService(mapper).resolve(3L, "WEB");

        assertEquals(List.of(8L, 9L), resolved.candidateUserIds());
        assertEquals("{\"timezone\":\"Asia/Shanghai\"}", resolved.retryRuleJson());
    }

    @Test
    void retry_schedule_is_derived_from_authoritative_source_specific_policy()
    {
        BizLead lead=new BizLead();lead.setDeptId(3L);lead.setSourceCode("WEB");
        BizLeadAssignmentPolicy policy=new BizLeadAssignmentPolicy();
        policy.setPolicyId(11L);policy.setRowVersion(2);policy.setSourceCode("WEB");
        policy.setRetryRuleJson(canonicalRuleJson(99,501));
        when(mapper.selectActiveAssignmentPolicy(3L,"WEB")).thenReturn(policy);

        LeadAssignmentPolicyService.RetrySchedulePolicy resolved=
                new LeadAssignmentPolicyService(mapper).resolveRetrySchedule(lead);

        assertEquals(99L,resolved.templateVersionId());
        assertEquals(501L,resolved.ruleVersionId());
        assertEquals(11L,resolved.policyId());
        assertEquals(List.of("T0","T1_AM","T1_NOON","T1_PM","T2_AM","T2_NOON","T2_PM"),
                resolved.windows().stream().map(value->value.windowCode()).toList());
    }

    @Test
    void wildcard_source_policy_is_valid_server_default()
    {
        BizLead lead=new BizLead();lead.setDeptId(3L);lead.setSourceCode(null);
        BizLeadAssignmentPolicy policy=new BizLeadAssignmentPolicy();
        policy.setPolicyId(12L);policy.setSourceCode("*");
        policy.setRetryRuleJson(canonicalRuleJson(100,502));
        when(mapper.selectActiveAssignmentPolicy(3L,"*")).thenReturn(policy);

        LeadAssignmentPolicyService.RetrySchedulePolicy resolved=
                new LeadAssignmentPolicyService(mapper).resolveRetrySchedule(lead);

        assertEquals(100L,resolved.templateVersionId());
        assertEquals("Asia/Shanghai",resolved.timezone());
    }

    @Test
    void persistedUnsupportedOrIncompletePolicyFailsClosed()
    {
        BizLead lead=new BizLead();lead.setDeptId(3L);lead.setSourceCode("WEB");
        BizLeadAssignmentPolicy policy=new BizLeadAssignmentPolicy();
        policy.setPolicyId(13L);policy.setSourceCode("WEB");
        policy.setRetryRuleJson("""
                {"templateVersionId":99,"ruleVersionId":503,"windows":[
                  {"windowCode":"T0","windowOrder":0,"dayOffset":0,
                   "startOffsetMinutes":0,"durationMinutes":60,"maxAttempts":1},
                  {"windowCode":"VIP","windowOrder":1,"dayOffset":1,
                   "startTime":"09:00","endTime":"11:00","maxAttempts":1}
                ]}
                """);
        when(mapper.selectActiveAssignmentPolicy(3L,"WEB")).thenReturn(policy);

        ServiceException error=assertThrows(ServiceException.class,
                ()->new LeadAssignmentPolicyService(mapper).resolveRetrySchedule(lead));

        assertEquals("PRECONDITION_FAILED",error.getBusinessCode());
    }

    @Test
    void saveValidatesPublishedTd003ScopeCandidatesAndOptimisticVersion()
    {
        LeadAssignmentPolicyCommand command=command();
        BizLeadAssignmentPolicy stored=new BizLeadAssignmentPolicy();
        stored.setPolicyId(11L);stored.setPolicyCode("LEAD-3-WEB");stored.setPolicyName("旧策略");
        stored.setSalesDeptId(3L);stored.setSourceCode("WEB");stored.setBusinessType("LEAD");
        stored.setStatus("ACTIVE");stored.setRowVersion(2);
        when(actors.current()).thenReturn(com.ruoyi.system.support.BusinessFixtures.actor());
        when(todos.selectTemplateVersionById(99L)).thenReturn(java.util.Map.of(
                "template_code","TD-003","status","PUBLISHED","business_type","LEAD"));
        when(mapper.countActiveLeadSource("WEB")).thenReturn(1);
        when(mapper.selectActiveCandidateUsersInDepartment(3L,List.of(9L,8L)))
                .thenReturn(List.of(8L,9L));
        when(mapper.selectAssignmentPolicyByIdForUpdate(11L)).thenReturn(stored);
        when(mapper.updateAssignmentPolicyConditionally(
                org.mockito.ArgumentMatchers.eq(11L),any(),org.mockito.ArgumentMatchers.eq(3L),
                org.mockito.ArgumentMatchers.eq("WEB"),any(),org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq("alice"))).thenReturn(1);
        when(mapper.insertAssignmentPolicyCandidate(any(),any(),any(),any())).thenReturn(1);
        when(mapper.selectAssignmentPolicyCandidateViews(List.of(11L))).thenReturn(List.of());

        LeadAssignmentPolicyService.PolicyView result=apiService().save(command);

        assertEquals(3,result.rowVersion());
        verify(permissions).require(LeadPermissions.ASSIGNMENT_POLICY_EDIT);
        verify(access,times(2)).requireDepartmentAdministerable(3L);
        verify(mapper).deleteAssignmentPolicyCandidates(11L);
        verify(mapper).insertAssignmentPolicyCandidate(11L,9L,0,"alice");
        verify(mapper).insertAssignmentPolicyCandidate(11L,8L,1,"alice");
    }

    @Test
    void rejectsUnknownOrDisabledSource()
    {
        LeadAssignmentPolicyCommand command=command();
        when(todos.selectTemplateVersionById(99L)).thenReturn(java.util.Map.of(
                "template_code","TD-003","status","PUBLISHED","business_type","LEAD"));
        when(mapper.countActiveLeadSource("WEB")).thenReturn(0);

        ServiceException error=assertThrows(ServiceException.class,()->apiService().save(command));

        assertEquals("PRECONDITION_FAILED",error.getBusinessCode());
        verify(mapper,never()).insertAssignmentPolicy(any());
    }

    private LeadAssignmentPolicyService apiService()
    {
        return new LeadAssignmentPolicyService(mapper,actors,permissions,access,todos);
    }

    private LeadAssignmentPolicyCommand command()
    {
        LeadAssignmentPolicyCommand command=new LeadAssignmentPolicyCommand();
        command.setPolicyId(11L);command.setPolicyName("WEB轮转");command.setSalesDeptId(3L);
        command.setSourceCode("WEB");command.setExpectedVersion(2);
        command.setTemplateVersionId(99L);command.setRuleVersionId(501L);
        command.setTimezone("Asia/Shanghai");command.setCandidateUserIds(List.of(9L,8L));
        command.setWindows(List.of(relative("T0",0,0,0,120),
                clock("T1_AM",1,1,"09:00","11:00"),
                clock("T1_NOON",2,1,"11:00","14:00"),
                clock("T1_PM",3,1,"14:00","18:00"),
                clock("T2_AM",4,2,"09:00","11:00"),
                clock("T2_NOON",5,2,"11:00","14:00"),
                clock("T2_PM",6,2,"14:00","18:00")));
        return command;
    }

    private LeadAssignmentPolicyCommand.RetryWindow relative(String code,int order,int day,
            int offset,int duration)
    {
        LeadAssignmentPolicyCommand.RetryWindow value=new LeadAssignmentPolicyCommand.RetryWindow();
        value.setWindowCode(code);value.setWindowOrder(order);value.setDayOffset(day);
        value.setStartOffsetMinutes(offset);value.setDurationMinutes(duration);
        value.setMaxAttempts(1);value.setOccurrenceNo(1);
        return value;
    }

    private LeadAssignmentPolicyCommand.RetryWindow clock(String code,int order,int day,
            String start,String end)
    {
        LeadAssignmentPolicyCommand.RetryWindow value=new LeadAssignmentPolicyCommand.RetryWindow();
        value.setWindowCode(code);value.setWindowOrder(order);value.setDayOffset(day);
        value.setStartTime(start);value.setEndTime(end);
        value.setMaxAttempts(1);value.setOccurrenceNo(1);
        return value;
    }

    private String canonicalRuleJson(long templateVersionId,long ruleVersionId)
    {
        return """
                {"templateVersionId":%d,"ruleVersionId":%d,"timezone":"Asia/Shanghai","windows":[
                  {"windowCode":"T0","windowOrder":0,"dayOffset":0,"startOffsetMinutes":0,"durationMinutes":120,"maxAttempts":1},
                  {"windowCode":"T1_AM","windowOrder":1,"dayOffset":1,"startTime":"09:00","endTime":"11:00","maxAttempts":1},
                  {"windowCode":"T1_NOON","windowOrder":2,"dayOffset":1,"startTime":"11:00","endTime":"14:00","maxAttempts":1},
                  {"windowCode":"T1_PM","windowOrder":3,"dayOffset":1,"startTime":"14:00","endTime":"18:00","maxAttempts":1},
                  {"windowCode":"T2_AM","windowOrder":4,"dayOffset":2,"startTime":"09:00","endTime":"11:00","maxAttempts":1},
                  {"windowCode":"T2_NOON","windowOrder":5,"dayOffset":2,"startTime":"11:00","endTime":"14:00","maxAttempts":1},
                  {"windowCode":"T2_PM","windowOrder":6,"dayOffset":2,"startTime":"14:00","endTime":"18:00","maxAttempts":1}
                ]}
                """.formatted(templateVersionId,ruleVersionId);
    }
}
