package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class SystemManagementEncodingExternalMysqlIT
{
    private static final Map<String,String[]> DISPLAY_COLUMNS=Map.ofEntries(
            Map.entry("sys_user",new String[]{"nick_name","remark"}),
            Map.entry("sys_role",new String[]{"role_name","remark"}),
            Map.entry("sys_dept",new String[]{"dept_name","leader"}),
            Map.entry("sys_post",new String[]{"post_name"}),
            Map.entry("sys_dict_type",new String[]{"dict_name","remark"}),
            Map.entry("sys_dict_data",new String[]{"dict_label","remark"}),
            Map.entry("sys_config",new String[]{"config_name","remark"}),
            Map.entry("sys_job",new String[]{"job_name"}),
            Map.entry("sys_notice",new String[]{"notice_title","remark"}),
            Map.entry("sys_menu",new String[]{"remark"}));

    @Test
    void latestMigrationRepairsSystemManagementDisplayTextWithoutChangingIdentifiers() throws Exception
    {
        String url=System.getenv("TODO_MIGRATION_DB_URL");
        assumeTrue(url!=null&&!url.isBlank(),"External MySQL is required");
        String user=System.getenv("TODO_MIGRATION_DB_USER");
        String password=System.getenv("TODO_MIGRATION_DB_PASSWORD");
        Flyway.configure().dataSource(url,user,password).baselineOnMigrate(true)
                .baselineVersion("0.15.0").locations("classpath:db/migration").load().migrate();

        try(Connection connection=DriverManager.getConnection(url,user,password))
        {
            for(Map.Entry<String,String[]> table:DISPLAY_COLUMNS.entrySet())
            {
                for(String column:table.getValue())
                {
                    assertEquals(0L,countReversibleMojibake(connection,table.getKey(),column),
                            table.getKey()+"."+column);
                }
            }
            assertEquals(1L,count(connection,
                    "select count(*) from sys_user where user_name='admin' and nick_name='若依'"));
            assertEquals(1L,count(connection,
                    "select count(*) from sys_role where role_key='admin' and role_name='超级管理员'"));
            assertEquals(1L,count(connection,
                    "select count(*) from sys_dept where dept_id=100 and dept_name='若依科技'"));
            assertEquals(1L,count(connection,
                    "select count(*) from sys_post where post_id=1 and post_name='董事长'"));
            assertEquals(1L,count(connection,
                    "select count(*) from sys_dict_data where dict_type='sys_user_sex'"
                            +" and dict_value='0' and dict_label='男'"));
        }
    }

    private long countReversibleMojibake(Connection connection,String table,String column) throws Exception
    {
        return count(connection,"select count(*) from "+table+" where "+column+" is not null"
                +" and "+column+"<>'' and "+column+" not regexp '[一-鿿]'"
                +" and convert(cast(convert("+column+" using latin1) as binary) using utf8mb4)"
                +" regexp '[一-鿿]'");
    }

    private long count(Connection connection,String sql) throws Exception
    {
        try(Statement statement=connection.createStatement();ResultSet rows=statement.executeQuery(sql))
        {rows.next();return rows.getLong(1);}
    }
}
