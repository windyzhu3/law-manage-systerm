package com.law.todo.spi;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
        if(businessType==null||businessType.isBlank()||businessId==null||businessId<=0)return Optional.empty();
        return positive(mapper.selectBusinessOwner(businessType.trim().toUpperCase(),businessId));
    }

    @Override public Optional<Long> supervisor(long userId,int levels)
    {
        if(userId<=0||levels<=0)return Optional.empty();
        return positive(mapper.selectDepartmentSupervisor(userId,levels));
    }

    @Override @Transactional
    public Optional<Long> roundRobin(String strategyKey,List<Long> sortedAvailableCandidates)
    {
        if(strategyKey==null||strategyKey.isBlank())return Optional.empty();
        List<Long> candidates=normalized(sortedAvailableCandidates);
        if(candidates.isEmpty())return Optional.empty();
        Long selected=mapper.selectAndAdvanceRoundRobin(strategyKey.trim(),candidates);
        return candidates.contains(selected)?Optional.of(selected):Optional.empty();
    }

    @Override public boolean isAvailable(long userId,LocalDateTime effectiveAt)
    {
        return userId>0&&effectiveAt!=null&&mapper.countAvailableUser(userId,effectiveAt)>0;
    }

    @Override public Optional<Long> delegateFor(long userId,LocalDateTime effectiveAt)
    {
        if(userId<=0||effectiveAt==null)return Optional.empty();
        return positive(mapper.selectActiveDelegate(userId,effectiveAt));
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

    private Optional<Long> positive(Long value)
    {
        return value==null||value<=0?Optional.empty():Optional.of(value);
    }
}
