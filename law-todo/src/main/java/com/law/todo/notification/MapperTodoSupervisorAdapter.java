package com.law.todo.notification;

import java.util.List;

import org.springframework.stereotype.Component;

import com.law.todo.mapper.TodoMapper;
import com.law.todo.spi.TodoSupervisorPort;

@Component
public class MapperTodoSupervisorAdapter implements TodoSupervisorPort
{
    private final TodoMapper mapper;
    public MapperTodoSupervisorAdapter(TodoMapper mapper){this.mapper=mapper;}

    @Override public List<Long> supervisors(Long ownerId,Long ownerDeptId)
    {
        if(ownerId==null&&ownerDeptId==null)return List.of();
        List<Long> resolved=mapper.selectGovernedSupervisors(ownerId,ownerDeptId);
        if(resolved==null)return List.of();
        return resolved.stream().filter(value->value!=null&&value>0).distinct().sorted().toList();
    }
}
