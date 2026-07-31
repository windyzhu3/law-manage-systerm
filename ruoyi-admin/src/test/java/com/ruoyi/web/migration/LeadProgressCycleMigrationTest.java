package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class LeadProgressCycleMigrationTest
{
    @Test
    void migrationPreservesHistoricalFollowupsAndAddsNullableProvenanceWithSafeIndexes()
            throws Exception
    {
        String adminUrl=System.getenv("TODO_MIGRATION_DB_URL");
        assumeTrue(adminUrl!=null&&!adminUrl.isBlank(),
                "Migration database is provided by the CI quality gate");
        String user=required("TODO_MIGRATION_DB_USER");
        String password=required("TODO_MIGRATION_DB_PASSWORD");
        String schema="lead_progress_cycle_"+UUID.randomUUID().toString().replace("-","");
        createSchema(adminUrl,user,password,schema);
        try
        {
            String url=withSchema(adminUrl,schema);
            createPreMigrationState(url,user,password);
            String before=fingerprint(url,user,password);

            Flyway flyway=Flyway.configure().dataSource(url,user,password)
                    .baselineOnMigrate(true).baselineVersion("0.20.75")
                    .locations("classpath:db/migration").target("0.20.76").load();
            assertTrue(flyway.migrate().success);
            assertEquals("0.20.76",flyway.info().current().getVersion().getVersion());

            assertEquals(before,fingerprint(url,user,password));
            try(Connection connection=DriverManager.getConnection(url,user,password);
                Statement statement=connection.createStatement())
            {
                assertEquals(2,count(statement,"select count(*) from biz_lead_followup "
                        +"where progress_at is null and source_todo_id is null "
                        +"and schedule_plan_id is null and idempotency_key is null"));
                assertEquals(2,count(statement,"select count(distinct index_name) "
                        +"from information_schema.statistics where table_schema=database() "
                        +"and table_name='biz_lead_followup' and index_name in "
                        +"('uk_biz_lead_followup_idempotency','idx_biz_lead_followup_progress')"));
                statement.executeUpdate("insert into biz_lead_followup(lead_id,idempotency_key) "
                        +"values(3,'LEAD_PROGRESS:7001')");
                assertThrows(SQLException.class,()->statement.executeUpdate(
                        "insert into biz_lead_followup(lead_id,idempotency_key) "
                        +"values(4,'LEAD_PROGRESS:7001')"));
                statement.executeUpdate("insert into biz_lead_followup(lead_id) values(5),(6)");
            }
            assertEquals(0,flyway.migrate().migrationsExecuted);
        }
        finally
        {
            dropSchema(adminUrl,user,password,schema);
        }
    }

    private void createPreMigrationState(String url,String user,String password) throws Exception
    {
        try(Connection connection=DriverManager.getConnection(url,user,password);
            Statement statement=connection.createStatement())
        {
            statement.execute("""
                    create table biz_lead_followup(
                      followup_id bigint not null auto_increment,
                      lead_id bigint not null,
                      follow_type varchar(30) default 'phone',
                      follow_result varchar(60) default '',
                      content varchar(1000) default '',
                      next_follow_time datetime null,
                      follow_user_id bigint null,
                      task_status char(1) default '1',
                      create_by varchar(64) default '',create_time datetime null,
                      update_by varchar(64) default '',update_time datetime null,
                      remark varchar(500) null,
                      primary key(followup_id),key idx_lead_followup_lead(lead_id),
                      key idx_lead_followup_user(follow_user_id)
                    ) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci
                    """);
            statement.executeUpdate("""
                    insert into biz_lead_followup(
                      followup_id,lead_id,follow_type,follow_result,content,next_follow_time,
                      follow_user_id,task_status,create_by,create_time,update_by,update_time,remark)
                    values
                      (1,91,'phone','VALID','first','2026-07-31 10:00:00',8,'1','alice',
                       '2026-07-31 09:00:00','alice','2026-07-31 09:30:00','legacy-one'),
                      (2,92,'wechat','QUOTE','second',null,9,'0','bob',
                       '2026-07-30 09:00:00','',null,null)
                    """);
        }
    }

    private String fingerprint(String url,String user,String password) throws Exception
    {
        StringBuilder value=new StringBuilder();
        try(Connection connection=DriverManager.getConnection(url,user,password);
            Statement statement=connection.createStatement();ResultSet rows=statement.executeQuery("""
                    select followup_id,lead_id,follow_type,follow_result,content,
                      coalesce(cast(next_follow_time as char),'<NULL>'),
                      coalesce(cast(follow_user_id as char),'<NULL>'),task_status,create_by,
                      coalesce(cast(create_time as char),'<NULL>'),update_by,
                      coalesce(cast(update_time as char),'<NULL>'),coalesce(remark,'<NULL>')
                    from biz_lead_followup order by followup_id
                    """))
        {
            while(rows.next())for(int index=1;index<=13;index++)
                value.append(index==1?'\n':'|').append(rows.getString(index));
        }
        return value.toString();
    }

    private int count(Statement statement,String sql) throws Exception
    {
        try(ResultSet rows=statement.executeQuery(sql))
        {rows.next();return rows.getInt(1);}
    }

    private void createSchema(String url,String user,String password,String schema) throws Exception
    {
        try(Connection connection=DriverManager.getConnection(url,user,password);
            Statement statement=connection.createStatement())
        {statement.execute("create database `"+schema+"` character set utf8mb4 collate utf8mb4_unicode_ci");}
    }

    private void dropSchema(String url,String user,String password,String schema) throws Exception
    {
        try(Connection connection=DriverManager.getConnection(url,user,password);
            Statement statement=connection.createStatement())
        {statement.execute("drop database if exists `"+schema+"`");}
    }

    private String withSchema(String url,String schema)
    {
        int query=url.indexOf('?');String base=query<0?url:url.substring(0,query);
        String parameters=query<0?"":url.substring(query);int slash=base.lastIndexOf('/');
        return base.substring(0,slash+1)+schema+parameters;
    }

    private String required(String name)
    {
        String value=System.getenv(name);
        if(value==null||value.isBlank())throw new IllegalStateException(name+" is required");
        return value;
    }
}
