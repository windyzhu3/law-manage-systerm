package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class FlywayMigrationTest
{
    private static final String SQL_NULL = "<SQL-NULL>";

    @Test
    void migratesV015BaselineToTodoPhaseOneSchema()
    {
        String url = System.getenv("TODO_MIGRATION_DB_URL");
        assumeTrue(url != null && !url.isBlank(), "Migration database is provided by the CI quality gate");
        Flyway baselineFlyway = Flyway.configure()
            .dataSource(url, System.getenv("TODO_MIGRATION_DB_USER"), System.getenv("TODO_MIGRATION_DB_PASSWORD"))
            .baselineOnMigrate(true)
            .baselineVersion("0.15.0")
            .locations("classpath:db/migration")
            .target("0.20.27")
            .load();

        baselineFlyway.migrate();
        assertNoHistoricalMigrationExportRoleGrant(url);
        RoleSnapshot beforeFoundationGovernanceMigration = snapshotRoleState(url);
        Flyway flyway = Flyway.configure()
            .dataSource(url, System.getenv("TODO_MIGRATION_DB_USER"), System.getenv("TODO_MIGRATION_DB_PASSWORD"))
            .baselineOnMigrate(true)
            .baselineVersion("0.15.0")
            .locations("classpath:db/migration")
            .load();

        MigrateResult result = flyway.migrate();
        MigrationInfo current = flyway.info().current();

        assertTrue(result.success);
        assertEquals("0.20.29", current.getVersion().getVersion());
        verifyDatabaseInvariants(url);
        verifyV02PrdCatalogue(url);
        verifyDecisionAccountabilitySchema(url);
        verifyAdmissionEvidenceSchema(url);
        verifyFoundationResourceReadinessSchema(url);
        verifyHistoricalMigrationReadinessSchema(url);
        verifyHistoricalMigrationExportPermission(url);
        verifyFileSecurityReadinessSchema(url);
        verifyFinanceReadinessSchema(url);
        verifyAcceptanceReadinessSchema(url);
        verifyFoundationAdmissionAggregateQuery(url);
        verifyFoundationGovernanceRoles(url, beforeFoundationGovernanceMigration);
        verifySameMarkerRoleCollisionReceivesNoGrants(url);
    }

    private void verifyFoundationGovernanceRoles(String url, RoleSnapshot beforeFoundationGovernanceMigration)
    {
        Map<String, Set<String>> expectedPermissions = Map.of(
            "foundation_product_owner", Set.of("todo:decision:view", "todo:decision:edit", "todo:admission:view",
                "todo:admission:edit"),
            "foundation_security_reviewer", Set.of("todo:admission:view", "todo:admission:edit"),
            "foundation_arch_dba_reviewer", Set.of("todo:admission:view", "todo:admission:edit", "todo:admission:export"),
            "foundation_qa_acceptor", Set.of("todo:admission:view", "todo:admission:edit"),
            "foundation_independent_reviewer", Set.of("todo:admission:view", "todo:admission:edit")
        );
        Map<String, GovernanceRoleDefinition> expectedDefinitions = Map.of(
            "foundation_product_owner", new GovernanceRoleDefinition("Foundation产品负责人", "40"),
            "foundation_security_reviewer", new GovernanceRoleDefinition("Foundation安全评审人", "41"),
            "foundation_arch_dba_reviewer", new GovernanceRoleDefinition("Foundation架构DBA评审人", "42"),
            "foundation_qa_acceptor", new GovernanceRoleDefinition("Foundation QA验收人", "43"),
            "foundation_independent_reviewer", new GovernanceRoleDefinition("Foundation独立准入评审人", "44")
        );
        try (Connection connection = DriverManager.getConnection(url, System.getenv("TODO_MIGRATION_DB_USER"),
            System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            assertEquals(5L, count(connection,
                "select count(*) from sys_role where role_key in ("
                    + "'foundation_product_owner','foundation_security_reviewer',"
                    + "'foundation_arch_dba_reviewer','foundation_qa_acceptor',"
                    + "'foundation_independent_reviewer') and status='0' and del_flag='0'"));
            assertEquals(5L, count(connection,
                "select count(*) from sys_role where create_by='flyway-v0.20.28'"));
            assertEquals(expectedPermissions.keySet(), roleKeysCreatedByFoundationMigration(connection));
            assertEquals(expectedDefinitions, governanceRoleDefinitions(connection));
            assertEquals(0L, count(connection,
                "select count(*) from sys_user where user_name like 'ft\\_%' escape '\\\\'"));
            assertEquals(0L, count(connection,
                "select count(*) from sys_dept where dept_code in ('FOUNDATION_TEST_FIRM','FOUNDATION_TEST_SALES',"
                    + "'FOUNDATION_TEST_CASE_MANAGEMENT','FOUNDATION_TEST_GENERAL_LAW','FOUNDATION_TEST_FINANCE',"
                    + "'FOUNDATION_TEST_GOVERNANCE')"));
            assertEquals(0L, count(connection,
                "select count(*) from sys_dept where create_by='foundation-test-seeder'"));
            assertEquals(0L, count(connection,
                "select count(*) from sys_user_role ur join sys_user u on u.user_id=ur.user_id "
                    + "where u.user_name like 'ft\\_%' escape '\\\\'"));

            Set<Long> allowedMenuIds = definitionMenuAndAncestorIds(connection);
            RoleSnapshot afterFoundationGovernanceMigration = snapshotRoleState(connection);
            assertPreExistingRolesUnchanged(beforeFoundationGovernanceMigration, afterFoundationGovernanceMigration);
            assertEquals(expectedPermissions.keySet(), newRoleKeys(beforeFoundationGovernanceMigration,
                afterFoundationGovernanceMigration), "Unexpected governance role delta");
            assertEquals(expectedRoleMenuGrantMultiset(beforeFoundationGovernanceMigration.roleMenuGrants(),
                expectedRoleMenuGrants(connection, expectedPermissions, allowedMenuIds)),
                afterFoundationGovernanceMigration.roleMenuGrants(), "Unexpected role-menu grant delta");
            for (Map.Entry<String, Set<String>> expected : expectedPermissions.entrySet())
            {
                assertEquals(expected.getValue(), buttonPermissions(connection, expected.getKey()),
                    () -> "Unexpected button permissions for " + expected.getKey());
                assertEquals((long) expected.getValue().size(), roleMenuGrantCount(connection, expected.getKey(), "F"),
                    () -> "Unexpected button permission grant count for " + expected.getKey());
                assertEquals(allowedMenuIds, nonButtonMenuIds(connection, expected.getKey()),
                    () -> "Unexpected non-button menus for " + expected.getKey());
                assertEquals((long) allowedMenuIds.size(), nonButtonMenuGrantCount(connection, expected.getKey()),
                    () -> "Unexpected non-button menu grant count for " + expected.getKey());
            }
        }
        catch (SQLException exception)
        {
            throw new AssertionError("Foundation governance role invariants failed", exception);
        }
    }

    private void verifySameMarkerRoleCollisionReceivesNoGrants(String url)
    {
        try (Connection connection = DriverManager.getConnection(url, System.getenv("TODO_MIGRATION_DB_USER"),
            System.getenv("TODO_MIGRATION_DB_PASSWORD")); Statement statement = connection.createStatement())
        {
            statement.executeUpdate("delete rm from sys_role_menu rm join sys_role r on r.role_id=rm.role_id "
                + "where r.role_key in ('foundation_product_owner','foundation_security_reviewer',"
                + "'foundation_arch_dba_reviewer','foundation_qa_acceptor','foundation_independent_reviewer')");
            statement.executeUpdate("delete from sys_role where role_key in ('foundation_product_owner',"
                + "'foundation_security_reviewer','foundation_arch_dba_reviewer','foundation_qa_acceptor',"
                + "'foundation_independent_reviewer')");
            statement.executeUpdate("delete from flyway_schema_history where version='0.20.28'");
            statement.executeUpdate("insert into sys_role(role_name,role_key,role_sort,data_scope,menu_check_strictly,"
                + "dept_check_strictly,status,del_flag,create_by,create_time) values ('collision',"
                + "'foundation_product_owner',999,'5',1,1,'0','0','flyway-v0.20.28',sysdate())");
        }
        catch (SQLException exception)
        {
            throw new AssertionError("Collision scenario setup failed", exception);
        }

        Flyway collisionFlyway = Flyway.configure()
            .dataSource(url, System.getenv("TODO_MIGRATION_DB_USER"), System.getenv("TODO_MIGRATION_DB_PASSWORD"))
            .baselineOnMigrate(true)
            .baselineVersion("0.15.0")
            .locations("classpath:db/migration")
            .outOfOrder(true)
            .load();
        assertTrue(collisionFlyway.migrate().success);

        try (Connection connection = DriverManager.getConnection(url, System.getenv("TODO_MIGRATION_DB_USER"),
            System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            assertEquals(0L, count(connection, "select count(*) from sys_role_menu rm join sys_role r "
                + "on r.role_id=rm.role_id where r.role_key='foundation_product_owner' and r.role_name='collision'"));
            assertEquals(4L, count(connection, "select count(*) from sys_role where role_key in "
                + "('foundation_security_reviewer','foundation_arch_dba_reviewer','foundation_qa_acceptor',"
                + "'foundation_independent_reviewer') and create_by='flyway-v0.20.28'"));
        }
        catch (SQLException exception)
        {
            throw new AssertionError("Collision scenario verification failed", exception);
        }
    }

    private RoleSnapshot snapshotRoleState(String url)
    {
        try (Connection connection = DriverManager.getConnection(url, System.getenv("TODO_MIGRATION_DB_USER"),
            System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            return snapshotRoleState(connection);
        }
        catch (SQLException exception)
        {
            throw new AssertionError("Foundation governance role snapshot failed", exception);
        }
    }

    private RoleSnapshot snapshotRoleState(Connection connection) throws SQLException
    {
        Map<Long, RoleState> roleStates = new HashMap<>();
        Map<RoleMenuGrant, Integer> roleMenuGrants = new HashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("select role_id,role_key,role_name,role_sort,data_scope,"
                 + "menu_check_strictly,dept_check_strictly,status,del_flag,create_by,update_by,create_time,update_time,remark "
                 + "from sys_role"))
        {
            while (rows.next())
            {
                RoleState role = new RoleState(rows.getLong("role_id"), nullable(rows, "role_key"),
                    nullable(rows, "role_name"), nullable(rows, "role_sort"), nullable(rows, "data_scope"),
                    nullable(rows, "menu_check_strictly"), nullable(rows, "dept_check_strictly"), nullable(rows, "status"),
                    nullable(rows, "del_flag"), nullable(rows, "create_by"), nullable(rows, "update_by"),
                    nullableObject(rows, "create_time"), nullableObject(rows, "update_time"), nullable(rows, "remark"));
                roleStates.put(role.roleId(), role);
            }
        }
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("select r.role_key,rm.menu_id from sys_role_menu rm "
                 + "join sys_role r on r.role_id=rm.role_id"))
        {
            while (rows.next())
            {
                roleMenuGrants.merge(new RoleMenuGrant(rows.getString(1), rows.getLong(2)), 1, Integer::sum);
            }
        }
        return new RoleSnapshot(roleStates, roleMenuGrants);
    }

    private Set<RoleMenuGrant> expectedRoleMenuGrants(Connection connection, Map<String, Set<String>> expectedPermissions,
        Set<Long> allowedMenuIds) throws SQLException
    {
        Set<RoleMenuGrant> expectedGrants = new HashSet<>();
        for (Map.Entry<String, Set<String>> expected : expectedPermissions.entrySet())
        {
            for (Long menuId : allowedMenuIds)
            {
                expectedGrants.add(new RoleMenuGrant(expected.getKey(), menuId));
            }
            for (String permission : expected.getValue())
            {
                expectedGrants.add(new RoleMenuGrant(expected.getKey(), buttonMenuId(connection, permission)));
            }
        }
        return expectedGrants;
    }

    private long buttonMenuId(Connection connection, String permission) throws SQLException
    {
        try (PreparedStatement statement = connection.prepareStatement(
            "select menu_id from sys_menu where perms=? and menu_type='F'"))
        {
            statement.setString(1, permission);
            try (ResultSet rows = statement.executeQuery())
            {
                assertTrue(rows.next(), () -> "Required button permission menu must exist: " + permission);
                long menuId = rows.getLong(1);
                assertTrue(!rows.next(), () -> "Required button permission menu must be unique: " + permission);
                return menuId;
            }
        }
    }

    private String nullable(ResultSet rows, String column) throws SQLException
    {
        String value = rows.getString(column);
        return value == null ? SQL_NULL : value;
    }

    private Object nullableObject(ResultSet rows, String column) throws SQLException
    {
        Object value = rows.getObject(column);
        return value == null ? SQL_NULL : value;
    }

    private void assertPreExistingRolesUnchanged(RoleSnapshot before, RoleSnapshot after)
    {
        for (Map.Entry<Long, RoleState> entry : before.roleStates().entrySet())
        {
            assertEquals(entry.getValue(), after.roleStates().get(entry.getKey()),
                () -> "Pre-existing role was deleted or mutated: role_id=" + entry.getKey());
        }
        assertEquals(before.roleStates().size() + 5, after.roleStates().size(),
            "Migration must add exactly five roles without replacing existing role IDs");
    }

    private Set<String> newRoleKeys(RoleSnapshot before, RoleSnapshot after)
    {
        Set<Long> newRoleIds = new HashSet<>(after.roleStates().keySet());
        newRoleIds.removeAll(before.roleStates().keySet());
        Set<String> newRoleKeys = new HashSet<>();
        for (Long roleId : newRoleIds)
        {
            newRoleKeys.add(after.roleStates().get(roleId).roleKey());
        }
        return newRoleKeys;
    }

    private Map<RoleMenuGrant, Integer> expectedRoleMenuGrantMultiset(Map<RoleMenuGrant, Integer> before,
        Set<RoleMenuGrant> expectedNewGrants)
    {
        Map<RoleMenuGrant, Integer> expected = new HashMap<>(before);
        for (RoleMenuGrant grant : expectedNewGrants)
        {
            expected.merge(grant, 1, Integer::sum);
        }
        return expected;
    }

    private record RoleSnapshot(Map<Long, RoleState> roleStates, Map<RoleMenuGrant, Integer> roleMenuGrants)
    {
    }

    private record RoleState(long roleId, String roleKey, String roleName, String roleSort, String dataScope,
        String menuCheckStrictly, String deptCheckStrictly, String status, String delFlag, String createBy,
        String updateBy, Object createTime, Object updateTime, String remark)
    {
    }

    private record RoleMenuGrant(String roleKey, long menuId)
    {
    }

    private record GovernanceRoleDefinition(String roleName, String roleSort)
    {
    }

    private Map<String, GovernanceRoleDefinition> governanceRoleDefinitions(Connection connection) throws SQLException
    {
        Map<String, GovernanceRoleDefinition> definitions = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
            "select role_key,role_name,role_sort from sys_role where role_key in ("
                + "'foundation_product_owner','foundation_security_reviewer','foundation_arch_dba_reviewer',"
                + "'foundation_qa_acceptor','foundation_independent_reviewer') and data_scope='5' "
                + "and menu_check_strictly=1 and dept_check_strictly=1 and status='0' and del_flag='0' "
                + "and create_by='flyway-v0.20.28'"))
        {
            try (ResultSet rows = statement.executeQuery())
            {
                while (rows.next())
                {
                    definitions.put(rows.getString("role_key"), new GovernanceRoleDefinition(
                        rows.getString("role_name"), rows.getString("role_sort")));
                }
            }
        }
        return definitions;
    }

    private Set<String> roleKeysCreatedByFoundationMigration(Connection connection) throws SQLException
    {
        Set<String> roleKeys = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
            "select role_key from sys_role where create_by='flyway-v0.20.28'");
             ResultSet rows = statement.executeQuery())
        {
            while (rows.next())
            {
                roleKeys.add(rows.getString(1));
            }
        }
        return roleKeys;
    }

    private Set<String> buttonPermissions(Connection connection, String roleKey) throws SQLException
    {
        Set<String> permissions = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
            "select m.perms from sys_role_menu rm join sys_role r on r.role_id=rm.role_id "
                + "join sys_menu m on m.menu_id=rm.menu_id where r.role_key=? and m.menu_type='F' "
                + "and m.perms is not null and m.perms<>''"))
        {
            statement.setString(1, roleKey);
            try (ResultSet rows = statement.executeQuery())
            {
                while (rows.next())
                {
                    permissions.add(rows.getString(1));
                }
            }
        }
        return permissions;
    }

    private Set<String> roleKeysWithPermission(Connection connection, String permission) throws SQLException
    {
        Set<String> roleKeys = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
            "select r.role_key from sys_role_menu rm join sys_role r on r.role_id=rm.role_id "
                + "join sys_menu m on m.menu_id=rm.menu_id where m.perms=?"))
        {
            statement.setString(1, permission);
            try (ResultSet rows = statement.executeQuery())
            {
                while (rows.next())
                {
                    roleKeys.add(rows.getString(1));
                }
            }
        }
        return roleKeys;
    }

    private long roleMenuGrantCount(Connection connection, String roleKey, String menuType) throws SQLException
    {
        try (PreparedStatement statement = connection.prepareStatement(
            "select count(*) from sys_role_menu rm join sys_role r on r.role_id=rm.role_id "
                + "join sys_menu m on m.menu_id=rm.menu_id where r.role_key=? and m.menu_type=?"))
        {
            statement.setString(1, roleKey);
            statement.setString(2, menuType);
            try (ResultSet rows = statement.executeQuery())
            {
                assertTrue(rows.next());
                return rows.getLong(1);
            }
        }
    }

    private long nonButtonMenuGrantCount(Connection connection, String roleKey) throws SQLException
    {
        try (PreparedStatement statement = connection.prepareStatement(
            "select count(*) from sys_role_menu rm join sys_role r on r.role_id=rm.role_id "
                + "join sys_menu m on m.menu_id=rm.menu_id where r.role_key=? and m.menu_type<>'F'"))
        {
            statement.setString(1, roleKey);
            try (ResultSet rows = statement.executeQuery())
            {
                assertTrue(rows.next());
                return rows.getLong(1);
            }
        }
    }

    private Set<Long> nonButtonMenuIds(Connection connection, String roleKey) throws SQLException
    {
        Set<Long> menuIds = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
            "select m.menu_id from sys_role_menu rm join sys_role r on r.role_id=rm.role_id "
                + "join sys_menu m on m.menu_id=rm.menu_id where r.role_key=? and m.menu_type<>'F'"))
        {
            statement.setString(1, roleKey);
            try (ResultSet rows = statement.executeQuery())
            {
                while (rows.next())
                {
                    menuIds.add(rows.getLong(1));
                }
            }
        }
        return menuIds;
    }

    private Set<Long> definitionMenuAndAncestorIds(Connection connection) throws SQLException
    {
        Set<Long> menuIds = new HashSet<>();
        long menuId;
        try (PreparedStatement statement = connection.prepareStatement(
            "select menu_id from sys_menu where component='todo/config/index' order by menu_id limit 1");
             ResultSet rows = statement.executeQuery())
        {
            assertTrue(rows.next(), "Foundation configuration menu must exist");
            menuId = rows.getLong(1);
        }
        while (menuId != 0 && menuIds.add(menuId))
        {
            try (PreparedStatement statement = connection.prepareStatement("select parent_id from sys_menu where menu_id=?"))
            {
                statement.setLong(1, menuId);
                try (ResultSet rows = statement.executeQuery())
                {
                    assertTrue(rows.next(), "Foundation configuration menu ancestor must exist");
                    menuId = rows.getLong(1);
                }
            }
        }
        return menuIds;
    }

    private void verifyHistoricalMigrationExportPermission(String url)
    {
        try (Connection connection = DriverManager.getConnection(url, System.getenv("TODO_MIGRATION_DB_USER"),
            System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            assertEquals(1L,count(connection,"select count(*) from sys_menu where perms='todo:admission:export'"));
            assertEquals(1L,count(connection,"select count(*) from sys_role_menu rm join sys_menu m on m.menu_id=rm.menu_id "
                    + "join sys_role r on r.role_id=rm.role_id where m.perms='todo:admission:export' "
                    + "and r.role_key='foundation_arch_dba_reviewer' and r.status='0' and r.del_flag='0'"));
            assertEquals(Set.of("foundation_arch_dba_reviewer"), roleKeysWithPermission(connection,
                "todo:admission:export"));
        }
        catch (SQLException exception)
        {
            throw new AssertionError("Historical migration export permission invariants failed",exception);
        }
    }

    private void assertNoHistoricalMigrationExportRoleGrant(String url)
    {
        try (Connection connection = DriverManager.getConnection(url, System.getenv("TODO_MIGRATION_DB_USER"),
            System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            assertEquals(0L, count(connection, "select count(*) from sys_role_menu rm join sys_menu m on m.menu_id=rm.menu_id "
                + "where m.perms='todo:admission:export'"));
        }
        catch (SQLException exception)
        {
            throw new AssertionError("Historical migration export permission boundary invariants failed", exception);
        }
    }

    private void verifyFoundationAdmissionAggregateQuery(String url)
    {
        try (InputStream resource = getClass().getResourceAsStream(
                "/mapper/todo/TodoFoundationAdmissionReadinessMapper.xml");
             Connection connection = DriverManager.getConnection(url, System.getenv("TODO_MIGRATION_DB_USER"),
                System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            assertTrue(resource != null, "Foundation admission mapper must be available on the runtime classpath");
            String mapperXml = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
            Matcher select = Pattern.compile("<select[^>]*id=\"selectAdmissionFacts\"[^>]*>([\\s\\S]*?)</select>")
                .matcher(mapperXml);
            assertTrue(select.find(), "Foundation admission aggregate SQL must be present");
            try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(select.group(1)))
            {
                assertTrue(rows.next());
                assertEquals(12, rows.getInt("decision_total"));
                assertEquals(0, rows.getInt("decision_accountable"));
                assertEquals(8, rows.getInt("phase_one_total"));
                assertEquals(0, rows.getInt("phase_one_closed"));
                assertEquals(25, rows.getInt("prd_total"));
                assertEquals(25, rows.getInt("prd_ready"));
                assertEquals(25, rows.getInt("prd_valid_definition"));
                assertEquals(150, rows.getInt("prd_acceptance_ref_total"));
                assertEquals("OPEN", rows.getString("g02_evidence_status"));
                assertEquals("OPEN", rows.getString("g04_evidence_status"));
                assertEquals("OPEN", rows.getString("g05_evidence_status"));
                assertEquals("OPEN", rows.getString("g06_evidence_status"));
                assertEquals("OPEN", rows.getString("g07_evidence_status"));
                assertEquals(1, rows.getInt("foundation_migration_present"));
                assertEquals(0, rows.getInt("failed_migration_count"));
                assertEquals(4, rows.getInt("core_idempotency_index_count"));
            }
        }
        catch (SQLException | IOException exception)
        {
            throw new AssertionError("Foundation admission aggregate query failed", exception);
        }
    }

    private void verifyAcceptanceReadinessSchema(String url)
    {
        try (Connection connection = DriverManager.getConnection(url, System.getenv("TODO_MIGRATION_DB_USER"),
            System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            assertEquals(8L, count(connection,
                "select count(*) from todo_foundation_acceptance_requirement where gate_code='G-07'"));
            assertEquals(19L, count(connection,
                "select count(distinct template_code) from todo_acceptance_ref_mapping"));
            assertEquals(114L, count(connection,
                "select count(*) from todo_acceptance_ref_mapping"));
            assertEquals(114L, count(connection,
                "select count(*) from todo_acceptance_ref_mapping where status='UNMAPPED' "
                    + "and scenario_id is null and planned_test_ref is null and evidence_note is null "
                    + "and owner_user_id is null and reviewer_user_id is null"));
            assertEquals(0L, count(connection, "select count(*) from todo_acceptance_scenario"));
            assertEquals(0L, count(connection, "select count(*) from todo_acceptance_action"));
            assertEquals(0L, count(connection,
                "select count(*) from todo_trigger_rule r join todo_template t on t.template_id=r.template_id "
                    + "where t.template_code in ('TD-001','TD-002','TD-003','TD-004','TD-005','TD-006',"
                    + "'TD-007','TD-008','TD-009','TD-010','TD-011','TD-012','TD-013','TD-014','TD-015',"
                    + "'TD-016','TD-022','TD-023','TD-025') and r.enabled='Y'"));
        }
        catch (SQLException exception)
        {
            throw new AssertionError("Acceptance readiness database invariants failed", exception);
        }
    }

    private void verifyFinanceReadinessSchema(String url)
    {
        try (Connection connection = DriverManager.getConnection(url, System.getenv("TODO_MIGRATION_DB_USER"),
            System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            assertEquals(9L, count(connection,
                "select count(*) from todo_foundation_finance_requirement where gate_code='G-06'"));
            assertEquals(6L, count(connection,
                "select count(*) from todo_foundation_finance_requirement where source_status='CONFIRMED'"));
            assertEquals(2L, count(connection,
                "select count(*) from todo_foundation_finance_requirement where source_status='NEEDS_DECISION'"));
            assertEquals(1L, count(connection,
                "select count(*) from todo_foundation_finance_requirement where source_status='NEEDS_REVIEW'"));
            assertEquals(1L, count(connection,
                "select count(*) from todo_foundation_finance_requirement "
                    + "where gate_code='G-06' and requirement_code='FINANCE_BUSINESS_SIGNOFF' "
                    + "and source_status='NEEDS_REVIEW' "
                    + "and source_ref='doc/reviews/v0.2-foundation-g06-finance-formula-review-package.md'"));
            assertEquals(4L, count(connection,
                "select count(*) from information_schema.columns where table_schema=database() "
                    + "and table_name='biz_contract_fee_plan' "
                    + "and column_name in ('receivable_amount','received_amount','confirm_status','invoice_status')"));
            assertEquals(0L, count(connection,
                "select count(*) from information_schema.columns where table_schema=database() "
                    + "and table_name='biz_contract_fee_plan' "
                    + "and column_name in ('trigger_type','trigger_node_code','trigger_business_id',"
                    + "'collection_owner_id','due_rule_json','risk_fee_calc_id')"));
        }
        catch (SQLException exception)
        {
            throw new AssertionError("Finance readiness database invariants failed", exception);
        }
    }

    private void verifyFileSecurityReadinessSchema(String url)
    {
        try (Connection connection = DriverManager.getConnection(url, System.getenv("TODO_MIGRATION_DB_USER"),
            System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            assertEquals(7L,count(connection,"select count(*) from todo_foundation_file_security_requirement where gate_code='G-05'"));
            assertEquals(6L,count(connection,"select count(*) from todo_foundation_file_security_requirement where source_status='CONFIRMED'"));
            assertEquals(0L,count(connection,"select count(*) from todo_foundation_file_security_requirement where source_status='NEEDS_EVIDENCE'"));
            assertEquals(1L,count(connection,"select count(*) from todo_foundation_file_security_requirement where source_status='NEEDS_REVIEW'"));
            assertEquals(1L,count(connection,"select count(*) from todo_foundation_file_security_requirement "
                +"where requirement_code='PRD_MATERIAL_TYPE_E2E' and source_status='CONFIRMED' "
                +"and source_ref like '%FileMaterialEndToEndTest.java'"));
            assertEquals(1L,count(connection,"select count(*) from todo_foundation_file_security_requirement "
                +"where requirement_code='SECURITY_REVIEW_SIGNOFF' and source_status='NEEDS_REVIEW' "
                +"and source_ref='doc/reviews/v0.2-foundation-g05-file-security-review-package.md'"));
            assertEquals(5L,count(connection,"select count(*) from information_schema.tables where table_schema=database() and table_name in ('file_object','file_object_version','file_business_relation','file_access_log','file_storage_cleanup')"));
            assertEquals(4L,count(connection,"select count(*) from information_schema.columns where table_schema=database() and table_name='file_access_token' and column_name in ('token_hash','relation_id','actor_id','consumed_at')"));
            assertEquals(1L,count(connection,"select count(distinct index_name) from information_schema.statistics where table_schema=database() and table_name='file_access_token' and index_name='uk_file_access_token_hash' and non_unique=0"));
        }
        catch (SQLException exception)
        {
            throw new AssertionError("File security readiness database invariants failed",exception);
        }
    }

    private void verifyHistoricalMigrationReadinessSchema(String url)
    {
        try (Connection connection = DriverManager.getConnection(url, System.getenv("TODO_MIGRATION_DB_USER"),
            System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            assertEquals(8L,count(connection,"select count(*) from todo_foundation_migration_requirement where gate_code='G-04'"));
            assertEquals(3L,count(connection,"select count(*) from todo_foundation_migration_requirement where source_status='CONFIRMED'"));
            assertEquals(1L,count(connection,"select count(*) from todo_foundation_migration_requirement where source_status='NEEDS_DECISION'"));
            assertEquals(4L,count(connection,"select count(*) from todo_foundation_migration_requirement where source_status='NEEDS_EVIDENCE'"));
            assertEquals(4L,count(connection,"select count(*) from todo_foundation_migration_requirement where gate_code='G-04' "
                + "and source_status='NEEDS_EVIDENCE' and source_ref='doc/reviews/v0.2-foundation-g04-historical-migration-review-package.md'"));
            assertEquals(0L,count(connection,"select count(*) from information_schema.columns where table_schema=database() and table_name='biz_case' and column_name='business_line'"));
            assertEquals(0L,count(connection,"select count(*) from todo_instance i left join todo_template_version v on v.version_id=i.template_version_id where v.version_id is null"));
        }
        catch (SQLException exception)
        {
            throw new AssertionError("Historical migration readiness database invariants failed",exception);
        }
    }

    private void verifyFoundationResourceReadinessSchema(String url)
    {
        try (Connection connection = DriverManager.getConnection(url, System.getenv("TODO_MIGRATION_DB_USER"),
            System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            assertEquals(51L,count(connection,"select count(*) from todo_foundation_resource_requirement where gate_code='G-02'"));
            assertEquals(40L,count(connection,"select count(*) from todo_foundation_resource_requirement where resource_type='DICTIONARY'"));
            assertEquals(11L,count(connection,"select count(*) from todo_foundation_resource_requirement where resource_type='ROLE'"));
            assertEquals(39L,count(connection,"select count(*) from todo_foundation_resource_requirement where source_status='NEEDS_DECISION'"));
            assertEquals(5L,count(connection,"select count(*) from todo_foundation_resource_requirement where source_status='CONFLICTING' and decision_ref='Q-003'"));
            assertEquals(7L,count(connection,"select count(*) from todo_foundation_resource_requirement where source_status='CONFIRMED'"));
            assertEquals(3L,count(connection,"select count(*) from todo_foundation_resource_requirement where expected_values_json is not null"));
            assertEquals(3L,count(connection,"select json_length(expected_values_json) from todo_foundation_resource_requirement where resource_code='law_business_line'"));
            assertEquals(13L,count(connection,"select count(*) from todo_foundation_resource_requirement r "
                + "join json_table(coalesce(r.expected_values_json,json_array()), '$[*]' "
                + "columns(expected_value varchar(100) path '$.value',expected_label varchar(100) path '$.label')) expected"));
            assertEquals(1L,count(connection,"select count(*) from sys_dict_type where dict_type='law_business_line' and status='0'"));
            assertEquals(3L,count(connection,"select count(*) from sys_dict_data where dict_type='law_business_line' and status='0' "
                + "and dict_value in ('NON_LITIGATION','COMPREHENSIVE','EXECUTION')"));
            assertEquals(1L,count(connection,"select count(*) from sys_role where role_key='sales' and status='0' and del_flag='0'"));
            assertEquals(0L,count(connection,"select count(*) from sys_role_menu rm join sys_role r on r.role_id=rm.role_id where r.role_key='sales'"));
        }
        catch (SQLException exception)
        {
            throw new AssertionError("Foundation resource readiness database invariants failed",exception);
        }
    }

    private void verifyAdmissionEvidenceSchema(String url)
    {
        try (Connection connection = DriverManager.getConnection(url, System.getenv("TODO_MIGRATION_DB_USER"),
            System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            assertEquals(5L, count(connection,"select count(*) from todo_admission_evidence"));
            assertEquals(5L, count(connection,"select count(*) from todo_admission_evidence where status='OPEN' "
                + "and owner_user_id is null and reviewer_user_id is null and due_at is null and artifact_ref is null"));
            assertEquals(5L, count(connection,"select count(distinct gate_code) from todo_admission_evidence "
                + "where gate_code in ('G-02','G-04','G-05','G-06','G-07')"));
            assertEquals(2L, count(connection,"select count(*) from sys_menu where perms in ('todo:admission:view','todo:admission:edit')"));
        }
        catch (SQLException exception)
        {
            throw new AssertionError("Admission evidence database invariants failed", exception);
        }
    }

    private void verifyDecisionAccountabilitySchema(String url)
    {
        try (Connection connection = DriverManager.getConnection(url, System.getenv("TODO_MIGRATION_DB_USER"),
            System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            assertEquals(4L, count(connection,
                "select count(*) from information_schema.columns where table_schema=database() "
                    + "and table_name='todo_decision' and column_name in ('owner_user_id','owner_role_key','due_at','delivery_phase')"));
            assertEquals(12L, count(connection,
                "select count(*) from todo_decision where decision_code between 'Q-001' and 'Q-012' "
                    + "and status='OPEN' and owner_user_id is null and due_at is null"));
            assertEquals(8L, count(connection,
                "select count(*) from todo_decision where decision_code between 'Q-001' and 'Q-012' and delivery_phase='PHASE_ONE'"));
            assertEquals(4L, count(connection,
                "select count(*) from todo_decision where decision_code between 'Q-001' and 'Q-012' and delivery_phase='PHASE_TWO'"));
        }
        catch (SQLException exception)
        {
            throw new AssertionError("Decision accountability database invariants failed", exception);
        }
    }

    private void verifyV02PrdCatalogue(String url)
    {
        try (Connection connection = DriverManager.getConnection(url, System.getenv("TODO_MIGRATION_DB_USER"),
            System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            assertEquals(25L, count(connection, "select count(*) from todo_prd_definition_catalog"));
            assertEquals(25L, count(connection,
                "select count(*) from todo_prd_definition_catalog where definition_package_state='READY'"));
            assertEquals(25L, count(connection,
                "select count(*) from todo_prd_definition_catalog where production_state='BLOCKED'"));
            assertEquals(25L, count(connection,
                "select count(*) from todo_prd_definition_catalog c join todo_template t on t.template_code=c.template_code "
                    + "join todo_template_version v on v.template_id=t.template_id "
                    + "where v.status='DRAFT' and cast(v.definition_json as char)=cast(c.definition_json as char)"));
            assertEquals(0L, count(connection,
                "select count(*) from todo_trigger_rule r join todo_template t on t.template_id=r.template_id "
                    + "where t.template_code between 'TD-001' and 'TD-025' and r.enabled='Y'"));
            assertEquals(12L, count(connection,
                "select count(*) from todo_decision where decision_code between 'Q-001' and 'Q-012' "
                    + "and status='OPEN' and blocking='Y'"));
        }
        catch (SQLException exception)
        {
            throw new AssertionError("v0.2 PRD catalogue database invariants failed", exception);
        }
    }

    private long count(Connection connection, String sql) throws SQLException
    {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql))
        {
            assertTrue(rows.next());
            return rows.getLong(1);
        }
    }

    private void verifyDatabaseInvariants(String url)
    {
        try(Connection connection=DriverManager.getConnection(url,System.getenv("TODO_MIGRATION_DB_USER"),System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            long templateId;long versionId;
            try(Statement statement=connection.createStatement();ResultSet rows=statement.executeQuery("select t.template_id,v.version_id from todo_template t join todo_template_version v on v.template_id=t.template_id where t.template_code='LEAD_FIRST_CONTACT' and v.version_no=1")){assertTrue(rows.next());templateId=rows.getLong(1);versionId=rows.getLong(2);}
            try(Statement cleanup=connection.createStatement()){cleanup.executeUpdate("delete from todo_instance where trigger_idempotency_key='migration-invariant-key'");}
            long todoId=insertTodo(connection,templateId,versionId,"migration-invariant-key");
            assertThrows(SQLException.class,()->insertTodo(connection,templateId,versionId,"migration-invariant-key"));
            try(PreparedStatement update=connection.prepareStatement("update todo_instance set status='CLAIMED' where todo_id=? and status='CREATED'")){update.setLong(1,todoId);assertEquals(1,update.executeUpdate());assertEquals(0,update.executeUpdate());}
        }
        catch(SQLException exception){throw new AssertionError("Todo database invariants failed",exception);}
    }

    private long insertTodo(Connection connection,long templateId,long versionId,String key) throws SQLException
    {
        try(PreparedStatement insert=connection.prepareStatement("insert into todo_instance(todo_no,template_id,template_version_id,title,business_type,business_id,status,trigger_idempotency_key) values(?,?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS))
        {
            insert.setString(1,"IT"+System.nanoTime());insert.setLong(2,templateId);insert.setLong(3,versionId);insert.setString(4,"迁移验收待办");insert.setString(5,"LEAD");insert.setLong(6,999999L);insert.setString(7,"CREATED");insert.setString(8,key);insert.executeUpdate();try(ResultSet keys=insert.getGeneratedKeys()){assertTrue(keys.next());return keys.getLong(1);}
        }
    }
}
