package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.ruoyi.system.foundation.FoundationTestIdentityProvisioningService;
import com.ruoyi.system.foundation.FoundationTestPasswordPolicy;
import com.ruoyi.system.mapper.FoundationTestIdentityMapper;
import com.ruoyi.web.foundation.FoundationTestIdentityConfiguration;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

final class FoundationTestIdentityTestSupport
{
    private static final String PASSWORD_ENVIRONMENT_VARIABLE = "FOUNDATION_TEST_USER_PASSWORD";
    private static final String CREATED_BY = "foundation-test-seeder";
    private static final String USER_MARKER = "FOUNDATION_TEST_IDENTITY|DO_NOT_USE_FOR_PRODUCTION_EVIDENCE";
    private static final String FOUNDATION_DEPARTMENT_CODES =
        "'FOUNDATION_TEST_FIRM','FOUNDATION_TEST_SALES','FOUNDATION_TEST_CASE_MANAGEMENT',"
            + "'FOUNDATION_TEST_GENERAL_LAW','FOUNDATION_TEST_FINANCE','FOUNDATION_TEST_GOVERNANCE'";
    private static final String[] SNAPSHOT_TABLES = {
        "sys_role order by role_id",
        "sys_role_menu order by role_id,menu_id",
        "sys_dept order by dept_id",
        "sys_user order by user_id",
        "sys_user_role order by user_id,role_id",
        "sys_user_post order by user_id,post_id"
    };

    private final String url;
    private final String password;

    private FoundationTestIdentityTestSupport(String url, String password)
    {
        this.url = url;
        this.password = password;
    }

    static FoundationTestIdentityTestSupport migrate()
    {
        String password = System.getenv(PASSWORD_ENVIRONMENT_VARIABLE);
        assumeTrue(password != null && !password.isBlank(),
            PASSWORD_ENVIRONMENT_VARIABLE + " is provided only by the isolated test gate");
        String url = MigrationTestDatabase.migrate();
        FoundationTestIdentityTestSupport database = new FoundationTestIdentityTestSupport(url, password);
        database.restoreKnownFlywayContractTestSentinel();
        return database;
    }

    String password()
    {
        return password;
    }

    JdbcTemplate jdbcTemplate()
    {
        return new JdbcTemplate(dataSource());
    }

    ConfigurableApplicationContext startContext(String... profiles)
    {
        return new SpringApplicationBuilder(FoundationIdentityTestApplication.class)
            .environment(environment(applicationProperties(), profiles))
            .web(WebApplicationType.NONE)
            .registerShutdownHook(false)
            .run();
    }

    ConfigurableApplicationContext startProfileGuardContext(
        FoundationTestIdentityProvisioningService provisioningProbe, String... profiles)
    {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("foundation.test-identities.enabled", "true");
        properties.put("foundation.test-identities.password", password);
        properties.put("spring.main.banner-mode", "off");
        properties.put("spring.main.log-startup-info", "false");
        return new SpringApplicationBuilder(FoundationIdentityProfileGuardTestApplication.class)
            .environment(environment(properties, profiles))
            .web(WebApplicationType.NONE)
            .registerShutdownHook(false)
            .initializers(applicationContext -> applicationContext.getBeanFactory()
                .registerSingleton("foundationProfileGuardProvisioningProbe", provisioningProbe))
            .run();
    }

    IdentityCleanup identityCleanup()
    {
        JdbcTemplate jdbc = jdbcTemplate();
        Long userAutoIncrement = autoIncrement(jdbc, "sys_user");
        Long departmentAutoIncrement = autoIncrement(jdbc, "sys_dept");
        return new IdentityCleanup(this, userAutoIncrement, departmentAutoIncrement);
    }

    DatabaseSnapshot snapshot(JdbcTemplate jdbc)
    {
        Map<String, List<Map<String, String>>> tables = new LinkedHashMap<>();
        for (String tableAndOrder : SNAPSHOT_TABLES)
        {
            String table = tableAndOrder.substring(0, tableAndOrder.indexOf(' '));
            tables.put(table, tableSnapshot(jdbc, tableAndOrder));
        }
        return new DatabaseSnapshot(tables);
    }

    List<Map<String, String>> tableSnapshot(JdbcTemplate jdbc, String tableAndOrder)
    {
        return jdbc.query("select * from " + tableAndOrder, (rows, rowNum) -> rowMap(rows));
    }

    Map<String, UserIdentitySnapshot> activeTestIdentities(JdbcTemplate jdbc)
    {
        Map<String, UserIdentitySnapshot> identities = new LinkedHashMap<>();
        jdbc.query("select user_id,user_name from sys_user where user_type='99' and status='0' "
            + "and del_flag='0' order by user_id", rows -> {
                while (rows.next())
                {
                    long userId = rows.getLong("user_id");
                    identities.put(rows.getString("user_name"), userIdentityById(jdbc, userId));
                }
            });
        return identities;
    }

    Map<Long, UserIdentitySnapshot> nonTestIdentities(JdbcTemplate jdbc)
    {
        Map<Long, UserIdentitySnapshot> identities = new LinkedHashMap<>();
        jdbc.query("select user_id from sys_user where user_type is null or user_type<>'99' order by user_id",
            rows -> {
                while (rows.next())
                {
                    long userId = rows.getLong(1);
                    identities.put(userId, userIdentityById(jdbc, userId));
                }
            });
        return identities;
    }

