package com.law.todo.spi;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Supplies the inert lifecycle observer unless a deployment contributes a
 * transactional observer of its own.
 */
@Configuration(proxyBeanMethods = false)
public class TodoCompletionLifecycleConfiguration
{
    @Bean
    @ConditionalOnMissingBean(TodoCompletionLifecyclePort.class)
    TodoCompletionLifecyclePort todoCompletionLifecyclePort()
    {
        return new NoOpTodoCompletionLifecyclePort();
    }
}
