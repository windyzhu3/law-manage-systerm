package com.ruoyi.web.migration;

import static com.ruoyi.system.foundation.FoundationTestIdentityErrorCode.FOUNDATION_TEST_IDENTITIES_PROFILE_FORBIDDEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.system.foundation.FoundationTestIdentityException;
import com.ruoyi.system.foundation.FoundationTestIdentityProvisioningResult;
import com.ruoyi.system.foundation.FoundationTestIdentityProvisioningService;
import com.ruoyi.system.mapper.FoundationTestIdentityMapper;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class FoundationTestIdentityEndToEndTest
{
    private static final String CREATED_BY = "foundation-test-seeder";
    private static final String USER_MARKER = "FOUNDATION_TEST_IDENTITY|DO_NOT_USE_FOR_PRODUCTION_EVIDENCE";

    /* Independent contract fixtures: never derive E2E expectations from the production catalog. */
    private static final Set<ExpectedDepartment> EXPECTED_DEPARTMENTS = Set.of(
        department("FOUNDATION_TEST_FIRM", "Foundation\u6d4b\u8bd5\u5f8b\u6240", null, 1),
        department("FOUNDATION_TEST_SALES", "Foundation\u6d4b\u8bd5\u9500\u552e\u90e8", "FOUNDATION_TEST_FIRM", 2),
        department("FOUNDATION_TEST_CASE_MANAGEMENT", "Foundation\u6d4b\u8bd5\u6848\u7ba1\u90e8",
            "FOUNDATION_TEST_FIRM", 3),
        department("FOUNDATION_TEST_GENERAL_LAW", "Foundation\u6d4b\u8bd5\u7efc\u6cd5\u90e8",
            "FOUNDATION_TEST_FIRM", 4),
        department("FOUNDATION_TEST_FINANCE", "Foundation\u6d4b\u8bd5\u8d22\u52a1\u90e8", "FOUNDATION_TEST_FIRM", 5),
        department("FOUNDATION_TEST_GOVERNANCE", "Foundation\u6d4b\u8bd5\u6cbb\u7406\u7ec4",
            "FOUNDATION_TEST_FIRM", 6));

    private static final Set<ExpectedUser> EXPECTED_USERS = Set.of(
        user("ft_info", "\u6d4b\u8bd5\u4fe1\u606f\u5458", "FOUNDATION_TEST_SALES",
            "lead_information_officer"),
        user("ft_product_owner", "\u6d4b\u8bd5\u4ea7\u54c1\u8d1f\u8d23\u4eba", "FOUNDATION_TEST_GOVERNANCE",
            "foundation_product_owner"),
        user("ft_sales", "\u6d4b\u8bd5\u9500\u552e\u4eba\u5458", "FOUNDATION_TEST_SALES", "sales"),
        user("ft_case_manager", "\u6d4b\u8bd5\u6848\u7ba1\u5458", "FOUNDATION_TEST_CASE_MANAGEMENT", "case_manager"),
        user("ft_partner_manager", "\u6d4b\u8bd5\u5408\u4f19\u4eba\u6cd5\u52a1\u7ecf\u7406", "FOUNDATION_TEST_GENERAL_LAW",
            "law_partner_manager"),
        user("ft_lawyer_l1", "\u6d4b\u8bd5\u4e00\u7ea7\u5f8b\u5e08", "FOUNDATION_TEST_GENERAL_LAW", "lawyer"),
        user("ft_lawyer_l2", "\u6d4b\u8bd5\u4e8c\u7ea7\u5f8b\u5e08", "FOUNDATION_TEST_GENERAL_LAW", "lawyer"),
        user("ft_intern_lawyer", "\u6d4b\u8bd5\u5b9e\u4e60\u5f8b\u5e08", "FOUNDATION_TEST_GENERAL_LAW", "intern_lawyer"),
        user("ft_finance", "\u6d4b\u8bd5\u8d22\u52a1\u4eba\u5458", "FOUNDATION_TEST_FINANCE", "finance_manager"),
        user("ft_security_reviewer", "\u6d4b\u8bd5\u5b89\u5168\u8bc4\u5ba1\u4eba", "FOUNDATION_TEST_GOVERNANCE",
            "foundation_security_reviewer"),
        user("ft_arch_dba", "\u6d4b\u8bd5\u67b6\u6784DBA\u8bc4\u5ba1\u4eba", "FOUNDATION_TEST_GOVERNANCE",
            "foundation_arch_dba_reviewer"),
        user("ft_qa_acceptor", "\u6d4b\u8bd5QA\u9a8c\u6536\u4eba", "FOUNDATION_TEST_GOVERNANCE",
            "foundation_qa_acceptor"),
        user("ft_independent_reviewer", "\u6d4b\u8bd5\u72ec\u7acb\u51c6\u5165\u8bc4\u5ba1\u4eba", "FOUNDATION_TEST_GOVERNANCE",
            "foundation_independent_reviewer"));

    private static final Map<String, Set<String>> EXPECTED_GOVERNANCE_PERMISSIONS = Map.of(
        "lead_information_officer", Set.of("lead:query", "lead:tag:confirm"),
        "foundation_product_owner", Set.of(
            "todo:decision:view", "todo:decision:edit", "todo:admission:view", "todo:admission:edit"),
        "foundation_security_reviewer", Set.of("todo:admission:view", "todo:admission:edit"),
        "foundation_arch_dba_reviewer", Set.of(
            "todo:admission:view", "todo:admission:edit", "todo:admission:export"),
        "foundation_qa_acceptor", Set.of("todo:admission:view", "todo:admission:edit"),
        "foundation_independent_reviewer", Set.of("todo:admission:view", "todo:admission:edit"));

    @Test
    void provisionsRepairsAndRecreatesOnlyIsolatedTestIdentitiesWithoutHashChurn()
    {
        FoundationTestIdentityTestSupport database = FoundationTestIdentityTestSupport.migrate();
        JdbcTemplate jdbc = database.jdbcTemplate();

        try (FoundationTestIdentityTestSupport.IdentityCleanup ignored = database.identityCleanup())
        {
            assertProductionPathEmpty(jdbc);
            assertForbiddenProfileCannotEnterProvisioning(database, jdbc);

            // The gate deliberately models one startup invocation; concurrent startup is outside this task.
            try (ConfigurableApplicationContext context = database.startContext("test"))
            {
                FoundationTestIdentityProvisioningService service =
                    context.getBean(FoundationTestIdentityProvisioningService.class);
                FoundationTestIdentityMapper mapper = context.getBean(FoundationTestIdentityMapper.class);
                JdbcTemplate contextJdbc = context.getBean(JdbcTemplate.class);
                BCryptPasswordEncoder encoder = context.getBean(BCryptPasswordEncoder.class);

                assertExactActiveFoundationState(contextJdbc, encoder, database.password());
                assertRealMyBatisSetMapping(mapper);
                Map<String, String> originalHashes = activePasswordHashes(contextJdbc);
                FoundationTestIdentityTestSupport.DatabaseSnapshot beforeIdempotent = database.snapshot(contextJdbc);

                FoundationTestIdentityProvisioningResult second = service.provision(database.password());

                assertEquals(new FoundationTestIdentityProvisioningResult(0, 32, 0), second);
                assertEquals(beforeIdempotent, database.snapshot(contextJdbc),
                    "Idempotent provisioning must preserve every relevant row and relationship");
                assertEquals(originalHashes, activePasswordHashes(contextJdbc),
                    "Idempotent provisioning must preserve BCrypt bytes exactly");

                assertRepairsOnlyCorruptedTargets(database, contextJdbc, service, encoder, originalHashes);
                assertRecreatesDeletedUserWithoutChangingHistory(
                    database, contextJdbc, service, encoder, originalHashes);
            }
        }

        assertProductionPathEmpty(jdbc);
    }

    private void assertForbiddenProfileCannotEnterProvisioning(FoundationTestIdentityTestSupport database,
        JdbcTemplate jdbc)
    {
        FoundationTestIdentityTestSupport.DatabaseSnapshot before = database.snapshot(jdbc);
        RuntimeException forbiddenStartup = assertThrows(RuntimeException.class,
            () -> database.startContext("local", "prod"));
        FoundationTestIdentityException profileFailure = findCause(forbiddenStartup,
            FoundationTestIdentityException.class);
        assertNotNull(profileFailure, "local,prod startup must expose the stable Foundation profile error");
        assertEquals(FOUNDATION_TEST_IDENTITIES_PROFILE_FORBIDDEN, profileFailure.getCode());

        FoundationTestIdentityProvisioningService provisioningProbe =
            mock(FoundationTestIdentityProvisioningService.class);
        RuntimeException seamFailure = assertThrows(RuntimeException.class,
            () -> database.startProfileGuardContext(provisioningProbe, "local", "prod"));
        FoundationTestIdentityException seamProfileFailure = findCause(seamFailure,
            FoundationTestIdentityException.class);
        assertNotNull(seamProfileFailure, "Focused profile guard seam must expose the stable profile error");
        assertEquals(FOUNDATION_TEST_IDENTITIES_PROFILE_FORBIDDEN, seamProfileFailure.getCode());
        verifyNoInteractions(provisioningProbe);

        assertEquals(before, database.snapshot(jdbc),
            "Forbidden startup must preserve every relevant database row and relationship");
        assertProductionPathEmpty(jdbc);
    }

    private void assertRepairsOnlyCorruptedTargets(FoundationTestIdentityTestSupport database, JdbcTemplate jdbc,
        FoundationTestIdentityProvisioningService service, BCryptPasswordEncoder encoder,
        Map<String, String> originalHashes)
    {
        Map<String, FoundationTestIdentityTestSupport.UserIdentitySnapshot> testBefore =
            database.activeTestIdentities(jdbc);
        Map<Long, FoundationTestIdentityTestSupport.UserIdentitySnapshot> nonTestBefore =
            database.nonTestIdentities(jdbc);
        List<Map<String, String>> departmentsBefore = database.tableSnapshot(jdbc, "sys_dept order by dept_id");
        List<Map<String, String>> rolesBefore = database.tableSnapshot(jdbc, "sys_role order by role_id");
        List<Map<String, String>> roleMenusBefore =
            database.tableSnapshot(jdbc, "sys_role_menu order by role_id,menu_id");

        moveUserToDepartment(jdbc, "ft_sales", "FOUNDATION_TEST_FINANCE");
        addRole(jdbc, "ft_case_manager", "sales");

        FoundationTestIdentityProvisioningResult result = service.provision(database.password());

        assertEquals(new FoundationTestIdentityProvisioningResult(0, 30, 2), result);
        Map<String, FoundationTestIdentityTestSupport.UserIdentitySnapshot> testAfter =
            database.activeTestIdentities(jdbc);
        Set<String> unaffected = expectedUserNames();
        unaffected.removeAll(Set.of("ft_sales", "ft_case_manager"));
        unaffected.forEach(userName -> assertEquals(testBefore.get(userName), testAfter.get(userName),
            () -> "Repair changed complete unaffected identity " + userName));

        assertEquals(testBefore.get("ft_case_manager"), testAfter.get("ft_case_manager"),
            "Role repair must restore the exact original row and relationships");
        FoundationTestIdentityTestSupport.UserIdentitySnapshot salesBefore = testBefore.get("ft_sales");
        FoundationTestIdentityTestSupport.UserIdentitySnapshot salesAfter = testAfter.get("ft_sales");
        assertEquals(withoutKeys(salesBefore.row(), Set.of("dept_id", "update_time")),
            withoutKeys(salesAfter.row(), Set.of("dept_id", "update_time")),
            "Placement repair may change only department and its update timestamp");
        assertEquals(salesBefore.roleIds(), salesAfter.roleIds());
        assertEquals(salesBefore.postIds(), salesAfter.postIds());
        assertEquals("FOUNDATION_TEST_SALES", departmentCode(jdbc, salesAfter.userId()));

        assertEquals(nonTestBefore, database.nonTestIdentities(jdbc),
            "Repair must preserve every non-test user row and relationship");
        assertEquals(departmentsBefore, database.tableSnapshot(jdbc, "sys_dept order by dept_id"));
        assertEquals(rolesBefore, database.tableSnapshot(jdbc, "sys_role order by role_id"));
        assertEquals(roleMenusBefore, database.tableSnapshot(jdbc, "sys_role_menu order by role_id,menu_id"));
        assertEquals(originalHashes, activePasswordHashes(jdbc),
            "Placement and role repair must not churn password hashes");
        assertExactActiveFoundationState(jdbc, encoder, database.password());
    }

    private void assertRecreatesDeletedUserWithoutChangingHistory(FoundationTestIdentityTestSupport database,
        JdbcTemplate jdbc, FoundationTestIdentityProvisioningService service, BCryptPasswordEncoder encoder,
        Map<String, String> originalHashes)
    {
        String recreatedUserName = "ft_lawyer_l2";
        Map<String, FoundationTestIdentityTestSupport.UserIdentitySnapshot> testBefore =
            database.activeTestIdentities(jdbc);
        Map<Long, FoundationTestIdentityTestSupport.UserIdentitySnapshot> nonTestBefore =
            database.nonTestIdentities(jdbc);
        List<Map<String, String>> departmentsBefore = database.tableSnapshot(jdbc, "sys_dept order by dept_id");
        List<Map<String, String>> rolesBefore = database.tableSnapshot(jdbc, "sys_role order by role_id");
        List<Map<String, String>> roleMenusBefore =
            database.tableSnapshot(jdbc, "sys_role_menu order by role_id,menu_id");
        FoundationTestIdentityTestSupport.UserIdentitySnapshot activeBeforeDelete = testBefore.get(recreatedUserName);

        assertEquals(1, jdbc.update("update sys_user set del_flag='2' where user_id=?",
            activeBeforeDelete.userId()));
        FoundationTestIdentityTestSupport.UserIdentitySnapshot historicalBeforeProvision =
            database.userIdentityById(jdbc, activeBeforeDelete.userId());
        assertEquals("2", historicalBeforeProvision.row().get("del_flag"));
        assertEquals(withoutKeys(activeBeforeDelete.row(), Set.of("del_flag")),
            withoutKeys(historicalBeforeProvision.row(), Set.of("del_flag")),
            "Soft deletion must preserve the complete historical row apart from its deletion flag");
        assertEquals(activeBeforeDelete.roleIds(), historicalBeforeProvision.roleIds());
        assertEquals(activeBeforeDelete.postIds(), historicalBeforeProvision.postIds());

        FoundationTestIdentityProvisioningResult result = service.provision(database.password());

        assertEquals(new FoundationTestIdentityProvisioningResult(2, 30, 0), result);
        assertEquals(historicalBeforeProvision, database.userIdentityById(jdbc, activeBeforeDelete.userId()),
            "Recreation must preserve the complete historical row and all its relationships");

        Map<String, FoundationTestIdentityTestSupport.UserIdentitySnapshot> testAfter =
            database.activeTestIdentities(jdbc);
        Set<String> unaffected = expectedUserNames();
        unaffected.remove(recreatedUserName);
        unaffected.forEach(userName -> assertEquals(testBefore.get(userName), testAfter.get(userName),
            () -> "Recreation changed complete unaffected identity " + userName));
        assertEquals(nonTestBefore, database.nonTestIdentities(jdbc),
            "Recreation must preserve every non-test user row and relationship");
        assertEquals(departmentsBefore, database.tableSnapshot(jdbc, "sys_dept order by dept_id"));
        assertEquals(rolesBefore, database.tableSnapshot(jdbc, "sys_role order by role_id"));
        assertEquals(roleMenusBefore, database.tableSnapshot(jdbc, "sys_role_menu order by role_id,menu_id"));

        FoundationTestIdentityTestSupport.UserIdentitySnapshot recreated = testAfter.get(recreatedUserName);
        assertNotNull(recreated);
        assertNotEquals(activeBeforeDelete.userId(), recreated.userId());
        assertTrue(recreated.userId() > 0, "MyBatis must return the real generated user key");
        assertEquals("0", recreated.row().get("del_flag"));
        assertTrue(encoder.matches(database.password(), recreated.row().get("password")));
        Set<String> recreatedFields = Set.of("user_id", "password", "pwd_update_date", "create_time", "del_flag");
        assertEquals(withoutKeys(activeBeforeDelete.row(), recreatedFields),
            withoutKeys(recreated.row(), recreatedFields),
            "The new active identity must reproduce every stable field from its deleted predecessor");
        assertEquals(activeBeforeDelete.roleIds(), recreated.roleIds());
        assertEquals(activeBeforeDelete.postIds(), recreated.postIds());

        Map<String, String> hashesAfterRecreation = activePasswordHashes(jdbc);
        originalHashes.forEach((userName, hash) -> {
            if (!recreatedUserName.equals(userName))
            {
                assertEquals(hash, hashesAfterRecreation.get(userName),
                    () -> "Deleted-history recreation churned unrelated hash for " + userName);
            }
        });
        assertExactActiveFoundationState(jdbc, encoder, database.password());
    }

    private void assertProductionPathEmpty(JdbcTemplate jdbc)
    {
        assertEquals(0, jdbc.queryForObject(
            "select count(*) from sys_user where user_name like 'ft\\_%' escape '\\\\'", Integer.class));
        assertEquals(0, jdbc.queryForObject(
            "select count(*) from sys_dept where dept_code like 'FOUNDATION_TEST_%'", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from sys_user where user_type='99'", Integer.class));
    }

    private void assertExactActiveFoundationState(JdbcTemplate jdbc, BCryptPasswordEncoder encoder,
        String rawPassword)
    {
        List<ExpectedDepartment> departmentRows = jdbc.query(
            "select d.dept_code,d.dept_name,p.dept_code parent_code,d.order_num,d.status,d.del_flag,d.create_by "
                + "from sys_dept d left join sys_dept p on p.dept_id=d.parent_id "
                + "where d.dept_code like 'FOUNDATION_TEST_%' order by d.dept_code",
            (rows, rowNum) -> new ExpectedDepartment(rows.getString("dept_code"), rows.getString("dept_name"),
                rows.getString("parent_code"), rows.getInt("order_num"), rows.getString("status"),
                rows.getString("del_flag"), rows.getString("create_by")));
        assertEquals(6, departmentRows.size(), "Foundation state must contain exactly six reserved departments");
        assertEquals(EXPECTED_DEPARTMENTS, new LinkedHashSet<>(departmentRows));

        List<ExpectedUser> userRows = jdbc.query(
            "select u.user_name,u.nick_name,d.dept_code,r.role_key,u.user_type,u.status,u.del_flag,u.create_by,"
                + "u.remark from sys_user u left join sys_dept d on d.dept_id=u.dept_id "
                + "left join sys_user_role ur on ur.user_id=u.user_id left join sys_role r on r.role_id=ur.role_id "
                + "where (u.user_type='99' or u.user_name like 'ft\\_%' escape '\\\\') "
                + "and u.status='0' and u.del_flag='0' order by u.user_name,r.role_key",
            (rows, rowNum) -> new ExpectedUser(rows.getString("user_name"), rows.getString("nick_name"),
                rows.getString("dept_code"), rows.getString("role_key"), rows.getString("user_type"),
                rows.getString("status"), rows.getString("del_flag"), rows.getString("create_by"),
                rows.getString("remark")));
        assertEquals(13, userRows.size(), "Foundation state must contain exactly thirteen active users and role links");
        assertEquals(EXPECTED_USERS, new LinkedHashSet<>(userRows));
        assertEquals(13, activeFoundationRoleLinkCount(jdbc));
        assertEquals(0, jdbc.queryForObject(
            "select count(*) from sys_user_role ur join sys_user u on u.user_id=ur.user_id "
                + "join sys_role r on r.role_id=ur.role_id where u.user_type='99' and u.status='0' "
                + "and u.del_flag='0' and r.role_key in ('enforcement_primary_assistant',"
                + "'enforcement_secondary_assistant','execution_manager','execution_assistant_l1',"
                + "'execution_assistant_l2')", Integer.class));

        Map<String, Set<String>> expectedRoles = EXPECTED_USERS.stream().collect(Collectors.toUnmodifiableMap(
            ExpectedUser::userName, expected -> Set.of(expected.roleKey())));
        assertEquals(expectedRoles, activeRoleAssignments(jdbc));
        Map<String, String> hashes = activePasswordHashes(jdbc);
        assertEquals(13, hashes.size());
        hashes.forEach((userName, hash) -> assertTrue(encoder.matches(rawPassword, hash),
            () -> "BCrypt password mismatch for " + userName));

        List<Long> departmentIds = jdbc.queryForList(
            "select dept_id from sys_dept where dept_code like 'FOUNDATION_TEST_%' order by dept_id", Long.class);
        List<Long> userIds = jdbc.queryForList(
            "select user_id from sys_user where user_type='99' and status='0' and del_flag='0' order by user_id",
            Long.class);
        assertEquals(6, new LinkedHashSet<>(departmentIds).size());
        assertEquals(13, new LinkedHashSet<>(userIds).size());
        assertTrue(departmentIds.stream().allMatch(id -> id != null && id > 0),
            "MyBatis must return real generated department keys");
        assertTrue(userIds.stream().allMatch(id -> id != null && id > 0),
            "MyBatis must return real generated user keys");
    }

    private void assertRealMyBatisSetMapping(FoundationTestIdentityMapper mapper)
    {
        EXPECTED_GOVERNANCE_PERMISSIONS.forEach((roleKey, expected) -> {
            SysRole role = mapper.selectRoleByKey(roleKey);
            assertNotNull(role);
            Set<String> actual = mapper.selectPermissionKeysByRoleId(role.getRoleId());
            assertNotNull(actual);
            assertEquals(expected, actual, () -> "Real MyBatis Set mapping mismatch for " + roleKey);
        });
    }

    private Map<String, Set<String>> activeRoleAssignments(JdbcTemplate jdbc)
    {
        return jdbc.query(
            "select u.user_name,r.role_key from sys_user u left join sys_user_role ur on ur.user_id=u.user_id "
                + "left join sys_role r on r.role_id=ur.role_id where u.user_type='99' and u.status='0' "
                + "and u.del_flag='0' order by u.user_name,r.role_key",
            rows -> {
                Map<String, Set<String>> result = new HashMap<>();
                while (rows.next())
                {
                    result.computeIfAbsent(rows.getString(1), ignored -> new LinkedHashSet<>());
                    if (rows.getString(2) != null)
                    {
                        result.get(rows.getString(1)).add(rows.getString(2));
                    }
                }
                return result;
            });
    }

    private Map<String, String> activePasswordHashes(JdbcTemplate jdbc)
    {
        return jdbc.query(
            "select user_name,password from sys_user where user_type='99' and status='0' and del_flag='0' "
                + "order by user_name",
            rows -> {
                Map<String, String> result = new LinkedHashMap<>();
                while (rows.next())
                {
                    result.put(rows.getString(1), rows.getString(2));
                }
                return result;
            });
    }

    private int activeFoundationRoleLinkCount(JdbcTemplate jdbc)
    {
        return jdbc.queryForObject(
            "select count(*) from sys_user_role ur join sys_user u on u.user_id=ur.user_id "
                + "where u.user_type='99' and u.status='0' and u.del_flag='0'", Integer.class);
    }

    private void moveUserToDepartment(JdbcTemplate jdbc, String userName, String departmentCode)
    {
        assertEquals(1, jdbc.update("update sys_user set dept_id=(select dept_id from sys_dept where dept_code=?) "
            + "where user_name=? and user_type='99' and del_flag='0'", departmentCode, userName));
    }

    private void addRole(JdbcTemplate jdbc, String userName, String roleKey)
    {
        assertEquals(1, jdbc.update("insert into sys_user_role(user_id,role_id) "
            + "select u.user_id,r.role_id from sys_user u join sys_role r on r.role_key=? "
            + "where u.user_name=? and u.user_type='99' and u.del_flag='0'", roleKey, userName));
    }

    private String departmentCode(JdbcTemplate jdbc, long userId)
    {
        return jdbc.queryForObject("select d.dept_code from sys_user u join sys_dept d on d.dept_id=u.dept_id "
            + "where u.user_id=?", String.class, userId);
    }

    private Map<String, String> withoutKeys(Map<String, String> row, Set<String> keys)
    {
        Map<String, String> result = new LinkedHashMap<>(row);
        keys.forEach(result::remove);
        return result;
    }

    private Set<String> expectedUserNames()
    {
        return EXPECTED_USERS.stream().map(ExpectedUser::userName)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private <T extends Throwable> T findCause(Throwable failure, Class<T> expectedType)
    {
        Throwable current = failure;
        while (current != null)
        {
            if (expectedType.isInstance(current))
            {
                return expectedType.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    private static ExpectedDepartment department(String code, String name, String parentCode, int orderNum)
    {
        return new ExpectedDepartment(code, name, parentCode, orderNum, "0", "0", CREATED_BY);
    }

    private static ExpectedUser user(String userName, String nickName, String departmentCode, String roleKey)
    {
        return new ExpectedUser(userName, nickName, departmentCode, roleKey, "99", "0", "0", CREATED_BY,
            USER_MARKER + "|" + roleKey);
    }

    private record ExpectedDepartment(String code, String name, String parentCode, int orderNum, String status,
        String delFlag, String createBy) {}

    private record ExpectedUser(String userName, String nickName, String departmentCode, String roleKey,
        String userType, String status, String delFlag, String createBy, String remark) {}
}
