package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

import com.law.business.lead.dto.LeadProgressCompleteCommand;
import com.law.business.security.BusinessActor;
import com.law.business.security.BusinessActorProvider;
import com.law.todo.application.TodoRoutingService;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.schedule.TodoScheduleService;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.domain.BizLeadFollowup;
import com.ruoyi.system.domain.BizLeadSetting;
import com.ruoyi.system.mapper.BizLeadMapper;
import com.ruoyi.system.service.ISysDictTypeService;
import com.ruoyi.system.service.lead.LeadAssignmentPolicyService;
import com.ruoyi.system.service.lead.LeadProgressCycleService;

class LeadProgressCycleRuntimeMySqlTest
{
    @Test
    void concurrentRepeatableReadCompletionCreatesOneFactOnePlanAndOneLink() throws Exception
    {
        try(Harness harness=new Harness("concurrent"))
        {
            CyclicBarrier absentReads=new CyclicBarrier(2);
            AtomicInteger reads=new AtomicInteger();
            BizLeadMapper fenced=harness.leadPort(absentReads,reads,false);
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
                        if("selectLeadById".equals(method.getName()))return lead();
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
            statement.executeUpdate("insert into todo_template values(10,'TD-004','Progress','LEAD',1,'0')");
            statement.executeUpdate("insert into todo_template_version values(88,10,1,'PUBLISHED')");
            statement.executeUpdate("insert into todo_prd_definition_catalog values("+
                    "'TD-004','READY','READY','READY',json_array())");
            statement.executeUpdate("insert into todo_attachment(todo_id,attachment_type) "
                    +"values(7001,'FOLLOWUP_PROOF')");
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
}
