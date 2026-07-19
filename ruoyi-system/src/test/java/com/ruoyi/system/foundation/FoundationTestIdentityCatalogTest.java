package com.ruoyi.system.foundation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class FoundationTestIdentityCatalogTest
{
    @Test
    void exposesTheExactReservedDepartmentsAndTestUsers()
    {
        assertEquals(6, FoundationTestIdentityCatalog.departments().size());
        assertEquals(12, FoundationTestIdentityCatalog.users().size());
        assertEquals(Set.of(
            "FOUNDATION_TEST_FIRM", "FOUNDATION_TEST_SALES", "FOUNDATION_TEST_CASE_MANAGEMENT",
            "FOUNDATION_TEST_GENERAL_LAW", "FOUNDATION_TEST_FINANCE", "FOUNDATION_TEST_GOVERNANCE"),
            FoundationTestIdentityCatalog.departments().stream()
                .map(FoundationTestIdentityCatalog.DepartmentSpec::code).collect(Collectors.toSet()));
        assertEquals("Foundation测试律所", FoundationTestIdentityCatalog.departments().get(0).name());
        assertEquals(null, FoundationTestIdentityCatalog.departments().get(0).parentCode());
        assertEquals("foundation_product_owner", user("ft_product_owner").roleKey());
        assertEquals("FOUNDATION_TEST_SALES", user("ft_sales").departmentCode());
        assertEquals("lawyer", user("ft_lawyer_l1").roleKey());
        assertEquals("lawyer", user("ft_lawyer_l2").roleKey());
    }

    @Test
    void exposesExactRoleCatalogAndKeepsEveryUserMappingUnique()
    {
        assertEquals(Set.of(
            "foundation_product_owner", "sales", "case_manager", "law_partner_manager", "lawyer",
            "intern_lawyer", "finance_manager", "foundation_security_reviewer",
            "foundation_arch_dba_reviewer", "foundation_qa_acceptor", "foundation_independent_reviewer"),
            FoundationTestIdentityCatalog.requiredRoleKeys());
        assertEquals(Set.of(
            "enforcement_primary_assistant", "enforcement_secondary_assistant", "execution_manager",
            "execution_assistant_l1", "execution_assistant_l2"),
            FoundationTestIdentityCatalog.forbiddenQ003RoleKeys());
        assertFalse(FoundationTestIdentityCatalog.users().stream()
            .map(FoundationTestIdentityCatalog.UserSpec::roleKey)
            .anyMatch(FoundationTestIdentityCatalog.forbiddenQ003RoleKeys()::contains));
        assertEquals(12, unique(FoundationTestIdentityCatalog.users().stream()
            .map(FoundationTestIdentityCatalog.UserSpec::userName).collect(Collectors.toList())));
        assertEquals(12, unique(FoundationTestIdentityCatalog.users().stream()
            .map(FoundationTestIdentityCatalog.UserSpec::nickName).collect(Collectors.toList())));
        assertTrue(FoundationTestIdentityCatalog.users().stream()
            .allMatch(user -> user.departmentCode() != null && user.roleKey() != null));
    }

    @Test
    void returnsImmutableCollections()
    {
        assertThrows(UnsupportedOperationException.class,
            () -> FoundationTestIdentityCatalog.departments().clear());
        assertThrows(UnsupportedOperationException.class,
            () -> FoundationTestIdentityCatalog.requiredRoleKeys().clear());
    }

    private FoundationTestIdentityCatalog.UserSpec user(String userName)
    {
        return FoundationTestIdentityCatalog.users().stream()
            .filter(user -> user.userName().equals(userName)).findFirst().orElseThrow();
    }

    private int unique(List<String> values)
    {
        return new java.util.HashSet<>(values).size();
    }
}
