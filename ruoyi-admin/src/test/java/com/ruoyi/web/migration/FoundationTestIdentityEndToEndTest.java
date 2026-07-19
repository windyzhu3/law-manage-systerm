package com.ruoyi.web.migration;

import static com.ruoyi.system.foundation.FoundationTestIdentityErrorCode.FOUNDATION_TEST_IDENTITIES_PROFILE_FORBIDDEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.system.foundation.FoundationTestIdentityCatalog;
import com.ruoyi.system.foundation.FoundationTestIdentityException;
import com.ruoyi.system.foundation.FoundationTestIdentityProvisioningResult;
import com.ruoyi.system.foundation.FoundationTestIdentityProvisioningService;
import com.ruoyi.system.foundation.FoundationTestPasswordPolicy;
import com.ruoyi.system.mapper.FoundationTestIdentityMapper;
import com.ruoyi.web.foundation.FoundationTestIdentityConfiguration;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.EnableTransactionManagement;

class FoundationTestIdentityEndToEndTest
{
    @Test
    void provisionsRepairsAndRecreatesOnlyIsolatedTestIdentitiesWithoutHashChurn()
    {
        FoundationIdentityDatabaseSupport database = FoundationIdentityDatabaseSupport.migrate();
        JdbcTemplate jdbc = database.jdbcTemplate();

        assertProductionPathEmpty(jdbc);
        RuntimeException forbiddenStartup = assertThrows(RuntimeException.class,
            () -> database.startContext("local", "prod"));
        FoundationTestIdentityException profileFailure = findCause(forbiddenStartup,
            FoundationTestIdentityException.class);
        assertNotNull(profileFailure, "local,prod startup must expose the stable Foundation profile error");
        assertEquals(FOUNDATION_TEST_IDENTITIES_PROFILE_FORBIDDEN, profileFailure.getCode());
        assertProductionPathEmpty(jdbc);

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
            int originalDepartmentCount = foundationDepartmentCount(contextJdbc);
            int originalActiveUserCount = activeFoundationUserCount(contextJdbc);
            int originalActiveRoleLinkCount = activeFoundationRoleLinkCount(contextJdbc);

            FoundationTestIdentityProvisioningResult second = service.provision(database.password());

            assertEquals(new FoundationTestIdentityProvisioningResult(0, 30, 0), second);
            assertEquals(originalDepartmentCount, foundationDepartmentCount(contextJdbc));
            assertEquals(originalActiveUserCount, activeFoundationUserCount(contextJdbc));
            assertEquals(originalActiveRoleLinkCount, activeFoundationRoleLinkCount(contextJdbc));
            assertEquals(originalHashes, activePasswordHashes(contextJdbc),
                "Idempotent provisioning must preserve BCrypt bytes exactly");

            RealUserSnapshot realUserBefore = realUserSnapshot(contextJdbc, "admin");
            moveUserToDepartment(contextJdbc, "ft_sales", "FOUNDATION_TEST_FINANCE");
            addRole(contextJdbc, "ft_case_manager", "sales");

            FoundationTestIdentityProvisioningResult third = service.provision(database.password());

            assertEquals(new FoundationTestIdentityProvisioningResult(0, 28, 2), third);
            assertEquals(originalHashes, activePasswordHashes(contextJdbc),
                "Placement and role repair must not churn password hashes");
            assertEquals(realUserBefore, realUserSnapshot(contextJdbc, "admin"),
                "Provisioning must not mutate a real user");
            assertExactActiveFoundationState(contextJdbc, encoder, database.password());

            String recreatedUserName = "ft_lawyer_l2";
            long deletedUserId = userId(contextJdbc, recreatedUserName);
            String deletedHash = originalHashes.get(recreatedUserName);
            assertEquals(1, contextJdbc.update("update sys_user set del_flag='2' where user_id=?", deletedUserId));

            FoundationTestIdentityProvisioningResult fourth = service.provision(database.password());

            assertEquals(new FoundationTestIdentityProvisioningResult(2, 28, 0), fourth);
            List<UserHistoryRow> history = contextJdbc.query(
                "select user_id,del_flag,password from sys_user where user_name=? order by user_id",
                (rows, rowNum) -> new UserHistoryRow(rows.getLong(1), rows.getString(2), rows.getString(3)),
                recreatedUserName);
            assertEquals(2, history.size());
            assertEquals(new UserHistoryRow(deletedUserId, "2", deletedHash), history.get(0));
            UserHistoryRow activeRecreation = history.get(1);
            assertNotEquals(deletedUserId, activeRecreation.userId());
            assertTrue(activeRecreation.userId() > 0, "MyBatis must return the real generated user key");
            assertEquals("0", activeRecreation.delFlag());
            assertTrue(encoder.matches(database.password(), activeRecreation.password()));
            Map<String, String> hashesAfterRecreation = activePasswordHashes(contextJdbc);
            originalHashes.forEach((userName, hash) -> {
                if (!recreatedUserName.equals(userName))
                {
                    assertEquals(hash, hashesAfterRecreation.get(userName),
                        () -> "Deleted-history recreation churned unrelated hash for " + userName);
                }
            });
            assertExactActiveFoundationState(contextJdbc, encoder, database.password());
        }
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
        Set<String> expectedDepartmentCodes = FoundationTestIdentityCatalog.departments().stream()
            .map(FoundationTestIdentityCatalog.DepartmentSpec::code).collect(Collectors.toSet());
        Set<String> actualDepartmentCodes = new LinkedHashSet<>(jdbc.queryForList(
            "select dept_code from sys_dept where dept_code like 'FOUNDATION_TEST_%' "
                + "and create_by='foundation-test-seeder' order by dept_code", String.class));
        assertEquals(expectedDepartmentCodes, actualDepartmentCodes);
        assertEquals(6, foundationDepartmentCount(jdbc));
        assertEquals(6, jdbc.queryForObject(
            "select count(*) from sys_dept where dept_code like 'FOUNDATION_TEST_%'", Integer.class));

