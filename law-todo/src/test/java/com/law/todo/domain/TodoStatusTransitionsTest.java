package com.law.todo.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class TodoStatusTransitionsTest
{
    @Test void followsMainLifecycle()
    {
        assertTrue(TodoStatusTransitions.canTransition(TodoStatus.CREATED, TodoStatus.CLAIMED));
        assertTrue(TodoStatusTransitions.canTransition(TodoStatus.CLAIMED, TodoStatus.IN_PROGRESS));
        assertTrue(TodoStatusTransitions.canTransition(TodoStatus.IN_PROGRESS, TodoStatus.SUBMITTED));
        assertTrue(TodoStatusTransitions.canTransition(TodoStatus.SUBMITTED, TodoStatus.COMPLETED));
    }

    @Test void supportsReturnAndResume()
    {
        assertTrue(TodoStatusTransitions.canTransition(TodoStatus.SUBMITTED, TodoStatus.RETURNED));
        assertTrue(TodoStatusTransitions.canTransition(TodoStatus.RETURNED, TodoStatus.IN_PROGRESS));
    }

    @Test void rejectsLeavingTerminalStatus()
    {
        assertFalse(TodoStatusTransitions.canTransition(TodoStatus.COMPLETED, TodoStatus.IN_PROGRESS));
        assertThrows(IllegalStateException.class,
                () -> TodoStatusTransitions.requireAllowed(TodoStatus.CANCELLED, TodoStatus.CREATED));
    }

    @Test void resolvesPersistedCode()
    {
        assertEquals(TodoStatus.IN_PROGRESS, TodoStatus.fromCode("IN_PROGRESS"));
        assertThrows(IllegalArgumentException.class, () -> TodoStatus.fromCode("UNKNOWN"));
    }
}
