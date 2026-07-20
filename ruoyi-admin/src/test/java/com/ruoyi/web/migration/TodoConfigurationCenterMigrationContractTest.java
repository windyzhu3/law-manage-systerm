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
            assertTrue(sql.contains(component), () -> "Missing configuration page component: " + component);
        }
        for (String dictionary : List.of(
            "law_todo_business_stage", "law_todo_business_type", "law_todo_template_type",
            "law_todo_publish_status", "law_todo_trigger_mode", "law_todo_condition_operator",
            "law_todo_owner_rule_type", "law_todo_sla_type", "law_todo_sla_unit",
            "law_todo_sla_start_strategy", "law_todo_timeout_strategy",
            "law_todo_dod_rule_type", "law_todo_rule_status", "law_todo_version_status"))
        {
            assertTrue(sql.contains(dictionary), () -> "Missing configuration dictionary: " + dictionary);
        }
    }

    @Test
    void seedsRuntimeCompatibleDictionaryValues()
    {
        assertSeededValues("law_todo_rule_status", List.of("0", "1"));
        assertSeededValues("law_todo_sla_type", List.of("RESPONSE"));
        assertSeededValues("law_todo_sla_start_strategy", List.of("TODO_CREATED"));
        assertSeededValues("law_todo_dod_rule_type", List.of("TASK"));
        assertSeededValues("law_todo_owner_rule_type", List.of(
            "USER", "ROLE", "DEPT", "POST", "PAYLOAD", "BUSINESS_OWNER", "SUPERVISOR", "ROUND_ROBIN",
            "ASSIGNMENT_LEVEL"));
        assertTrue(!sql.contains("'PAYLOAD_FIELD','law_todo_owner_rule_type'"),
            "Owner dictionary must not expose an unsupported PAYLOAD_FIELD strategy");
    }

    @Test
    void definesMenuAndReleaseMetadataContracts()
    {
        assertTrue(sql.contains("select x.menu_name,@todo_engine_directory_id"),
            "Page menus must be children of the Todo Engine directory");
        assertTrue(sql.contains("1,0,'C','0','0',x.perms"),
            "Page menus must be catalog menus with menu_type C");
        assertTrue(sql.contains("update sys_menu set visible='1',status='1' where component='todo/config/index'"),
            "Legacy todo/config/index menu must be hidden and disabled");
        for (String permission : List.of(
            "todo:template:list", "todo:template:create", "todo:template:edit", "todo:template:copy",
            "todo:trigger:list", "todo:trigger:create", "todo:trigger:edit", "todo:trigger:toggle",
            "todo:sla-rule:list", "todo:sla-rule:create", "todo:sla-rule:edit", "todo:sla-rule:copy",
            "todo:sla-rule:toggle", "todo:dod-rule:list", "todo:dod-rule:create", "todo:dod-rule:edit",
            "todo:dod-rule:copy", "todo:dod-rule:toggle", "todo:simulation:list", "todo:simulation:simulate",
            "todo:release:list", "todo:release:publish", "todo:release:diff", "todo:release:rollback"))
        {
            assertTrue(sql.contains("'" + permission + "'"), () -> "Missing exact configuration permission: " + permission);
        }
        for (String column : List.of("change_summary", "impact_scope", "rollback_source_version_id"))
        {
            assertTrue(sql.contains("add column " + column), () -> "Missing release metadata column: " + column);
        }
    }

    private void assertSeededValues(String dictionary, List<String> values)
    {
        for (String value : values)
        {
            assertTrue(sql.contains("'" + value + "','" + dictionary + "'"),
                () -> "Missing seeded value " + value + " for dictionary " + dictionary);
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
