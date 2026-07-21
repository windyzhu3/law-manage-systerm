package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class TodoConfigurationCenterLocalizationMigrationContractTest
{
    private final String sql = resource("db/migration/V0_20_34__todo_configuration_center_localization.sql");

    @Test
    void localizesAllConfigurationMenusAndKeepsLegacyPageDisabled()
    {
        Map<String, String> menuNames = Map.of(
            "todo/config/template/index", "待办模板",
            "todo/config/trigger/index", "触发规则",
            "todo/config/sla/index", "SLA规则",
            "todo/config/dod/index", "完成条件",
            "todo/config/simulation/index", "模拟测试",
            "todo/config/release/index", "发布记录");
        menuNames.forEach((component, name) -> assertRow(
            "Missing localized menu " + name + " for " + component, name, component));
        assertTrue(sql.contains("where component='todo/config/index'"));
        assertTrue(sql.contains("visible='1',status='1'"));
    }

    @Test
    void localizesEveryConfigurationDictionaryTypeAndValue()
    {
        Map<String, String> typeNames = Map.ofEntries(
            Map.entry("law_todo_business_stage", "业务阶段"),
            Map.entry("law_todo_business_type", "业务类型"),
            Map.entry("law_todo_template_type", "模板类型"),
            Map.entry("law_todo_publish_status", "发布状态"),
            Map.entry("law_todo_trigger_mode", "触发方式"),
            Map.entry("law_todo_condition_operator", "条件操作符"),
            Map.entry("law_todo_owner_rule_type", "负责人规则类型"),
            Map.entry("law_todo_sla_type", "SLA类型"),
            Map.entry("law_todo_sla_unit", "SLA时间单位"),
            Map.entry("law_todo_sla_start_strategy", "SLA计时起点"),
            Map.entry("law_todo_timeout_strategy", "超时策略"),
            Map.entry("law_todo_dod_rule_type", "完成条件类型"),
            Map.entry("law_todo_rule_status", "规则状态"),
            Map.entry("law_todo_version_status", "版本状态"));
        typeNames.forEach((type, name) -> assertRow(
            "Missing localized dictionary type " + type, name, type));

        Map<String, String> representativeValues = Map.ofEntries(
            Map.entry("'线索','LEAD','law_todo_business_stage'", "业务阶段"),
            Map.entry("'客户','CUSTOMER','law_todo_business_type'", "业务类型"),
            Map.entry("'标准模板','STANDARD','law_todo_template_type'", "模板类型"),
            Map.entry("'已发布','PUBLISHED','law_todo_publish_status'", "发布状态"),
            Map.entry("'事件触发','EVENT','law_todo_trigger_mode'", "触发方式"),
            Map.entry("'不等于','NE','law_todo_condition_operator'", "条件操作符"),
            Map.entry("'业务负责人','BUSINESS_OWNER','law_todo_owner_rule_type'", "负责人规则类型"),
            Map.entry("'响应型','RESPONSE','law_todo_sla_type'", "SLA类型"),
            Map.entry("'分钟','MINUTE','law_todo_sla_unit'", "SLA时间单位"),
            Map.entry("'待办创建时','TODO_CREATED','law_todo_sla_start_strategy'", "SLA计时起点"),
            Map.entry("'自动动作','AUTO_ACTION','law_todo_timeout_strategy'", "超时策略"),
            Map.entry("'任务完成条件','TASK','law_todo_dod_rule_type'", "完成条件类型"),
            Map.entry("'启用','0','law_todo_rule_status'", "规则状态"),
            Map.entry("'已回滚','ROLLED_BACK','law_todo_version_status'", "版本状态"),
            Map.entry("'已退役','RETIRED','law_todo_version_status'", "版本状态"));
        representativeValues.forEach((row, type) -> {
            String[] values = row.replace("'", "").split(",");
            assertRow("Missing localized value for " + type + ": " + row, values);
        });
    }

    private void assertRow(String message, String... values)
    {
        StringBuilder expression = new StringBuilder("select\\s+");
        for (int index = 0; index < values.length; index++)
        {
            if (index > 0)
            {
                expression.append("\\s*,\\s*");
            }
            expression.append("'").append(Pattern.quote(values[index])).append("'(?:\\s+[a-z_]+)?");
        }
        assertTrue(Pattern.compile(expression.toString(), Pattern.CASE_INSENSITIVE).matcher(sql).find(), message);
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
