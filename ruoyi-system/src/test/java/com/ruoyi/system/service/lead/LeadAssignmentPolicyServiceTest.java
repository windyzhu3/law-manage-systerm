package com.ruoyi.system.service.lead;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.system.domain.BizLeadAssignmentPolicy;
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
}
