package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class FoundationGovernanceRoleMigrationContractTest
{
    private static final Set<String> GOVERNANCE_ROLE_KEYS = Set.of(
        "foundation_product_owner",
        "foundation_security_reviewer",
        "foundation_arch_dba_reviewer",
        "foundation_qa_acceptor",
        "foundation_independent_reviewer"
    );

    private static final Set<String> FORBIDDEN_Q003_ROLE_KEYS = Set.of(
        "enforcement_primary_assistant",
        "enforcement_secondary_assistant",
        "execution_manager",
        "execution_assistant_l1",
        "execution_assistant_l2"
    );

    private static final String ROLE_CREATOR = "flyway-v0.20.28";
    private static final Pattern ROLE_INSERT_STATEMENT = Pattern.compile(
        "\\binsert\\s+(?:ignore\\s+)?into\\s+`?sys_role`?\\b.*?;", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    @Test
    void migrationResourceEnforcesGovernanceRoleExclusivityAndWriteBoundary() throws IOException
    {
        try (InputStream resource = getClass().getResourceAsStream(
            "/db/migration/V0_20_28__foundation_governance_roles.sql"))
        {
            assertNotNull(resource, "Foundation governance role migration must be available on the runtime classpath");
            String sql = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
            String normalizedSql = normalize(sql);
            Set<String> definedRoleKeys = new java.util.HashSet<>();
            Matcher roleInserts = ROLE_INSERT_STATEMENT.matcher(sql);
            int roleInsertCount = 0;
            while (roleInserts.find())
            {
                roleInsertCount++;
                String statement = normalize(roleInserts.group());
                assertEquals(1, occurrenceCount(statement, ROLE_CREATOR),
                    "Each formal role insert must use the Foundation migration creator marker exactly once");
                assertFalse(Pattern.compile("\\b(values|union|join)\\b").matcher(statement).find(),
                    "Formal role inserts must be one-row SELECT statements");
                assertTrue(Pattern.compile("\\bselect\\b.*\\bwhere\\s+not\\s+exists\\b").matcher(statement).find(),
                    "Formal role inserts must use idempotent SELECT ... WHERE NOT EXISTS");
                Set<String> statementRoleKeys = new java.util.HashSet<>();
                for (String roleKey : GOVERNANCE_ROLE_KEYS)
                {
                    if (occurrenceCount(statement, roleKey) == 2)
                    {
                        statementRoleKeys.add(roleKey);
                    }
                }
                assertEquals(1, statementRoleKeys.size(),
                    "Each formal role insert must define one expected role in SELECT and WHERE NOT EXISTS");
                definedRoleKeys.addAll(statementRoleKeys);
            }
            assertEquals(5, roleInsertCount, "Migration must create exactly five formal roles");
            assertEquals(GOVERNANCE_ROLE_KEYS, definedRoleKeys,
                "Formal role inserts must define exactly the five approved governance roles");
            for (String roleKey : FORBIDDEN_Q003_ROLE_KEYS)
            {
                assertFalse(normalizedSql.contains(roleKey), () -> "Migration must not define Q-003 role " + roleKey);
            }
            for (String forbiddenTable : Set.of("sys_user", "sys_dept", "sys_user_role"))
            {
                assertFalse(forbiddenIdentityTableMutationPattern(forbiddenTable).matcher(sql).find(),
                    () -> "Migration must not mutate " + forbiddenTable);
            }
            assertFalse(normalizedSql.contains("todo_foundation_decision"),
                "Migration must not reference Foundation decisions");
            assertFalse(normalizedSql.contains("todo_admission_evidence"),
                "Migration must not reference admission evidence");
            for (String protectedTable : Set.of("sys_role", "sys_role_menu"))
            {
                Pattern mutation = Pattern.compile("\\b(?:update|delete\\s+from)\\s+`?" + protectedTable + "`?\\b",
                    Pattern.CASE_INSENSITIVE);
                assertFalse(mutation.matcher(sql).find(), () -> "Migration must not mutate " + protectedTable);
            }
        }
    }

    @Test
    void forbiddenIdentityTableMutationPatternCoversEveryProhibitedOperation()
    {
        for (String table : Set.of("sys_user", "sys_dept", "sys_user_role"))
        {
            Pattern mutation = forbiddenIdentityTableMutationPattern(table);
            for (String sql : Set.of(
                "INSERT INTO `" + table + "` values (1)",
                "insert\nignore\tinto `" + table + "` select 1",
                "REPLACE  INTO `" + table + "` values (1)",
                "update\n`" + table + "` set status='0'",
                "DELETE\tFROM `" + table + "`",
                "truncate\n table\t`" + table + "`",
                "ALTER\tTABLE `" + table + "` add marker int",
                "drop\n table `" + table + "`"))
            {
                assertTrue(mutation.matcher(sql).find(), () -> "Pattern must reject: " + sql);
            }
        }
    }

    @Test
    void migrationResourceGrantsOnlyRoleIdsProvenNewByThisMigration() throws IOException
    {
        try (InputStream resource = getClass().getResourceAsStream(
            "/db/migration/V0_20_28__foundation_governance_roles.sql"))
        {
            assertNotNull(resource);
            String normalizedSql = normalize(new String(resource.readAllBytes(), StandardCharsets.UTF_8));
            for (String roleKey : GOVERNANCE_ROLE_KEYS)
            {
                assertTrue(normalizedSql.contains("set @" + roleKey + "_existing_role_id="),
                    () -> "Migration must snapshot pre-existing role ID for " + roleKey);
                assertTrue(normalizedSql.contains("set @" + roleKey + "_new_role_id=if(@" + roleKey
                    + "_existing_role_id is null,"),
                    () -> "Migration must derive a new-only role ID for " + roleKey);
            }
            assertTrue(normalizedSql.contains("from ( select @foundation_product_owner_new_role_id role_id,"),
                "Role-menu grants must be derived from the proven-new role ID table");
            assertTrue(normalizedSql.contains("where created.role_id is not null"),
                "Colliding roles must be excluded from role-menu grants");
        }
    }

    private Pattern forbiddenIdentityTableMutationPattern(String table)
    {
        return Pattern.compile("\\b(?:insert\\s+(?:ignore\\s+)?into|replace\\s+into|update|delete\\s+from|"
            + "truncate(?:\\s+table)?|alter\\s+table|drop\\s+table)\\s+`?" + table + "`?\\b", Pattern.CASE_INSENSITIVE);
    }

    private String normalize(String value)
    {
        return value.replaceAll("\\s+", " ").toLowerCase();
    }

    private int occurrenceCount(String value, String expected)
    {
        int occurrences = 0;
        int start = 0;
        while ((start = value.indexOf(expected, start)) >= 0)
        {
            occurrences++;
            start += expected.length();
        }
        return occurrences;
    }
}
