package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
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
import org.apache.ibatis.jdbc.ScriptRunner;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.law.business.event.BusinessEventCommand;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.event.BusinessEventType;
import com.law.business.lead.dto.LeadRetryCompleteCommand;
import com.law.business.security.BusinessActor;
import com.law.business.security.BusinessActorProvider;
import com.law.todo.application.TodoRoutingService;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.schedule.TodoScheduleService;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.BizLeadCallRecord;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.domain.BizLeadFollowup;
import com.ruoyi.system.domain.BusinessEventRecord;
import com.ruoyi.system.mapper.BizLeadMapper;
import com.ruoyi.system.mapper.BusinessEventMapper;
import com.ruoyi.system.mapper.LeadFlowMapper;
import com.ruoyi.system.service.ISysDictTypeService;
import com.ruoyi.system.service.event.OutboxBusinessEventPublisher;
import com.ruoyi.system.service.lead.LeadAccessPolicy;
import com.ruoyi.system.service.lead.LeadAssignmentPolicyService;
import com.ruoyi.system.service.lead.LeadCallRecordService;
import com.ruoyi.system.service.lead.LeadPoolService;
import com.ruoyi.system.service.lead.LeadRetryService;

class LeadFlowMapperExternalMysqlIT
{
    private static final List<String> V015_BASELINE=List.of(
            "ry_20260417.sql","quartz.sql","lead_module_20260602.sql",
            "lead_menu_20260602.sql","customer_contract_module_20260603.sql",
            "customer_contract_dict_patch_20260611.sql","case_module_20260611.sql",
            "matter_module_20260615.sql","matter_menu_patch_20260617.sql",
            "finance_module_20260624.sql","customer_tag_assign_permission_fix_20260627.sql");
    private static final String V049_FILE=
            "V0_20_49__todo_schedule_policy_snapshot.sql";
    private static final String V050_FILE=
            "V0_20_50__todo_schedule_policy_provenance.sql";

    @Test
    void pre050ActiveScheduleGetsExplicitLegacyProvenanceAndRemainsOperable() throws Exception
    {
        String adminUrl=requiredEnvironment("TODO_MIGRATION_DB_URL");
        String user=requiredEnvironment("TODO_MIGRATION_DB_USER");
        String password=requiredEnvironment("TODO_MIGRATION_DB_PASSWORD");
        String schema="task6_legacy_policy_"+UUID.randomUUID().toString().replace("-","");
        createSchema(adminUrl,user,password,schema);
        try
        {
            String schemaUrl=withSchema(adminUrl,schema);
            initializeV015Baseline(schemaUrl,user,password);
            migrate(schemaUrl,user,password,"0.20.48");
            DataSource dataSource=new UnpooledDataSource(
                    "com.mysql.cj.jdbc.Driver",schemaUrl,user,password);
            LegacyScheduleIds ids=insertPre049Schedule(dataSource);

            migrate(schemaUrl,user,password,"0.20.50");

            SqlSessionFactory sessions=new SqlSessionFactoryBuilder().build(myBatis(dataSource));
            try(SqlSession session=sessions.openSession(false))
            {
                TodoMapper mapper=session.getMapper(TodoMapper.class);
                TodoScheduleService schedules=new TodoScheduleService(
                        mapper,new TodoRoutingService(mapper));
                TodoScheduleService.ScheduleOccurrenceContext context=
                        schedules.lockOccurrenceContext(ids.occurrenceId());
                assertEquals(TodoScheduleService.LEGACY_POLICY,
                        context.assignmentPolicySnapshotSource());
                assertNull(context.assignmentPolicyId());
                assertNull(context.assignmentPolicyVersion());

                TodoScheduleService.ScheduleCompletion completion=schedules.completeOccurrence(
                        context,"CONNECTED",LocalDateTime.of(2026,7,25,11,0));
                assertEquals(TodoScheduleService.LEGACY_POLICY,
                        completion.assignmentPolicySnapshotSource());
                assertNull(completion.assignmentPolicyId());
                assertNull(completion.assignmentPolicyVersion());
                session.commit();
            }
            assertLegacyCompletion(dataSource,ids);
        }
        finally
        {
            dropSchema(adminUrl,user,password,schema);
        }
    }

