package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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

import com.law.business.lead.dto.LeadProgressCompleteCommand;
import com.law.business.security.BusinessActor;
import com.law.business.security.BusinessActorProvider;
import com.law.todo.application.TodoRoutingService;
import com.law.todo.application.TodoAutoActionService;
import com.law.todo.application.TodoCommandService;
import com.law.todo.application.TodoCompletionOrchestrator;
import com.law.todo.application.TodoDodService;
import com.law.todo.application.TodoExceptionOperationService;
import com.law.todo.application.command.TodoActionCommands.ActionCommand;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoOperationCommands.ForceCommand;
import com.law.todo.domain.TodoAccessPolicy;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.schedule.TodoScheduleService;
import com.law.todo.spi.NoOpTodoCompletionLifecyclePort;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.domain.BizLeadFollowup;
import com.ruoyi.system.domain.BizLeadSetting;
import com.ruoyi.system.mapper.BizLeadMapper;
import com.ruoyi.system.service.ISysDictTypeService;
import com.ruoyi.system.service.lead.LeadAssignmentPolicyService;
import com.ruoyi.system.service.lead.LeadProgressCycleService;
import com.ruoyi.system.service.event.LeadProgressHandoffTodoHandler;

class LeadProgressCycleRuntimeMySqlTest
{
    @Test
    void concurrentRepeatableReadCompletionCreatesOneFactOnePlanAndOneLink() throws Exception
    {
        try(Harness harness=new Harness("concurrent"))
        {
            AtomicInteger reads=new AtomicInteger();
            BizLeadMapper fenced=harness.leadPort(null,reads,false);
            LeadProgressCycleService service=harness.service(fenced,harness.schedules);
            LocalDateTime progressAt=LocalDateTime.now().minusMinutes(1).withNano(0);

            List<Outcome> outcomes=harness.completeConcurrently(service,progressAt);

            assertNull(outcomes.get(0).failure(),String.valueOf(outcomes.get(0).failure()));
            assertNull(outcomes.get(1).failure(),String.valueOf(outcomes.get(1).failure()));
            assertEquals(outcomes.get(0).value().followupId(),outcomes.get(1).value().followupId());
            assertEquals(outcomes.get(0).value().schedulePlanId(),outcomes.get(1).value().schedulePlanId());
            assertEquals(1,outcomes.stream().filter(value->value.value().replayed()).count());
            assertEquals(1,harness.count("select count(*) from biz_lead_followup "
                    +"where idempotency_key='LEAD_PROGRESS:7001'"));
            assertEquals(1,harness.count("select count(*) from todo_schedule_plan "
                    +"where idempotency_key='LEAD_PROGRESS_5D:91:7001'"));
            assertEquals(1,harness.count("select count(*) from todo_schedule_window window_row "
                    +"join todo_schedule_plan plan on plan.plan_id=window_row.plan_id "
                    +"where plan.idempotency_key='LEAD_PROGRESS_5D:91:7001'"));
            assertEquals(0,harness.count("select count(*) from biz_lead_followup "
                    +"where idempotency_key='LEAD_PROGRESS:7001' and schedule_plan_id is null"));
        }
    }

    @Test
    void committedReassignmentWinsTheLeadLockAndRejectsStaleOwnerCompletion() throws Exception
    {
        try(Harness harness=new Harness("reassign"))
        {
            harness.assertMutationWinsBeforeCompletion(
                    "update biz_lead set owner_id=9,row_version=row_version+1 where lead_id=91",
                    "ACCESS_DENIED");
        }
    }

    @Test
    void committedPoolTransitionWinsTheLeadLockAndRejectsStaleStateCompletion() throws Exception
    {
        try(Harness harness=new Harness("pool"))
        {
            harness.assertMutationWinsBeforeCompletion(
                    "update biz_lead set pool_status='1',disposition='PUBLIC_POOL',"+
                            "row_version=row_version+1 where lead_id=91",
                    "STATE_CONFLICT");
        }
    }

