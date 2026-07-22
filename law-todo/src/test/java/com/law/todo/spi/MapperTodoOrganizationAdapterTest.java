package com.law.todo.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.law.todo.mapper.TodoMapper;

class MapperTodoOrganizationAdapterTest
{
    @Test
    void resolvesOnlyActiveOrganizationMembersAndAvailability()
    {
        TodoMapper mapper=mock(TodoMapper.class);
        when(mapper.selectActiveUserIdsForRole(100L)).thenReturn(List.of(9L,7L,9L));
        when(mapper.selectActiveUserIdsForDepartment(200L)).thenReturn(List.of(8L));
        when(mapper.selectActiveUserIdsForPost(300L)).thenReturn(List.of(10L));
        when(mapper.countActiveUser(7L)).thenReturn(1);
        when(mapper.countActiveUser(99L)).thenReturn(0);

        TodoOrganizationPort adapter=new MapperTodoOrganizationAdapter(mapper);

        assertEquals(List.of(7L,9L),adapter.usersForRole(100L));
        assertEquals(List.of(8L),adapter.usersForDepartment(200L));
        assertEquals(List.of(10L),adapter.usersForPost(300L));
        assertTrue(adapter.isAvailable(7L,LocalDateTime.now()));
        assertFalse(adapter.isAvailable(99L,LocalDateTime.now()));
    }
}
