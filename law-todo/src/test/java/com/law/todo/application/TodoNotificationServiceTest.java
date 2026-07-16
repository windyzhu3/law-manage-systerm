package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.Map;

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

    @Test void stationAdapterSeparatesSourceIdentityFromDerivedDeliveryIdentity()
    {
        NotificationCommand command=new NotificationCommand("extension:31:approved",9L,7L,"EXTENSION_APPROVED","Extension approved","Due date updated");
        StationNotificationAdapter adapter=new StationNotificationAdapter(mapper);

        adapter.send(command);

        verify(mapper).insertStationNotification(org.mockito.ArgumentMatchers.argThat(row ->
            row.size()==8 && "extension:31:approved".equals(row.get("sourceId"))
                && !row.get("sourceId").equals(row.get("deliveryKey"))));
    }

    @Test void deliveryIdentityUsesTheWholeRequiredTupleAndExactTupleReplays()
    {
        StationNotificationAdapter adapter=new StationNotificationAdapter(mapper);
        adapter.send(new NotificationCommand("same-source",9L,7L,"TYPE_A","one",null));
        adapter.send(new NotificationCommand("same-source",9L,8L,"TYPE_A","two",null));
        adapter.send(new NotificationCommand("same-source",9L,7L,"TYPE_B","three",null));
        adapter.send(new NotificationCommand("same-source",10L,7L,"TYPE_A","four",null));
        adapter.send(new NotificationCommand("same-source",9L,7L,"TYPE_A","replay",null));
        ArgumentCaptor<Map<String,Object>> captor=ArgumentCaptor.forClass(Map.class);
        verify(mapper,org.mockito.Mockito.times(5)).insertStationNotification(captor.capture());
        List<String> keys=captor.getAllValues().stream().map(row->String.valueOf(row.get("deliveryKey"))).toList();
        assertNotEquals(keys.get(0),keys.get(1));assertNotEquals(keys.get(0),keys.get(2));assertNotEquals(keys.get(0),keys.get(3));
        assertEquals(keys.get(0),keys.get(4));
    }

    @Test void notificationServiceDelegatesTypedCommandToPort()
    {
        TodoNotificationPort port=org.mockito.Mockito.mock(TodoNotificationPort.class);
        NotificationCommand command=new NotificationCommand("key-1",9L,7L,"REMINDER","Reminder",null);

        new TodoNotificationService(mapper,port).send(command);

        verify(port).send(command);
    }
}
