package com.ruoyi.web.e2e;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Grants the otherwise inert diagnostic permission only inside a deliberately
 * enabled e2e/test process. Production profiles never register this bean.
 */
@Component
@Profile({"e2e","test"})
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(prefix="foundation.e2e-identity",name="enabled",havingValue="true")
public class E2eBackendIdentityPermissionSeeder implements ApplicationRunner
{
    private final JdbcTemplate jdbc;

    public E2eBackendIdentityPermissionSeeder(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @Override public void run(ApplicationArguments arguments)
    {
        jdbc.update("""
            insert into sys_role_menu(role_id,menu_id)
            select role.role_id,permission.menu_id
            from sys_role role
            join sys_menu permission on permission.perms='foundation:e2e:identity'
            where role.role_key='sales' and role.status='0' and role.del_flag='0'
              and not exists(
                select 1 from sys_role_menu existing
                where existing.role_id=role.role_id and existing.menu_id=permission.menu_id)
            """);
    }
}
