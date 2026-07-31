package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.law.todo.application.TodoRoutingService;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.schedule.TodoScheduleService;
import com.law.todo.schedule.TodoScheduleService.CreateSchedulePlanCommand;
import com.law.todo.schedule.TodoScheduleService.SchedulePurpose;

class TodoSchedulePurposeMigrationTest
{
    private static final LocalDateTime NOW=LocalDateTime.of(2026,7,31,10,0);

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

    @Test
    void concurrentRepeatableReadCreationReturnsOnePlanAndOneWindowSet() throws Exception
    {
        try(ConcurrencyHarness harness=new ConcurrencyHarness("same"))
        {
            CreateSchedulePlanCommand command=progressCommand(991L,NOW,
                    "LEAD_PROGRESS_5D:991:7001");
            List<ConcurrentOutcome> outcomes=harness.createConcurrently(command,command);

            assertNull(outcomes.get(0).failure(),String.valueOf(outcomes.get(0).failure()));
            assertNull(outcomes.get(1).failure(),String.valueOf(outcomes.get(1).failure()));
            assertEquals(outcomes.get(0).planId(),outcomes.get(1).planId());
            assertEquals(1,harness.count("select count(*) from todo_schedule_plan "
                    +"where idempotency_key='LEAD_PROGRESS_5D:991:7001'"));
            assertEquals(1,harness.count("select count(*) from todo_schedule_window window_row "
                    +"join todo_schedule_plan plan on plan.plan_id=window_row.plan_id "
                    +"where plan.idempotency_key='LEAD_PROGRESS_5D:991:7001'"));
        }
    }

