package com.ruoyi.system.service.lead;

import java.util.List;
import org.springframework.stereotype.Service;
import com.law.business.shared.error.BusinessErrorCode;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.BizLeadAssignmentPolicy;
import com.ruoyi.system.mapper.LeadFlowMapper;

@Service
public class LeadAssignmentPolicyService
{
    private final LeadFlowMapper mapper;

    public LeadAssignmentPolicyService(LeadFlowMapper mapper) { this.mapper = mapper; }

    public ResolvedPolicy resolve(Long salesDeptId, String sourceCode)
    {
        if (salesDeptId == null) throw new ServiceException("Sales department is required",
                BusinessErrorCode.VALIDATION_FAILED.name());
        String source = sourceCode == null || sourceCode.isBlank() ? "*" : sourceCode.trim();
        BizLeadAssignmentPolicy policy = mapper.selectActiveAssignmentPolicy(salesDeptId, source);
        if (policy == null) throw new ServiceException("No active assignment policy",
                BusinessErrorCode.DATA_NOT_FOUND.name());
        List<Long> candidates = mapper.selectActivePolicyCandidates(policy.getPolicyId());
        return new ResolvedPolicy(policy.getPolicyId(), policy.getRetryRuleJson(),
                candidates == null ? List.of() : List.copyOf(candidates));
    }

    public record ResolvedPolicy(Long policyId, String retryRuleJson, List<Long> candidateUserIds) { }
}
