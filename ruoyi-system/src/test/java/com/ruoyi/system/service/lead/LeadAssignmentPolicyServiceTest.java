package com.ruoyi.system.service.lead;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
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
        policy.setRetryRuleJson("""
                {"templateVersionId":99,"ruleVersionId":501,"timezone":"Asia/Shanghai","windows":[
                  {"windowCode":"T0","windowOrder":0,"dayOffset":0,
                   "startOffsetMinutes":0,"durationMinutes":120,"maxAttempts":3},
                  {"windowCode":"VIP","windowOrder":1,"dayOffset":1,
                   "startTime":"10:15","endTime":"11:45","maxAttempts":2}
                ]}
                """);
        when(mapper.selectActiveAssignmentPolicy(3L,"WEB")).thenReturn(policy);

        LeadAssignmentPolicyService.RetrySchedulePolicy resolved=
                new LeadAssignmentPolicyService(mapper).resolveRetrySchedule(lead);

        assertEquals(99L,resolved.templateVersionId());
        assertEquals(501L,resolved.ruleVersionId());
        assertEquals(11L,resolved.policyId());
        assertEquals(List.of("T0","VIP"),
                resolved.windows().stream().map(value->value.windowCode()).toList());
    }

    @Test
    void wildcard_source_policy_is_valid_server_default()
    {
        BizLead lead=new BizLead();lead.setDeptId(3L);lead.setSourceCode(null);
        BizLeadAssignmentPolicy policy=new BizLeadAssignmentPolicy();
        policy.setPolicyId(12L);policy.setSourceCode("*");
        policy.setRetryRuleJson("""
                {"templateVersionId":100,"ruleVersionId":502,"windows":[
                  {"windowCode":"T0","windowOrder":0,"dayOffset":0,
                   "startOffsetMinutes":0,"durationMinutes":60,"maxAttempts":1}
                ]}
                """);
        when(mapper.selectActiveAssignmentPolicy(3L,"*")).thenReturn(policy);

        LeadAssignmentPolicyService.RetrySchedulePolicy resolved=
                new LeadAssignmentPolicyService(mapper).resolveRetrySchedule(lead);

        assertEquals(100L,resolved.templateVersionId());
        assertEquals("Asia/Shanghai",resolved.timezone());
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
        LeadAssignmentPolicyCommand.RetryWindow t0=new LeadAssignmentPolicyCommand.RetryWindow();
        t0.setWindowCode("T0");t0.setWindowOrder(0);t0.setDayOffset(0);
        t0.setStartOffsetMinutes(0);t0.setDurationMinutes(120);t0.setMaxAttempts(3);
        command.setWindows(List.of(t0));
        return command;
    }
}
