package com.ruoyi.web.migration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
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

import com.law.todo.application.LeadTodoReleaseService;
import com.law.todo.application.TodoTemplateService;
import com.law.todo.application.TodoTemplateService.EntrySlotBinding;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoConfigurationCommands.LeadReleaseCommand;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoConfigurationMapper;

/** Real InnoDB proof that a coordinated release validates a stable four-version snapshot. */
class LeadTodoReleaseVersionLockExternalMysqlIT
{
    private static final Actor ACTOR=new Actor(7L,"release-owner",2L);
    private static final LeadReleaseCommand COMMAND=new LeadReleaseCommand(
            "release-lock-it",88L,"hash-88",80L,89L,79L,3);

    @Test
    void concurrentStatusWriterWaitsUntilReleaseFinishesUsingTheLockedPublishedSnapshot() throws Exception
    {
        Database database=database();
        ExecutorService workers=Executors.newFixedThreadPool(2);
        CountDownLatch releaseReachedSwitch=new CountDownLatch(1);
        CountDownLatch allowReleaseToFinish=new CountDownLatch(1);
        try
        {
            DataSource dataSource=database.dataSource();
            createTablesAndFixtures(dataSource);
            SqlSessionFactory sessions=sessions(dataSource);
            TodoTemplateService templates=mock(TodoTemplateService.class);
            when(templates.switchEntrySlot("LEAD_FIRST_CONTACT_ENTRY",52L,3,ACTOR))
                    .thenAnswer(invocation->{
                        releaseReachedSwitch.countDown();
                        assertTrue(allowReleaseToFinish.await(10,SECONDS));
                        return new EntrySlotBinding("LEAD_FIRST_CONTACT_ENTRY",52L,88L,"TD-001");
                    });

            Future<LeadTodoReleaseService.LeadReleaseView> release=workers.submit(()->{
                try(SqlSession session=sessions.openSession(false))
                {
                    session.getConnection().createStatement().execute(
                            "set session innodb_lock_wait_timeout=10");
                    LeadTodoReleaseService service=new LeadTodoReleaseService(
                            session.getMapper(TodoConfigurationMapper.class),templates);
                    LeadTodoReleaseService.LeadReleaseView result=service.activate(COMMAND,ACTOR);
                    session.commit();
                    return result;
                }
            });

            assertTrue(releaseReachedSwitch.await(10,SECONDS));
            Future<Integer> writer=workers.submit(()->updateStatus(dataSource,80L,"RETIRED"));
            assertTrue(awaitVersionLockWait(database,"todo_template_version"));
            assertFalse(writer.isDone(),"status writer must remain behind the release version-row locks");
            allowReleaseToFinish.countDown();

            assertEquals(88L,release.get(10,SECONDS).activeTd001VersionId());
            assertEquals(1,writer.get(10,SECONDS));
            assertEquals("RETIRED",status(dataSource,80L));
            assertEquals("APPLIED",actionStatus(dataSource,COMMAND.actionId()));
        }
        finally
        {
            allowReleaseToFinish.countDown();
            workers.shutdownNow();
            workers.awaitTermination(10,SECONDS);
            database.close();
        }
    }