        Set<String> expectedUsers = FoundationTestIdentityCatalog.users().stream()
            .map(FoundationTestIdentityCatalog.UserSpec::userName).collect(Collectors.toSet());
        Set<String> actualUsers = new LinkedHashSet<>(jdbc.queryForList(
            "select user_name from sys_user where user_type='99' and status='0' and del_flag='0' "
                + "and create_by='foundation-test-seeder' order by user_name", String.class));
        assertEquals(expectedUsers, actualUsers);
        assertEquals(12, activeFoundationUserCount(jdbc));

        Map<String, Set<String>> expectedRoles = FoundationTestIdentityCatalog.users().stream()
            .collect(Collectors.toMap(FoundationTestIdentityCatalog.UserSpec::userName,
                spec -> Set.of(spec.roleKey())));
        assertEquals(expectedRoles, activeRoleAssignments(jdbc));
        assertEquals(12, activeFoundationRoleLinkCount(jdbc));
        assertEquals(0, jdbc.queryForObject(
            "select count(*) from sys_user_role ur join sys_user u on u.user_id=ur.user_id "
                + "join sys_role r on r.role_id=ur.role_id where u.user_type='99' and u.status='0' "
                + "and u.del_flag='0' and r.role_key in ('enforcement_primary_assistant',"
                + "'enforcement_secondary_assistant','execution_manager','execution_assistant_l1',"
                + "'execution_assistant_l2')", Integer.class));

        Map<String, String> hashes = activePasswordHashes(jdbc);
        assertEquals(12, hashes.size());
        hashes.forEach((userName, hash) -> assertTrue(encoder.matches(rawPassword, hash),
            () -> "BCrypt password mismatch for " + userName));

        Map<String, String> expectedDepartments = FoundationTestIdentityCatalog.users().stream()
            .collect(Collectors.toMap(FoundationTestIdentityCatalog.UserSpec::userName,
                FoundationTestIdentityCatalog.UserSpec::departmentCode));
        Map<String, String> actualDepartments = jdbc.query(
            "select u.user_name,d.dept_code from sys_user u join sys_dept d on d.dept_id=u.dept_id "
                + "where u.user_type='99' and u.status='0' and u.del_flag='0'",
            rows -> {
                Map<String, String> result = new HashMap<>();
                while (rows.next())
                {
                    result.put(rows.getString(1), rows.getString(2));
                }
                return result;
            });
        assertEquals(expectedDepartments, actualDepartments);

