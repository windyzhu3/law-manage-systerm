package com.ruoyi.system.service.lead;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.system.domain.BizLeadAssignmentPolicy;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.mapper.LeadFlowMapper;

@ExtendWith(MockitoExtension.class)
class LeadAssignmentPolicyServiceTest
{
    @Mock private LeadFlowMapper mapper;

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
}
