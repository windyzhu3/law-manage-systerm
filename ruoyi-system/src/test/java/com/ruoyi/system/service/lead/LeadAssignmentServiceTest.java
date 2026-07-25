package com.ruoyi.system.service.lead;

import static com.ruoyi.system.support.BusinessFixtures.actor;
import static com.ruoyi.system.support.BusinessFixtures.lead;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.security.BusinessActorProvider;
import com.law.business.shared.status.LeadStatus;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.mapper.BizLeadMapper;
import com.ruoyi.system.mapper.SysUserMapper;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;

@ExtendWith(MockitoExtension.class)
class LeadAssignmentServiceTest
{
    @Mock private BizLeadMapper mapper;
    @Mock private LeadAccessPolicy access;
    @Mock private BusinessActorProvider actors;
    @Mock private BusinessEventPublisher events;
    @Mock private SysUserMapper users;
    private LeadAssignmentService service;

    @BeforeEach
    void setUp()
    {
        service = new LeadAssignmentService(mapper, access, actors, events, users);
    }

    @Test
    void assignmentUsesExpectedStatusAndGeneratedLogId()
    {
        BizLead lead = lead(7L, LeadStatus.UNASSIGNED.code(), "0");
        lead.setPoolStatus("1");
        lead.setRowVersion(4);
        when(access.requireReadable(7L, false, true)).thenReturn(lead);
        when(actors.current()).thenReturn(actor());
        when(mapper.assignLead(7L, 9L, "alice", LeadStatus.UNASSIGNED.code(), 4)).thenReturn(1);
        when(mapper.insertAssignmentLog(argThat(log -> "assign".equals(log.get("actionType")))))
                .thenAnswer(invocation -> { ((Map<String, Object>) invocation.getArgument(0)).put("logId", 21L); return 1; });
        SysUser owner = new SysUser();
        owner.setDeptId(3L);
        owner.setStatus("0");
        owner.setDelFlag("0");
        when(users.selectUserById(9L)).thenReturn(owner);

        assertEquals(1, service.assign(7L, 9L, "首次分配"));

        verify(events).publish(argThat(event -> "LEAD_ASSIGNED:7:21".equals(event.getIdempotencyKey())
                && Integer.valueOf(1).equals(event.getPayload().get("schemaVersion"))
                && Long.valueOf(8L).equals(event.getPayload().get("operatorId"))
                && Long.valueOf(21L).equals(event.getPayload().get("assignmentId"))
                && Long.valueOf(9L).equals(event.getPayload().get("ownerId"))
                && event.getPayload().containsKey("ownerDeptId")
                && Long.valueOf(3L).equals(event.getPayload().get("ownerDeptId"))
                && !event.getPayload().containsKey("toOwnerId")));
    }

    @Test
    void staleAssignmentDoesNotWriteLogOrPublishEvent()
    {
        BizLead lead = lead(7L, LeadStatus.UNASSIGNED.code(), "0");
        lead.setPoolStatus("1");
        lead.setRowVersion(4);
        when(access.requireReadable(7L, false, true)).thenReturn(lead);
        when(users.selectUserById(9L)).thenReturn(assignableOwner());
        when(actors.current()).thenReturn(actor());
        when(mapper.assignLead(7L, 9L, "alice", LeadStatus.UNASSIGNED.code(), 4)).thenReturn(0);

        assertThrows(com.ruoyi.common.exception.ServiceException.class,
                () -> service.assign(7L, 9L, "stale"));

        verify(mapper, never()).insertAssignmentLog(any());
        verify(events, never()).publish(any());
    }

    @Test
    void missingTargetUserIsRejectedBeforeAnyAssignmentWrite()
    {
        assertTargetRejected(null);
    }

    @Test
    void disabledTargetUserIsRejectedBeforeAnyAssignmentWrite()
    {
        SysUser owner = assignableOwner();
        owner.setStatus("1");
        assertTargetRejected(owner);
    }

    @Test
    void deletedTargetUserIsRejectedBeforeAnyAssignmentWrite()
    {
        SysUser owner = assignableOwner();
        owner.setDelFlag("2");
        assertTargetRejected(owner);
    }

    @Test
    void targetWithoutDepartmentIsRejectedBeforeAnyAssignmentWrite()
    {
        SysUser owner = assignableOwner();
        owner.setDeptId(null);
        assertTargetRejected(owner);
    }

    @Test
    void doesNotExposeConstructorWithoutUserMapper()
    {
        assertThrows(NoSuchMethodException.class, () -> LeadAssignmentService.class.getConstructor(
                BizLeadMapper.class, LeadAccessPolicy.class, BusinessActorProvider.class,
                BusinessEventPublisher.class));
    }

    private void assertTargetRejected(SysUser owner)
    {
        BizLead lead = lead(7L, LeadStatus.UNASSIGNED.code(), "0");
        lead.setPoolStatus("1");
        lead.setRowVersion(4);
        when(access.requireReadable(7L, false, true)).thenReturn(lead);
        when(users.selectUserById(9L)).thenReturn(owner);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.assign(7L, 9L, "invalid target"));

        assertEquals("PRECONDITION_FAILED", exception.getBusinessCode());
        verify(mapper, never()).assignLead(any(), any(), any(), any(), any());
        verify(mapper, never()).insertAssignmentLog(any());
        verify(events, never()).publish(any());
    }

    private SysUser assignableOwner()
    {
        SysUser owner = new SysUser();
        owner.setDeptId(3L);
        owner.setStatus("0");
        owner.setDelFlag("0");
        return owner;
    }
}
