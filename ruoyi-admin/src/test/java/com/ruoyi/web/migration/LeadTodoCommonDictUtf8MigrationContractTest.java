package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LeadTodoCommonDictUtf8MigrationContractTest
{
    private static final String MIGRATION =
        "/db/migration/V0_20_57__lead_todo_common_dictionary_utf8_repair.sql";

    @Test void repairsThePublishedLeadTodoBooleanDictionaryByStableIdentity() throws Exception
    {
        String sql=read().replaceAll("\\s+"," ").toLowerCase();
        assertTrue(sql.contains("dict_type='law_yes_no_flag'"));
        assertTrue(sql.contains("when '1' then '是'"));
        assertTrue(sql.contains("when '0' then '否'"));
        assertTrue(sql.contains("dict_value in ('0','1')"));
    }

    @Test void doesNotMutateWorkflowIdentityOrSecurityBindings() throws Exception
    {
        String sql=read().replaceAll("\\s+"," ").toLowerCase();
        for(String table:Set.of("sys_user","sys_role","sys_role_menu","sys_user_role",
                "todo_template","todo_template_version","todo_trigger_rule"))
            assertFalse(sql.matches("(?s).*\\b(?:insert\\s+into|update|delete\\s+from)\\s+`?"
                +table+"`?\\b.*"),table);
    }

    @Test void migrationTextIsUtf8WithoutKnownMojibakeMarkers() throws Exception
    {
        String sql=read();
        for(String marker:Set.of("\uFFFD","Ã","Â","æ˜","å"))
            assertFalse(sql.contains(marker),marker);
    }

    private String read() throws Exception
    {
        try(InputStream resource=getClass().getResourceAsStream(MIGRATION))
        {
            assertNotNull(resource,"The lead Todo common dictionary repair must be packaged");
            return new String(resource.readAllBytes(),StandardCharsets.UTF_8);
        }
    }
}
