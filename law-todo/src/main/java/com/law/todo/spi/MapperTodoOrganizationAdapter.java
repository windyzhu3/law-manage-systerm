package com.law.todo.spi;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.law.todo.mapper.TodoMapper;

/** RuoYi organization adapter used by typed Todo owner rules. */
@Component
public class MapperTodoOrganizationAdapter implements TodoOrganizationPort
{
    private final TodoMapper mapper;

    public MapperTodoOrganizationAdapter(TodoMapper mapper)
    {
        this.mapper=mapper;
    }

    @Override public List<Long> usersForRole(long roleId)
    {
        return normalized(mapper.selectActiveUserIdsForRole(roleId));
    }

    @Override public List<Long> usersForDepartment(long departmentId)
    {
        return normalized(mapper.selectActiveUserIdsForDepartment(departmentId));
    }

    @Override public List<Long> usersForPost(long postId)
    {
        return normalized(mapper.selectActiveUserIdsForPost(postId));
    }

    @Override public Optional<Long> businessOwner(String businessType,Long businessId)
    {
        return Optional.empty();
    }

    @Override public Optional<Long> supervisor(long userId,int levels)
    {
        return Optional.empty();
    }

    @Override public Optional<Long> roundRobin(String strategyKey,List<Long> sortedAvailableCandidates)
    {
        return Optional.empty();
    }

    @Override public boolean isAvailable(long userId,LocalDateTime effectiveAt)
    {
        return mapper.countActiveUser(userId)>0;
    }

    @Override public Optional<Long> delegateFor(long userId,LocalDateTime effectiveAt)
    {
        return Optional.empty();
    }

    @Override public List<Long> assignmentLevel(int level,String businessType,Long businessId)
    {
        return List.of();
    }

    private List<Long> normalized(List<Long> values)
    {
        if(values==null)return List.of();
        return values.stream().filter(value->value!=null&&value>0).distinct().sorted().toList();
    }
}
