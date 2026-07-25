package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.view.TodoBusinessSummary;
import com.law.todo.application.view.TodoChainView;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.TodoAccessPolicy;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.spi.TodoBusinessAccessChecker;

@ExtendWith(MockitoExtension.class)
class TodoBusinessViewServiceTest
{
    @Mock TodoMapper mapper;@Mock TodoBusinessAccessChecker access;@Mock TodoAccessPolicy todoAccess;
    private final Actor actor=new Actor(7L,"alice",3L);

    @Test void summarizesVisibleBusinessTodos()
    {
        when(access.supports("CONTRACT")).thenReturn(true);when(access.canView("CONTRACT",8L,7L,3L)).thenReturn(true);
        LocalDateTime due=LocalDateTime.of(2026,7,13,10,0);
        when(mapper.selectBusinessTodoSummary(anyMap())).thenReturn(Map.of("active_count",3L,"overdue_count",1L,"nearest_due_at",due));
        when(mapper.selectBusinessTodoOwners(anyMap())).thenReturn(List.of(11L,12L));
        when(mapper.selectBusinessRecentAction(anyMap())).thenReturn(Map.of("action_type","TRANSFER"));

        TodoBusinessSummary value=service().summary("CONTRACT",8L,actor);

        assertEquals(3,value.activeCount());assertEquals(1,value.overdueCount());assertEquals(due,value.nearestDueAt());assertEquals(List.of(11L,12L),value.ownerIds());assertEquals("TRANSFER",value.recentAction().get("action_type"));
    }

    @Test void rejectsBusinessOutsideDataScope()
    {
        when(access.supports("CASE")).thenReturn(true);when(access.canView("CASE",9L,7L,3L)).thenReturn(false);
        TodoException error=assertThrows(TodoException.class,()->service().summary("CASE",9L,actor));
        assertEquals("TODO_ACCESS_DENIED",error.getBusinessCode());
    }

    @Test void returnsChronologicalVisibleRootChain()
    {
        when(mapper.selectRootTodo(1L)).thenReturn(Map.of("business_type","CONTRACT","business_id",8L));
        when(access.supports("CONTRACT")).thenReturn(true);when(access.canView("CONTRACT",8L,7L,3L)).thenReturn(true);
        when(mapper.selectTodoChain(anyMap())).thenReturn(List.of(Map.of("todo_id",1L),Map.of("todo_id",2L),Map.of("todo_id",3L)));

        TodoChainView chain=service().chain(1L,actor);

        assertEquals(1L,chain.rootTodoId());assertEquals(List.of(1L,2L,3L),chain.nodes().stream().map(row->Long.valueOf(String.valueOf(row.get("todo_id")))).toList());
    }

    @Test void exposesOnlyServerAuthorizedActionsForBusinessTodo()
    {
        when(access.supports("LEAD")).thenReturn(true);when(access.canView("LEAD",8L,7L,3L)).thenReturn(true);
        TodoInstance todo=new TodoInstance();todo.setTodoId(21L);todo.setStatus("SUBMITTED");todo.setOwnerId(9L);
        when(mapper.selectBusinessTodos(anyMap())).thenReturn(List.of(Map.of("todo_id",21L,"status","SUBMITTED","owner_id",9L)));
        when(mapper.selectAllowedActionFacts(List.of(21L),7L,3L))
                .thenReturn(List.of(Map.of("todo_id",21L,"can_claim",0,"can_review",1)));

        List<Map<String,Object>> rows=service().businessTodos("LEAD",8L,actor);

        assertEquals(List.of("return"),rows.get(0).get("allowedActions"));
    }

    @Test void bulkActionsUseOneFactQueryForManyTodos()
    {
        TodoInstance claim=todo(21L,"CREATED",null);
        TodoInstance owned=todo(22L,"SUBMITTED",7L);
        TodoInstance review=todo(23L,"SUBMITTED",9L);
        when(mapper.selectAllowedActionFacts(List.of(21L,22L,23L),7L,3L))
                .thenReturn(List.of(
                        Map.of("todo_id",21L,"can_claim",1,"can_review",0),
                        Map.of("todo_id",22L,"can_claim",0,"can_review",0),
                        Map.of("todo_id",23L,"can_claim",0,"can_review",1)));

        Map<Long,List<String>> actions=service().allowedActions(
                List.of(claim,owned,review),actor);

        assertEquals(List.of("claim"),actions.get(21L));
        assertEquals(List.of("complete","transfer","cancel"),actions.get(22L));
        assertEquals(List.of("return"),actions.get(23L));
        verify(mapper).selectAllowedActionFacts(List.of(21L,22L,23L),7L,3L);
        verify(todoAccess,never()).canClaim(
                org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(todoAccess,never()).canReview(
                org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
    }

    private TodoInstance todo(Long id,String status,Long ownerId)
    {
        TodoInstance todo=new TodoInstance();
        todo.setTodoId(id);todo.setStatus(status);todo.setOwnerId(ownerId);
        return todo;
    }

    private TodoBusinessViewService service(){return new TodoBusinessViewService(mapper,List.of(access),todoAccess);}
}
