package com.law.todo.spi;

import java.util.Map;

import com.law.todo.domain.model.TodoInstance;

/**
 * Replaceable completion lifecycle port for transactional observers such as
 * compliance journaling or external delivery staging.
 *
 * Implementations run inside the authoritative Todo completion transaction.
 * An exception therefore rejects the whole completion instead of exposing a
 * partially completed business graph.
 */
public interface TodoCompletionLifecyclePort
{
    void afterBusinessCompletion(TodoInstance todo,Map<String,Object> routingPayload);

    void afterRouting(TodoInstance todo,Map<String,Object> routingPayload);
}