    @Test
    void scheduleAndLinkFailuresRollBackEveryProgressCycleWrite() throws Exception
    {
        try(Harness harness=new Harness("rollback"))
        {
            TodoScheduleService failingSchedule=mock(TodoScheduleService.class);
            when(failingSchedule.createPlan(any())).thenThrow(new IllegalStateException("schedule failure"));
            LeadProgressCycleService scheduleFailure=harness.service(
                    harness.leadPort(null,new AtomicInteger(),false),failingSchedule);

            assertThrows(IllegalStateException.class,()->harness.complete(scheduleFailure,
                    LocalDateTime.now().minusMinutes(2).withNano(0)));
            assertEquals(0,harness.count("select count(*) from biz_lead_followup"));
            assertEquals(0,harness.count("select count(*) from todo_schedule_plan"));

            LeadProgressCycleService linkFailure=harness.service(
                    harness.leadPort(null,new AtomicInteger(),true),harness.schedules);
            assertThrows(RuntimeException.class,()->harness.complete(linkFailure,
                    LocalDateTime.now().minusMinutes(1).withNano(0)));
            assertEquals(0,harness.count("select count(*) from biz_lead_followup"));
            assertEquals(0,harness.count("select count(*) from todo_schedule_plan"));
            assertEquals(0,harness.count("select count(*) from todo_schedule_window"));
        }
    }

    @Test
    void normalAndForceCompletionSerializeWithoutDeadlockOrPartialLoserWrites() throws Exception
    {
        try(Harness harness=new Harness("normal_force"))
        {
            harness.assertNormalWinsAgainst("FORCE");
        }
    }

    @Test
    void normalAndAutomaticCompletionSerializeWithoutDeadlockOrPartialLoserWrites() throws Exception
    {
        try(Harness harness=new Harness("normal_auto"))
        {
            harness.assertNormalWinsAgainst("AUTO");
        }
    }

    private record Outcome(LeadProgressCycleService.ProgressCycleOutcome value,Throwable failure) { }

    private final class Harness implements AutoCloseable
    {
        private final String adminUrl;
        private final String user;
        private final String password;
        private final String schema;
        private final String url;
        private final BizLeadMapper leadDelegate;
        private final TodoMapper todos;
        private final TodoScheduleService schedules;
        private final TransactionTemplate transaction;
        private final ExecutorService workers=Executors.newFixedThreadPool(2);

        private Harness(String label) throws Exception
        {
            adminUrl=System.getenv("TODO_MIGRATION_DB_URL");
            assumeTrue(adminUrl!=null&&!adminUrl.isBlank(),
                    "Migration database is provided by the CI quality gate");
            user=required("TODO_MIGRATION_DB_USER");password=required("TODO_MIGRATION_DB_PASSWORD");
            schema="lp_"+label.substring(0,Math.min(label.length(),8))+"_"
                    +UUID.randomUUID().toString().replace("-","").substring(0,16);
            createSchema(adminUrl,user,password,schema);url=withSchema(adminUrl,schema);
            createTables(url,user,password);
            Flyway.configure().dataSource(url,user,password).baselineOnMigrate(true)
                    .baselineVersion("0.20.75").locations("classpath:db/migration")
                    .target("0.20.76").load().migrate();
            DataSource dataSource=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",url,user,password);
            Configuration configuration=myBatis(dataSource);
            SqlSessionFactory sessions=new SqlSessionFactoryBuilder().build(configuration);
            SqlSessionTemplate template=new SqlSessionTemplate(sessions);
            leadDelegate=template.getMapper(BizLeadMapper.class);
            todos=template.getMapper(TodoMapper.class);
            schedules=new TodoScheduleService(todos,new TodoRoutingService(todos));
            transaction=new TransactionTemplate(new DataSourceTransactionManager(dataSource));
            transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            transaction.setTimeout(15);
        }

        private LeadProgressCycleService service(BizLeadMapper leads,TodoScheduleService schedulePort)
        {
            BusinessActorProvider actors=()->new BusinessActor(8L,"alice","Alice",3L,false);
            ISysDictTypeService dictionaries=mock(ISysDictTypeService.class);
            SysDictData progress=new SysDictData();progress.setDictValue("PHONE");
            when(dictionaries.selectDictDataByType("law_lead_progress_type"))
                    .thenReturn(List.of(progress));
            LeadAssignmentPolicyService policies=mock(LeadAssignmentPolicyService.class);
            when(policies.resolveProgressSchedule(any())).thenReturn(
                    new LeadAssignmentPolicyService.ProgressSchedulePolicy(
                            11L,2,501L,"Asia/Shanghai"));
            return new LeadProgressCycleService(leads,actors,dictionaries,todos,schedulePort,policies);
        }