        List<Long> departmentIds = jdbc.queryForList(
            "select dept_id from sys_dept where dept_code like 'FOUNDATION_TEST_%' order by dept_id", Long.class);
        List<Long> userIds = jdbc.queryForList(
            "select user_id from sys_user where user_type='99' and status='0' and del_flag='0' order by user_id",
            Long.class);
        assertEquals(6, new LinkedHashSet<>(departmentIds).size());
        assertEquals(12, new LinkedHashSet<>(userIds).size());
        assertTrue(departmentIds.stream().allMatch(id -> id != null && id > 0),
            "MyBatis must return real generated department keys");
        assertTrue(userIds.stream().allMatch(id -> id != null && id > 0),
            "MyBatis must return real generated user keys");
    }

    private void assertRealMyBatisSetMapping(FoundationTestIdentityMapper mapper)
    {
        FoundationTestIdentityCatalog.governanceRolePermissions().forEach((roleKey, expected) -> {
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

    private int foundationDepartmentCount(JdbcTemplate jdbc)
    {
        return jdbc.queryForObject(
            "select count(*) from sys_dept where dept_code like 'FOUNDATION_TEST_%' "
                + "and create_by='foundation-test-seeder'", Integer.class);
    }

    private int activeFoundationUserCount(JdbcTemplate jdbc)
    {
        return jdbc.queryForObject("select count(*) from sys_user where user_type='99' and status='0' "
            + "and del_flag='0' and create_by='foundation-test-seeder'", Integer.class);
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

    private long userId(JdbcTemplate jdbc, String userName)
    {
        return jdbc.queryForObject("select user_id from sys_user where user_name=? and user_type='99' "
            + "and status='0' and del_flag='0'", Long.class, userName);
    }

    private RealUserSnapshot realUserSnapshot(JdbcTemplate jdbc, String userName)
    {
        List<String> row = jdbc.queryForObject(
            "select * from sys_user where user_name=? and user_type<>'99' order by user_id limit 1",
            (rows, rowNum) -> FoundationIdentityDatabaseSupport.rowValues(rows), userName);
        List<Long> roles = jdbc.queryForList("select ur.role_id from sys_user_role ur join sys_user u "
            + "on u.user_id=ur.user_id where u.user_name=? and u.user_type<>'99' order by ur.role_id",
            Long.class, userName);
        return new RealUserSnapshot(row, roles);
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

    private record RealUserSnapshot(List<String> row, List<Long> roles) {}

    private record UserHistoryRow(long userId, String delFlag, String password) {}
}

final class FoundationIdentityDatabaseSupport
{
    private static final String PASSWORD_ENVIRONMENT_VARIABLE = "FOUNDATION_TEST_USER_PASSWORD";
    private static final String[] SNAPSHOT_TABLES = {
        "sys_role order by role_id",
        "sys_role_menu order by role_id,menu_id",
        "sys_dept order by dept_id",
        "sys_user order by user_id",
        "sys_user_role order by user_id,role_id"
    };

    private final String url;
    private final String password;

    private FoundationIdentityDatabaseSupport(String url, String password)
    {
        this.url = url;
        this.password = password;
    }

    static FoundationIdentityDatabaseSupport migrate()
    {
        String password = System.getenv(PASSWORD_ENVIRONMENT_VARIABLE);
        assumeTrue(password != null && !password.isBlank(),
            PASSWORD_ENVIRONMENT_VARIABLE + " is provided only by the isolated test gate");
        String url = MigrationTestDatabase.migrate();
        FoundationIdentityDatabaseSupport database = new FoundationIdentityDatabaseSupport(url, password);
        database.restoreKnownFlywayContractTestSentinel();
        return database;
    }

    String password()
    {
        return password;
    }

    JdbcTemplate jdbcTemplate()
    {
        org.springframework.jdbc.datasource.DriverManagerDataSource dataSource =
            new org.springframework.jdbc.datasource.DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(url);
        dataSource.setUsername(MigrationTestDatabase.user());
        dataSource.setPassword(MigrationTestDatabase.password());
        return new JdbcTemplate(dataSource);
    }

    private void restoreKnownFlywayContractTestSentinel()
    {
        JdbcTemplate jdbc = jdbcTemplate();
        int knownSentinel = jdbc.queryForObject(
            "select count(*) from sys_role r where r.role_key='foundation_product_owner' "
                + "and r.role_name='collision' and r.role_sort=999 and r.data_scope='5' "
                + "and r.menu_check_strictly=1 and r.dept_check_strictly=1 and r.status='0' and r.del_flag='0' "
                + "and r.create_by='flyway-v0.20.28' and not exists "
                + "(select 1 from sys_role_menu rm where rm.role_id=r.role_id)", Integer.class);
        if (knownSentinel == 0)
        {
            return;
        }
        if (knownSentinel != 1)
        {
            throw new AssertionError("Unexpected duplicate Flyway contract-test sentinels");
        }
        int foundationIdentityLinks = jdbc.queryForObject(
            "select count(*) from sys_user_role ur join sys_role r on r.role_id=ur.role_id "
                + "where r.role_key='foundation_product_owner'", Integer.class);
        assertEquals(0, foundationIdentityLinks,
            "Known Flyway contract sentinel may be restored only before Foundation identity provisioning");
        assertEquals(1, jdbc.update("delete from sys_role where role_key='foundation_product_owner' "
            + "and role_name='collision' and create_by='flyway-v0.20.28'"));
        assertEquals(1, jdbc.update("delete from flyway_schema_history where version='0.20.28' and success=1"));
        Flyway.configure()
            .dataSource(url, MigrationTestDatabase.user(), MigrationTestDatabase.password())
            .baselineOnMigrate(true)
            .baselineVersion("0.15.0")
            .locations("classpath:db/migration")
            .load()
            .migrate();
    }

    ConfigurableApplicationContext startContext(String... profiles)
    {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.datasource.url", url);
        properties.put("spring.datasource.username", MigrationTestDatabase.user());
        properties.put("spring.datasource.password", MigrationTestDatabase.password());
        properties.put("spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver");
        properties.put("spring.datasource.hikari.maximum-pool-size", "3");
        properties.put("spring.datasource.hikari.minimum-idle", "0");
        properties.put("spring.flyway.enabled", "false");
        properties.put("spring.quartz.auto-startup", "false");
        properties.put("spring.task.scheduling.enabled", "false");
        properties.put("spring.main.banner-mode", "off");
        properties.put("spring.main.log-startup-info", "false");
        properties.put("mybatis.type-aliases-package", "com.ruoyi.common.core.domain.entity");
        properties.put("mybatis.mapper-locations", "classpath:mapper/system/FoundationTestIdentityMapper.xml");
        properties.put("foundation.test-identities.enabled", "true");
        properties.put("foundation.test-identities.password", password);
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles(profiles);
        environment.getPropertySources().addFirst(new MapPropertySource("foundationIdentityIntegration", properties));
        return new SpringApplicationBuilder(FoundationIdentityTestApplication.class)
            .environment(environment)
            .web(WebApplicationType.NONE)
            .registerShutdownHook(false)
            .properties(properties)
            .run();
    }

    DatabaseSnapshot snapshot(JdbcTemplate jdbc)
    {
        Map<String, List<List<String>>> tables = new LinkedHashMap<>();
        for (String tableAndOrder : SNAPSHOT_TABLES)
        {
            String table = tableAndOrder.substring(0, tableAndOrder.indexOf(' '));
            tables.put(table, jdbc.query("select * from " + tableAndOrder,
                (rows, rowNum) -> rowValues(rows)));
        }
        return new DatabaseSnapshot(tables);
    }

    static List<String> rowValues(ResultSet rows) throws SQLException
    {
        ResultSetMetaData metadata = rows.getMetaData();
        List<String> values = new ArrayList<>(metadata.getColumnCount());
        for (int column = 1; column <= metadata.getColumnCount(); column++)
        {
            String value = rows.getString(column);
            values.add(value == null ? "<SQL-NULL>" : value);
        }
        return values;
    }

    record DatabaseSnapshot(Map<String, List<List<String>>> tables) {}
}

@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
@EnableTransactionManagement
@MapperScan(basePackageClasses = FoundationTestIdentityMapper.class)
@Import({FoundationTestIdentityConfiguration.class, FoundationTestIdentityProvisioningService.class,
    FoundationTestPasswordPolicy.class})
class FoundationIdentityTestApplication
{
    @Bean
    BCryptPasswordEncoder foundationTestPasswordEncoder()
    {
        return new BCryptPasswordEncoder();
    }
}
