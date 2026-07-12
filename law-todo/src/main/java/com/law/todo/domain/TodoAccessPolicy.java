package com.law.todo.domain;

import com.law.todo.domain.model.TodoInstance;

public interface TodoAccessPolicy
{
    boolean canClaim(TodoInstance todo,Long userId);
    boolean canOperate(TodoInstance todo,Long userId);
    boolean canView(TodoInstance todo,Long userId,Long deptId);
}
