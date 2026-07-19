package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
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

    @Test
    void migrationDefinesOnlyFormalGovernanceRolesWithoutTestIdentityOrAdmissionWrites() throws IOException
    {
        try (InputStream resource = getClass().getResourceAsStream(
            "/db/migration/V0_20_28__foundation_governance_roles.sql"))
        {
            assertNotNull(resource, "Foundation governance role migration must be available on the runtime classpath");
            String sql = new String(resource.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();

            for (String roleKey : GOVERNANCE_ROLE_KEYS)
            {
                assertTrue(sql.contains(roleKey), () -> "Migration must define governance role " + roleKey);
            }
            for (String roleKey : FORBIDDEN_Q003_ROLE_KEYS)
            {
                assertFalse(sql.contains(roleKey), () -> "Migration must not define Q-003 role " + roleKey);
            }
            for (String forbiddenSql : Set.of("insert into sys_user", "insert into sys_dept", "insert into sys_user_role",
                "todo_foundation_decision", "todo_admission_evidence"))
            {
                assertFalse(sql.contains(forbiddenSql), () -> "Migration must not write " + forbiddenSql);
            }
        }
    }
}
