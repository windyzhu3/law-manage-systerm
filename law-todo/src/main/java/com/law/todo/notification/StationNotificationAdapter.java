package com.law.todo.notification;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoMapper;

@Component
public class StationNotificationAdapter implements TodoNotificationPort
{
    private final TodoMapper mapper;
    public StationNotificationAdapter(TodoMapper mapper){this.mapper=mapper;}

    @Override public void send(NotificationCommand command)
    {
        if(command==null||command.idempotencyKey()==null||command.idempotencyKey().isBlank()
                ||command.todoId()==null||command.recipientUserId()==null||command.type()==null||command.type().isBlank())
            throw new TodoException("TODO_NOTIFICATION_INVALID","Notification command is incomplete");
        Map<String,Object> row=new LinkedHashMap<>();
        row.put("idempotencyKey",command.idempotencyKey());row.put("todoId",command.todoId());
        row.put("recipientUserId",command.recipientUserId());row.put("type",command.type());
        row.put("title",command.title());row.put("content",command.content());row.put("status","UNREAD");
        mapper.insertStationNotification(row);
    }
}
