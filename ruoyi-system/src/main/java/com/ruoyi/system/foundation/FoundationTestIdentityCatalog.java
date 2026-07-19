package com.ruoyi.system.foundation;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FoundationTestIdentityCatalog
{
    public static final String USER_MARKER = "FOUNDATION_TEST_IDENTITY|DO_NOT_USE_FOR_PRODUCTION_EVIDENCE";
    public static final String CREATED_BY = "foundation-test-seeder";
    public static final String TEST_USER_TYPE = "99";

    private static final List<DepartmentSpec> DEPARTMENTS = List.of(
        new DepartmentSpec("FOUNDATION_TEST_FIRM", "Foundation测试律所", null, 1),
        new DepartmentSpec("FOUNDATION_TEST_SALES", "Foundation测试销售部", "FOUNDATION_TEST_FIRM", 2),
        new DepartmentSpec("FOUNDATION_TEST_CASE_MANAGEMENT", "Foundation测试案管部", "FOUNDATION_TEST_FIRM", 3),
        new DepartmentSpec("FOUNDATION_TEST_GENERAL_LAW", "Foundation测试综法部", "FOUNDATION_TEST_FIRM", 4),
        new DepartmentSpec("FOUNDATION_TEST_FINANCE", "Foundation测试财务部", "FOUNDATION_TEST_FIRM", 5),
        new DepartmentSpec("FOUNDATION_TEST_GOVERNANCE", "Foundation测试治理组", "FOUNDATION_TEST_FIRM", 6));

    private static final List<UserSpec> USERS = List.of(
        new UserSpec("ft_product_owner", "测试产品负责人", "FOUNDATION_TEST_GOVERNANCE", "foundation_product_owner"),
        new UserSpec("ft_sales", "测试销售人员", "FOUNDATION_TEST_SALES", "sales"),
        new UserSpec("ft_case_manager", "测试案管员", "FOUNDATION_TEST_CASE_MANAGEMENT", "case_manager"),
        new UserSpec("ft_partner_manager", "测试合伙人法务经理", "FOUNDATION_TEST_GENERAL_LAW", "law_partner_manager"),
        new UserSpec("ft_lawyer_l1", "测试一级律师", "FOUNDATION_TEST_GENERAL_LAW", "lawyer"),
        new UserSpec("ft_lawyer_l2", "测试二级律师", "FOUNDATION_TEST_GENERAL_LAW", "lawyer"),
        new UserSpec("ft_intern_lawyer", "测试实习律师", "FOUNDATION_TEST_GENERAL_LAW", "intern_lawyer"),
        new UserSpec("ft_finance", "测试财务人员", "FOUNDATION_TEST_FINANCE", "finance_manager"),
        new UserSpec("ft_security_reviewer", "测试安全评审人", "FOUNDATION_TEST_GOVERNANCE", "foundation_security_reviewer"),
        new UserSpec("ft_arch_dba", "测试架构DBA评审人", "FOUNDATION_TEST_GOVERNANCE", "foundation_arch_dba_reviewer"),
        new UserSpec("ft_qa_acceptor", "测试QA验收人", "FOUNDATION_TEST_GOVERNANCE", "foundation_qa_acceptor"),
        new UserSpec("ft_independent_reviewer", "测试独立准入评审人", "FOUNDATION_TEST_GOVERNANCE", "foundation_independent_reviewer"));

    private static final Set<String> REQUIRED_ROLE_KEYS = Set.of(
        "foundation_product_owner", "sales", "case_manager", "law_partner_manager", "lawyer", "intern_lawyer",
        "finance_manager", "foundation_security_reviewer", "foundation_arch_dba_reviewer", "foundation_qa_acceptor",
        "foundation_independent_reviewer");

    private static final Set<String> FORBIDDEN_Q003_ROLE_KEYS = Set.of(
        "enforcement_primary_assistant", "enforcement_secondary_assistant", "execution_manager",
        "execution_assistant_l1", "execution_assistant_l2");

    private static final Map<String, Set<String>> GOVERNANCE_ROLE_PERMISSIONS = Map.of(
        "foundation_product_owner", Set.of(
            "todo:decision:view", "todo:decision:edit", "todo:admission:view", "todo:admission:edit"),
        "foundation_security_reviewer", Set.of("todo:admission:view", "todo:admission:edit"),
        "foundation_arch_dba_reviewer", Set.of(
            "todo:admission:view", "todo:admission:edit", "todo:admission:export"),
        "foundation_qa_acceptor", Set.of("todo:admission:view", "todo:admission:edit"),
        "foundation_independent_reviewer", Set.of("todo:admission:view", "todo:admission:edit"));

    private FoundationTestIdentityCatalog()
    {
    }

    public static List<DepartmentSpec> departments()
    {
        return DEPARTMENTS;
    }

    public static List<UserSpec> users()
    {
        return USERS;
    }

    public static Set<String> requiredRoleKeys()
    {
        return REQUIRED_ROLE_KEYS;
    }

    public static Set<String> forbiddenQ003RoleKeys()
    {
        return FORBIDDEN_Q003_ROLE_KEYS;
    }

    public static Map<String, Set<String>> governanceRolePermissions()
    {
        return GOVERNANCE_ROLE_PERMISSIONS;
    }

    public record DepartmentSpec(String code, String name, String parentCode, int orderNum) {}

    public record UserSpec(String userName, String nickName, String departmentCode, String roleKey) {}
}
