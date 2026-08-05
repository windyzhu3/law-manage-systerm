package com.ruoyi.web.migration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Map;
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
import com.law.todo.application.TodoRoutingService;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.schedule.TodoScheduleService;

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
        CountDownLatch releaseMaterializer=new CountDownLatch(1);
        try
        {
            String schemaUrl=withSchema(adminUrl,schema);
            DataSource dataSource=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",schemaUrl,user,password);
            createTablesAndFixtures(dataSource);
            SqlSessionFactory sessions=new SqlSessionFactoryBuilder().build(myBatis(dataSource));

            Future<Long> materializer=workers.submit(()->{
                try(SqlSession session=sessions.openSession(false))
                {
                    session.getConnection().createStatement()
                            .execute("set session innodb_lock_wait_timeout=10");
                    TodoMapper mapper=planFence(session.getMapper(TodoMapper.class),
                            materializerOwnsPlan,releaseMaterializer);
                    TodoInstance previous=new TodoInstance();
                    previous.setTodoId(44L);
                    previous.setBusinessType("LEAD");
                    previous.setBusinessId(7L);
                    TodoInstance result=new TodoRoutingService(mapper).createScheduledNext(
                            previous,22L,"3:T1_AM:1",NOW);
                    session.commit();
                    return result.getTodoId();
                }
            });

            assertTrue(materializerOwnsPlan.await(10,SECONDS));
            Future<String> connected=workers.submit(()->{
                try(SqlSession session=sessions.openSession(false))
                {
                    session.getConnection().createStatement()
                            .execute("set session innodb_lock_wait_timeout=10");
                    TodoMapper mapper=session.getMapper(TodoMapper.class);
                    TodoScheduleService.ScheduleCompletion result=
                            new TodoScheduleService(mapper,new TodoRoutingService(mapper))
                                    .completeOccurrence(9L,"CONNECTED",NOW);
                    session.commit();
                    return result.windowCode();
                }
            });

            assertTrue(awaitPlanLockWait(adminUrl,user,password,schema),
                    "CONNECTED connection must wait on the materializer-owned plan row");
            assertFalse(connected.isDone(),"CONNECTED must remain serialized behind the plan lock");
            releaseMaterializer.countDown();

            assertEquals(55L,materializer.get(10,SECONDS));
            assertEquals("T1_AM",connected.get(10,SECONDS));
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

    private static TodoMapper planFence(TodoMapper delegate,CountDownLatch ownsPlan,
            CountDownLatch release)
    {
        return (TodoMapper)Proxy.newProxyInstance(TodoMapper.class.getClassLoader(),
                new Class<?>[]{TodoMapper.class},(proxy,method,args)->{
                    try
                    {
                        if("selectTemplateVersionById".equals(method.getName()))
                            return Map.of("status","PUBLISHED","business_type","LEAD",
                                    "definition_json","""
                                      {"schemaVersion":1,"templateCode":"TD-003",
                                       "routing":{"config":{"start":"td003","nodes":[
                                         {"key":"td003","type":"TASK","templateCode":"TD-003","templateVersionId":22}
                                       ]}},"autoActions":[],"decisionRefs":[],"acceptanceRefs":[]}
                                      """);
                        Object result=method.invoke(delegate,args);
                        if("selectSchedulePlanForUpdate".equals(method.getName()))
                        {
                            ownsPlan.countDown();
                            assertTrue(release.await(10,SECONDS));
                        }
                        return result;
                    }
                    catch(InvocationTargetException wrapped)
                    {
                        throw wrapped.getCause();
                    }
                });
    }

    private static Configuration myBatis(DataSource dataSource) throws Exception
    {
        Configuration configuration=new Configuration(new Environment("todo-schedule-lock-order-it",
                new JdbcTransactionFactory(),dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
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
                      previous_todo_id bigint null,
                      template_version_id bigint not null,
                      business_type varchar(32) not null,
                      business_id bigint not null,
                      schedule_purpose varchar(32) not null,
                      idempotency_key varchar(192) not null,
                      timezone varchar(64) not null,
                      rule_version_id bigint not null,
                      assignment_policy_id bigint null,
                      assignment_policy_version int null,
                      assignment_policy_snapshot_source varchar(32) not null,
                      first_contact_at datetime null,
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
                      window_code varchar(32) not null,
                      window_order int not null,
                      materialize_at datetime not null,
                      due_at datetime not null,
                      max_attempts int not null,
                      status varchar(20) not null
                      ,cancel_reason varchar(128) null
                      ,completed_at datetime null
                      ,claimed_at datetime null
                      ,update_time datetime not null
                      ,version int not null
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
                      claimed_at datetime null,
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
            statement.execute("""
                    create table todo_instance(
                      todo_id bigint not null primary key,
                      next_idempotency_key varchar(255) null,
                      status varchar(20) not null,
                      root_todo_id bigint null,
                      route_node_key varchar(128) null,
                      route_token json null,
                      occurrence_key varchar(192) null,
                      template_version_id bigint null,
                      business_type varchar(32) null,
                      business_id bigint null
                    ) engine=innodb
                    """);
            statement.executeUpdate("""
                    insert into todo_schedule_plan(
                      plan_id,previous_todo_id,template_version_id,business_type,business_id,
                      schedule_purpose,idempotency_key,timezone,rule_version_id,assignment_policy_id,assignment_policy_version,
                      assignment_policy_snapshot_source,status,update_time,version
                    ) values(3,44,22,'LEAD',7,'LEAD_RETRY','LOCK-ORDER-3','Asia/Shanghai',99,101,4,
                      'RESOLVED_POLICY','ACTIVE',now(),0)
                    """);
            statement.executeUpdate("""
                    insert into todo_schedule_window(window_id,plan_id,window_code,window_order,
                      materialize_at,due_at,max_attempts,status,update_time,version)
                    values(12,3,'T1_AM',1,now(),now(),3,'MATERIALIZED',now(),0)
                    """);
            statement.executeUpdate("""
                    insert into todo_schedule_occurrence(
                      occurrence_id,plan_id,window_id,window_code,occurrence_no,
                      occurrence_key,due_at,todo_id,status,update_time,version
                    ) values(9,3,12,'T1_AM',1,'3:T1_AM:1',now(),55,'MATERIALIZED',now(),0)
                    """);
            statement.executeUpdate("""
                    insert into todo_instance(todo_id,next_idempotency_key,status,root_todo_id,
                      route_node_key,route_token,occurrence_key,template_version_id,
                      business_type,business_id)
                    values(55,'SCHEDULE:3:T1_AM:1','CREATED',55,'td003',
                      json_object('rootTodoId',55,'nodeKey','td003','branchKey',null,
                        'occurrence',0,'status','ACTIVE'),'3:T1_AM:1',22,'LEAD',7)
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