    @Test
    void published049DatabaseMigratesForwardAndCompletesActiveResolvedSchedule() throws Exception
    {
        String adminUrl=requiredEnvironment("TODO_MIGRATION_DB_URL");
        String user=requiredEnvironment("TODO_MIGRATION_DB_USER");
        String password=requiredEnvironment("TODO_MIGRATION_DB_PASSWORD");
        String schema="task6_published_049_"+UUID.randomUUID().toString().replace("-","");
        Path publishedMigrations=publishedV049MigrationSet();
        createSchema(adminUrl,user,password,schema);
        try
        {
            String schemaUrl=withSchema(adminUrl,schema);
            initializeV015Baseline(schemaUrl,user,password);
            migrate(schemaUrl,user,password,"0.20.49",publishedMigrations);
            DataSource dataSource=new UnpooledDataSource(
                    "com.mysql.cj.jdbc.Driver",schemaUrl,user,password);
            assertPublishedV049Checksum(dataSource);
            LegacyScheduleIds ids=insertPublished049Schedule(dataSource);

            migrate(schemaUrl,user,password,"0.20.50");

            SqlSessionFactory sessions=new SqlSessionFactoryBuilder().build(myBatis(dataSource));
            try(SqlSession session=sessions.openSession(false))
            {
                TodoMapper mapper=session.getMapper(TodoMapper.class);
                TodoScheduleService schedules=new TodoScheduleService(
                        mapper,new TodoRoutingService(mapper));
                TodoScheduleService.ScheduleOccurrenceContext context=
                        schedules.lockOccurrenceContext(ids.occurrenceId());
                assertEquals(TodoScheduleService.RESOLVED_POLICY,
                        context.assignmentPolicySnapshotSource());
                assertEquals(77L,context.assignmentPolicyId());
                assertEquals(6,context.assignmentPolicyVersion());

                TodoScheduleService.ScheduleCompletion completion=schedules.completeOccurrence(
                        context,"CONNECTED",LocalDateTime.of(2026,7,25,11,0));
                assertEquals(TodoScheduleService.RESOLVED_POLICY,
                        completion.assignmentPolicySnapshotSource());
                assertEquals(77L,completion.assignmentPolicyId());
                assertEquals(6,completion.assignmentPolicyVersion());
                session.commit();
            }
            assertResolvedCompletion(dataSource,ids);
        }
        finally
        {
            dropSchema(adminUrl,user,password,schema);
            deleteRecursively(publishedMigrations);
        }
    }

    private static void assertPublishedV049Checksum(DataSource dataSource) throws Exception
    {
        try(Connection connection=dataSource.getConnection();
            PreparedStatement query=connection.prepareStatement(
                    "select checksum from flyway_schema_history where version='0.20.49' "
                    +"and success=1"))
        {
            try(ResultSet row=query.executeQuery())
            {
                if(!row.next())throw new AssertionError(
                        "Published 0.20.49 Flyway history was not found");
                assertEquals(1_353_770_454,row.getInt("checksum"));
            }
        }
    }

    @Test
    void provenanceMigrationRejectsPartialPublished049PolicyIdentity() throws Exception
    {
        String adminUrl=requiredEnvironment("TODO_MIGRATION_DB_URL");
        String user=requiredEnvironment("TODO_MIGRATION_DB_USER");
        String password=requiredEnvironment("TODO_MIGRATION_DB_PASSWORD");
        String schema="task6_invalid_policy_"+UUID.randomUUID().toString().replace("-","");
        createSchema(adminUrl,user,password,schema);
        try
        {
            String schemaUrl=withSchema(adminUrl,schema);
            try(Connection connection=DriverManager.getConnection(schemaUrl,user,password);
                Statement statement=connection.createStatement())
            {
                statement.execute("create table todo_schedule_plan("
                        +"plan_id bigint not null primary key,"
                        +"assignment_policy_id bigint null,"
                        +"assignment_policy_version int null) engine=innodb");
                statement.executeUpdate("insert into todo_schedule_plan values(1,77,null)");
            }

            assertThrows(FlywayException.class,
                    ()->migrateFromPublishedV049Baseline(schemaUrl,user,password));

            try(Connection connection=DriverManager.getConnection(schemaUrl,user,password);
                Statement statement=connection.createStatement())
            {
                assertEquals(0,count(statement,
                        "select count(*) from information_schema.columns "
                        +"where table_schema=database() and table_name='todo_schedule_plan' "
                        +"and column_name='assignment_policy_snapshot_source'"));
            }
        }
        finally
        {
            dropSchema(adminUrl,user,password,schema);
        }
    }

