package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
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
import com.law.todo.mapper.TodoMapper;
import com.law.todo.spi.TodoBusinessAccessChecker;

@ExtendWith(MockitoExtension.class)
class TodoBusinessViewServiceTest
{
    @Mock TodoMapper mapper;@Mock TodoBusinessAccessChecker access;
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

    private TodoBusinessViewService service(){return new TodoBusinessViewService(mapper,List.of(access));}
}