    @Test
    void statusWriterThatCommitsFirstMakesReleaseFailAndRollBackItsAction() throws Exception
    {
        Database database=database();
        ExecutorService workers=Executors.newFixedThreadPool(2);
        CountDownLatch writerOwnsVersion=new CountDownLatch(1);
        CountDownLatch allowWriterCommit=new CountDownLatch(1);
        try
        {
            DataSource dataSource=database.dataSource();
            createTablesAndFixtures(dataSource);
            SqlSessionFactory sessions=sessions(dataSource);
            TodoTemplateService templates=mock(TodoTemplateService.class);

            Future<Integer> writer=workers.submit(()->{
                try(Connection connection=dataSource.getConnection();
                        PreparedStatement statement=connection.prepareStatement(
                                "update todo_template_version set status='RETIRED' where version_id=80"))
                {
                    connection.setAutoCommit(false);
                    connection.createStatement().execute("set session innodb_lock_wait_timeout=10");
                    int changed=statement.executeUpdate();
                    writerOwnsVersion.countDown();
                    assertTrue(allowWriterCommit.await(10,SECONDS));
                    connection.commit();
                    return changed;
                }
            });
            assertTrue(writerOwnsVersion.await(10,SECONDS));

            Future<String> release=workers.submit(()->{
                try(SqlSession session=sessions.openSession(false))
                {
                    session.getConnection().createStatement().execute(
                            "set session innodb_lock_wait_timeout=10");
                    LeadTodoReleaseService service=new LeadTodoReleaseService(
                            session.getMapper(TodoConfigurationMapper.class),templates);
                    try
                    {
                        service.activate(COMMAND,ACTOR);
                        session.commit();
                        return "UNEXPECTED_SUCCESS";
                    }
                    catch(TodoException rejected)
                    {
                        session.rollback();
                        return rejected.getBusinessCode();
                    }
                }
            });

            assertTrue(awaitVersionLockWait(database,"todo_template_version"));
            assertFalse(release.isDone(),"release must wait for the earlier status writer");
            allowWriterCommit.countDown();

            assertEquals(1,writer.get(10,SECONDS));
            assertEquals("TODO_LEAD_RELEASE_VERSION_INVALID",release.get(10,SECONDS));
            verify(templates,never()).switchEntrySlot(any(),any(Long.class),any(Integer.class),any());
            assertEquals(0,countAction(dataSource,COMMAND.actionId()));
        }
        finally
        {
            allowWriterCommit.countDown();
            workers.shutdownNow();
            workers.awaitTermination(10,SECONDS);
            database.close();
        }
    }