        private BizLeadMapper leadPort(CyclicBarrier absentReads,AtomicInteger reads,boolean failLink)
        {
            return (BizLeadMapper)Proxy.newProxyInstance(BizLeadMapper.class.getClassLoader(),
                    new Class<?>[]{BizLeadMapper.class},(proxy,method,args)->{
                        if("linkProgressFollowupSchedule".equals(method.getName())&&failLink)return 0;
                        try
                        {
                            Object result=method.invoke(leadDelegate,args);
                            if(absentReads!=null&&"selectProgressFollowupByIdempotencyKey"
                                    .equals(method.getName())&&result==null&&reads.incrementAndGet()<=2)
                                absentReads.await(10,TimeUnit.SECONDS);
                            return result;
                        }
                        catch(InvocationTargetException wrapped){throw wrapped.getCause();}
                    });
        }

        private BizLeadMapper leadPort(CountDownLatch firstLeadLocked,CountDownLatch releaseFirst)
        {
            AtomicInteger locks=new AtomicInteger();
            return (BizLeadMapper)Proxy.newProxyInstance(BizLeadMapper.class.getClassLoader(),
                    new Class<?>[]{BizLeadMapper.class},(proxy,method,args)->{
                        try
                        {
                            Object result=method.invoke(leadDelegate,args);
                            if("selectLeadForProgressCycleForUpdate".equals(method.getName())
                                    &&locks.incrementAndGet()==1)
                            {
                                firstLeadLocked.countDown();
                                if(!releaseFirst.await(10,TimeUnit.SECONDS))
                                    throw new IllegalStateException("Timed out releasing first lead lock");
                            }
                            return result;
                        }
                        catch(InvocationTargetException wrapped){throw wrapped.getCause();}
                    });
        }

        private TodoMapper todoPort()
        {
            return (TodoMapper)Proxy.newProxyInstance(TodoMapper.class.getClassLoader(),
                    new Class<?>[]{TodoMapper.class},(proxy,method,args)->{
                        if("selectTemplateVersionById".equals(method.getName()))
                            return Map.of("status","PUBLISHED","templateCode","TD-004",
                                    "businessType","LEAD");
                        try{return method.invoke(todos,args);}
                        catch(InvocationTargetException wrapped){throw wrapped.getCause();}
                    });
        }

        private CompletionPorts completionPorts(BizLeadMapper leads)
        {
            TodoMapper todoPort=todoPort();
            LeadProgressHandoffTodoHandler handler=new LeadProgressHandoffTodoHandler(
                    service(leads,new TodoScheduleService(todoPort,new TodoRoutingService(todoPort))));
            TodoCompletionOrchestrator completion=new TodoCompletionOrchestrator(List.of(handler));
            TodoAccessPolicy access=mock(TodoAccessPolicy.class);
            when(access.canOperate(any(),any())).thenReturn(true);
            TodoDodService dod=new TodoDodService(List.of());
            TodoCommandService commands=new TodoCommandService(todoPort,access,dod,completion,null,
                    new NoOpTodoCompletionLifecyclePort());
            TodoExceptionOperationService exceptions=new TodoExceptionOperationService(
                    todoPort,dod,completion);
            return new CompletionPorts(commands,exceptions);
        }

