package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
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
                +"w.max_attempts from todo_schedule_plan p join todo_schedule_window w "
                +"on w.plan_id=p.plan_id where p.plan_id=?"))
        {
            query.setLong(1,planId);
            try(ResultSet row=query.executeQuery())
            {
                if(!row.next())throw new AssertionError("Persisted policy schedule was not found");
                assertEquals(expectedPolicyId,row.getLong("assignment_policy_id"));
                assertEquals(expectedPolicyVersion,row.getInt("assignment_policy_version"));
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
                    +"assignment_policy_version,first_contact_at,status,create_time,"
                    +"update_time,version) values("+todoId+",1,'LEAD',"+leadA
                    +",'Asia/Shanghai',11,1,0,'2026-07-25 09:00:00','ACTIVE',sysdate(),sysdate(),0)",
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

    private static String requiredEnvironment(String name)
    {
        String value=System.getenv(name);
        if(value==null||value.isBlank())throw new IllegalStateException(name+" is required");
        return value;
    }
}
