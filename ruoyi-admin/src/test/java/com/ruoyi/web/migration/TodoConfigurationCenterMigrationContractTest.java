package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class TodoConfigurationCenterMigrationContractTest
{
    private final String sql = resource("db/migration/V0_20_30__todo_configuration_center.sql");

    @Test
    void definesReusableRulesAndDrawerMenus()
    {
        assertTrue(sql.contains("create table todo_sla_rule"));
        assertTrue(sql.contains("create table todo_dod_rule"));
        assertTrue(sql.contains("create table todo_template_draft_rule_ref"));
        assertTrue(sql.contains("create table todo_simulation_record"));
        for (String component : List.of(
            "todo/config/template/index", "todo/config/trigger/index",
            "todo/config/sla/index", "todo/config/dod/index",
            "todo/config/simulation/index", "todo/config/release/index"))
        {
            assertTrue(sql.contains(component));
        }
        for (String dictionary : List.of(
            "law_todo_business_stage", "law_todo_business_type", "law_todo_template_type",
            "law_todo_publish_status", "law_todo_trigger_mode", "law_todo_condition_operator",
            "law_todo_owner_rule_type", "law_todo_sla_type", "law_todo_sla_unit",
            "law_todo_sla_start_strategy", "law_todo_timeout_strategy",
            "law_todo_dod_rule_type", "law_todo_rule_status", "law_todo_version_status"))
        {
            assertTrue(sql.contains(dictionary));
        }
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
