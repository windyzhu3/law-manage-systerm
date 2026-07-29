package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class NavigationMenuEncodingExternalMysqlIT
{
    @Test
    void latestMigrationRepairsReversibleMenuMojibakeWithoutChangingLatinLabels() throws Exception
    {
        String url=System.getenv("TODO_MIGRATION_DB_URL");
        assumeTrue(url!=null&&!url.isBlank(),"External MySQL is required");
        String user=System.getenv("TODO_MIGRATION_DB_USER");
        String password=System.getenv("TODO_MIGRATION_DB_PASSWORD");
        Flyway.configure().dataSource(url,user,password).baselineOnMigrate(true)
                .baselineVersion("0.15.0").locations("classpath:db/migration").load().migrate();

        try(Connection connection=DriverManager.getConnection(url,user,password))
        {
            assertEquals(0L,count(connection,"""
                    select count(*) from sys_menu
                    where menu_name<>''
                      and menu_name not regexp '[一-鿿]'
                      and convert(cast(convert(menu_name using latin1) as binary) using utf8mb4)
                          regexp '[一-鿿]'
                    """));
            assertEquals(1L,count(connection,
                    "select count(*) from sys_menu where menu_name='Todo Engine'"));
        }
    }

    private long count(Connection connection,String sql) throws Exception
    {
        try(Statement statement=connection.createStatement();ResultSet rows=statement.executeQuery(sql))
        {rows.next();return rows.getLong(1);}
    }
}
