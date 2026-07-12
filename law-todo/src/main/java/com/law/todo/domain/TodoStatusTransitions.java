package com.law.todo.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class TodoStatusTransitions
{
    private static final Map<TodoStatus, Set<TodoStatus>> ALLOWED = new EnumMap<>(TodoStatus.class);

    static
    {
        allow(TodoStatus.CREATED, TodoStatus.CLAIMED, TodoStatus.IN_PROGRESS, TodoStatus.CANCELLED);
        allow(TodoStatus.CLAIMED, TodoStatus.IN_PROGRESS, TodoStatus.CANCELLED);
        allow(TodoStatus.IN_PROGRESS, TodoStatus.SUBMITTED, TodoStatus.CANCELLED);
        allow(TodoStatus.SUBMITTED, TodoStatus.COMPLETED, TodoStatus.RETURNED, TodoStatus.CANCELLED);
        allow(TodoStatus.RETURNED, TodoStatus.IN_PROGRESS, TodoStatus.CANCELLED);
    }

    private TodoStatusTransitions() { }

    public static boolean canTransition(TodoStatus from, TodoStatus to)
    {
        return from == to || ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static void requireAllowed(TodoStatus from, TodoStatus to)
    {
        if (!canTransition(from, to)) throw new TodoException("TODO_STATE_TRANSITION_INVALID", "待办状态不允许从 " + from + " 转换为 " + to);
    }

    private static void allow(TodoStatus from, TodoStatus first, TodoStatus... rest)
    {
        ALLOWED.put(from, EnumSet.of(first, rest));
    }
}