    @Test
    void sourceSpecificAndWildcardPoliciesPersistTheirExactScheduleSnapshots() throws Exception
    {
        String url=requiredEnvironment("TODO_MIGRATION_DB_URL");
        String user=requiredEnvironment("TODO_MIGRATION_DB_USER");
        String password=requiredEnvironment("TODO_MIGRATION_DB_PASSWORD");
        DataSource dataSource=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",url,user,password);
        SqlSessionFactory sessions=new SqlSessionFactoryBuilder().build(myBatis(dataSource));
        try(SqlSession session=sessions.openSession(false))
        {
            Connection connection=session.getConnection();
            long templateVersionId=td003Version(connection);
            try(PreparedStatement publish=connection.prepareStatement(
                    "update todo_template_version set status='PUBLISHED' where version_id=?"))
            {
                publish.setLong(1,templateVersionId);
                assertEquals(1,publish.executeUpdate());
            }
            long deptId=920_000_000L+Math.abs(System.nanoTime()%10_000_000L);
            long wildcardId=insertPolicy(connection,"TASK6-WILD-"+deptId,deptId,"*",9,
                    retryPolicyJson(templateVersionId,701L,2));
            long sourceId=insertPolicy(connection,"TASK6-SOURCE-"+deptId,deptId,"WEB",7,
                    retryPolicyJson(templateVersionId,702L,4));

            LeadFlowMapper facts=session.getMapper(LeadFlowMapper.class);
            LeadAssignmentPolicyService policies=new LeadAssignmentPolicyService(facts);
            TodoMapper todos=session.getMapper(TodoMapper.class);
            TodoScheduleService schedules=new TodoScheduleService(todos,new TodoRoutingService(todos));

            BizLead sourceLead=policyLead(deptId,"WEB");
            LeadAssignmentPolicyService.RetrySchedulePolicy source=
                    policies.resolveRetrySchedule(sourceLead);
            long sourcePlan=createPolicyPlan(schedules,templateVersionId,source,930_000_001L);
            assertPolicySnapshot(connection,sourcePlan,sourceId,7,702L,4);

            BizLead wildcardLead=policyLead(deptId,"REFERRAL");
            LeadAssignmentPolicyService.RetrySchedulePolicy wildcard=
                    policies.resolveRetrySchedule(wildcardLead);
            long wildcardPlan=createPolicyPlan(schedules,templateVersionId,wildcard,930_000_002L);
            assertPolicySnapshot(connection,wildcardPlan,wildcardId,9,701L,2);
            session.rollback();
        }
    }

    @Test
    void factLeadTransitionAndOutboxShareOneRealMysqlTransaction() throws Exception
    {
        String url=requiredEnvironment("TODO_MIGRATION_DB_URL");
        String user=requiredEnvironment("TODO_MIGRATION_DB_USER");
        String password=requiredEnvironment("TODO_MIGRATION_DB_PASSWORD");
        long leadId=900_000_000L+System.nanoTime()%10_000_000L;
        String leadNo="TASK6-"+leadId;
        DataSource dataSource=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",url,user,password);
        insertLead(dataSource,leadId,leadNo);
        SqlSessionFactory sessions=new SqlSessionFactoryBuilder().build(myBatis(dataSource));
        String eventKey="LEAD_FIRST_CONTACT_VALID:"+leadId+":TX";
        String callKey="LEAD_CALL:MANUAL:TX-"+leadId;
        try
        {
            try(SqlSession session=sessions.openSession(false))
            {
                writeFlow(session,leadId,leadNo,callKey,eventKey);
                session.rollback();
            }
            assertState(dataSource,leadId,eventKey,0,0,0,0,null);

            try(SqlSession session=sessions.openSession(false))
            {
                writeFlow(session,leadId,leadNo,callKey,eventKey);
                session.commit();
            }
            assertState(dataSource,leadId,eventKey,1,1,1,1,"VALID");
        }
        finally
        {
            cleanup(dataSource,leadId,eventKey);
        }
    }

    @Test
    void crossLeadAndCrossTodoRetryCommandsCannotMutateEitherGraph() throws Exception
    {
        String url=requiredEnvironment("TODO_MIGRATION_DB_URL");
        String user=requiredEnvironment("TODO_MIGRATION_DB_USER");
        String password=requiredEnvironment("TODO_MIGRATION_DB_PASSWORD");
        long seed=910_000_000L+Math.abs(System.nanoTime()%10_000_000L);
        long leadA=seed,leadB=seed+1,todoId=seed+100;
        DataSource dataSource=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",url,user,password);
        ScheduleIds ids=insertRetryBoundary(dataSource,leadA,leadB,todoId);
        try
        {
            try(SqlSession session=new SqlSessionFactoryBuilder().build(myBatis(dataSource)).openSession(false))
            {
                LeadRetryService service=retryService(session);
                ServiceException crossLead=assertThrows(ServiceException.class,
                        ()->service.completeWindow(retryCommand(leadB,todoId,ids.occurrenceId())));
                assertEquals("LEAD_RETRY_STATE_INVALID",crossLead.getMessage());
                session.rollback();
            }
            assertRetryBoundaryState(dataSource,leadA,leadB,ids,0);
            try(SqlSession session=new SqlSessionFactoryBuilder().build(myBatis(dataSource)).openSession(false))
            {
                LeadRetryService service=retryService(session);
                ServiceException crossTodo=assertThrows(ServiceException.class,
                        ()->service.completeWindow(retryCommand(leadA,todoId+999,ids.occurrenceId())));
                assertEquals("LEAD_RETRY_STATE_INVALID",crossTodo.getMessage());
                session.rollback();
            }
            assertRetryBoundaryState(dataSource,leadA,leadB,ids,0);
        }
        finally
        {
            cleanupRetryBoundary(dataSource,leadA,leadB,ids);
        }
    }

