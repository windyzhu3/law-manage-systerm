package com.ruoyi.web.migration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.sql.DataSource;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;

import com.law.todo.mapper.TodoMapper;

class TodoScheduleLockOrderExternalMysqlIT
{
    private static final LocalDateTime NOW=LocalDateTime.of(2026,7,25,11,0);

    @Test
    void materializerAndConnectedCompletionSerializeOnPlanBeforeOccurrence() throws Exception
    {
        String adminUrl=requiredEnvironment("TODO_MIGRATION_DB_URL");
        String user=requiredEnvironment("TODO_MIGRATION_DB_USER");
        String password=requiredEnvironment("TODO_MIGRATION_DB_PASSWORD");
        String schema="todo_schedule_lock_it_"+UUID.randomUUID().toString().replace("-","");
        createSchema(adminUrl,user,password,schema);
        ExecutorService workers=Executors.newFixedThreadPool(2);
        CountDownLatch materializerOwnsPlan=new CountDownLatch(1);
        CountDownLatch connectedWillLockPlan=new CountDownLatch(1);
        CountDownLatch releaseMaterializer=new CountDownLatch(1);
        try
        {
            String schemaUrl=withSchema(adminUrl,schema);
            DataSource dataSource=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",schemaUrl,user,password);
            createTablesAndFixtures(dataSource);
            SqlSessionFactory sessions=new SqlSessionFactoryBuilder().build(myBatis(dataSource));

            Future<String> materializer=workers.submit(()->{
                try(SqlSession session=sessions.openSession(false))
                {
                    session.getConnection().createStatement()
                            .execute("set session innodb_lock_wait_timeout=10");
                    TodoMapper mapper=session.getMapper(TodoMapper.class);
                    Map<String,Object> identity=mapper.selectScheduleOccurrenceIdentityByKey("3:T1_AM:1");
                    assertEquals(3L,number(identity,"planId"));
                    assertEquals("ACTIVE",mapper.selectSchedulePlanForUpdate(3L).get("status"));
                    materializerOwnsPlan.countDown();
                    assertTrue(releaseMaterializer.await(10,SECONDS));
                    Map<String,Object> occurrence=
                            mapper.selectScheduleOccurrenceWindowForUpdate("3:T1_AM:1",3L);
                    assertEquals("CLAIMED",occurrence.get("status"));
                    assertEquals(1,mapper.linkScheduleOccurrenceByKey(
                            "3:T1_AM:1",55L,0,NOW));
                    session.commit();
                    return "MATERIALIZED";
                }
            });

            assertTrue(materializerOwnsPlan.await(10,SECONDS));
            Future<String> connected=workers.submit(()->{
                try(SqlSession session=sessions.openSession(false))
                {
                    session.getConnection().createStatement()
                            .execute("set session innodb_lock_wait_timeout=10");
                    TodoMapper mapper=session.getMapper(TodoMapper.class);
                    assertEquals("CLAIMED",mapper.selectScheduleOccurrenceById(9L).get("status"));
                    connectedWillLockPlan.countDown();
                    assertEquals("ACTIVE",mapper.selectSchedulePlanForUpdate(3L).get("status"));
                    assertEquals(1,mapper.recordScheduleOccurrenceResult(9L,"CONNECTED",NOW));
                    assertEquals(1,mapper.completeSchedulePlan(3L,"CONTACTED",NOW));
                    session.commit();
                    return "CONTACTED";
                }
            });

            assertTrue(connectedWillLockPlan.await(10,SECONDS));
            assertTrue(awaitPlanLockWait(adminUrl,user,password,schema),
                    "CONNECTED connection must wait on the materializer-owned plan row");
            assertFalse(connected.isDone(),"CONNECTED must remain serialized behind the plan lock");
            releaseMaterializer.countDown();

            assertEquals("MATERIALIZED",materializer.get(10,SECONDS));
            assertEquals("CONTACTED",connected.get(10,SECONDS));
            assertFinalSerializedState(dataSource);
        }
        finally
        {
            releaseMaterializer.countDown();
            workers.shutdownNow();
            workers.awaitTermination(10,SECONDS);
            dropSchema(adminUrl,user,password,schema);
        }
    }

