package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class LeadSupervisorRuntimePermissionMigrationContractTest
{
    private static final String MIGRATION =
        "/db/migration/V0_20_58__lead_supervisor_runtime_permissions.sql";
    private static final Set<String> REQUIRED=Set.of(
        "lead:invalid-review:list","lead:invalid-review:handle",
        "lead:dead-pool:list","lead:dead-pool:restore",
        "todo:list","todo:query","todo:chain:query","file:object:read");
    private static final Set<String> FORBIDDEN=Set.of(
        "lead:add","lead:edit","lead:remove","lead:assign",
        "lead:assignment-policy:list","lead:assignment-policy:edit",
        "todo:complete","todo:force:complete","todo:force:cancel",
        "todo:batch:transfer","todo:sla:waive","todo:regenerate",
        "file:object:upload","file:object:relate","file:object:retire");

    @Test
    void grantsOnlySupervisorReviewDeadPoolAndReadOnlyTodoSurface() throws IOException
    {
        String normalized=normalize(readMigration());
        REQUIRED.forEach(permission->assertTrue(normalized.contains("'"+permission+"'"),
            ()->"Missing supervisor runtime permission: "+permission));
        FORBIDDEN.forEach(permission->assertFalse(normalized.contains("'"+permission+"'"),
            ()->"Supervisor migration must not grant unrelated mutation: "+permission));
        assertTrue(normalized.contains("parent_id=0 and path='lead'"));
        assertTrue(normalized.contains("path in ('invalid-review','dead-pool')"));
    }

    @Test
    void reusesOneExistingRoleAndIsReplaySafeWithoutIdentityMutation() throws IOException
    {
        String sql=readMigration();
        String normalized=normalize(sql);
        assertTrue(normalized.contains(
            "where role_key='law_partner_manager' and status='0' and del_flag='0'"));
        assertTrue(normalized.contains("not exists"));
        assertFalse(Pattern.compile(
            "\\b(?:insert\\s+(?:ignore\\s+)?into|replace\\s+into|update|delete\\s+from)\\s+`?sys_role`?\\b",
            Pattern.CASE_INSENSITIVE).matcher(sql).find());
        for(String table:Set.of("sys_user","sys_dept","sys_user_role"))
            assertFalse(Pattern.compile(
                "\\b(?:insert\\s+(?:ignore\\s+)?into|replace\\s+into|update|delete\\s+from)\\s+`?"
                    +table+"`?\\b",Pattern.CASE_INSENSITIVE).matcher(sql).find());
    }

    private String readMigration() throws IOException
    {
        try(InputStream resource=getClass().getResourceAsStream(MIGRATION))
        {
            assertNotNull(resource,"The forward supervisor permission migration must be packaged");
            return new String(resource.readAllBytes(),StandardCharsets.UTF_8);
        }
    }

    private String normalize(String value){return value.replaceAll("\\s+"," ").toLowerCase();}
}
