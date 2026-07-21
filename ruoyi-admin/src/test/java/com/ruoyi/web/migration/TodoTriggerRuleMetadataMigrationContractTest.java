package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TodoTriggerRuleMetadataMigrationContractTest
{
    @Test
    void addsUniqueNonNullRuleIdentityWithDeterministicBackfill()
    {
        String sql = resource("db/migration/V0_20_35__todo_trigger_rule_metadata.sql").replaceAll("\\s+", " ").toLowerCase();
        assertTrue(sql.contains("add column rule_code varchar(64)"));
        assertTrue(sql.contains("add column rule_name varchar(128)"));
        assertTrue(sql.contains("concat('trigger_',trigger_rule_id)"));
        assertTrue(sql.contains("modify column rule_code varchar(64) not null"));
        assertTrue(sql.contains("modify column rule_name varchar(128) not null"));
        assertTrue(sql.contains("unique key uk_todo_trigger_rule_code (rule_code)"));
    }

    private String resource(String path)
    {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path))
        {
            assertNotNull(input, () -> "Migration resource must exist: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException exception)
        {
            throw new AssertionError("Unable to read migration resource: " + path, exception);
        }
    }
}