    private static SqlSessionFactory sessions(DataSource dataSource) throws Exception
    {
        Configuration configuration=new Configuration(new Environment("lead-release-lock-it",
                new JdbcTransactionFactory(),dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        String resource="mapper/todo/TodoConfigurationMapper.xml";
        try(InputStream input=Resources.getResourceAsStream(resource))
        {
            new XMLMapperBuilder(input,configuration,resource,
                    configuration.getSqlFragments()).parse();
        }
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private static void createTablesAndFixtures(DataSource dataSource) throws Exception
    {
        try(Connection connection=dataSource.getConnection();Statement statement=connection.createStatement())
        {
            statement.execute("""
                    create table todo_template(
                      template_id bigint not null primary key,template_code varchar(64) not null,
                      template_name varchar(128) not null,business_type varchar(32) not null,
                      status char(1) not null
                    ) engine=innodb
                    """);
            statement.execute("""
                    create table todo_template_version(
                      version_id bigint not null primary key,version_no int not null,template_id bigint not null,
                      status varchar(20) not null,definition_hash varchar(64) null,compiled_json json null,
                      event_type varchar(64) null,payload_version int null
                    ) engine=innodb
                    """);
            statement.execute("""
                    create table todo_trigger_rule(
                      trigger_rule_id bigint not null primary key,version int not null,enabled char(1) not null,
                      entry_slot_code varchar(64) null,template_version_id bigint not null,
                      rule_code varchar(128) not null,event_type varchar(64) not null,
                      business_type varchar(32) not null,template_id bigint not null
                    ) engine=innodb
                    """);
            statement.execute("""
                    create table todo_definition_action(
                      action_id varchar(128) not null primary key,action_type varchar(64) not null,
                      action_status varchar(20) not null,request_fingerprint varchar(64) not null,
                      entity_type varchar(64) not null,entity_id bigint null,source_entity_id bigint null,
                      operator_id bigint null,operator_name varchar(64) null,operator_dept_id bigint null,
                      payload_json json null,create_time datetime not null default current_timestamp
                    ) engine=innodb
                    """);
            statement.execute("""
                    create table todo_configuration_resource_item(
                      resource_item_id bigint not null primary key,resource_type varchar(64) not null,
                      status varchar(20) not null,business_type varchar(32) not null,
                      resource_code varchar(128) not null,resource_name varchar(128) not null,
                      value_json json not null,sort_order int not null
                    ) engine=innodb
                    """);
            statement.execute("""
                    create table todo_simulation_evidence(
                      evidence_id bigint not null auto_increment primary key,template_id bigint not null,
                      version_id bigint not null,definition_hash varchar(64) not null,
                      scenario_code varchar(128) not null,scenario_version int not null,
                      result_status varchar(20) not null,expire_time datetime null
                    ) engine=innodb
                    """);

            statement.executeUpdate("""
                    insert into todo_template(template_id,template_code,template_name,business_type,status) values
                      (101,'TD-001','TD-001','LEAD','0'),(102,'TD-002','TD-002','LEAD','0'),
                      (103,'TD-003','TD-003','LEAD','0'),(104,'TD-004','TD-004','LEAD','0')
                    """);
            String compiled="""
                    {"routing":{"config":{"businessOutcomes":[
                      {"targetTemplateCode":"TD-002","targetVersionId":80},
                      {"targetTemplateCode":"TD-003","targetVersionId":89},
                      {"targetTemplateCode":"TD-004","targetVersionId":79}],"nodes":[
                      {"type":"TASK","templateCode":"TD-001","templateVersionId":88},
                      {"type":"TASK","templateCode":"TD-002","templateVersionId":80},
                      {"type":"TASK","templateCode":"TD-003","templateVersionId":89},
                      {"type":"TASK","templateCode":"TD-004","templateVersionId":79}]}}}
                    """;
            try(PreparedStatement insert=connection.prepareStatement("""
                    insert into todo_template_version(
                      version_id,version_no,template_id,status,definition_hash,compiled_json,event_type,payload_version)
                    values(?,?,?,?,?,cast(? as json),'LEAD_ASSIGNED',1)
                    """))
            {
                insertVersion(insert,88L,5,101L,"hash-88",compiled);
                insertVersion(insert,80L,4,102L,"hash-80",null);
                insertVersion(insert,89L,6,103L,"hash-89",null);
                insertVersion(insert,79L,3,104L,"hash-79",null);
            }
            statement.executeUpdate("""
                    insert into todo_trigger_rule(
                      trigger_rule_id,version,enabled,entry_slot_code,template_version_id,
                      rule_code,event_type,business_type,template_id)
                    values(52,3,'N','LEAD_FIRST_CONTACT_ENTRY',88,
                      'TRIGGER_LEAD_ASSIGNED_TD001_V5','LEAD_ASSIGNED','LEAD',101)
                    """);
            statement.executeUpdate("""
                    insert into todo_configuration_resource_item(
                      resource_item_id,resource_type,status,business_type,resource_code,resource_name,value_json,sort_order)
                    values
                      (1,'SIMULATION_SCENARIO','ACTIVE','LEAD','S1','S1',json_object('templateCode','TD-001','requiredForPublish',true,'scenarioVersion',1),1),
                      (2,'SIMULATION_SCENARIO','ACTIVE','LEAD','S2','S2',json_object('templateCode','TD-002','requiredForPublish',true,'scenarioVersion',1),1),
                      (3,'SIMULATION_SCENARIO','ACTIVE','LEAD','S3','S3',json_object('templateCode','TD-003','requiredForPublish',true,'scenarioVersion',1),1),
                      (4,'SIMULATION_SCENARIO','ACTIVE','LEAD','S4','S4',json_object('templateCode','TD-004','requiredForPublish',true,'scenarioVersion',1),1)
                    """);
            statement.executeUpdate("""
                    insert into todo_simulation_evidence(
                      template_id,version_id,definition_hash,scenario_code,scenario_version,result_status)
                    values
                      (101,88,'hash-88','S1',1,'PASSED'),(101,88,'hash-88','FULL_SIMULATION',1,'PASSED'),
                      (102,80,'hash-80','S2',1,'PASSED'),(102,80,'hash-80','FULL_SIMULATION',1,'PASSED'),
                      (103,89,'hash-89','S3',1,'PASSED'),(103,89,'hash-89','FULL_SIMULATION',1,'PASSED'),
                      (104,79,'hash-79','S4',1,'PASSED'),(104,79,'hash-79','FULL_SIMULATION',1,'PASSED')
                    """);
        }
    }

    private static void insertVersion(PreparedStatement insert,long versionId,int versionNo,
            long templateId,String hash,String compiled) throws Exception
    {
        insert.setLong(1,versionId);insert.setInt(2,versionNo);insert.setLong(3,templateId);
        insert.setString(4,"PUBLISHED");insert.setString(5,hash);insert.setString(6,compiled);
        insert.executeUpdate();
    }

    private static int updateStatus(DataSource dataSource,long versionId,String status) throws Exception
    {
        try(Connection connection=dataSource.getConnection();
                PreparedStatement statement=connection.prepareStatement(
                        "update todo_template_version set status=? where version_id=?"))
        {
            connection.createStatement().execute("set session innodb_lock_wait_timeout=10");
            statement.setString(1,status);statement.setLong(2,versionId);
            return statement.executeUpdate();
        }
    }

    private static String status(DataSource dataSource,long versionId) throws Exception
    {
        try(Connection connection=dataSource.getConnection();
                PreparedStatement statement=connection.prepareStatement(
                        "select status from todo_template_version where version_id=?"))
        {
            statement.setLong(1,versionId);
            try(ResultSet rows=statement.executeQuery()){rows.next();return rows.getString(1);}
        }
    }

    private static String actionStatus(DataSource dataSource,String actionId) throws Exception
    {
        try(Connection connection=dataSource.getConnection();
                PreparedStatement statement=connection.prepareStatement(
                        "select action_status from todo_definition_action where action_id=?"))
        {
            statement.setString(1,actionId);
            try(ResultSet rows=statement.executeQuery()){rows.next();return rows.getString(1);}
        }
    }

    private static int countAction(DataSource dataSource,String actionId) throws Exception
    {
        try(Connection connection=dataSource.getConnection();
                PreparedStatement statement=connection.prepareStatement(
                        "select count(*) from todo_definition_action where action_id=?"))
        {
            statement.setString(1,actionId);
            try(ResultSet rows=statement.executeQuery()){rows.next();return rows.getInt(1);}
        }
    }

    private static boolean awaitVersionLockWait(Database database,String table) throws Exception
    {
        long deadline=System.nanoTime()+SECONDS.toNanos(10);
        String sql="""
                select count(*) from performance_schema.data_lock_waits waits
                join performance_schema.data_locks requested
                  on requested.engine_lock_id=waits.requesting_engine_lock_id
                where requested.object_schema=? and requested.object_name=?
                """;
        while(System.nanoTime()<deadline)
        {
            try(Connection connection=DriverManager.getConnection(
                    database.adminUrl(),database.user(),database.password());
                    PreparedStatement statement=connection.prepareStatement(sql))
            {
                statement.setString(1,database.schema());statement.setString(2,table);
                try(ResultSet rows=statement.executeQuery())
                {if(rows.next()&&rows.getInt(1)>0)return true;}
            }
            Thread.sleep(25);
        }
        return false;
    }

    private static Database database() throws Exception
    {
        String url=System.getenv("TODO_MIGRATION_DB_URL");
        assumeTrue(url!=null&&!url.isBlank(),"Migration database is provided by the CI quality gate");
        String user=System.getenv("TODO_MIGRATION_DB_USER");
        String password=System.getenv("TODO_MIGRATION_DB_PASSWORD");
        String schema="lead_release_lock_it_"+UUID.randomUUID().toString().replace("-","");
        try(Connection connection=DriverManager.getConnection(url,user,password);
                Statement statement=connection.createStatement())
        {
            statement.execute("create database `"+schema
                    +"` character set utf8mb4 collate utf8mb4_unicode_ci");
        }
        return new Database(url,user,password,schema);
    }

    private static String withSchema(String url,String schema)
    {
        int query=url.indexOf('?');String base=query<0?url:url.substring(0,query);
        String parameters=query<0?"":url.substring(query);int slash=base.lastIndexOf('/');
        if(slash<"jdbc:mysql://".length())
            throw new IllegalArgumentException("TODO_MIGRATION_DB_URL must include a database name");
        return base.substring(0,slash+1)+schema+parameters;
    }

    private record Database(String adminUrl,String user,String password,String schema) implements AutoCloseable
    {
        DataSource dataSource()
        {return new UnpooledDataSource("com.mysql.cj.jdbc.Driver",withSchema(adminUrl,schema),user,password);}
        @Override public void close() throws Exception
        {
            try(Connection connection=DriverManager.getConnection(adminUrl,user,password);
                    Statement statement=connection.createStatement())
            {statement.execute("drop database if exists `"+schema+"`");}
        }
    }
}
