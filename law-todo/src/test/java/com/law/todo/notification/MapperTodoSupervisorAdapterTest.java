package com.law.todo.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.law.todo.mapper.TodoMapper;

class MapperTodoSupervisorAdapterTest
{
    @Test void resolvesAndNormalizesGovernedSupervisorsFromOwnerOrDepartment()
    {
        TodoMapper mapper=mock(TodoMapper.class);when(mapper.selectGovernedSupervisors(null,3L)).thenReturn(Arrays.asList(10L,9L,10L,null,-1L));
        assertEquals(List.of(9L,10L),new MapperTodoSupervisorAdapter(mapper).supervisors(null,3L));verify(mapper).selectGovernedSupervisors(null,3L);
    }

    @Test void unresolvedAssignmentHasNoSupervisorQueryOrRecipient()
    {
        TodoMapper mapper=mock(TodoMapper.class);assertEquals(List.of(),new MapperTodoSupervisorAdapter(mapper).supervisors(null,null));
    }
}