    @Test
    void explicitSystemActorPersistsRealOutboxWithoutPrincipal() throws Exception
    {
        String url=requiredEnvironment("TODO_MIGRATION_DB_URL");
        String user=requiredEnvironment("TODO_MIGRATION_DB_USER");
        String password=requiredEnvironment("TODO_MIGRATION_DB_PASSWORD");
        DataSource dataSource=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",url,user,password);
        String key="TASK6:SYSTEM:"+Math.abs(System.nanoTime());
        SecurityContextHolder.clearContext();
        try
        {
            try(SqlSession session=new SqlSessionFactoryBuilder().build(myBatis(dataSource)).openSession(false))
            {
                new OutboxBusinessEventPublisher(session.getMapper(BusinessEventMapper.class)).publish(
                        new BusinessEventCommand(BusinessEventType.LEAD_INVALID_REVIEW_CONFIRMED,
                                "LEAD",1L,"L-1",key,java.util.Map.of("schemaVersion",1)),
                        new BusinessActor(0L,"system","system",null,false));
                session.commit();
            }
            try(Connection connection=dataSource.getConnection();PreparedStatement statement=connection.prepareStatement(
                    "select create_by from business_event where idempotency_key=?"))
            {
                statement.setString(1,key);
                try(ResultSet row=statement.executeQuery())
                {
                    row.next();assertEquals("system",row.getString(1));
                }
            }
        }
        finally
        {
            try(Connection connection=dataSource.getConnection();PreparedStatement statement=connection.prepareStatement(
                    "delete from business_event where idempotency_key=?"))
            {
                statement.setString(1,key);statement.executeUpdate();
            }
        }
    }

    private static void writeFlow(SqlSession session,long leadId,String leadNo,String callKey,String eventKey)
    {
        LeadFlowMapper facts=session.getMapper(LeadFlowMapper.class);
        BizLeadMapper leads=session.getMapper(BizLeadMapper.class);
        BusinessEventMapper events=session.getMapper(BusinessEventMapper.class);
        BizLeadCallRecord call=new BizLeadCallRecord();
        call.setLeadId(leadId);call.setTodoId(1L);call.setCallChannel("MANUAL");
        call.setExternalCallId("TX-"+leadId);call.setStartedAt(LocalDateTime.of(2026,7,25,9,0));
        call.setCallResult("CONNECTED");call.setIdempotencyKey(callKey);call.setCreateBy("task6");
        assertEquals(1,facts.insertCallRecordIfAbsent(call));

        BizLeadFollowup followup=new BizLeadFollowup();
        followup.setLeadId(leadId);followup.setFollowType("phone");followup.setFollowResult("VALID");
        followup.setContent("transaction");followup.setFollowUserId(1L);followup.setCreateBy("task6");
        assertEquals(1,leads.insertFollowup(followup));
        assertEquals(1,leads.completeFirstContact(leadId,"1","VALID","Client","Shanghai",
                "Demand","1",null,null,0,"task6"));

        BusinessEventRecord event=new BusinessEventRecord();
        event.setEventType("LEAD_FIRST_CONTACT_VALID");event.setPayloadVersion(1);
        event.setAggregateType("LEAD");event.setAggregateId(leadId);event.setAggregateNo(leadNo);
        event.setIdempotencyKey(eventKey);event.setPayload("{\"schemaVersion\":1}");
        event.setEventStatus("PENDING");event.setRetryCount(0);event.setCreateBy("task6");
        assertEquals(1,events.insertBusinessEvent(event));
    }

    private static Configuration myBatis(DataSource dataSource) throws Exception
    {
        Configuration configuration=new Configuration(new Environment("lead-flow-it",
                new JdbcTransactionFactory(),dataSource));
        configuration.getTypeAliasRegistry().registerAliases("com.ruoyi.system.domain");
        for(String resource:new String[]{"mapper/system/BizLeadMapper.xml",
                "mapper/system/LeadFlowMapper.xml","mapper/system/BusinessEventMapper.xml",
                "mapper/todo/TodoMapper.xml"})
        {
            try(InputStream input=Resources.getResourceAsStream(resource))
            {
                new XMLMapperBuilder(input,configuration,resource,configuration.getSqlFragments()).parse();
            }
        }
        return configuration;
    }

    private static void initializeV015Baseline(String url,String user,String password) throws Exception
    {
        try(Connection connection=DriverManager.getConnection(url,user,password))
        {
            ScriptRunner runner=new ScriptRunner(connection);
            runner.setLogWriter(null);runner.setErrorLogWriter(null);
            runner.setStopOnError(true);
            for(String file:V015_BASELINE)
            {
                String sql=Files.readString(Path.of("..","sql",file),StandardCharsets.UTF_8)
                        .replace("; execute stmt;",";\nexecute stmt;")
                        .replace("; deallocate prepare stmt;",";\ndeallocate prepare stmt;");
                try(var input=new StringReader(sql))
                {
                    runner.runScript(input);
                }
            }
        }
    }

    private static void migrate(String url,String user,String password,String target)
    {
        Flyway.configure().dataSource(url,user,password).baselineOnMigrate(true)
                .baselineVersion("0.15.0").locations("classpath:db/migration")
                .target(target).load().migrate();
    }