    UserIdentitySnapshot userIdentityById(JdbcTemplate jdbc, long userId)
    {
        Map<String, String> row = jdbc.queryForObject("select * from sys_user where user_id=?",
            (rows, rowNum) -> rowMap(rows), userId);
        List<Long> roles = jdbc.queryForList(
            "select role_id from sys_user_role where user_id=? order by role_id", Long.class, userId);
        List<Long> posts = jdbc.queryForList(
            "select post_id from sys_user_post where user_id=? order by post_id", Long.class, userId);
        return new UserIdentitySnapshot(userId, row.get("user_name"), row, roles, posts);
    }

    private Map<String, Object> applicationProperties()
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
        return properties;
    }

    private StandardEnvironment environment(Map<String, Object> properties, String... profiles)
    {
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles(profiles);
        environment.getPropertySources().addFirst(new MapPropertySource("foundationIdentityIntegration", properties));
        return environment;
    }

    private DataSource dataSource()
    {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(url);
        dataSource.setUsername(MigrationTestDatabase.user());
        dataSource.setPassword(MigrationTestDatabase.password());
        return dataSource;
    }

    private Long autoIncrement(JdbcTemplate jdbc, String table)
    {
        Long value = jdbc.queryForObject("select auto_increment from information_schema.tables "
            + "where table_schema=database() and table_name=?", Long.class, table);
        if (value == null || value < 1)
        {
            throw new AssertionError("Missing auto-increment state for " + table);
        }
        return value;
    }

    private void cleanFoundationIdentities(long userAutoIncrement, long departmentAutoIncrement)
    {
        DataSource dataSource = dataSource();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        transaction.executeWithoutResult(status -> {
            String markedUsers = "select user_id from sys_user where user_type='99' and create_by='" + CREATED_BY
                + "' and remark like '" + USER_MARKER + "%'";
            jdbc.update("delete from sys_user_post where user_id in (" + markedUsers + ")");
            jdbc.update("delete from sys_user_role where user_id in (" + markedUsers + ")");
            jdbc.update("delete from sys_user where user_type='99' and create_by=? and remark like ?",
                CREATED_BY, USER_MARKER + "%");
            jdbc.update("delete from sys_dept where dept_code in (" + FOUNDATION_DEPARTMENT_CODES + ") "
                + "and create_by='" + CREATED_BY + "'");
        });
        jdbc.execute("alter table sys_user auto_increment=" + userAutoIncrement);
        jdbc.execute("alter table sys_dept auto_increment=" + departmentAutoIncrement);
        int residualUsers = jdbc.queryForObject("select count(*) from sys_user where user_type='99' "
            + "and create_by=? and remark like ?", Integer.class, CREATED_BY, USER_MARKER + "%");
        int residualDepartments = jdbc.queryForObject("select count(*) from sys_dept where dept_code in ("
            + FOUNDATION_DEPARTMENT_CODES + ") and create_by=?", Integer.class, CREATED_BY);
        if (residualUsers != 0 || residualDepartments != 0)
        {
            throw new AssertionError("Foundation identity cleanup left residual rows");
        }
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
        if (foundationIdentityLinks != 0)
        {
            throw new AssertionError(
                "Known Flyway contract sentinel may be restored only before Foundation identity provisioning");
        }
        if (jdbc.update("delete from sys_role where role_key='foundation_product_owner' "
            + "and role_name='collision' and create_by='flyway-v0.20.28'") != 1)
        {
            throw new AssertionError("Known Flyway contract sentinel was not deleted exactly once");
        }
        if (jdbc.update("delete from flyway_schema_history where version='0.20.28' and success=1") != 1)
        {
            throw new AssertionError("Known Flyway contract migration history was not deleted exactly once");
        }
        Flyway.configure()
            .dataSource(url, MigrationTestDatabase.user(), MigrationTestDatabase.password())
            .baselineOnMigrate(true)
            .baselineVersion("0.15.0")
            .locations("classpath:db/migration")
            .outOfOrder(true)
            .load()
            .migrate();
    }

    static Map<String, String> rowMap(ResultSet rows) throws SQLException
    {
        ResultSetMetaData metadata = rows.getMetaData();
        Map<String, String> values = new LinkedHashMap<>();
        for (int column = 1; column <= metadata.getColumnCount(); column++)
        {
            String value = rows.getString(column);
            values.put(metadata.getColumnLabel(column).toLowerCase(), value == null ? "<SQL-NULL>" : value);
        }
        return values;
    }

    record DatabaseSnapshot(Map<String, List<Map<String, String>>> tables) {}

    record UserIdentitySnapshot(long userId, String userName, Map<String, String> row, List<Long> roleIds,
        List<Long> postIds) {}

    static final class IdentityCleanup implements AutoCloseable
    {
        private final FoundationTestIdentityTestSupport database;
        private final long userAutoIncrement;
        private final long departmentAutoIncrement;
        private boolean closed;

        private IdentityCleanup(FoundationTestIdentityTestSupport database, long userAutoIncrement,
            long departmentAutoIncrement)
        {
            this.database = database;
            this.userAutoIncrement = userAutoIncrement;
            this.departmentAutoIncrement = departmentAutoIncrement;
        }

        @Override
        public void close()
        {
            if (!closed)
            {
                database.cleanFoundationIdentities(userAutoIncrement, departmentAutoIncrement);
                closed = true;
            }
        }
    }
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

@SpringBootConfiguration(proxyBeanMethods = false)
@Import(FoundationTestIdentityConfiguration.class)
class FoundationIdentityProfileGuardTestApplication
{
}
