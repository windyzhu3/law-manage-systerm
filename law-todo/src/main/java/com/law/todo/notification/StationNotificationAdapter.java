package com.law.todo.notification;

import java.util.LinkedHashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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
        row.put("sourceId",command.idempotencyKey());
        row.put("deliveryKey",deliveryKey(command.todoId(),command.recipientUserId(),command.type(),command.idempotencyKey()));row.put("todoId",command.todoId());
        row.put("recipientUserId",command.recipientUserId());row.put("type",command.type());
        row.put("title",command.title());row.put("content",command.content());row.put("status","UNREAD");
        mapper.insertStationNotification(row);
    }
    private String deliveryKey(Long todoId,Long recipient,String type,String source)
    {
        String canonical=part(String.valueOf(todoId))+part(String.valueOf(recipient))+part(type)+part(source);
        try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException ex){throw new IllegalStateException("SHA-256 is required",ex);}
    }
    private String part(String value){byte[] bytes=value.getBytes(StandardCharsets.UTF_8);return bytes.length+":"+value+"|";}
}
