package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

class FlywayRuntimeMigrationTest
{
    @Test
    void appliesMigrationsWithoutContractTestFixtures() throws Exception
    {
        String url = MigrationTestDatabase.migrate();

        try (Connection connection = DriverManager.getConnection(url,
            MigrationTestDatabase.user(), MigrationTestDatabase.password()))
        {
            assertEquals(1L, count(connection,
                "select count(*) from sys_role where role_key='foundation_product_owner' "
                    + "and role_name='Foundation产品负责人' and role_sort=40 "
                    + "and status='0' and del_flag='0'"));
            assertEquals(4L, count(connection,
                "select count(*) from sys_role r join sys_role_menu rm on rm.role_id=r.role_id "
                    + "join sys_menu m on m.menu_id=rm.menu_id "
                    + "where r.role_key='foundation_product_owner' and m.perms in "
                    + "('todo:decision:view','todo:decision:edit','todo:admission:view','todo:admission:edit')"));
        }
    }

    private long count(Connection connection, String sql) throws Exception
    {
        try (PreparedStatement statement = connection.prepareStatement(sql);
            ResultSet rows = statement.executeQuery())
        {
            rows.next();
            return rows.getLong(1);
        }
    }
}