    private static void migrate(String url,String user,String password,String target,
            Path migrationDirectory)
    {
        String location="filesystem:"+migrationDirectory.toAbsolutePath().toString()
                .replace('\\','/');
        Flyway.configure().dataSource(url,user,password).baselineOnMigrate(true)
                .baselineVersion("0.15.0").locations(location)
                .target(target).load().migrate();
    }

    private static void migrateFromPublishedV049Baseline(String url,String user,String password)
    {
        Flyway.configure().dataSource(url,user,password).baselineOnMigrate(true)
                .baselineVersion("0.20.49").locations("classpath:db/migration")
                .target("0.20.50").load().migrate();
    }

    private static Path publishedV049MigrationSet() throws Exception
    {
        Path directory=Files.createTempDirectory("task6-published-309e7904-");
        Path current=Path.of("src","main","resources","db","migration");
        try(var migrations=Files.list(current))
        {
            for(Path source:migrations.filter(path->path.getFileName().toString().endsWith(".sql"))
                    .filter(path->!V049_FILE.equals(path.getFileName().toString()))
                    .filter(path->!V050_FILE.equals(path.getFileName().toString())).toList())
                Files.copy(source,directory.resolve(source.getFileName()),
                        StandardCopyOption.REPLACE_EXISTING);
        }
        Path published=Path.of("src","test","resources","db","published-309e7904",V049_FILE);
        Files.copy(published,directory.resolve(V049_FILE),StandardCopyOption.REPLACE_EXISTING);
        return directory;
    }

    private static void deleteRecursively(Path root) throws Exception
    {
        if(root==null||!Files.exists(root))return;
        try(var paths=Files.walk(root))
        {
            for(Path path:paths.sorted(Comparator.reverseOrder()).toList())
                Files.deleteIfExists(path);
        }
    }

    private static LegacyScheduleIds insertPre049Schedule(DataSource dataSource) throws Exception
    {
        try(Connection connection=dataSource.getConnection();Statement statement=connection.createStatement())
        {
            statement.executeUpdate("insert into todo_schedule_plan(previous_todo_id,"
                    +"template_version_id,business_type,business_id,timezone,rule_version_id,"
                    +"first_contact_at,status,create_time,update_time,version) values("
                    +"990000001,1,'LEAD',990000002,'Asia/Shanghai',11,"
                    +"'2026-07-25 09:00:00','ACTIVE',sysdate(),sysdate(),0)",
                    Statement.RETURN_GENERATED_KEYS);
            long planId=generated(statement);
            statement.executeUpdate("insert into todo_schedule_window(plan_id,window_code,"
                    +"window_order,day_offset,start_time,end_time,materialize_at,due_at,"
                    +"max_attempts,occurrence_no,status,create_time,update_time,version) values("
                    +planId+",'T1_AM',1,1,'09:00:00','11:00:00','2026-07-26 09:00:00',"
                    +"'2026-07-26 11:00:00',3,1,'MATERIALIZED',sysdate(),sysdate(),0)",
                    Statement.RETURN_GENERATED_KEYS);
            long windowId=generated(statement);
            statement.executeUpdate("insert into todo_schedule_occurrence(plan_id,window_id,"
                    +"window_code,occurrence_no,occurrence_key,due_at,todo_id,status,create_time,"
                    +"update_time,version) values("+planId+","+windowId+",'T1_AM',1,'"
                    +planId+":T1_AM:1','2026-07-26 11:00:00',990000003,'MATERIALIZED',"
                    +"sysdate(),sysdate(),0)",Statement.RETURN_GENERATED_KEYS);
            return new LegacyScheduleIds(planId,windowId,generated(statement));
        }
    }

    private static LegacyScheduleIds insertPublished049Schedule(DataSource dataSource)
            throws Exception
    {
        try(Connection connection=dataSource.getConnection();
            Statement statement=connection.createStatement())
        {
            statement.executeUpdate("insert into todo_schedule_plan(previous_todo_id,"
                    +"template_version_id,business_type,business_id,timezone,rule_version_id,"
                    +"assignment_policy_id,assignment_policy_version,first_contact_at,status,"
                    +"create_time,update_time,version) values("
                    +"991000001,1,'LEAD',991000002,'Asia/Shanghai',11,77,6,"
                    +"'2026-07-25 09:00:00','ACTIVE',sysdate(),sysdate(),0)",
                    Statement.RETURN_GENERATED_KEYS);
            long planId=generated(statement);
            statement.executeUpdate("insert into todo_schedule_window(plan_id,window_code,"
                    +"window_order,day_offset,start_time,end_time,materialize_at,due_at,"
                    +"max_attempts,occurrence_no,status,create_time,update_time,version) values("
                    +planId+",'T1_AM',1,1,'09:00:00','11:00:00','2026-07-26 09:00:00',"
                    +"'2026-07-26 11:00:00',3,1,'MATERIALIZED',sysdate(),sysdate(),0)",
                    Statement.RETURN_GENERATED_KEYS);
            long windowId=generated(statement);
            statement.executeUpdate("insert into todo_schedule_occurrence(plan_id,window_id,"
                    +"window_code,occurrence_no,occurrence_key,due_at,todo_id,status,create_time,"
                    +"update_time,version) values("+planId+","+windowId+",'T1_AM',1,'"
                    +planId+":T1_AM:1','2026-07-26 11:00:00',991000003,'MATERIALIZED',"
                    +"sysdate(),sysdate(),0)",Statement.RETURN_GENERATED_KEYS);
            return new LegacyScheduleIds(planId,windowId,generated(statement));
        }
    }

