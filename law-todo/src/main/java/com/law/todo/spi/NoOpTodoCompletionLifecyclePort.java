package com.law.todo.spi;

import java.util.Map;

import com.law.todo.domain.model.TodoInstance;

/** Default lifecycle observer. Deployments may replace it with one real observer bean. */
public class NoOpTodoCompletionLifecyclePort implements TodoCompletionLifecyclePort
{
    @Override public void afterBusinessCompletion(TodoInstance todo,Map<String,Object> routingPayload){ }

    @Override public void afterRouting(TodoInstance todo,Map<String,Object> routingPayload){ }
}