        private void assertNormalWinsAgainst(String competitor) throws Exception
        {
            CountDownLatch firstLeadLocked=new CountDownLatch(1);
            CountDownLatch releaseFirst=new CountDownLatch(1);
            CompletionPorts ports=completionPorts(leadPort(firstLeadLocked,releaseFirst));
            LocalDateTime progressAt=LocalDateTime.now().minusMinutes(1).withNano(0);
            Map<String,Object> payload=Map.of("progressType","PHONE",
                    "progressAt",progressAt.toString(),"remark","substantive progress");
            Actor owner=new Actor(8L,"alice",3L);
            Future<EntryOutcome> normal=workers.submit(()->entry(()->ports.commands().complete(
                    7001L,new ActionCommand("NORMAL:7001",null,payload,List.of()),owner)));
            assertTrue(firstLeadLocked.await(5,TimeUnit.SECONDS));
            Future<EntryOutcome> competing=workers.submit(()->"FORCE".equals(competitor)
                    ?entry(()->ports.exceptions().forceComplete(7001L,
                            new ForceCommand("FORCE:7001","approved",payload),
                            new Actor(1L,"admin",3L)))
                    :entry(()->ports.commands().autoComplete(7001L,
                            new ActionCommand("AUTO:7001:RACE",null,payload,List.of()),
                            TodoAutoActionService.SERVICE_ACTOR)));
            assertThrows(TimeoutException.class,()->competing.get(300,TimeUnit.MILLISECONDS),
                    "Competing completion must wait behind the authoritative lead lock");
            releaseFirst.countDown();

            EntryOutcome winner=normal.get(10,TimeUnit.SECONDS);
            EntryOutcome loser=competing.get(10,TimeUnit.SECONDS);
            assertNull(winner.failure(),String.valueOf(winner.failure()));
            assertEquals("COMPLETED",winner.value().getStatus());
            TodoException conflict=assertInstanceOf(TodoException.class,loser.failure());
            assertEquals("TODO_CONCURRENT_MODIFICATION",conflict.getBusinessCode());
            assertEquals(1,count("select count(*) from todo_instance where todo_id=7001 "
                    +"and status='COMPLETED'"));
            assertEquals(1,count("select count(*) from todo_action_log where todo_id=7001 "
                    +"and action_type='COMPLETE'"));
            assertEquals(0,count("select count(*) from todo_exception_log where todo_id=7001"));
            assertEquals(0,count("select count(*) from todo_action_log where todo_id=7001 "
                    +"and action_source='SYSTEM'"));
            assertEquals(1,count("select count(*) from biz_lead_followup "
                    +"where idempotency_key='LEAD_PROGRESS:7001'"));
            assertEquals(1,count("select count(*) from todo_schedule_plan "
                    +"where idempotency_key='LEAD_PROGRESS_5D:91:7001'"));
            assertEquals(1,count("select count(*) from todo_schedule_window window_row "
                    +"join todo_schedule_plan plan on plan.plan_id=window_row.plan_id "
                    +"where plan.idempotency_key='LEAD_PROGRESS_5D:91:7001'"));
            assertEquals(1,count("select count(*) from todo_instance"));
        }

        private EntryOutcome entry(java.util.concurrent.Callable<TodoInstance> action)
        {
            try{return new EntryOutcome(transaction.execute(ignored->{
                try{return action.call();}
                catch(RuntimeException failure){throw failure;}
                catch(Exception failure){throw new RuntimeException(failure);}
            }),null);}
            catch(Throwable failure){return new EntryOutcome(null,failure);}
        }

        private void assertMutationWinsBeforeCompletion(String mutationSql,String expectedCode)
                throws Exception
        {
            LeadProgressCycleService service=service(
                    leadPort(null,new AtomicInteger(),false),schedules);
            CountDownLatch completionStarted=new CountDownLatch(1);
            try(Connection mutation=DriverManager.getConnection(url,user,password);
                Statement statement=mutation.createStatement())
            {
                mutation.setAutoCommit(false);
                mutation.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                assertEquals(1,statement.executeUpdate(mutationSql));

                Future<Outcome> completion=workers.submit(()->{
                    completionStarted.countDown();
                    return outcome(service,LocalDateTime.now().minusMinutes(1).withNano(0));
                });
                assertTrue(completionStarted.await(5,TimeUnit.SECONDS));
                assertThrows(TimeoutException.class,()->completion.get(300,TimeUnit.MILLISECONDS),
                        "Completion must wait on the authoritative lead row lock");

                mutation.commit();
                Outcome outcome=completion.get(10,TimeUnit.SECONDS);
                assertNull(outcome.value());
                ServiceException failure=assertInstanceOf(ServiceException.class,outcome.failure());
                assertEquals(expectedCode,failure.getBusinessCode());
            }
            assertNoProgressWrites();
        }

