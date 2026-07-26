package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LeadTodoUtf8RepairMigrationContractTest
{
    private static final String MIGRATION =
        "/db/migration/V0_20_56__lead_todo_utf8_text_repair.sql";

    @Test
    void migrationRepairsEveryVisibleLegacyLeadMenuAndDictionaryLabel() throws IOException
    {
        String sql = readMigration();
        String normalized = normalize(sql);

        Map<String, String> menuLabels = Map.of(
            "lead", "线索管理",
            "dashboard", "线索工作台",
            "all", "全部线索",
            "mine", "我的线索",
            "pool", "线索公海",
            "followup", "跟进任务",
            "recycle", "线索回收站",
            "settings", "线索设置"
        );
        menuLabels.forEach((path, label) -> {
            assertTrue(normalized.contains("when '" + path + "' then '" + label + "'"),
                () -> "Missing menu text repair for " + path);
        });

        Map<String, Set<String>> dictionaryLabels = Map.of(
            "law_lead_status", Set.of("待分配", "待跟进", "跟进中", "已转化", "无效", "已关闭"),
            "law_lead_priority", Set.of("高优先级", "中优先级", "低优先级"),
            "law_lead_follow_type", Set.of("电话", "微信", "面谈", "邮件"),
            "law_lead_pool_status", Set.of("已有归属", "公海线索"),
            "law_lead_setting_type", Set.of("线索来源", "线索标签", "无效原因")
        );
        dictionaryLabels.forEach((dictType, labels) -> {
            assertTrue(normalized.contains("when '" + dictType + "' then case dict_value"),
                () -> "Missing governed CASE branch for " + dictType);
            labels.forEach(label -> assertTrue(normalized.contains("'" + label + "'"),
                () -> "Missing dictionary label: " + label));
        });
    }

    @Test
    void migrationRepairsStableLeadSettingsWithoutTouchingIdentityOrWorkflowDefinitions() throws IOException
    {
        String sql = readMigration();
        String normalized = normalize(sql);

        for (String label : Set.of(
            "无法联系", "重复线索", "明确拒绝",
            "线上咨询", "客户转介绍", "市场活动", "到所咨询",
            "重点客户", "企业客户"))
        {
            assertTrue(normalized.contains("'" + label + "'"),
                () -> "Missing lead setting label: " + label);
        }
        for (String forbiddenTable : Set.of(
            "sys_role", "sys_role_menu", "sys_user", "sys_user_role", "sys_dept",
            "todo_template", "todo_template_version", "todo_trigger_rule"))
        {
            assertFalse(normalized.matches("(?s).*\\b(?:insert\\s+into|update|delete\\s+from)\\s+`?"
                    + forbiddenTable + "`?\\b.*"),
                () -> "Text repair must not mutate " + forbiddenTable);
        }
    }

    @Test
    void migrationItselfContainsValidChineseAndNoKnownMojibakeMarkers() throws IOException
    {
        String sql = readMigration();
        for (String marker : Set.of("\uFFFD", "Ã", "Â", "çº", "å¾", "æˆ", "è·", "é…"))
        {
            assertFalse(sql.contains(marker), () -> "Migration contains mojibake marker: " + marker);
        }
    }

    private String readMigration() throws IOException
    {
        try (InputStream resource = getClass().getResourceAsStream(MIGRATION))
        {
            assertNotNull(resource, "The forward UTF-8 text repair migration must be packaged at runtime");
            return new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String normalize(String value)
    {
        return value.replaceAll("\\s+", " ").toLowerCase();
    }
}
