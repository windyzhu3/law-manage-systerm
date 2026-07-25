package com.ruoyi.system.service.lead;

import static com.ruoyi.system.support.BusinessFixtures.actor;
import static com.ruoyi.system.support.BusinessFixtures.administrator;
import static com.ruoyi.system.support.BusinessFixtures.lead;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.business.security.BusinessActorProvider;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.mapper.BizLeadMapper;

@ExtendWith(MockitoExtension.class)
class LeadAccessPolicyTest
{
    @Mock private BizLeadMapper mapper;
    @Mock private BusinessActorProvider actors;

    @Test
    void inaccessibleLeadIsRejected()
    {
        BizLead lead = lead(7L, "1", "0");
        when(mapper.selectLeadById(7L)).thenReturn(lead);
        when(actors.current()).thenReturn(actor());
        when(mapper.countLeadInDataScope(7L, 8L, 3L, false)).thenReturn(0);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> new LeadAccessPolicy(mapper, actors).requireOperable(7L));

        assertEquals("ACCESS_DENIED", exception.getBusinessCode());
    }

    @Test
    void administratorCanOperateWithoutDataScopeQuery()
    {
        BizLead lead = lead(7L, "1", "0");
        when(mapper.selectLeadById(7L)).thenReturn(lead);
        when(actors.current()).thenReturn(administrator());

        BizLead result = new LeadAccessPolicy(mapper, actors).requireOperable(7L);

        assertSame(lead, result);
        verify(mapper, never()).countLeadInDataScope(7L, 1L, 1L, false);
    }

    @Test
    void deadPoolUsesOriginatingSalesScopeAfterOwnerAndDepartmentWereCleared()
    {
        BizLead dead = lead(7L, "4", "0");
        dead.setDisposition("DEAD_POOL");
        when(mapper.selectLeadById(7L)).thenReturn(dead);
        when(actors.current()).thenReturn(actor());
        when(mapper.countDeadPoolInDataScope(7L,8L,3L)).thenReturn(1);

        assertSame(dead,new LeadAccessPolicy(mapper,actors).requireDeadPoolRestorable(7L));
    }

    @Test
    void unauthorizedPeerCannotRestoreDeadPoolLead()
    {
        BizLead dead = lead(7L, "4", "0");
        dead.setDisposition("DEAD_POOL");
        when(mapper.selectLeadById(7L)).thenReturn(dead);
        when(actors.current()).thenReturn(actor());
        when(mapper.countDeadPoolInDataScope(7L,8L,3L)).thenReturn(0);

        ServiceException exception=assertThrows(ServiceException.class,
                ()->new LeadAccessPolicy(mapper,actors).requireDeadPoolRestorable(7L));
        assertEquals("ACCESS_DENIED",exception.getBusinessCode());
    }
}
