package com.law.todo.application;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.notification.StationNotificationAdapter;
import com.law.todo.notification.TodoNotificationPort;
import com.law.todo.notification.TodoNotificationPort.NotificationCommand;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class TodoNotificationService
{
    private final TodoMapper mapper;private final TodoNotificationPort port;
    public TodoNotificationService(TodoMapper mapper){this(mapper,new StationNotificationAdapter(mapper));}
    @Autowired public TodoNotificationService(TodoMapper mapper,TodoNotificationPort port){this.mapper=mapper;this.port=port;}
    public List<Map<String,Object>> list(Long userId,String status){return mapper.selectNotifications(userId,status);}
    @Transactional public boolean read(Long id,Long userId){return mapper.markNotificationRead(id,userId)>0;}
    @Transactional public void send(NotificationCommand command){port.send(command);}
}
