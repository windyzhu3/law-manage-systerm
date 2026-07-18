package com.ruoyi.web.todo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;

import com.ruoyi.web.controller.todo.TodoHistoricalMigrationReadinessController;

class HistoricalMigrationPreflightApiTest
{
    @Test void exposes_g04_preflight_with_admission_view_permission() throws Exception
    {
        Method method=TodoHistoricalMigrationReadinessController.class.getDeclaredMethod("preflight",String.class);

        assertEquals("/preflight",method.getAnnotation(GetMapping.class).value()[0]);
        assertTrue(method.getAnnotation(PreAuthorize.class).value().contains("todo:admission:view"));
    }
}
