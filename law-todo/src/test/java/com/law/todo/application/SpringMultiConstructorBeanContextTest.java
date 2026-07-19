package com.law.todo.application;

import com.law.todo.mapper.TodoHistoricalMigrationReadinessMapper;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.routing.RoutingGraphValidator;
import com.law.todo.routing.TodoRoutingEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class SpringMultiConstructorBeanContextTest
{
    @Test
    void springSelectsTheProductionConstructorForEveryUnambiguousTodoBean(@TempDir Path exportRoot)
    {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext())
        {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "todo.migration-export.temp-dir", exportRoot.toString())));
            context.registerBean(TodoMapper.class, () -> mock(TodoMapper.class));
            context.registerBean(TodoHistoricalMigrationReadinessMapper.class,
                () -> mock(TodoHistoricalMigrationReadinessMapper.class));
            context.register(HistoricalMigrationExportArchiveWriter.class, TodoAssignmentResolver.class,
                TodoHistoricalMigrationPreflightService.class, RoutingGraphValidator.class, TodoRoutingEngine.class);

            context.refresh();

            assertNotNull(context.getBean(HistoricalMigrationExportArchiveWriter.class));
            assertNotNull(context.getBean(TodoAssignmentResolver.class));
            assertNotNull(context.getBean(TodoHistoricalMigrationPreflightService.class));
            assertNotNull(context.getBean(RoutingGraphValidator.class));
            assertNotNull(context.getBean(TodoRoutingEngine.class));
        }
    }
}
