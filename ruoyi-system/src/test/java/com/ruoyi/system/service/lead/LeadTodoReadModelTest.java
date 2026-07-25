package com.ruoyi.system.service.lead;

import static com.ruoyi.system.support.BusinessFixtures.actor;
import static com.ruoyi.system.support.BusinessFixtures.lead;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.business.security.BusinessActorProvider;
import com.law.todo.application.TodoBusinessViewService;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.domain.model.TodoInstance;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.domain.LeadTodoWorkItemView;
import com.ruoyi.system.mapper.BizLeadMapper;

@ExtendWith(MockitoExtension.class)
class LeadTodoReadModelTest
{
    @Mock private BizLeadMapper mapper;
    @Mock private BusinessActorProvider actors;
    @Mock private LeadAccessPolicy access;
    @Mock private TodoBusinessViewService todoViews;

    @Test
    void callTimelineRequiresLeadScopeAndUsesOneSetQuery()
    {
        BizLead owned = lead(7L, "1", "0");
        when(access.requireReadable(7L, false, false)).thenReturn(owned);
        when(mapper.selectLeadCallTimeline(7L)).thenReturn(List.of(item(7L, 91L)));

        List<LeadTodoWorkItemView> rows = service().callTimeline(7L);

        assertEquals(1, rows.size());
        assertEquals(91L, rows.get(0).getTodoId());
        verify(mapper).selectLeadCallTimeline(7L);
    }

    @Test
    void reviewQueueCarriesActorScopeIntoSinglePagedMapperQuery()
    {
        when(actors.current()).thenReturn(actor());
        LeadTodoWorkItemView expected = item(7L, 92L);
        when(mapper.selectLeadInvalidReviewQueue("PENDING", "张", 8L, 3L, true))
                .thenReturn(List.of(expected));

        List<LeadTodoWorkItemView> rows = service().invalidReviewQueue("PENDING", "张");

        assertEquals(List.of(expected), rows);
    }

    @Test
    void reviewQueueProjectsServerAuthoritativeActionsWithOneBulkCall()
    {
        when(actors.current()).thenReturn(actor());
        LeadTodoWorkItemView first=item(7L,92L);
        first.setTodoStatus("CREATED");
        LeadTodoWorkItemView second=item(8L,93L);
        second.setTodoStatus("SUBMITTED");
        LeadTodoWorkItemView third=item(9L,94L);
        third.setTodoStatus("COMPLETED");
        when(mapper.selectLeadInvalidReviewQueue("PENDING",null,8L,3L,true))
                .thenReturn(List.of(first,second,third));
        when(todoViews.allowedActions(
                org.mockito.ArgumentMatchers.<TodoInstance>anyList(),
                org.mockito.ArgumentMatchers.any(Actor.class)))
                .thenReturn(Map.of(92L,List.of("claim"),
                        93L,List.of("complete"),94L,List.of()));

        List<LeadTodoWorkItemView> rows=new LeadQueryService(
                mapper,actors,access,todoViews).invalidReviewQueue("PENDING",null);

        assertEquals(List.of("claim"),rows.get(0).getAllowedActions());
        assertEquals(List.of("complete"),rows.get(1).getAllowedActions());
        assertEquals(List.of(),rows.get(2).getAllowedActions());
        verify(todoViews).allowedActions(
                org.mockito.ArgumentMatchers.<TodoInstance>anyList(),
                org.mockito.ArgumentMatchers.any(Actor.class));
    }

    @Test
    void retryAndDeadPoolQueuesUseTheSameRoleDataScopeContract()
    {
        when(actors.current()).thenReturn(actor());
        when(mapper.selectLeadRetryQueue(null, null, 8L, 3L, true)).thenReturn(List.of(item(7L, 93L)));
        when(mapper.selectLeadDeadPoolQueue(null, null, 8L, 3L, true)).thenReturn(List.of(item(8L, 94L)));

        assertEquals(93L, service().retryQueue(null, null).get(0).getTodoId());
        assertEquals(94L, service().deadPoolQueue(null, null).get(0).getTodoId());
    }

    @Test
    void unauthorizedPeerNeverReceivesTimelineRows()
    {
        when(access.requireReadable(7L, false, false))
                .thenThrow(new ServiceException("denied", "ACCESS_DENIED"));

        assertThrows(ServiceException.class, () -> service().retryTimeline(7L));
        verify(mapper, never()).selectLeadRetryTimeline(7L);
    }

    @Test
    void readModelContainsTodoSlaAndBusinessSummaryFields()
    {
        assertField("leadNo");
        assertField("leadName");
        assertField("mobile");
        assertField("todoTitle");
        assertField("todoStatus");
        assertField("todoOwnerId");
        assertField("todoOwnerName");
        assertField("dueAt");
        assertField("slaStatus");
        assertField("overdue");
        assertField("escalated");
        assertField("escalatedAt");
        assertField("allowedActions");
    }

    private LeadQueryService service() { return new LeadQueryService(mapper, actors, access); }

    private LeadTodoWorkItemView item(Long leadId, Long todoId)
    {
        LeadTodoWorkItemView value = new LeadTodoWorkItemView();
        value.setLeadId(leadId);
        value.setTodoId(todoId);
        return value;
    }

    private void assertField(String name)
    {
        try { LeadTodoWorkItemView.class.getDeclaredField(name); }
        catch (NoSuchFieldException missing) { throw new AssertionError("Missing read-model field " + name); }
    }
}
