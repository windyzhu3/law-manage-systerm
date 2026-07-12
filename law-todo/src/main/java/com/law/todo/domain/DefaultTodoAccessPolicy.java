package com.law.todo.domain;

import java.util.Map;
import org.springframework.stereotype.Component;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;

@Component
public class DefaultTodoAccessPolicy implements TodoAccessPolicy
{
    private final TodoMapper mapper;
    public DefaultTodoAccessPolicy(TodoMapper mapper){this.mapper=mapper;}
    public boolean canClaim(TodoInstance todo,Long userId,Long deptId){if(todo.getOwnerId()!=null)return todo.getOwnerId().equals(userId);return mapper.countCandidateAccess(todo.getTodoId(),userId,deptId)>0;}
    public boolean canOperate(TodoInstance todo,Long userId){return todo.getOwnerId()!=null&&todo.getOwnerId().equals(userId);}
    public boolean canView(TodoInstance todo,Long userId,Long deptId){return canOperate(todo,userId)||(deptId!=null&&deptId.equals(todo.getOwnerDeptId()))||canClaim(todo,userId,deptId)||mapper.countCcAccess(todo.getTodoId(),userId)>0||mapper.countSupervisorAccess(todo.getTodoId(),userId)>0;}
    public boolean canReview(TodoInstance todo,Long userId){return mapper.countSupervisorAccess(todo.getTodoId(),userId)>0;}
}