    @Test
    void concurrentRepeatableReadCreationRejectsMismatchedSameKey() throws Exception
    {
        try(ConcurrencyHarness harness=new ConcurrencyHarness("mismatch"))
        {
            String key="LEAD_PROGRESS_5D:991:7001";
            List<ConcurrentOutcome> outcomes=harness.createConcurrently(
                    progressCommand(991L,NOW,key),progressCommand(991L,NOW.plusHours(1),key));

            assertEquals(1,outcomes.stream().filter(outcome->outcome.planId()!=null).count());
            List<Throwable> failures=outcomes.stream().map(ConcurrentOutcome::failure)
                    .filter(java.util.Objects::nonNull).toList();
            assertEquals(1,failures.size());
            TodoException conflict=findTodoException(failures.get(0));
            assertEquals("TODO_SCHEDULE_IDEMPOTENCY_CONFLICT",conflict.getBusinessCode());
            assertEquals(1,harness.count("select count(*) from todo_schedule_plan "
                    +"where idempotency_key='"+key+"'"));
            assertEquals(1,harness.count("select count(*) from todo_schedule_window window_row "
                    +"join todo_schedule_plan plan on plan.plan_id=window_row.plan_id "
                    +"where plan.idempotency_key='"+key+"'"));
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

    private void createRuntimeFixtures(String url,String user,String password) throws Exception
    {
        try(Connection connection=DriverManager.getConnection(url,user,password);
            Statement statement=connection.createStatement())
        {
            statement.execute("""
                    create table todo_template(
                      template_id bigint not null primary key,template_code varchar(64) not null,
                      template_name varchar(128) not null,business_type varchar(32) not null,
                      current_version int not null,status char(1) not null
                    ) engine=innodb
                    """);
            statement.execute("""
                    create table todo_template_version(
                      version_id bigint not null primary key,template_id bigint not null,
                      version_no int not null,status varchar(20) not null
                    ) engine=innodb
                    """);
            statement.execute("""
                    create table todo_prd_definition_catalog(
                      template_code varchar(64) not null primary key,
                      definition_package_state varchar(32) null,foundation_state varchar(32) null,
                      production_state varchar(32) null,blockers_json json null
                    ) engine=innodb
                    """);
            statement.execute("""
                    create table todo_trigger_rule(
                      trigger_rule_id bigint not null primary key,template_version_id bigint not null,
                      event_type varchar(64) not null,enabled char(1) not null,condition_json json null
                    ) engine=innodb
                    """);
            statement.execute("""
                    create table todo_schedule_window(
                      window_id bigint not null auto_increment,plan_id bigint not null,
                      window_code varchar(32) not null,window_order int not null,day_offset int not null,
                      start_time time not null,end_time time not null,materialize_at datetime not null,
                      due_at datetime not null,max_attempts int not null,occurrence_no int not null,
                      status varchar(20) not null,create_time datetime not null,update_time datetime not null,
                      version int not null default 0,primary key(window_id),
                      unique key uk_todo_schedule_window(plan_id,window_code)
                    ) engine=innodb
                    """);
            statement.executeUpdate("insert into todo_template values(10,'TD-004','Progress','LEAD',1,'0')");
            statement.executeUpdate("insert into todo_template_version values(33,10,1,'PUBLISHED')");
        }
    }

    private CreateSchedulePlanCommand progressCommand(long businessId,LocalDateTime completedAt,
            String idempotencyKey)
    {
        return new CreateSchedulePlanCommand(7001L,33L,"LEAD",businessId,completedAt,
                "Asia/Shanghai",4L,11L,2,List.of(
                        new TodoScheduleService.ScheduleWindowRule(
                                "P5D",0,0,null,null,0,7200,1,1)),
                SchedulePurpose.LEAD_PROGRESS_5D,idempotencyKey);
    }

    private TodoException findTodoException(Throwable failure)
    {
        Throwable current=failure;
        while(current!=null&&!(current instanceof TodoException))current=current.getCause();
        if(current instanceof TodoException todo)return todo;
        throw new AssertionError("Expected TodoException but received "+failure,failure);
    }

    private Configuration myBatis(DataSource dataSource) throws Exception
    {
        Configuration configuration=new Configuration(new Environment("todo-schedule-purpose-it",
                new SpringManagedTransactionFactory(),dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        String mapperResource="mapper/todo/TodoMapper.xml";
        try(var input=Resources.getResourceAsStream(mapperResource))
        {
            new XMLMapperBuilder(input,configuration,mapperResource,
                    configuration.getSqlFragments()).parse();
        }
        return configuration;
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

    private record ConcurrentOutcome(Long planId,Throwable failure) { }

    private final class ConcurrencyHarness implements AutoCloseable
    {
        private final String adminUrl;
        private final String user;
        private final String password;
        private final String schema;
        private final String url;
        private final TodoMapper delegate;
        private final TransactionTemplate transaction;
        private final ExecutorService workers=Executors.newFixedThreadPool(2);

        private ConcurrencyHarness(String label) throws Exception
        {
            adminUrl=System.getenv("TODO_MIGRATION_DB_URL");
            assumeTrue(adminUrl!=null&&!adminUrl.isBlank(),
                    "Migration database is provided by the CI quality gate");
            user=required("TODO_MIGRATION_DB_USER");
            password=required("TODO_MIGRATION_DB_PASSWORD");
            schema="todo_schedule_"+label+"_"+UUID.randomUUID().toString().replace("-","");
            createSchema(adminUrl,user,password,schema);
            url=withSchema(adminUrl,schema);
            createPreMigrationState(url,user,password);
            Flyway.configure().dataSource(url,user,password).baselineOnMigrate(true)
                    .baselineVersion("0.20.74").locations("classpath:db/migration")
                    .target("0.20.75").load().migrate();
            createRuntimeFixtures(url,user,password);
            DataSource dataSource=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",url,user,password);
            SqlSessionFactory sessions=new SqlSessionFactoryBuilder().build(myBatis(dataSource));
            delegate=new SqlSessionTemplate(sessions).getMapper(TodoMapper.class);
            transaction=new TransactionTemplate(new DataSourceTransactionManager(dataSource));
            transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            transaction.setTimeout(15);
        }

        private List<ConcurrentOutcome> createConcurrently(CreateSchedulePlanCommand first,
                CreateSchedulePlanCommand second) throws Exception
        {
            CyclicBarrier absentReads=new CyclicBarrier(2);
            AtomicInteger initialReads=new AtomicInteger();
            TodoMapper fenced=(TodoMapper)Proxy.newProxyInstance(TodoMapper.class.getClassLoader(),
                    new Class<?>[]{TodoMapper.class},(proxy,method,args)->{
                        try
                        {
                            Object result=method.invoke(delegate,args);
                            if("selectSchedulePlanByIdempotencyKey".equals(method.getName())
                                    &&(result==null||result instanceof Map<?,?> map&&map.isEmpty())
                                    &&initialReads.incrementAndGet()<=2)
                                absentReads.await(10,TimeUnit.SECONDS);
                            return result;
                        }
                        catch(InvocationTargetException wrapped)
                        {
                            throw wrapped.getCause();
                        }
                    });
            TodoScheduleService service=new TodoScheduleService(fenced,new TodoRoutingService(fenced));
            Future<ConcurrentOutcome> firstResult=workers.submit(()->create(service,first));
            Future<ConcurrentOutcome> secondResult=workers.submit(()->create(service,second));
            return List.of(firstResult.get(20,TimeUnit.SECONDS),
                    secondResult.get(20,TimeUnit.SECONDS));
        }

        private ConcurrentOutcome create(TodoScheduleService service,CreateSchedulePlanCommand command)
        {
            try
            {
                return new ConcurrentOutcome(transaction.execute(
                        ignored->service.createPlan(command)),null);
            }
            catch(Throwable failure)
            {
                return new ConcurrentOutcome(null,failure);
            }
        }

        private int count(String sql) throws Exception
        {
            try(Connection connection=DriverManager.getConnection(url,user,password);
                Statement statement=connection.createStatement())
            {
                return TodoSchedulePurposeMigrationTest.this.count(statement,sql);
            }
        }

        @Override
        public void close() throws Exception
        {
            workers.shutdownNow();
            workers.awaitTermination(10,TimeUnit.SECONDS);
            dropSchema(adminUrl,user,password,schema);
        }
    }
}
