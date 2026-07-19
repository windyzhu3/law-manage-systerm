package com.ruoyi.web.migration;

import static com.ruoyi.system.foundation.FoundationTestIdentityErrorCode.FOUNDATION_TEST_IDENTITIES_DEPARTMENT_CONFLICT;
import static com.ruoyi.system.foundation.FoundationTestIdentityErrorCode.FOUNDATION_TEST_IDENTITIES_ROLE_MISMATCH;
import static com.ruoyi.system.foundation.FoundationTestIdentityErrorCode.FOUNDATION_TEST_IDENTITIES_ROLE_MISSING;
import static com.ruoyi.system.foundation.FoundationTestIdentityErrorCode.FOUNDATION_TEST_IDENTITIES_USER_CONFLICT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.system.foundation.FoundationTestIdentityCatalog;
import com.ruoyi.system.foundation.FoundationTestIdentityErrorCode;
import com.ruoyi.system.foundation.FoundationTestIdentityException;
import com.ruoyi.system.foundation.FoundationTestIdentityProvisioningService;
import com.ruoyi.system.mapper.FoundationTestIdentityMapper;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FoundationTestIdentityRollbackTest
{
    private FoundationIdentityDatabaseSupport database;
    private ConfigurableApplicationContext context;
    private JdbcTemplate jdbc;
    private FoundationTestIdentityMapper mapper;
    private FoundationTestIdentityProvisioningService service;
    private TransactionTemplate transaction;

    @BeforeAll
    void startRealMyBatisContext()
    {
        database = FoundationIdentityDatabaseSupport.migrate();
        // The integration gate models a single startup invocation, not concurrent application starts.
        context = database.startContext("test");
        jdbc = context.getBean(JdbcTemplate.class);
        mapper = context.getBean(FoundationTestIdentityMapper.class);
        service = context.getBean(FoundationTestIdentityProvisioningService.class);
        transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    }

    @AfterAll
    void closeContext()
    {
        if (context != null)
        {
            context.close();
        }
    }

    @Test
    void missingRequiredRoleUsesStableCodeAndRollsBackTheWholeScenario()
    {
        assertScenarioRollsBack(FOUNDATION_TEST_IDENTITIES_ROLE_MISSING, arranged -> {
            assertEquals(1, arranged.update("delete ur from sys_user_role ur join sys_role r "
                + "on r.role_id=ur.role_id where r.role_key='sales' and ur.user_id=(select user_id from sys_user "
                + "where user_name='ft_sales' and user_type='99' and del_flag='0')"));
            arranged.update("delete rm from sys_role_menu rm join sys_role r on r.role_id=rm.role_id "
                + "where r.role_key='sales'");
            assertEquals(1, arranged.update("delete from sys_role where role_key='sales'"));
        });
    }

    @Test
    void governancePermissionDriftUsesStableCodeAndRollsBackTheWholeScenario()
    {
        assertScenarioRollsBack(FOUNDATION_TEST_IDENTITIES_ROLE_MISMATCH, arranged -> {
            Long extraMenuId = arranged.queryForObject(
                "select m.menu_id from sys_menu m where m.menu_type='F' and m.perms is not null and m.perms<>'' "
                    + "and not exists (select 1 from sys_role_menu rm join sys_role r on r.role_id=rm.role_id "
                    + "where r.role_key='foundation_product_owner' and rm.menu_id=m.menu_id) "
                    + "order by m.menu_id limit 1", Long.class);
            assertNotNull(extraMenuId);
            assertEquals(1, arranged.update("insert into sys_role_menu(role_id,menu_id) "
                + "select role_id,? from sys_role where role_key='foundation_product_owner'", extraMenuId));
        });
    }

    @Test
    void anyRealRowAmongAllMatchingUsernamesUsesStableCodeAndRollsBackTheWholeScenario()
    {
        assertScenarioRollsBack(FOUNDATION_TEST_IDENTITIES_USER_CONFLICT, arranged -> assertEquals(1,
            arranged.update("insert into sys_user(dept_id,user_name,nick_name,user_type,password,status,del_flag,"
                + "create_by,create_time,remark) select dept_id,user_name,'real collision','00','unchanged-real-hash',"
                + "'0','0','admin',sysdate(),'real user, never a Foundation identity' from sys_user "
                + "where user_name='ft_sales' and user_type='99' and del_flag='0'")));
    }

    @Test
    void duplicateDepartmentNameReturnsSentinelAndRollsBackTheWholeScenario()
    {
        FoundationTestIdentityCatalog.DepartmentSpec target = FoundationTestIdentityCatalog.departments().stream()
            .filter(spec -> "FOUNDATION_TEST_SALES".equals(spec.code())).findFirst().orElseThrow();
        assertScenarioRollsBack(FOUNDATION_TEST_IDENTITIES_DEPARTMENT_CONFLICT, arranged -> {
            assertEquals(1, arranged.update("insert into sys_dept(parent_id,ancestors,dept_name,dept_code,order_num,"
                + "status,del_flag,create_by,create_time) values (0,'0',?,'REAL_FOUNDATION_SENTINEL',999,"
                + "'0','0','admin',sysdate())", target.name()));
            SysDept sentinel = mapper.selectDepartmentByName(target.name());
            assertNotNull(sentinel, "Duplicate-name aggregate must return the conflict sentinel");
            assertNull(sentinel.getDeptId(), "Duplicate-name sentinel must not masquerade as a real department");
        });
    }

    private void assertScenarioRollsBack(FoundationTestIdentityErrorCode expectedCode,
        Consumer<JdbcTemplate> arrange)
    {
        FoundationIdentityDatabaseSupport.DatabaseSnapshot before = database.snapshot(jdbc);

        FoundationTestIdentityException failure = assertThrows(FoundationTestIdentityException.class,
            () -> transaction.executeWithoutResult(status -> {
                arrange.accept(jdbc);
                service.provision(database.password());
            }));

        assertEquals(expectedCode, failure.getCode());
        assertEquals(before, database.snapshot(jdbc),
            () -> "Scenario must roll back every database change for " + expectedCode);
    }
}
