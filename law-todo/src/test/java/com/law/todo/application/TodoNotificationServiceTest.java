package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.law.todo.mapper.TodoMapper;

@ExtendWith(MockitoExtension.class)
class TodoNotificationServiceTest
{
    @Mock
    private TodoMapper mapper;

    @Test
    void marksOnlyCurrentUsersUnreadNotification()
    {
        when(mapper.markNotificationRead(9L, 7L)).thenReturn(1);

        assertTrue(new TodoNotificationService(mapper).read(9L, 7L));
        verify(mapper).markNotificationRead(9L, 7L);
    }

    @Test
    void returnsFalseWhenNotificationIsNotOwnedOrAlreadyRead()
    {
        when(mapper.markNotificationRead(9L, 7L)).thenReturn(0);

        assertFalse(new TodoNotificationService(mapper).read(9L, 7L));
    }
}
