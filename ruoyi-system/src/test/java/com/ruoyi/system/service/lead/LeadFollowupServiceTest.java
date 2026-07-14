package com.ruoyi.system.service.lead;

import static com.ruoyi.system.support.BusinessFixtures.actor;
import static com.ruoyi.system.support.BusinessFixtures.lead;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.business.lead.dto.LeadFollowupCommand;
import com.law.business.security.BusinessActorProvider;
import com.law.business.shared.status.LeadStatus;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.mapper.BizLeadMapper;
import com.ruoyi.system.service.ISysDictTypeService;

@ExtendWith(MockitoExtension.class)
class LeadFollowupServiceTest
{
    @Mock private BizLeadMapper mapper;
    @Mock private LeadAccessPolicy access;
    @Mock private BusinessActorProvider actors;
    @Mock private ISysDictTypeService dictionaries;
    private LeadFollowupService service;

    @BeforeEach
    void setUp()
    {
        service = new LeadFollowupService(mapper, access, actors, dictionaries);
    }

    @Test
    void addUsesAuthenticatedActorAndExpectedLeadStatus()
    {
        BizLead lead = lead(7L, LeadStatus.WAIT_FOLLOW.code(), "0");
        lead.setOwnerId(8L);
        when(access.requireOperable(7L)).thenReturn(lead);
        when(actors.current()).thenReturn(actor());
        when(dictionaries.selectDictDataByType("law_lead_follow_type")).thenReturn(List.of(dict("phone")));
        when(mapper.insertFollowup(argThat(followup -> Long.valueOf(8L).equals(followup.getFollowUserId())
                && "alice".equals(followup.getCreateBy())))).thenReturn(1);
        Date next = new Date(1_800_000_000_000L);
        when(mapper.touchLeadFollowTimeConditionally(7L, next, "alice", LeadStatus.WAIT_FOLLOW.code())).thenReturn(1);

        LeadFollowupCommand command = new LeadFollowupCommand();
        command.setLeadId(7L);
        command.setFollowType("phone");
        command.setFollowResult("connected");
        command.setContent("已确认委托需求");
        command.setNextFollowTime(next);

        assertEquals(1, service.add(command, false));

        verify(mapper).touchLeadFollowTimeConditionally(7L, next, "alice", LeadStatus.WAIT_FOLLOW.code());
    }

    private SysDictData dict(String value)
    {
        SysDictData item = new SysDictData();
        item.setDictValue(value);
        return item;
    }
}
