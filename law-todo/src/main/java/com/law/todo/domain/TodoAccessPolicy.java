package com.law.todo.domain;

import com.law.todo.domain.model.TodoInstance;

public interface TodoAccessPolicy
{
    boolean canClaim(TodoInstance todo,Long userId,Long deptId);
    default boolean canClaim(TodoInstance todo,Long userId){return canClaim(todo,userId,null);}
    boolean canOperate(TodoInstance todo,Long userId);
    boolean canView(TodoInstance todo,Long userId,Long deptId);
}