    private static void assertLegacyCompletion(DataSource dataSource,LegacyScheduleIds ids)
            throws Exception
    {
        try(Connection connection=dataSource.getConnection();
            PreparedStatement query=connection.prepareStatement(
                    "select p.status,p.assignment_policy_id,p.assignment_policy_version,"
                    +"p.assignment_policy_snapshot_source,o.status occurrence_status,o.result_code "
                    +"from todo_schedule_plan p join todo_schedule_occurrence o "
                    +"on o.plan_id=p.plan_id where p.plan_id=? and o.occurrence_id=?"))
        {
            query.setLong(1,ids.planId());query.setLong(2,ids.occurrenceId());
            try(ResultSet row=query.executeQuery())
            {
                if(!row.next())throw new AssertionError("Legacy schedule was not found after upgrade");
                assertEquals("CONTACTED",row.getString("status"));
                assertNull(row.getObject("assignment_policy_id"));
                assertNull(row.getObject("assignment_policy_version"));
                assertEquals(TodoScheduleService.LEGACY_POLICY,
                        row.getString("assignment_policy_snapshot_source"));
                assertEquals("COMPLETED",row.getString("occurrence_status"));
                assertEquals("CONNECTED",row.getString("result_code"));
            }
        }
    }

    private static void assertResolvedCompletion(DataSource dataSource,LegacyScheduleIds ids)
            throws Exception
    {
        try(Connection connection=dataSource.getConnection();
            PreparedStatement query=connection.prepareStatement(
                    "select p.status,p.assignment_policy_id,p.assignment_policy_version,"
                    +"p.assignment_policy_snapshot_source,o.status occurrence_status,o.result_code "
                    +"from todo_schedule_plan p join todo_schedule_occurrence o "
                    +"on o.plan_id=p.plan_id where p.plan_id=? and o.occurrence_id=?"))
        {
            query.setLong(1,ids.planId());query.setLong(2,ids.occurrenceId());
            try(ResultSet row=query.executeQuery())
            {
                if(!row.next())throw new AssertionError(
                        "Published 0.20.49 schedule was not found after upgrade");
                assertEquals("CONTACTED",row.getString("status"));
                assertEquals(77L,row.getLong("assignment_policy_id"));
                assertEquals(6,row.getInt("assignment_policy_version"));
                assertEquals(TodoScheduleService.RESOLVED_POLICY,
                        row.getString("assignment_policy_snapshot_source"));
                assertEquals("COMPLETED",row.getString("occurrence_status"));
                assertEquals("CONNECTED",row.getString("result_code"));
            }
        }
    }

    private static long td003Version(Connection connection) throws Exception
    {
        try(Statement statement=connection.createStatement();ResultSet row=statement.executeQuery(
                "select v.version_id from todo_template t join todo_template_version v "
                +"on v.template_id=t.template_id where t.template_code='TD-003' "
                +"order by v.version_no desc limit 1"))
        {
            if(!row.next())throw new AssertionError("TD-003 version fixture is required");
            return row.getLong(1);
        }
    }

    private static long insertPolicy(Connection connection,String code,long deptId,String source,
            int rowVersion,String retryJson) throws Exception
    {
        try(PreparedStatement insert=connection.prepareStatement(
                "insert into biz_lead_assignment_policy(policy_code,policy_name,sales_dept_id,"
                +"source_code,business_type,retry_rule_json,status,row_version,create_by) "
                +"values(?, ?, ?, ?, 'LEAD', cast(? as json), 'ACTIVE', ?, 'task6')",
                Statement.RETURN_GENERATED_KEYS))
        {
            insert.setString(1,code);insert.setString(2,code);insert.setLong(3,deptId);
            insert.setString(4,source);insert.setString(5,retryJson);insert.setInt(6,rowVersion);
            assertEquals(1,insert.executeUpdate());
            try(ResultSet keys=insert.getGeneratedKeys())
            {
                if(!keys.next())throw new AssertionError("Policy identity was not generated");
                return keys.getLong(1);
            }
        }
    }

    private static String retryPolicyJson(long templateVersionId,long ruleVersionId,int attempts)
    {
        return "{\"templateVersionId\":"+templateVersionId+",\"ruleVersionId\":"+ruleVersionId
                +",\"timezone\":\"Asia/Shanghai\",\"windows\":[{\"windowCode\":\"T0\","
                +"\"windowOrder\":0,\"dayOffset\":0,\"startOffsetMinutes\":0,"
                +"\"durationMinutes\":120,\"maxAttempts\":"+attempts+",\"occurrenceNo\":1}]}";
    }

