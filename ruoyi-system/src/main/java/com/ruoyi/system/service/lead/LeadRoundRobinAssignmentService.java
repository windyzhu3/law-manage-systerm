package com.ruoyi.system.service.lead;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.law.business.shared.error.BusinessErrorCode;
import com.law.todo.spi.TodoOrganizationPort;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.service.lead.LeadAssignmentPolicyService.ResolvedPolicy;

/**
 * Turns a confirmed intake tag into the governed initial sales assignment.
 * Candidate membership remains owned by the versioned lead policy; availability
 * and the persistent cursor remain owned by the Todo organization adapter.
 */
@Service
public class LeadRoundRobinAssignmentService
{
    private static final String STRATEGY_PREFIX="LEAD_ASSIGNMENT_POLICY:";

    private final LeadAssignmentPolicyService policies;
    private final TodoOrganizationPort organization;
    private final LeadAssignmentService assignments;

    public LeadRoundRobinAssignmentService(LeadAssignmentPolicyService policies,
        TodoOrganizationPort organization,LeadAssignmentService assignments)
    {
        this.policies=policies;
        this.organization=organization;
        this.assignments=assignments;
    }

    @Transactional
    public Long assignConfirmedTag(BizLead lead)
    {
        if(lead==null||lead.getLeadId()==null||lead.getDeptId()==null)
            throw error("Confirmed lead and sales department are required");
        ResolvedPolicy policy=policies.resolve(lead.getDeptId(),lead.getSourceCode());
        LocalDateTime now=LocalDateTime.now();
        List<Long> available=policy.candidateUserIds().stream()
            .filter(userId->userId!=null&&userId>0)
            .distinct()
            .filter(userId->organization.isAvailable(userId,now))
            .toList();
        if(available.isEmpty())throw error("No available sales candidate");
        Long owner=organization.roundRobin(STRATEGY_PREFIX+policy.policyId(),available)
            .orElseThrow(()->error("Round-robin owner could not be resolved"));
        assignments.assign(lead.getLeadId(),owner,"标签确认后按策略轮转分配");
        return owner;
    }

    private ServiceException error(String message)
    {
        return new ServiceException(message,BusinessErrorCode.PRECONDITION_FAILED.name());
    }
}
