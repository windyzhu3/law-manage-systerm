package com.ruoyi.system.service.lead;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.law.todo.spi.TodoOrganizationPort;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.service.lead.LeadAssignmentPolicyService.ResolvedPolicy;

@ExtendWith(MockitoExtension.class)
class LeadRoundRobinAssignmentServiceTest
{
    @Mock private LeadAssignmentPolicyService policies;
    @Mock private TodoOrganizationPort organization;
    @Mock private LeadAssignmentService assignments;

    @Test
    void skipsUnavailableCandidatesAdvancesThePolicyCursorAndAssignsTheSelectedOwner()
    {
        BizLead lead=lead();
        when(policies.resolve(201L,"WEB")).thenReturn(
            new ResolvedPolicy(31L,"{}",List.of(103L,101L)));
        when(organization.isAvailable(org.mockito.ArgumentMatchers.eq(101L),any())).thenReturn(false);
        when(organization.isAvailable(org.mockito.ArgumentMatchers.eq(103L),any())).thenReturn(true);
        when(organization.roundRobin("LEAD_ASSIGNMENT_POLICY:31",List.of(103L)))
            .thenReturn(Optional.of(103L));

        Long owner=new LeadRoundRobinAssignmentService(policies,organization,assignments)
            .assignConfirmedTag(lead);

        assertEquals(103L,owner);
        verify(assignments).assign(7L,103L,"标签确认后按策略轮转分配");
    }

    @Test
    void refusesToCreateAnOwnerlessAssignmentWhenEveryCandidateIsUnavailable()
    {
        BizLead lead=lead();
        when(policies.resolve(201L,"WEB")).thenReturn(
            new ResolvedPolicy(31L,"{}",List.of(101L)));
        when(organization.isAvailable(org.mockito.ArgumentMatchers.eq(101L),any())).thenReturn(false);

        assertThrows(ServiceException.class,()->new LeadRoundRobinAssignmentService(
            policies,organization,assignments).assignConfirmedTag(lead));
        verify(organization,never()).roundRobin(any(),any());
        verify(assignments,never()).assign(any(),any(),any());
    }

    @Test
    void preservesTheGovernedPolicyCandidateOrderWhenAdvancingTheCursor()
    {
        BizLead lead=lead();
        when(policies.resolve(201L,"WEB")).thenReturn(
            new ResolvedPolicy(31L,"{}",List.of(103L,101L)));
        when(organization.isAvailable(org.mockito.ArgumentMatchers.anyLong(),any())).thenReturn(true);
        when(organization.roundRobin("LEAD_ASSIGNMENT_POLICY:31",List.of(103L,101L)))
            .thenReturn(Optional.of(103L));

        Long owner=new LeadRoundRobinAssignmentService(policies,organization,assignments)
            .assignConfirmedTag(lead);

        assertEquals(103L,owner);
        verify(assignments).assign(org.mockito.ArgumentMatchers.eq(7L),
            org.mockito.ArgumentMatchers.eq(103L),any());
    }

    private BizLead lead()
    {
        BizLead lead=new BizLead();
        lead.setLeadId(7L);
        lead.setLeadNo("L-7");
        lead.setDeptId(201L);
        lead.setSourceCode("WEB");
        return lead;
    }
}
