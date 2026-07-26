package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LeadRetryContactTimeMigrationContractTest
{
    private static final String MIGRATION =
        "/db/migration/V0_20_59__lead_retry_contact_time_contract.sql";

    @Test
    void publishesANewTd003VersionWithVisibleRequiredContactTime() throws Exception
    {
        String sql = normalized();
        assertTrue(sql.contains("where t.template_code='td-003'"));
        assertTrue(sql.contains("insert into todo_template_version"));
        assertTrue(sql.contains("'contactedat'"));
        assertTrue(sql.contains("'datetime'"));
        assertTrue(sql.contains("'联系时间'"));
        assertTrue(sql.contains("$.dod.config.requiredfields"));
        assertTrue(sql.contains("$.ui.config.fields"));
        assertFalse(sql.matches("(?s).*update\\s+todo_template_version\\b.*"));
    }

    @Test
    void advancesCurrentVersionAndActiveAssignmentPolicyWithoutTouchingIdentities()
            throws Exception
    {
        String sql = normalized();
        assertTrue(sql.contains("update todo_template t"));
        assertTrue(sql.contains("set t.current_version=@td003_new_version_no"));
        assertTrue(sql.contains("update biz_lead_assignment_policy"));
        assertTrue(sql.contains("$.templateversionid"));
        assertFalse(sql.matches("(?s).*\\b(?:insert\\s+into|update|delete\\s+from)\\s+"
                + "(?:sys_user|sys_role|sys_user_role|sys_role_menu)\\b.*"));
    }

    private String normalized() throws Exception
    {
        try (InputStream resource = getClass().getResourceAsStream(MIGRATION))
        {
            assertNotNull(resource, "The forward TD-003 contact-time migration must be packaged");
            return new String(resource.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("\\s+", " ").toLowerCase();
        }
    }
}
