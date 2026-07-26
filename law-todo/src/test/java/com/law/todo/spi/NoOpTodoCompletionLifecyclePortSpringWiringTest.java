package com.law.todo.spi;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class NoOpTodoCompletionLifecyclePortSpringWiringTest
{
    @Test
    void registersTheDefaultLifecyclePortWhenNoDeploymentObserverExists()
    {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext())
        {
            context.register(TodoCompletionLifecycleConfiguration.class);
            context.refresh();

            assertInstanceOf(NoOpTodoCompletionLifecyclePort.class,
                context.getBean(TodoCompletionLifecyclePort.class));
        }
    }
}
