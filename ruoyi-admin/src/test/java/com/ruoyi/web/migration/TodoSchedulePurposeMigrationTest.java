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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class TodoSchedulePurposeMigrationTest
{
    @Test
    void migratesHistoricalPlansToDeterministicUniquePurposeKeysWithoutChangingTheirFacts()
            throws Exception
    {
        String adminUrl=System.getenv("TODO_MIGRATION_DB_URL");
        assumeTrue(adminUrl!=null&&!adminUrl.isBlank(),
                "Migration database is provided by the CI quality gate");
        String user=required("TODO_MIGRATION_DB_USER");
        String password=required("TODO_MIGRATION_DB_PASSWORD");
        String schema="todo_schedule_purpose_"+UUID.randomUUID().toString().replace("-","");
        createSchema(adminUrl,user,password,schema);
        try
        {
            String url=withSchema(adminUrl,schema);
            createPreMigrationState(url,user,password);
            Map<Long,String> immutableBefore=immutableFacts(url,user,password);

            Flyway flyway=Flyway.configure().dataSource(url,user,password)
                    .baselineOnMigrate(true).baselineVersion("0.20.74")
                    .locations("classpath:db/migration").target("0.20.75").load();
            assertTrue(flyway.migrate().success);
            assertEquals("0.20.75",flyway.info().current().getVersion().getVersion());

            assertEquals(immutableBefore,immutableFacts(url,user,password));
            assertMigratedState(url,user,password);
            assertEquals(0,flyway.migrate().migrationsExecuted,
                    "A completed forward migration must be stable on a second Flyway run");
        }
        finally
        {
            dropSchema(adminUrl,user,password,schema);
        }
    }

    private void assertMigratedState(String url,String user,String password) throws Exception
    {
        try(Connection connection=DriverManager.getConnection(url,user,password);
            Statement statement=connection.createStatement())
        {
            assertEquals(4,count(statement,"select count(*) from todo_schedule_plan"));
            assertEquals(4,count(statement,"select count(distinct idempotency_key) from todo_schedule_plan"));
            assertEquals(0,count(statement,"select count(*) from todo_schedule_plan "
                    +"where schedule_purpose is null or idempotency_key is null"));
            assertEquals(4,count(statement,"select count(*) from todo_schedule_plan "
                    +"where schedule_purpose='LEAD_RETRY'"));
            assertEquals("LEAD_RETRY:91:7001",scalar(statement,
                    "select idempotency_key from todo_schedule_plan where plan_id=1"));
            assertEquals("LEAD_RETRY:91:7001:LEGACY_PLAN_2",scalar(statement,
                    "select idempotency_key from todo_schedule_plan where plan_id=2"));
            assertEquals("LEAD_RETRY:91:LEGACY_PLAN_3",scalar(statement,
                    "select idempotency_key from todo_schedule_plan where plan_id=3"));
            assertEquals("LEAD_RETRY:92:7002",scalar(statement,
                    "select idempotency_key from todo_schedule_plan where plan_id=4"));
            assertEquals(2,count(statement,"select count(distinct index_name) from information_schema.statistics "
                    +"where table_schema=database() and table_name='todo_schedule_plan' "
                    +"and index_name in ('uk_todo_schedule_plan_idempotency',"
                    +"'idx_todo_schedule_plan_purpose')"));
            assertEquals(0,count(statement,"select count(*) from information_schema.columns "
                    +"where table_schema=database() and table_name='todo_schedule_plan' "
                    +"and column_name in ('schedule_purpose','idempotency_key') and is_nullable='YES'"));
            assertThrows(SQLException.class,()->statement.executeUpdate(
                    "update todo_schedule_plan set idempotency_key='LEAD_RETRY:91:7001' where plan_id=4"));
        }
    }

    private void createPreMigrationState(String url,String user,String password) throws Exception
    {
        try(Connection connection=DriverManager.getConnection(url,user,password);
            Statement statement=connection.createStatement())
        {
            statement.execute("""
                    create table todo_schedule_plan(
                      plan_id bigint not null auto_increment,
                      previous_todo_id bigint null,
                      template_version_id bigint not null,
                      business_type varchar(64) not null,
                      business_id bigint not null,
                      timezone varchar(64) not null default 'Asia/Shanghai',
                      rule_version_id bigint null,
                      assignment_policy_id bigint null,
                      assignment_policy_version int null,
                      assignment_policy_snapshot_source varchar(32) not null,
                      first_contact_at datetime not null,
                      current_window_code varchar(32) null,
                      status varchar(20) not null default 'ACTIVE',
                      completion_reason varchar(64) null,
                      completed_at datetime null,
                      create_time datetime not null,
                      update_time datetime not null,
                      version int not null default 0,
                      primary key(plan_id)
                    ) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci
                    """);
            statement.executeUpdate("""
                    insert into todo_schedule_plan(
                      plan_id,previous_todo_id,template_version_id,business_type,business_id,
                      timezone,rule_version_id,assignment_policy_id,assignment_policy_version,
                      assignment_policy_snapshot_source,first_contact_at,status,create_time,update_time,version)
                    values
                      (1,7001,33,'LEAD',91,'Asia/Shanghai',4,11,2,'RESOLVED_POLICY',
                       '2026-07-25 08:30:00','ACTIVE',now(),now(),0),
                      (2,7001,33,'LEAD',91,'Asia/Shanghai',4,11,2,'RESOLVED_POLICY',
                       '2026-07-25 08:30:00','COMPLETED',now(),now(),1),
                      (3,null,33,'LEAD',91,'Asia/Shanghai',4,null,null,'LEGACY_PRE_0_20_49',
                       '2026-07-25 08:30:00','EXHAUSTED',now(),now(),2),
                      (4,7002,33,'LEAD',92,'Asia/Shanghai',5,12,3,'RESOLVED_POLICY',
                       '2026-07-26 09:45:00','ACTIVE',now(),now(),0)
                    """);
        }
    }

    private Map<Long,String> immutableFacts(String url,String user,String password) throws Exception
    {
        Map<Long,String> facts=new LinkedHashMap<>();
        try(Connection connection=DriverManager.getConnection(url,user,password);
            Statement statement=connection.createStatement();ResultSet rows=statement.executeQuery("""
                    select plan_id,concat_ws('|',coalesce(cast(previous_todo_id as char),'<NULL>'),
                      template_version_id,business_type,business_id,timezone,
                      coalesce(cast(rule_version_id as char),'<NULL>'),
                      coalesce(cast(assignment_policy_id as char),'<NULL>'),
                      coalesce(cast(assignment_policy_version as char),'<NULL>'),
                      assignment_policy_snapshot_source,first_contact_at,status,version)
                    from todo_schedule_plan order by plan_id
                    """))
        {
            while(rows.next())facts.put(rows.getLong(1),rows.getString(2));
        }
        return facts;
    }

    private int count(Statement statement,String sql) throws Exception
    {
        return Integer.parseInt(scalar(statement,sql));
    }

    private String scalar(Statement statement,String sql) throws Exception
    {
        try(ResultSet rows=statement.executeQuery(sql))
        {
            if(!rows.next())throw new AssertionError("Query returned no row: "+sql);
            return rows.getString(1);
        }
    }

    private void createSchema(String url,String user,String password,String schema) throws Exception
    {
        try(Connection connection=DriverManager.getConnection(url,user,password);
            Statement statement=connection.createStatement())
        {
            statement.execute("create database `"+schema
                    +"` character set utf8mb4 collate utf8mb4_unicode_ci");
        }
    }

    private void dropSchema(String url,String user,String password,String schema) throws Exception
    {
        try(Connection connection=DriverManager.getConnection(url,user,password);
            Statement statement=connection.createStatement())
        {
            statement.execute("drop database if exists `"+schema+"`");
        }
    }

    private String withSchema(String url,String schema)
    {
        int query=url.indexOf('?');
        String base=query<0?url:url.substring(0,query);
        String parameters=query<0?"":url.substring(query);
        int slash=base.lastIndexOf('/');
        if(slash<"jdbc:mysql://".length())
            throw new IllegalArgumentException("TODO_MIGRATION_DB_URL must include a database name");
        return base.substring(0,slash+1)+schema+parameters;
    }

    private String required(String name)
    {
        String value=System.getenv(name);
        if(value==null||value.isBlank())throw new IllegalStateException(name+" is required");
        return value;
    }
}
