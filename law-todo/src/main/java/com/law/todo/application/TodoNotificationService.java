package com.law.todo.application;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.todo.mapper.TodoMapper;

@Service
public class TodoNotificationService
{
    private final TodoMapper mapper;public TodoNotificationService(TodoMapper mapper){this.mapper=mapper;}
    public List<Map<String,Object>> list(Long userId,String status){return mapper.selectNotifications(userId,status);}
    @Transactional public boolean read(Long id,Long userId){return mapper.markNotificationRead(id,userId)>0;}
}
