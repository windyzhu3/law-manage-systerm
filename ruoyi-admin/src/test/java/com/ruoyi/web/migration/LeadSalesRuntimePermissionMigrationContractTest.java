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

class LeadSalesRuntimePermissionMigrationContractTest
{
    private static final String MIGRATION =
        "/db/migration/V0_20_55__lead_sales_runtime_permissions.sql";

    private static final Set<String> REQUIRED_PERMISSIONS = Set.of(
        "lead:dashboard:view",
        "lead:mine:list",
        "lead:mine:query",
        "lead:tag:confirm",
        "lead:first-contact:handle",
        "lead:retry:list",
        "lead:retry:handle",
        "lead:call-record:add",
        "lead:call-record:view",
        "todo:list",
        "todo:query",
        "todo:complete",
        "todo:chain:query",
        "file:object:upload",
        "file:object:relate",
        "file:object:read"
    );

    private static final Set<String> FORBIDDEN_MANAGEMENT_PERMISSIONS = Set.of(
        "lead:all:list",
        "lead:assign",
        "lead:invalid-review:list",
        "lead:invalid-review:handle",
        "lead:dead-pool:list",
        "lead:dead-pool:restore",
        "lead:assignment-policy:list",
        "lead:assignment-policy:edit",
        "todo:template:manage",
        "todo:operations:list",
        "todo:force:complete",
        "todo:force:cancel",
        "todo:batch:transfer",
        "todo:sla:waive",
        "todo:regenerate",
        "file:object:retire",
        "file:object:audit"
    );

    @Test
    void migrationGrantsOnlyTheMinimumLeadTodoRuntimeSurface() throws IOException
    {
        String sql = readMigration();
        String normalized = normalize(sql);

        for (String permission : REQUIRED_PERMISSIONS)
        {
            assertTrue(normalized.contains("'" + permission + "'"),
                () -> "Missing required sales runtime permission: " + permission);
        }
        for (String permission : FORBIDDEN_MANAGEMENT_PERMISSIONS)
        {
            assertFalse(normalized.contains("'" + permission + "'"),
                () -> "Sales runtime migration must not grant management permission: " + permission);
        }
        assertTrue(normalized.contains("parent_id=0 and path='lead'"),
            "The lead route root must be granted explicitly");
        assertTrue(normalized.contains("parent_id=@lead_root_menu_id and path in ('mine','retry')"),
            "The /lead/mine and /lead/retry child routes must be granted explicitly");
    }

    @Test
    void migrationIsIdempotentAndNeverCreatesOrRebindsIdentities() throws IOException
    {
        String sql = readMigration();
        String normalized = normalize(sql);

        assertTrue(normalized.contains("where role_key='sales' and status='0' and del_flag='0'"),
            "The existing active sales role must be resolved by its stable key");
        assertTrue(normalized.contains("not exists"),
            "Role-menu grants must be idempotent");
        assertFalse(Pattern.compile("\\b(?:insert\\s+(?:ignore\\s+)?into|replace\\s+into|update|delete\\s+from)\\s+`?sys_role`?\\b",
            Pattern.CASE_INSENSITIVE).matcher(sql).find(), "Migration must not create or mutate roles");
        for (String identityTable : Set.of("sys_user", "sys_dept", "sys_user_role"))
        {
            assertFalse(Pattern.compile("\\b(?:insert\\s+(?:ignore\\s+)?into|replace\\s+into|update|delete\\s+from)\\s+`?"
                + identityTable + "`?\\b", Pattern.CASE_INSENSITIVE).matcher(sql).find(),
                () -> "Migration must not mutate " + identityTable);
        }
    }

    private String readMigration() throws IOException
    {
        try (InputStream resource = getClass().getResourceAsStream(MIGRATION))
        {
            assertNotNull(resource, "The forward sales permission migration must be packaged at runtime");
            return new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String normalize(String value)
    {
        return value.replaceAll("\\s+", " ").toLowerCase();
    }
}
