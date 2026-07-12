package com.law.todo.domain;

import java.time.LocalDateTime;
import com.law.todo.domain.model.TodoInstance;

public final class TodoExceptionPolicy
{
    public void requireMutable(TodoInstance todo)
    {
        if(todo==null) throw new TodoException("TODO_NOT_FOUND","待办不存在");
        if(TodoStatus.fromCode(todo.getStatus()).isTerminal()) throw new TodoException("TODO_TERMINAL","终态待办不能执行异常操作");
    }
    public void requireExtension(LocalDateTime current,LocalDateTime replacement)
    {
        if(current==null) throw new TodoException("TODO_SLA_NOT_FOUND","待办没有 SLA 记录");
        if(replacement==null||!replacement.isAfter(current)) throw new TodoException("TODO_SLA_WAIVER_INVALID","新截止时间必须晚于原截止时间");
    }
}
