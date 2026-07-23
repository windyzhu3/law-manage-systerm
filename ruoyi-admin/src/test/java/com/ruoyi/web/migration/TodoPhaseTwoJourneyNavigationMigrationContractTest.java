package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class TodoPhaseTwoJourneyNavigationMigrationContractTest
{
    private final String sql=resource("db/migration/V0_20_43__todo_phase_two_journey_navigation.sql");
    private final String normalized=sql.replaceAll("\\s+"," ").toLowerCase();

    @Test
    void addsIdempotentHiddenJourneyRouteWithExistingTemplatePermission()
    {
        assertTrue(normalized.contains("'todo-template-journey'"));
        assertTrue(normalized.contains("'todo/config/journey/index'"));
        assertTrue(normalized.contains("'todo:template:list'"));
        assertTrue(normalized.contains("'1'"));
        assertTrue(normalized.contains("not exists"));
        assertTrue(normalized.contains("component='todo/config/journey/index'"));
    }

    @Test
    void keepsExistingTemplateAndResourceNavigationUntouched()
    {
        assertTrue(normalized.contains("component='todo/config/template/index'"));
        assertFalse(normalized.contains("update sys_menu"));
        assertFalse(normalized.contains("delete from sys_menu"));
        assertFalse(normalized.contains("todo/config/resource/index' set"));
    }

    private String resource(String path)
    {
        try(InputStream input=getClass().getClassLoader().getResourceAsStream(path))
        {
            assertNotNull(input,()->"Migration resource must exist: "+path);
            return new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }
        catch(IOException exception)
        {
            throw new AssertionError("Unable to read migration resource: "+path,exception);
        }
    }
}