        private void assertNoProgressWrites() throws Exception
        {
            assertEquals(0,count("select count(*) from biz_lead_followup"));
            assertEquals(0,count("select count(*) from todo_schedule_plan"));
            assertEquals(0,count("select count(*) from todo_schedule_window"));
            assertEquals(1,count("select count(*) from todo_instance where todo_id=7001 "
                    +"and status='SUBMITTED'"));
        }

        private List<Outcome> completeConcurrently(LeadProgressCycleService service,
                LocalDateTime progressAt) throws Exception
        {
            Future<Outcome> first=workers.submit(()->outcome(service,progressAt));
            Future<Outcome> second=workers.submit(()->outcome(service,progressAt));
            return List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));
        }

        private Outcome outcome(LeadProgressCycleService service,LocalDateTime progressAt)
        {
            try{return new Outcome(complete(service,progressAt),null);}
            catch(Throwable failure){return new Outcome(null,failure);}
        }

        private LeadProgressCycleService.ProgressCycleOutcome complete(
                LeadProgressCycleService service,LocalDateTime progressAt)
        {
            return transaction.execute(ignored->service.complete(command(progressAt),todo()));
        }

        private int count(String sql) throws Exception
        {
            try(Connection connection=DriverManager.getConnection(url,user,password);
                Statement statement=connection.createStatement();ResultSet rows=statement.executeQuery(sql))
            {rows.next();return rows.getInt(1);}
        }

        @Override public void close() throws Exception
        {
            workers.shutdownNow();workers.awaitTermination(10,TimeUnit.SECONDS);
            dropSchema(adminUrl,user,password,schema);
        }
    }

    private Configuration myBatis(DataSource dataSource) throws Exception
    {
        Configuration value=new Configuration(new Environment("lead-progress-runtime",
                new SpringManagedTransactionFactory(),dataSource));
        value.setMapUnderscoreToCamelCase(true);
        value.getTypeAliasRegistry().registerAlias("BizLead",BizLead.class);
        value.getTypeAliasRegistry().registerAlias("BizLeadFollowup",BizLeadFollowup.class);
        value.getTypeAliasRegistry().registerAlias("BizLeadSetting",BizLeadSetting.class);
        for(String resource:List.of("mapper/system/BizLeadMapper.xml","mapper/todo/TodoMapper.xml"))
            try(var input=Resources.getResourceAsStream(resource))
            {new XMLMapperBuilder(input,value,resource,value.getSqlFragments()).parse();}
        return value;
    }

    private BizLead lead()
    {
        BizLead value=new BizLead();value.setLeadId(91L);value.setLeadNo("L-91");
        value.setStatus("2");value.setDelFlag("0");value.setPoolStatus("0");
        value.setDisposition("ACTIVE");value.setOwnerId(8L);value.setDeptId(3L);
        value.setSourceCode("WEB");return value;
    }

    private TodoInstance todo()
    {
        TodoInstance value=new TodoInstance();value.setTodoId(7001L);value.setTemplateCode("TD-004");
        value.setTemplateVersionId(88L);value.setBusinessType("LEAD");value.setBusinessId(91L);
        value.setOwnerId(8L);value.setOwnerDeptId(3L);return value;
    }

    private LeadProgressCompleteCommand command(LocalDateTime progressAt)
    {
        LeadProgressCompleteCommand value=new LeadProgressCompleteCommand();value.setLeadId(91L);
        value.setTodoId(7001L);value.setProgressType("PHONE");value.setProgressAt(progressAt);
        value.setRemark("substantive progress");return value;
    }

    private void createTables(String url,String user,String password) throws Exception
    {
        try(Connection connection=DriverManager.getConnection(url,user,password);
            Statement statement=connection.createStatement())
        {
            statement.execute("""
                    create table sys_user(
                      user_id bigint primary key,dept_id bigint null,status char(1) not null,
                      del_flag char(1) not null
                    ) engine=innodb
                    """);
            statement.execute("""
                    create table biz_lead(
                      lead_id bigint not null,lead_no varchar(64) not null,status varchar(20) not null,
                      del_flag char(1) not null,pool_status char(1) not null,
                      disposition varchar(32) not null,owner_id bigint null,dept_id bigint null,
                      source_code varchar(64) null,row_version int not null default 0,
                      primary key(lead_id)
                    ) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci
                    """);
            statement.execute("""
                    create table biz_lead_followup(
                      followup_id bigint not null auto_increment,lead_id bigint not null,
                      follow_type varchar(30) default 'phone',follow_result varchar(60) default '',
                      content varchar(1000) default '',next_follow_time datetime null,
                      follow_user_id bigint null,task_status char(1) default '1',
                      create_by varchar(64) default '',create_time datetime null,
                      update_by varchar(64) default '',update_time datetime null,remark varchar(500) null,
                      primary key(followup_id),key idx_lead_followup_lead(lead_id),
                      key idx_lead_followup_user(follow_user_id)
                    ) engine=innodb default charset=utf8mb4 collate=utf8mb4_unicode_ci
                    """);
            statement.execute("""
                    create table todo_template(
                      template_id bigint primary key,template_code varchar(64) not null,
                      template_name varchar(128) not null,business_type varchar(32) not null,
                      current_version int not null,status char(1) not null)
                    """);
            statement.execute("""
                    create table todo_template_version(
                      version_id bigint primary key,template_id bigint not null,version_no int not null,
                      status varchar(20) not null)
                    """);
            statement.execute("""
                    create table todo_prd_definition_catalog(
                      template_code varchar(64) primary key,definition_package_state varchar(32),
                      foundation_state varchar(32),production_state varchar(32),blockers_json json)
                    """);
            statement.execute("""
                    create table todo_trigger_rule(
                      trigger_rule_id bigint primary key,template_version_id bigint not null,
                      enabled char(1) not null,event_type varchar(64),condition_json json)
                    """);
            statement.execute("""
                    create table todo_attachment(
                      attachment_id bigint not null auto_increment,todo_id bigint not null,
                      attachment_type varchar(64) not null,primary key(attachment_id))
                    """);
            statement.execute("""
                    create table todo_instance(
                      todo_id bigint not null auto_increment,todo_no varchar(64) null,
                      template_id bigint null,template_version_id bigint null,
                      template_code varchar(64) not null,title varchar(255) null,
                      business_type varchar(64) null,business_id bigint not null,business_no varchar(64) null,
                      owner_id bigint null,owner_dept_id bigint null,status varchar(20) not null,
                      priority varchar(20) null,sla_status varchar(20) null,created_at datetime null,
                      due_at datetime null,completed_at datetime null,claimed_at datetime null,
                      started_at datetime null,submitted_at datetime null,cancelled_at datetime null,
                      previous_todo_id bigint null,root_todo_id bigint null,trigger_event_id varchar(64) null,
                      trigger_idempotency_key varchar(192) null,next_idempotency_key varchar(192) null,
                      dod_snapshot_json json null,definition_hash varchar(128) null,
                      route_definition_version_id bigint null,ui_schema_snapshot json null,
                      sla_snapshot json null,route_node_key varchar(64) null,route_token json null,
                      occurrence_key varchar(192) null,payload_schema_version int null,
                      version int not null default 0,update_by varchar(64) null,update_time datetime null,
                      primary key(todo_id)
                    ) engine=innodb
                    """);
            statement.execute("""
                    create table todo_action_log(
                      action_log_id bigint not null auto_increment,todo_id bigint not null,
                      action_id varchar(128) not null,action_type varchar(64) not null,
                      action_source varchar(20) null,from_status varchar(20) null,to_status varchar(20) null,
                      operator_id bigint null,operator_name varchar(64) null,opinion varchar(500) null,
                      payload_json json null,primary key(action_log_id),unique key uk_action_id(action_id)
                    ) engine=innodb
                    """);
            statement.execute("""
                    create table todo_exception_log(
                      exception_log_id bigint not null auto_increment,todo_id bigint not null,
                      action_id varchar(128) not null,operation_type varchar(64) not null,
                      from_status varchar(20) null,operator_id bigint null,operator_name varchar(64) null,
                      operator_dept_id bigint null,reason varchar(500) null,payload_json json null,
                      primary key(exception_log_id),unique key uk_exception_action(action_id)
                    ) engine=innodb
                    """);
            statement.execute("""
                    create table todo_auto_action_execution(
                      execution_key varchar(192) primary key,todo_id bigint not null,
                      rule_key varchar(128) null,action_type varchar(64) not null,status varchar(20) not null,
                      attempt_count int not null default 1,claimed_at datetime null,create_time datetime null,
                      update_time datetime null
                    ) engine=innodb
                    """);
            statement.execute("""
                    create table todo_schedule_plan(
                      plan_id bigint not null auto_increment,previous_todo_id bigint not null,
                      template_version_id bigint not null,business_type varchar(64) not null,
                      business_id bigint not null,schedule_purpose varchar(32) not null,
                      idempotency_key varchar(192) not null,timezone varchar(64) not null,
                      rule_version_id bigint null,assignment_policy_id bigint null,
                      assignment_policy_version int null,
                      assignment_policy_snapshot_source varchar(32) not null,
                      first_contact_at datetime not null,current_window_code varchar(32) null,
                      status varchar(20) not null,completion_reason varchar(64) null,
                      completed_at datetime null,create_time datetime not null,update_time datetime not null,
                      version int not null default 0,primary key(plan_id),
                      unique key uk_todo_schedule_plan_idempotency(idempotency_key)) engine=innodb
                    """);
            statement.execute("""
                    create table todo_schedule_window(
                      window_id bigint not null auto_increment,plan_id bigint not null,
                      window_code varchar(32) not null,window_order int not null,day_offset int not null,
                      start_time time not null,end_time time not null,materialize_at datetime not null,
                      due_at datetime not null,max_attempts int not null,occurrence_no int not null,
                      status varchar(20) not null,claimed_at datetime null,completed_at datetime null,
                      error_code varchar(128) null,cancel_reason varchar(64) null,
                      create_time datetime not null,update_time datetime not null,version int not null default 0,
                      primary key(window_id),unique key uk_todo_schedule_window(plan_id,window_code),
                      constraint fk_progress_window_plan foreign key(plan_id)
                        references todo_schedule_plan(plan_id)) engine=innodb
                    """);
            statement.executeUpdate("insert into biz_lead values("+
                    "91,'L-91','2','0','0','ACTIVE',8,3,'WEB',0)");
            statement.executeUpdate("insert into sys_user values(8,3,'0','0')");
            statement.executeUpdate("insert into todo_template values(10,'TD-004','Progress','LEAD',1,'0')");
            statement.executeUpdate("insert into todo_template_version values(88,10,1,'PUBLISHED')");
            statement.executeUpdate("insert into todo_prd_definition_catalog values("+
                    "'TD-004','READY','READY','READY',json_array())");
            statement.executeUpdate("insert into todo_attachment(todo_id,attachment_type) "
                    +"values(7001,'FOLLOWUP_PROOF')");
            statement.executeUpdate("insert into todo_instance(todo_id,todo_no,template_id,"
                    +"template_version_id,template_code,title,business_type,business_id,business_no,"
                    +"owner_id,owner_dept_id,status,priority,sla_status,created_at,submitted_at,version) "
                    +"values(7001,'TD004-7001',10,88,'TD-004','Progress','LEAD',91,'L-91',"
                    +"8,3,'SUBMITTED','NORMAL','NORMAL',sysdate(),sysdate(),0)");
            statement.executeUpdate("insert into todo_auto_action_execution(execution_key,todo_id,"
                    +"rule_key,action_type,status,attempt_count,claimed_at,create_time,update_time) "
                    +"values('AUTO:7001:RACE',7001,'RACE','COMPLETE_DEFAULT','CLAIMED',1,"
                    +"sysdate(),sysdate(),sysdate())");
        }
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

    private record CompletionPorts(TodoCommandService commands,
            TodoExceptionOperationService exceptions) { }
    private record EntryOutcome(TodoInstance value,Throwable failure) { }
}