    private static BizLead policyLead(long deptId,String sourceCode)
    {
        BizLead lead=new BizLead();
        lead.setDeptId(deptId);lead.setSourceCode(sourceCode);
        return lead;
    }

    private static long createPolicyPlan(TodoScheduleService schedules,long templateVersionId,
            LeadAssignmentPolicyService.RetrySchedulePolicy policy,long businessId)
    {
        return schedules.createPlan(new TodoScheduleService.CreateSchedulePlanCommand(
                businessId-100,templateVersionId,"LEAD",businessId,
                LocalDateTime.of(2026,7,25,9,0),policy.timezone(),policy.ruleVersionId(),
                policy.policyId(),policy.policyVersion(),policy.windows()));
    }

    private static void assertPolicySnapshot(Connection connection,long planId,long expectedPolicyId,
            int expectedPolicyVersion,long expectedRuleVersion,int expectedAttempts) throws Exception
    {
        try(PreparedStatement query=connection.prepareStatement(
                "select p.assignment_policy_id,p.assignment_policy_version,p.rule_version_id,"
                +"p.assignment_policy_snapshot_source,w.max_attempts "
                +"from todo_schedule_plan p join todo_schedule_window w "
                +"on w.plan_id=p.plan_id where p.plan_id=?"))
        {
            query.setLong(1,planId);
            try(ResultSet row=query.executeQuery())
            {
                if(!row.next())throw new AssertionError("Persisted policy schedule was not found");
                assertEquals(expectedPolicyId,row.getLong("assignment_policy_id"));
                assertEquals(expectedPolicyVersion,row.getInt("assignment_policy_version"));
                assertEquals(TodoScheduleService.RESOLVED_POLICY,
                        row.getString("assignment_policy_snapshot_source"));
                assertEquals(expectedRuleVersion,row.getLong("rule_version_id"));
                assertEquals(expectedAttempts,row.getInt("max_attempts"));
            }
        }
    }

    private static LeadRetryService retryService(SqlSession session)
    {
        BizLeadMapper leads=session.getMapper(BizLeadMapper.class);
        BusinessActorProvider actors=()->new BusinessActor(1L,"admin","admin",1L,true);
        LeadAccessPolicy access=new LeadAccessPolicy(leads,actors);
        TodoScheduleService schedules=new TodoScheduleService(session.getMapper(TodoMapper.class),
                mock(TodoRoutingService.class));
        return new LeadRetryService(leads,session.getMapper(LeadFlowMapper.class),access,
                mock(LeadCallRecordService.class),actors,mock(ISysDictTypeService.class),schedules,
                mock(LeadPoolService.class),mock(BusinessEventPublisher.class));
    }

    private static LeadRetryCompleteCommand retryCommand(long leadId,long todoId,long occurrenceId)
    {
        LeadRetryCompleteCommand command=new LeadRetryCompleteCommand();
        command.setLeadId(leadId);command.setTodoId(todoId);command.setOccurrenceId(occurrenceId);
        command.setResult("EXHAUSTED");
        return command;
    }

    private static ScheduleIds insertRetryBoundary(DataSource dataSource,long leadA,long leadB,long todoId)
            throws Exception
    {
        try(Connection connection=dataSource.getConnection();Statement statement=connection.createStatement())
        {
            statement.executeUpdate("insert into biz_lead(lead_id,lead_no,lead_name,status,pool_status,"
                    +"disposition,del_flag,owner_id,row_version,first_contact_status,first_contact_result,retry_stage)"
                    +" values("+leadA+",'RB-"+leadA+"','Retry A','2','0','ACTIVE','0',1,0,"
                    +"'COMPLETED','UNREACHABLE','T1_AM'),("+leadB+",'RB-"+leadB
                    +"','Retry B','2','0','ACTIVE','0',1,0,'COMPLETED','UNREACHABLE','T1_AM')");
            statement.executeUpdate("insert into todo_schedule_plan(previous_todo_id,template_version_id,"
                    +"business_type,business_id,timezone,rule_version_id,assignment_policy_id,"
                    +"assignment_policy_version,assignment_policy_snapshot_source,"
                    +"first_contact_at,status,create_time,"
                    +"update_time,version) values("+todoId+",1,'LEAD',"+leadA
                    +",'Asia/Shanghai',11,1,0,'RESOLVED_POLICY','2026-07-25 09:00:00',"
                    +"'ACTIVE',sysdate(),sysdate(),0)",
                    Statement.RETURN_GENERATED_KEYS);
            long planId=generated(statement);
            statement.executeUpdate("insert into todo_schedule_window(plan_id,window_code,window_order,"
                    +"day_offset,start_time,end_time,materialize_at,due_at,max_attempts,occurrence_no,status,"
                    +"create_time,update_time,version) values("+planId+",'T1_AM',1,1,'09:00:00','11:00:00',"
                    +"'2026-07-26 09:00:00','2026-07-26 11:00:00',3,1,'MATERIALIZED',sysdate(),sysdate(),0)",
                    Statement.RETURN_GENERATED_KEYS);
            long windowId=generated(statement);
            statement.executeUpdate("insert into todo_schedule_occurrence(plan_id,window_id,window_code,"
                    +"occurrence_no,occurrence_key,due_at,todo_id,status,create_time,update_time,version) values("
                    +planId+","+windowId+",'T1_AM',1,'"+planId+":T1_AM:1','2026-07-26 11:00:00',"
                    +todoId+",'MATERIALIZED',sysdate(),sysdate(),0)",Statement.RETURN_GENERATED_KEYS);
            return new ScheduleIds(planId,windowId,generated(statement));
        }
    }

