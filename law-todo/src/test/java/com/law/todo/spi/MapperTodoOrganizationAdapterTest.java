package com.law.todo.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.law.todo.mapper.TodoMapper;

class MapperTodoOrganizationAdapterTest
{
    private static final LocalDateTime NOW=LocalDateTime.of(2026,7,25,10,30);

    @Test
    void resolvesOnlyActiveOrganizationMembersAndAvailability()
    {
        TodoMapper mapper=mock(TodoMapper.class);
        when(mapper.selectActiveUserIdsForRole(100L)).thenReturn(List.of(9L,7L,9L));
        when(mapper.selectActiveUserIdsForDepartment(200L)).thenReturn(List.of(8L));
        when(mapper.selectActiveUserIdsForPost(300L)).thenReturn(List.of(10L));
        when(mapper.countAvailableUser(7L,NOW)).thenReturn(1);
        when(mapper.countAvailableUser(99L,NOW)).thenReturn(0);

        TodoOrganizationPort adapter=new MapperTodoOrganizationAdapter(mapper);

        assertEquals(List.of(7L,9L),adapter.usersForRole(100L));
        assertEquals(List.of(8L),adapter.usersForDepartment(200L));
        assertEquals(List.of(10L),adapter.usersForPost(300L));
        assertTrue(adapter.isAvailable(7L,NOW));
        assertFalse(adapter.isAvailable(99L,NOW));
    }

    @Test
    void resolvesBusinessOwnerAndExactSupervisorLevel()
    {
        TodoMapper mapper=mock(TodoMapper.class);
        when(mapper.selectBusinessOwner("LEAD",41L)).thenReturn(11L);
        when(mapper.selectDepartmentSupervisor(11L,2)).thenReturn(19L);
        MapperTodoOrganizationAdapter adapter=new MapperTodoOrganizationAdapter(mapper);

        assertEquals(11L,adapter.businessOwner("LEAD",41L).orElseThrow());
        assertEquals(19L,adapter.supervisor(11L,2).orElseThrow());
        assertEquals(Optional.empty(),adapter.supervisor(11L,0));
    }

    @Test
    void roundRobinAdvancesAtomicallyAcrossAvailableCandidates()
    {
        TodoMapper mapper=mock(TodoMapper.class);
        when(mapper.selectAndAdvanceRoundRobin("lead:sales",List.of(11L,12L))).thenReturn(12L);

        MapperTodoOrganizationAdapter adapter=new MapperTodoOrganizationAdapter(mapper);

        assertEquals(12L,adapter.roundRobin("lead:sales",List.of(11L,12L)).orElseThrow());
    }

    @Test
    void roundRobinNormalizesCandidatesAndRejectsSelectionOutsidePool()
    {
        TodoMapper mapper=mock(TodoMapper.class);
        when(mapper.selectAndAdvanceRoundRobin("lead:sales",List.of(11L,12L))).thenReturn(99L);

        MapperTodoOrganizationAdapter adapter=new MapperTodoOrganizationAdapter(mapper);

        assertEquals(Optional.empty(),adapter.roundRobin("lead:sales",java.util.Arrays.asList(12L,11L,12L,null)));
    }

    @Test
    void mapperRoundRobinMovesFromLockedCursorAndPersistsVersionConditionally()
    {
        TodoMapper mapper=mock(TodoMapper.class,CALLS_REAL_METHODS);
        when(mapper.selectRoundRobinCursorForUpdate("lead:sales"))
                .thenReturn(Map.of("lastUserId",11L,"version",4));
        when(mapper.advanceRoundRobinCursorConditionally("lead:sales",12L,11L,4))
                .thenReturn(1);

        Long selected=mapper.selectAndAdvanceRoundRobin("lead:sales",List.of(12L,11L,12L));

        assertEquals(12L,selected);
        verify(mapper).insertRoundRobinCursorIfAbsent("lead:sales");
        verify(mapper).advanceRoundRobinCursorConditionally("lead:sales",12L,11L,4);
    }

    @Test
    void unavailablePrimaryUsesActiveDelegate()
    {
        TodoMapper mapper=mock(TodoMapper.class);
        when(mapper.countAvailableUser(11L,NOW)).thenReturn(0);
        when(mapper.selectActiveDelegate(11L,NOW)).thenReturn(12L);
        MapperTodoOrganizationAdapter adapter=new MapperTodoOrganizationAdapter(mapper);

        assertFalse(adapter.isAvailable(11L,NOW));
        assertEquals(12L,adapter.delegateFor(11L,NOW).orElseThrow());
    }
}
