package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
    private static final Pattern ROLE_INSERT = Pattern.compile("\\binsert\\s+(?:ignore\\s+)?into\\s+sys_role\\b",
        Pattern.CASE_INSENSITIVE);

    @Test
    void migrationResourceEnforcesGovernanceRoleExclusivityAndWriteBoundary() throws IOException
    {
        try (InputStream resource = getClass().getResourceAsStream(
            "/db/migration/V0_20_28__foundation_governance_roles.sql"))
        {
            assertNotNull(resource, "Foundation governance role migration must be available on the runtime classpath");
            String sql = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
            String normalizedSql = sql.replaceAll("\\s+", " ").toLowerCase();

            assertEquals(5, matchCount(ROLE_INSERT, sql), "Migration must create exactly five formal roles");
            for (String roleKey : GOVERNANCE_ROLE_KEYS)
            {
                assertEquals(1, occurrenceCount(normalizedSql, roleKey),
                    () -> "Migration must define governance role exactly once: " + roleKey);
            }
            assertEquals(5, occurrenceCount(normalizedSql, ROLE_CREATOR),
                "Every formal role must use the Foundation migration creator marker");
            for (String roleKey : FORBIDDEN_Q003_ROLE_KEYS)
            {
                assertFalse(normalizedSql.contains(roleKey), () -> "Migration must not define Q-003 role " + roleKey);
            }
            for (String forbiddenTable : Set.of("sys_user", "sys_dept", "sys_user_role"))
            {
                Pattern insert = Pattern.compile("\\binsert\\s+(?:ignore\\s+)?into\\s+`?" + forbiddenTable + "`?\\b",
                    Pattern.CASE_INSENSITIVE);
                assertFalse(insert.matcher(sql).find(), () -> "Migration must not write " + forbiddenTable);
            }
            assertFalse(normalizedSql.contains("todo_foundation_decision"),
                "Migration must not reference Foundation decisions");
            assertFalse(normalizedSql.contains("todo_admission_evidence"),
                "Migration must not reference admission evidence");
        }
    }

    private int matchCount(Pattern pattern, String value)
    {
        Matcher matcher = pattern.matcher(value);
        int matches = 0;
        while (matcher.find())
        {
            matches++;
        }
        return matches;
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
