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

class LeadInformationOfficerRuntimeMigrationContractTest
{
    private static final String MIGRATION =
        "/db/migration/V0_20_60__lead_information_officer_runtime.sql";
    private static final Set<String> REQUIRED = Set.of(
        "lead:dashboard:view", "lead:all:list", "lead:query", "lead:tag:confirm");
    private static final Set<String> FORBIDDEN = Set.of(
        "lead:add", "lead:edit", "lead:remove", "lead:assign", "lead:first-contact:handle",
        "lead:retry:handle", "lead:invalid-review:handle", "lead:dead-pool:restore",
        "lead:assignment-policy:edit", "todo:complete", "file:object:retire");

    @Test
    void createsTheInformationOfficerRoleWithOnlyTheConfirmedIntakeSurface() throws IOException
    {
        String normalized = normalize(readMigration());
        assertTrue(normalized.contains("'lead_information_officer'"));
        assertTrue(normalized.contains("'1'"), "Information intake needs the governed all-lead data scope");
        REQUIRED.forEach(permission -> assertTrue(normalized.contains("'" + permission + "'"),
            () -> "Missing information-officer permission: " + permission));
        FORBIDDEN.forEach(permission -> assertFalse(normalized.contains("'" + permission + "'"),
            () -> "Information officer must not receive downstream mutation: " + permission));
        assertTrue(normalized.contains("parent_id=0 and path='lead'"));
        assertTrue(normalized.contains("parent_id=@lead_root_menu_id and path='all'"));
    }

    @Test
    void removesTagConfirmationFromSalesAndNeverCreatesProductionUsers() throws IOException
    {
        String sql = readMigration();
        String normalized = normalize(sql);
        assertTrue(normalized.contains("role_key='sales'"));
        assertTrue(normalized.contains("perms='lead:tag:confirm'"));
        assertTrue(Pattern.compile("\\bdelete\\s+from\\s+sys_role_menu\\b",
            Pattern.CASE_INSENSITIVE).matcher(sql).find());
        for (String identityTable : Set.of("sys_user", "sys_dept", "sys_user_role"))
        {
            assertFalse(Pattern.compile(
                "\\b(?:insert\\s+(?:ignore\\s+)?into|replace\\s+into|update|delete\\s+from)\\s+`?"
                    + identityTable + "`?\\b",
                Pattern.CASE_INSENSITIVE).matcher(sql).find(),
                () -> "Forward migration must not mutate " + identityTable);
        }
    }

    private String readMigration() throws IOException
    {
        try (InputStream resource = getClass().getResourceAsStream(MIGRATION))
        {
            assertNotNull(resource, "The information-officer forward migration must be packaged");
            return new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String normalize(String value)
    {
        return value.replaceAll("\\s+", " ").toLowerCase();
    }
}
