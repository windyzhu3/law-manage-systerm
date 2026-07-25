package com.ruoyi.system.integration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.mapper.BizLeadMapper;
import com.ruoyi.system.service.event.LeadFirstContactHandler;

@ExtendWith(MockitoExtension.class)
class LeadFirstContactHandlerTest
{
    @Mock BizLeadMapper mapper;

    @Test
    void completionIsKeyedByTemplateCodeAndUsesLoadedLeadVersion()
    {
        TodoInstance todo = todo();
        LeadFirstContactHandler handler = new LeadFirstContactHandler(mapper);
        assertTrue(handler.supports(todo));
        BizLead lead = lead(9);
        when(mapper.selectLeadById(7L)).thenReturn(lead);
        when(mapper.insertFollowup(any())).thenReturn(1);
        when(mapper.touchLeadFollowTime(7L, null, "alice", 9)).thenReturn(1);

        handler.complete(todo, Map.of("contactResult", "connected", "content", "customer agreed"), 8L, "alice");

        verify(mapper).insertFollowup(argThat(followup -> followup.getLeadId().equals(7L)
                && "connected".equals(followup.getFollowResult()) && followup.getFollowUserId().equals(8L)));
        verify(mapper).touchLeadFollowTime(7L, null, "alice", 9);
    }

    @Test
    void staleLeadVersionFailsCompletion()
    {
        TodoInstance todo = todo();
        when(mapper.selectLeadById(7L)).thenReturn(lead(9));
        when(mapper.insertFollowup(any())).thenReturn(1);
        when(mapper.touchLeadFollowTime(7L, null, "alice", 9)).thenReturn(0);

        assertThrows(TodoException.class, () -> new LeadFirstContactHandler(mapper)
                .complete(todo, Map.of("contactResult", "connected"), 8L, "alice"));
    }

    private TodoInstance todo()
    {
        TodoInstance todo = new TodoInstance();
        todo.setTemplateCode("LEAD_FIRST_CONTACT");
        todo.setTitle("custom title");
        todo.setBusinessType("LEAD");
        todo.setBusinessId(7L);
        return todo;
    }

    private BizLead lead(int rowVersion)
    {
        BizLead lead = new BizLead();
        lead.setLeadId(7L);
        lead.setRowVersion(rowVersion);
        return lead;
    }
}