    private static long generated(Statement statement) throws Exception
    {
        try(ResultSet keys=statement.getGeneratedKeys()){keys.next();return keys.getLong(1);}
    }

    private static void assertRetryBoundaryState(DataSource dataSource,long leadA,long leadB,
            ScheduleIds ids,int retries) throws Exception
    {
        try(Connection connection=dataSource.getConnection();Statement statement=connection.createStatement())
        {
            assertEquals(0,count(statement,"select sum(row_version) from biz_lead where lead_id in ("
                    +leadA+","+leadB+")"));
            assertEquals("ACTIVE",scalar(statement,"select status from todo_schedule_plan where plan_id="
                    +ids.planId()));
            assertEquals("MATERIALIZED",scalar(statement,
                    "select status from todo_schedule_occurrence where occurrence_id="+ids.occurrenceId()));
            assertEquals(retries,count(statement,"select count(*) from biz_lead_retry_record where plan_id="
                    +ids.planId()));
        }
    }

    private static String scalar(Statement statement,String sql) throws Exception
    {
        try(ResultSet rows=statement.executeQuery(sql)){rows.next();return rows.getString(1);}
    }

    private static void cleanupRetryBoundary(DataSource dataSource,long leadA,long leadB,ScheduleIds ids)
            throws Exception
    {
        try(Connection connection=dataSource.getConnection();Statement statement=connection.createStatement())
        {
            statement.executeUpdate("delete from biz_lead_retry_record where plan_id="+ids.planId());
            statement.executeUpdate("delete from todo_schedule_occurrence where plan_id="+ids.planId());
            statement.executeUpdate("delete from todo_schedule_window where plan_id="+ids.planId());
            statement.executeUpdate("delete from todo_schedule_plan where plan_id="+ids.planId());
            statement.executeUpdate("delete from biz_lead where lead_id in ("+leadA+","+leadB+")");
        }
    }

    private record ScheduleIds(long planId,long windowId,long occurrenceId) { }
    private record LegacyScheduleIds(long planId,long windowId,long occurrenceId) { }

    private static void insertLead(DataSource dataSource,long leadId,String leadNo) throws Exception
    {
        try(Connection connection=dataSource.getConnection();Statement statement=connection.createStatement())
        {
            statement.executeUpdate("insert into biz_lead(lead_id,lead_no,lead_name,status,pool_status,"
                    +"disposition,del_flag,owner_id,row_version) values("+leadId+",'"+leadNo
                    +"','Task 6','1','0','ACTIVE','0',1,0)");
        }
    }

    private static void assertState(DataSource dataSource,long leadId,String eventKey,int calls,int followups,
            int events,int version,String result) throws Exception
    {
        try(Connection connection=dataSource.getConnection();Statement statement=connection.createStatement())
        {
            assertEquals(calls,count(statement,"select count(*) from biz_lead_call_record where lead_id="+leadId));
            assertEquals(followups,count(statement,"select count(*) from biz_lead_followup where lead_id="+leadId));
            assertEquals(events,count(statement,"select count(*) from business_event where idempotency_key='"+eventKey+"'"));
            try(ResultSet row=statement.executeQuery("select row_version,first_contact_result from biz_lead where lead_id="+leadId))
            {
                row.next();assertEquals(version,row.getInt(1));assertEquals(result,row.getString(2));
            }
        }
    }

    private static int count(Statement statement,String sql) throws Exception
    {
        try(ResultSet rows=statement.executeQuery(sql)){rows.next();return rows.getInt(1);}
    }

    private static void cleanup(DataSource dataSource,long leadId,String eventKey) throws Exception
    {
        try(Connection connection=dataSource.getConnection();Statement statement=connection.createStatement())
        {
            statement.executeUpdate("delete from business_event where idempotency_key='"+eventKey+"'");
            statement.executeUpdate("delete from biz_lead_call_record where lead_id="+leadId);
            statement.executeUpdate("delete from biz_lead_followup where lead_id="+leadId);
            statement.executeUpdate("delete from biz_lead where lead_id="+leadId);
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
        if(value==null||value.isBlank())throw new IllegalStateException(name+" is required");
        return value;
    }
}