    private static Configuration myBatis(DataSource dataSource) throws Exception
    {
        Configuration configuration=new Configuration(new Environment("todo-schedule-lock-order-it",
                new JdbcTransactionFactory(),dataSource));
        String mapperResource="mapper/todo/TodoMapper.xml";
        try(InputStream input=Resources.getResourceAsStream(mapperResource))
        {
            new XMLMapperBuilder(input,configuration,mapperResource,
                    configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    private static void createTablesAndFixtures(DataSource dataSource) throws Exception
    {
        try(Connection connection=dataSource.getConnection();Statement statement=connection.createStatement())
        {
            statement.execute("""
                    create table todo_schedule_plan(
                      plan_id bigint not null primary key,
                      template_version_id bigint not null,
                      status varchar(20) not null,
                      completion_reason varchar(64) null,
                      completed_at datetime null,
                      update_time datetime not null,
                      version int not null
                    ) engine=innodb
                    """);
            statement.execute("""
                    create table todo_schedule_window(
                      window_id bigint not null primary key,
                      plan_id bigint not null,
                      status varchar(20) not null
                    ) engine=innodb
                    """);
            statement.execute("""
                    create table todo_schedule_occurrence(
                      occurrence_id bigint not null primary key,
                      plan_id bigint not null,
                      window_id bigint not null,
                      window_code varchar(32) not null,
                      occurrence_no int not null,
                      occurrence_key varchar(192) not null,
                      due_at datetime not null,
                      todo_id bigint null,
                      status varchar(20) not null,
                      result_code varchar(64) null,
                      completed_at datetime null,
                      error_code varchar(128) null,
                      error_message varchar(1000) null,
                      update_time datetime not null,
                      version int not null,
                      unique key uk_occurrence_key(occurrence_key)
                    ) engine=innodb
                    """);
            statement.executeUpdate("""
                    insert into todo_schedule_plan(
                      plan_id,template_version_id,status,update_time,version
                    ) values(3,22,'ACTIVE',now(),0)
                    """);
            statement.executeUpdate("""
                    insert into todo_schedule_window(window_id,plan_id,status)
                    values(12,3,'PROCESSING')
                    """);
            statement.executeUpdate("""
                    insert into todo_schedule_occurrence(
                      occurrence_id,plan_id,window_id,window_code,occurrence_no,
                      occurrence_key,due_at,status,update_time,version
                    ) values(9,3,12,'T1_AM',1,'3:T1_AM:1',now(),'CLAIMED',now(),0)
                    """);
        }
    }

    private static boolean awaitPlanLockWait(String url,String user,String password,String schema)
            throws Exception
    {
        long deadline=System.nanoTime()+SECONDS.toNanos(10);
        String sql="""
                select count(*)
                from performance_schema.data_lock_waits waits
                join performance_schema.data_locks requested
                  on requested.engine_lock_id=waits.requesting_engine_lock_id
                where requested.object_schema=? and requested.object_name='todo_schedule_plan'
                """;
        while(System.nanoTime()<deadline)
        {
            try(Connection connection=DriverManager.getConnection(url,user,password);
                PreparedStatement statement=connection.prepareStatement(sql))
            {
                statement.setString(1,schema);
                try(ResultSet rows=statement.executeQuery())
                {
                    if(rows.next()&&rows.getInt(1)>0)return true;
                }
            }
            Thread.sleep(25);
        }
        return false;
    }

    private static void assertFinalSerializedState(DataSource dataSource) throws Exception
    {
        try(Connection connection=dataSource.getConnection();
            Statement statement=connection.createStatement();
            ResultSet rows=statement.executeQuery("""
                    select p.status plan_status,o.status occurrence_status,
                           o.result_code,o.todo_id
                    from todo_schedule_plan p
                    join todo_schedule_occurrence o on o.plan_id=p.plan_id
                    where p.plan_id=3 and o.occurrence_id=9
                    """))
        {
            assertTrue(rows.next());
            assertEquals("CONTACTED",rows.getString("plan_status"));
            assertEquals("COMPLETED",rows.getString("occurrence_status"));
            assertEquals("CONNECTED",rows.getString("result_code"));
            assertEquals(55L,rows.getLong("todo_id"));
        }
    }

    private static long number(Map<String,Object> row,String key)
    {
        return ((Number)row.get(key)).longValue();
    }

    private static void createSchema(String url,String user,String password,String schema) throws Exception
    {
        try(Connection connection=DriverManager.getConnection(url,user,password);
            Statement statement=connection.createStatement())
        {
            statement.execute("create database `"+schema
                    +"` character set utf8mb4 collate utf8mb4_unicode_ci");
        }
    }

    private static void dropSchema(String url,String user,String password,String schema) throws Exception
    {
        try(Connection connection=DriverManager.getConnection(url,user,password);
            Statement statement=connection.createStatement())
        {
            statement.execute("drop database if exists `"+schema+"`");
        }
    }

    private static String withSchema(String url,String schema)
    {
        int query=url.indexOf('?');
        String base=query<0?url:url.substring(0,query);
        String parameters=query<0?"":url.substring(query);
        int slash=base.lastIndexOf('/');
        if(slash<"jdbc:mysql://".length())
            throw new IllegalArgumentException("TODO_MIGRATION_DB_URL must include a database name");
        return base.substring(0,slash+1)+schema+parameters;
    }

    private static String requiredEnvironment(String name)
    {
        String value=System.getenv(name);
        if(value==null||value.isBlank())
            throw new IllegalStateException(name+" is required for the external MySQL integration test");
        return value;
    }
}
