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
import com.law.todo.notification.StationNotificationAdapter;
import com.law.todo.notification.TodoNotificationPort.NotificationCommand;
import com.law.todo.notification.TodoNotificationPort;

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

    @Test void stationAdapterUsesCommandIdempotencyKeyAndMinimumPayload()
    {
        NotificationCommand command=new NotificationCommand("extension:31:approved",9L,7L,"EXTENSION_APPROVED","Extension approved","Due date updated");
        when(mapper.insertStationNotification(org.mockito.ArgumentMatchers.anyMap())).thenReturn(1,0);
        StationNotificationAdapter adapter=new StationNotificationAdapter(mapper);

        adapter.send(command);adapter.send(command);

        verify(mapper,org.mockito.Mockito.times(2)).insertStationNotification(org.mockito.ArgumentMatchers.argThat(row ->
            row.size()==7 && "extension:31:approved".equals(row.get("idempotencyKey"))));
    }

    @Test void notificationServiceDelegatesTypedCommandToPort()
    {
        TodoNotificationPort port=org.mockito.Mockito.mock(TodoNotificationPort.class);
        NotificationCommand command=new NotificationCommand("key-1",9L,7L,"REMINDER","Reminder",null);

        new TodoNotificationService(mapper,port).send(command);

        verify(port).send(command);
    }
}
