package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import com.law.business.security.BusinessActor;
import com.law.business.security.BusinessActorProvider;
import com.law.todo.application.TodoBusinessViewService;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.domain.DefaultTodoAccessPolicy;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.schedule.TodoScheduleService;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.domain.BizLeadAssignmentPolicy;
import com.ruoyi.system.domain.LeadAssignmentPolicyCandidateView;
import com.ruoyi.system.domain.LeadTodoWorkItemView;
import com.ruoyi.system.mapper.BizLeadMapper;
import com.ruoyi.system.mapper.BusinessEventMapper;
import com.ruoyi.system.mapper.LeadFlowMapper;
import com.ruoyi.system.service.event.OutboxBusinessEventPublisher;
import com.ruoyi.system.service.lead.LeadAccessPolicy;
import com.ruoyi.system.service.lead.LeadDeadPoolService;
import com.ruoyi.system.service.lead.LeadPermissionPolicy;
import com.ruoyi.system.service.lead.LeadQueryService;

/** Real MySQL proof for the joined workbench projections and role data scope. */
class LeadTodoReadModelExternalMysqlIT
{
    private static final long SELLER=9_910_101L;
    private static final long SUPERVISOR=9_910_102L;
    private static final long PEER=9_910_103L;
    private static final long SELLER_ROLE=9_910_201L;
    private static final long SUPERVISOR_ROLE=9_910_202L;
    private static final long PEER_ROLE=9_910_203L;
    private static final long ACTIVE_LEAD=9_910_301L;
    private static final long DEAD_LEAD=9_910_302L;
    private static final long SOURCE_TODO=9_910_401L;
    private static final long REVIEW_TODO=9_910_402L;
    private static final long DEAD_SOURCE_TODO=9_910_403L;
    private static final long DEAD_REVIEW_TODO=9_910_404L;
    private static final long RETRY_TODO=9_910_405L;
    private static final long PLAN=9_910_501L;
    private static final long WINDOW=9_910_502L;
    private static final long OCCURRENCE=9_910_503L;

    @Test
    void joinedReadModelsEnforceSalesSupervisorPeerAndDeadPoolScope() throws Exception
    {
        String url=required("TODO_MIGRATION_DB_URL");
        String user=required("TODO_MIGRATION_DB_USER");
        String password=required("TODO_MIGRATION_DB_PASSWORD");
        DataSource dataSource=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",url,user,password);
        cleanup(dataSource);
        try
        {
            insertFixtures(dataSource);
            Configuration configuration=myBatis(dataSource);
            try(SqlSession session=new SqlSessionFactoryBuilder().build(configuration).openSession(true))
            {
                BizLeadMapper mapper=session.getMapper(BizLeadMapper.class);
                LeadFlowMapper flow=session.getMapper(LeadFlowMapper.class);
                BusinessActorProvider supervisorActors=actors(SUPERVISOR,"task9_supervisor",101L);
                LeadAccessPolicy supervisorAccess=new LeadAccessPolicy(mapper,supervisorActors);
                LeadQueryService supervisorQueries=new LeadQueryService(
                        mapper,supervisorActors,supervisorAccess);
                LeadQueryService peerQueries=new LeadQueryService(mapper,
                        actors(PEER,"task9_peer",105L),
                        new LeadAccessPolicy(mapper,actors(PEER,"task9_peer",105L)));

                List<LeadTodoWorkItemView> calls=mapper.selectLeadCallTimeline(ACTIVE_LEAD);
                assertEquals(1,calls.size());
                assertEquals(SOURCE_TODO,calls.get(0).getTodoId());
                assertEquals("首联处理",calls.get(0).getTodoTitle());

                List<LeadTodoWorkItemView> supervisorReviews=mapper.selectLeadInvalidReviewQueue(
                        "PENDING",null,SUPERVISOR,101L,true);
                assertEquals(1,supervisorReviews.size());
                assertEquals(REVIEW_TODO,supervisorReviews.get(0).getTodoId());
                assertEquals("ESCALATED",supervisorReviews.get(0).getSlaStatus());
                assertEquals(true,supervisorReviews.get(0).getOverdue());
                assertEquals(true,supervisorReviews.get(0).getEscalated());
                assertNotNull(supervisorReviews.get(0).getEscalatedAt());

                assertEquals(0,mapper.selectLeadInvalidReviewQueue(
                        "PENDING",null,PEER,105L,true).size());
                assertEquals(1,supervisorQueries.invalidReviewQueue("COMPLETED",null).size());
                assertEquals(0,peerQueries.invalidReviewQueue("COMPLETED",null).size());

                List<LeadTodoWorkItemView> sellerRetries=mapper.selectLeadRetryQueue(
                        null,null,SELLER,104L,true);
                assertEquals(1,sellerRetries.size());
                assertEquals(OCCURRENCE,sellerRetries.get(0).getOccurrenceId());
                assertEquals(RETRY_TODO,sellerRetries.get(0).getTodoId());
                assertEquals(0,mapper.selectLeadRetryQueue(null,null,PEER,105L,true).size());

                List<LeadTodoWorkItemView> timeline=mapper.selectLeadRetryTimeline(ACTIVE_LEAD);
                assertEquals(1,timeline.size());
                assertEquals("T0",timeline.get(0).getWindowCode());

                List<LeadTodoWorkItemView> deadPool=supervisorQueries.deadPoolQueue(
                        "NO_DEMAND",null);
                assertEquals(1,deadPool.size());
                assertEquals(DEAD_LEAD,deadPool.get(0).getLeadId());
                assertEquals(DEAD_REVIEW_TODO,deadPool.get(0).getTodoId());
                assertEquals(DEAD_LEAD,supervisorQueries.detail(DEAD_LEAD).getLeadId());
                assertEquals(1,supervisorQueries.callTimeline(DEAD_LEAD).size());
                assertThrows(ServiceException.class,()->peerQueries.detail(DEAD_LEAD));
                assertThrows(ServiceException.class,()->peerQueries.callTimeline(DEAD_LEAD));
                assertEquals(1,mapper.countDeadPoolInDataScope(DEAD_LEAD,SUPERVISOR,101L));
                assertEquals(0,mapper.countDeadPoolInDataScope(DEAD_LEAD,PEER,105L));

                List<BizLeadAssignmentPolicy> policies=flow.selectAssignmentPolicies(
                        SUPERVISOR,101L,true);
                assertEquals(1,policies.size());
                assertEquals(9911001L,policies.get(0).getPolicyId());
                List<LeadAssignmentPolicyCandidateView> candidates=
                        flow.selectAssignmentPolicyCandidateViews(List.of(9911001L));
                assertEquals(List.of(SELLER),candidates.stream()
                        .map(LeadAssignmentPolicyCandidateView::getUserId).toList());
                assertEquals(List.of(SELLER),
                        flow.selectActiveCandidateUsersInDepartment(104L,List.of(SELLER,PEER)));

                LeadDeadPoolService restores=restoreService(session,supervisorActors);
                LeadDeadPoolService.DeadPoolOutcome first=restores.restoreToPublicPool(
                        DEAD_LEAD,"task9-read-model-restore"," Task9   restore ");
                LeadDeadPoolService.DeadPoolOutcome replay=restores.restoreToPublicPool(
                        DEAD_LEAD,"task9-read-model-restore","Task9 restore");
                assertEquals(false,first.replayed());
                assertEquals(true,replay.replayed());
                assertEquals(first.deadPoolLogId(),replay.deadPoolLogId());
                BizLead restored=mapper.selectLeadById(DEAD_LEAD);
                assertEquals("PUBLIC_POOL",restored.getDisposition());
                assertEquals(3,restored.getRowVersion());
            }
        }
        finally
        {
            cleanup(dataSource);
        }
    }

    @Test
    void concurrentRestoreSerializesOnOriginThenReturnsCanonicalReplay() throws Exception
    {
        String url=required("TODO_MIGRATION_DB_URL");
        String user=required("TODO_MIGRATION_DB_USER");
        String password=required("TODO_MIGRATION_DB_PASSWORD");
        DataSource dataSource=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",url,user,password);
        cleanup(dataSource);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try
        {
            insertFixtures(dataSource);
            SqlSessionFactory factory=new SqlSessionFactoryBuilder().build(myBatis(dataSource));
            CountDownLatch ready=new CountDownLatch(2);
            CountDownLatch start=new CountDownLatch(1);
            java.util.concurrent.Callable<LeadDeadPoolService.DeadPoolOutcome> command=()->{
                try(SqlSession session=factory.openSession(false))
                {
                    BusinessActorProvider actor=actors(SUPERVISOR,"task9_supervisor",101L);
                    LeadDeadPoolService service=restoreService(session,actor);
                    ready.countDown();
                    start.await();
                    LeadDeadPoolService.DeadPoolOutcome outcome=service.restoreToPublicPool(
                            DEAD_LEAD,"task9-concurrent-restore","canonical reason");
                    session.commit();
                    return outcome;
                }
            };
            Future<LeadDeadPoolService.DeadPoolOutcome> left=pool.submit(command);
            Future<LeadDeadPoolService.DeadPoolOutcome> right=pool.submit(command);
            ready.await();
            start.countDown();
            LeadDeadPoolService.DeadPoolOutcome first=left.get();
            LeadDeadPoolService.DeadPoolOutcome second=right.get();

            assertEquals(first.deadPoolLogId(),second.deadPoolLogId());
            assertEquals(1,List.of(first,second).stream().filter(value->!value.replayed()).count());
            assertEquals(1,List.of(first,second).stream().filter(
                    LeadDeadPoolService.DeadPoolOutcome::replayed).count());
            assertEquals(1,count(dataSource,"select count(*) from biz_lead_dead_pool_log where lead_id="
                    +DEAD_LEAD+" and action_type='RESTORE'"));
            assertEquals(1,count(dataSource,"select count(*) from business_event where aggregate_type='LEAD'"
                    +" and aggregate_id="+DEAD_LEAD+" and event_type='LEAD_MOVED_TO_POOL'"));
            assertEquals(3,count(dataSource,"select row_version from biz_lead where lead_id="+DEAD_LEAD));

            try(SqlSession session=factory.openSession(true))
            {
                LeadDeadPoolService sameActor=restoreService(session,
                        actors(SUPERVISOR,"task9_supervisor",101L));
                assertThrows(ServiceException.class,()->sameActor.restoreToPublicPool(
                        DEAD_LEAD,"task9-concurrent-restore","different reason"));
                LeadDeadPoolService peer=restoreService(session,actors(PEER,"task9_peer",105L));
                ServiceException denied=assertThrows(ServiceException.class,
                        ()->peer.restoreToPublicPool(DEAD_LEAD,
                                "task9-concurrent-restore","canonical reason"));
                assertEquals("ACCESS_DENIED",denied.getBusinessCode());
            }
        }
        finally
        {
            pool.shutdownNow();
            cleanup(dataSource);
        }
    }

    @Test
    void manyAllowedActionsUseExactlyOneRealMysqlBulkQuery() throws Exception
    {
        String url=required("TODO_MIGRATION_DB_URL");
        String user=required("TODO_MIGRATION_DB_USER");
        String password=required("TODO_MIGRATION_DB_PASSWORD");
        DataSource dataSource=new UnpooledDataSource(
                "com.mysql.cj.jdbc.Driver",url,user,password);
        cleanup(dataSource);
        try
        {
            insertFixtures(dataSource);
            Configuration configuration=myBatis(dataSource);
            TodoQueryCounter counter=new TodoQueryCounter();
            configuration.addInterceptor(counter);
            try(SqlSession session=new SqlSessionFactoryBuilder()
                    .build(configuration).openSession(true))
            {
                TodoMapper mapper=session.getMapper(TodoMapper.class);
                TodoBusinessViewService views=new TodoBusinessViewService(
                        mapper,List.of(),new DefaultTodoAccessPolicy(mapper));
                TodoInstance claim=actionTodo(SOURCE_TODO,"CREATED",null);
                TodoInstance owned=actionTodo(RETRY_TODO,"SUBMITTED",SELLER);
                TodoInstance terminal=actionTodo(
                        DEAD_REVIEW_TODO,"COMPLETED",SUPERVISOR);

                var actions=views.allowedActions(
                        List.of(claim,owned,terminal),
                        new Actor(SELLER,"task9_seller",104L));

                assertEquals(List.of("claim"),actions.get(SOURCE_TODO));
                assertEquals(List.of("complete","transfer","cancel"),
                        actions.get(RETRY_TODO));
                assertEquals(List.of(),actions.get(DEAD_REVIEW_TODO));
                assertEquals(1,counter.todoQueryCount());
                assertEquals(Set.of(
                        "com.law.todo.mapper.TodoMapper.selectAllowedActionFacts"),
                        counter.statementIds());
            }
        }
        finally
        {
            cleanup(dataSource);
        }
    }

    private static Configuration myBatis(DataSource dataSource) throws Exception
    {
        Configuration configuration=new Configuration(new Environment(
                "lead-todo-read-model",new JdbcTransactionFactory(),dataSource));
        configuration.getTypeAliasRegistry().registerAliases("com.ruoyi.system.domain");
        configuration.addMapper(BizLeadMapper.class);
        configuration.addMapper(LeadFlowMapper.class);
        configuration.addMapper(BusinessEventMapper.class);
        configuration.addMapper(TodoMapper.class);
        for(String resource:List.of("mapper/system/BizLeadMapper.xml",
                "mapper/system/LeadFlowMapper.xml","mapper/system/BusinessEventMapper.xml",
                "mapper/todo/TodoMapper.xml"))
        {
            try(InputStream input=Resources.getResourceAsStream(resource))
            {
                new XMLMapperBuilder(input,configuration,resource,
                        configuration.getSqlFragments()).parse();
            }
        }
        return configuration;
    }

    private static LeadDeadPoolService restoreService(SqlSession session,
            BusinessActorProvider actors)
    {
        BizLeadMapper leads=session.getMapper(BizLeadMapper.class);
        LeadFlowMapper flow=session.getMapper(LeadFlowMapper.class);
        LeadAccessPolicy access=new LeadAccessPolicy(leads,actors);
        return new LeadDeadPoolService(leads,flow,access,actors,
                org.mockito.Mockito.mock(TodoScheduleService.class),
                new OutboxBusinessEventPublisher(session.getMapper(BusinessEventMapper.class)),
                new AllowAllLeadPermissionPolicy());
    }

    private static BusinessActorProvider actors(long userId,String userName,long deptId)
    {
        BusinessActor actor=new BusinessActor(userId,userName,userName,deptId,false);
        return ()->actor;
    }

    private static TodoInstance actionTodo(long todoId,String status,Long ownerId)
    {
        TodoInstance todo=new TodoInstance();
        todo.setTodoId(todoId);
        todo.setStatus(status);
        todo.setOwnerId(ownerId);
        return todo;
    }

    private static int count(DataSource dataSource,String query) throws Exception
    {
        try(Connection connection=dataSource.getConnection();
                Statement sql=connection.createStatement();
                ResultSet result=sql.executeQuery(query))
        {
            result.next();
            return result.getInt(1);
        }
    }

    private static final class AllowAllLeadPermissionPolicy extends LeadPermissionPolicy
    {
        @Override public void require(String permission) { }
    }

    private static void insertFixtures(DataSource dataSource) throws Exception
    {
        try(Connection connection=dataSource.getConnection();Statement sql=connection.createStatement())
        {
            sql.executeUpdate("insert into sys_user(user_id,dept_id,user_name,nick_name,status,del_flag) values "
                    +"("+SELLER+",104,'task9_seller','Task9销售','0','0'),"
                    +"("+SUPERVISOR+",101,'task9_supervisor','Task9主管','0','0'),"
                    +"("+PEER+",105,'task9_peer','Task9同级','0','0')");
            sql.executeUpdate("insert into sys_role(role_id,role_name,role_key,role_sort,data_scope,status,del_flag) values "
                    +"("+SELLER_ROLE+",'Task9销售','task9_seller',1,'5','0','0'),"
                    +"("+SUPERVISOR_ROLE+",'Task9主管','task9_supervisor',2,'4','0','0'),"
                    +"("+PEER_ROLE+",'Task9同级','task9_peer',3,'5','0','0')");
            sql.executeUpdate("insert into sys_user_role(user_id,role_id) values "
                    +"("+SELLER+","+SELLER_ROLE+"),("+SUPERVISOR+","+SUPERVISOR_ROLE+"),"
                    +"("+PEER+","+PEER_ROLE+")");
            sql.executeUpdate("insert into biz_lead(lead_id,lead_no,lead_name,mobile,source_code,status,"
                    +"pool_status,disposition,owner_id,dept_id,invalid_review_status,first_contact_result,"
                    +"retry_stage,del_flag,row_version) values "
                    +"("+ACTIVE_LEAD+",'TASK9-A','Task9有效线索','13800000001','WEB','2','0','ACTIVE',"
                    +SELLER+",104,'PENDING','UNREACHABLE','T0','0',0),"
                    +"("+DEAD_LEAD+",'TASK9-D','Task9Dead线索','13800000002','WEB','4','0','DEAD_POOL',"
                    +"null,null,'CONFIRMED','SUSPECT_INVALID',null,'0',2)");
            sql.executeUpdate(todo(SOURCE_TODO,"TASK9-T1","TD-001","首联处理",ACTIVE_LEAD,SELLER,104,
                    "COMPLETED","NORMAL","date_add(sysdate(),interval 1 hour)",null));
            sql.executeUpdate(todo(REVIEW_TODO,"TASK9-T2","TD-002","疑似无效复核",ACTIVE_LEAD,SUPERVISOR,101,
                    "IN_PROGRESS","ESCALATED","date_sub(sysdate(),interval 1 hour)",SOURCE_TODO));
            sql.executeUpdate(todo(DEAD_SOURCE_TODO,"TASK9-T3","TD-001","Dead来源首联",DEAD_LEAD,SELLER,104,
                    "COMPLETED","NORMAL","date_sub(sysdate(),interval 2 day)",null));
            sql.executeUpdate(todo(DEAD_REVIEW_TODO,"TASK9-T4","TD-002","Dead复核",DEAD_LEAD,SUPERVISOR,101,
                    "COMPLETED","OVERDUE","date_sub(sysdate(),interval 1 day)",DEAD_SOURCE_TODO));
            sql.executeUpdate(todo(RETRY_TODO,"TASK9-T5","TD-003","T0重试",ACTIVE_LEAD,SELLER,104,
                    "IN_PROGRESS","NORMAL","date_add(sysdate(),interval 2 hour)",SOURCE_TODO));
            sql.executeUpdate("insert into todo_candidate(todo_id,candidate_type,"
                    +"candidate_value) values ("+SOURCE_TODO+",'USER',"+SELLER+")");
            sql.executeUpdate("insert into todo_sla_record(sla_record_id,todo_id,start_at,due_at,"
                    +"overdue100_at,escalate150_at,status,version) values "
                    +"(9910601,"+REVIEW_TODO+",date_sub(sysdate(),interval 2 hour),"
                    +"date_sub(sysdate(),interval 1 hour),date_sub(sysdate(),interval 1 hour),"
                    +"sysdate(),'RUNNING',0)");
            sql.executeUpdate("insert into biz_lead_call_record(call_record_id,lead_id,todo_id,call_channel,"
                    +"started_at,duration_seconds,call_result,idempotency_key) values "
                    +"(9910701,"+ACTIVE_LEAD+","+SOURCE_TODO+",'MANUAL',sysdate(),30,'NO_ANSWER','TASK9-CALL'),"
                    +"(9910702,"+DEAD_LEAD+","+DEAD_SOURCE_TODO+",'MANUAL',sysdate(),45,'INVALID','TASK9-DEAD-CALL')");
            sql.executeUpdate("insert into biz_lead_invalid_review(review_id,lead_id,reason_code,"
                    +"submitted_by,submitted_at,reviewer_id,todo_id,status,idempotency_key,row_version) values "
                    +"(9910801,"+ACTIVE_LEAD+",'NO_DEMAND',"+SELLER+",sysdate(),"+SUPERVISOR+","
                    +SOURCE_TODO+",'PENDING','TASK9-REVIEW-A',0),"
                    +"(9910802,"+DEAD_LEAD+",'NO_DEMAND',"+SELLER+",sysdate(),"+SUPERVISOR+","
                    +DEAD_SOURCE_TODO+",'COMPLETED','TASK9-REVIEW-D',1)");
            sql.executeUpdate("update biz_lead_invalid_review set review_result='TRUE_INVALID',"
                    +"reviewed_at=sysdate() where review_id=9910802");
            sql.executeUpdate("insert into biz_lead_dead_pool_log(dead_pool_log_id,lead_id,action_type,"
                    +"reason_code,from_disposition,to_disposition,operator_id,action_time,idempotency_key) values "
                    +"(9910901,"+DEAD_LEAD+",'ENTER','NO_DEMAND','ACTIVE','DEAD_POOL',"
                    +SUPERVISOR+",sysdate(),'TASK9-DEAD')");
            sql.executeUpdate("insert into biz_lead_assignment_policy(policy_id,policy_code,policy_name,"
                    +"sales_dept_id,source_code,business_type,retry_rule_json,status,row_version) values "
                    +"(9911001,'TASK9-POLICY','Task9策略',104,'WEB','LEAD',json_object('test',true),'ACTIVE',0)");
            sql.executeUpdate("insert into biz_lead_assignment_policy_candidate("
                    +"candidate_id,policy_id,user_id,sort_order,status) values "
                    +"(9911002,9911001,"+SELLER+",0,'ACTIVE')");
            sql.executeUpdate("insert into todo_schedule_plan(plan_id,previous_todo_id,template_version_id,"
                    +"business_type,business_id,schedule_purpose,idempotency_key,timezone,rule_version_id,assignment_policy_id,"
                    +"assignment_policy_version,assignment_policy_snapshot_source,first_contact_at,"
                    +"current_window_code,status,create_time,update_time,version) values "
                    +"("+PLAN+","+SOURCE_TODO+",(select v.version_id from todo_template t "
                    +"join todo_template_version v on v.template_id=t.template_id "
                    +"and v.version_no=t.current_version where t.template_code='TD-003'),"
                    +"'LEAD',"+ACTIVE_LEAD+",'LEAD_RETRY','TASK9-PLAN-"+PLAN+"','Asia/Shanghai',501,9911001,0,'RESOLVED_POLICY',"
                    +"sysdate(),'T0','ACTIVE',sysdate(),sysdate(),0)");
            sql.executeUpdate("insert into todo_schedule_window(window_id,plan_id,window_code,window_order,"
                    +"day_offset,start_time,end_time,materialize_at,due_at,max_attempts,occurrence_no,status,"
                    +"create_time,update_time,version) values "
                    +"("+WINDOW+","+PLAN+",'T0',0,0,'09:00:00','11:00:00',sysdate(),"
                    +"date_add(sysdate(),interval 2 hour),3,1,'MATERIALIZED',sysdate(),sysdate(),0)");
            sql.executeUpdate("insert into todo_schedule_occurrence(occurrence_id,plan_id,window_id,"
                    +"window_code,occurrence_no,occurrence_key,due_at,todo_id,status,create_time,update_time,version) values "
                    +"("+OCCURRENCE+","+PLAN+","+WINDOW+",'T0',1,'TASK9-OCC',"
                    +"date_add(sysdate(),interval 2 hour),"+RETRY_TODO+",'MATERIALIZED',sysdate(),sysdate(),0)");
        }
    }

    private static String todo(long id,String no,String template,String title,long lead,long owner,
            long dept,String status,String sla,String due,Long previous)
    {
        return "insert into todo_instance(todo_id,todo_no,template_id,template_version_id,template_code,"
                +"title,business_type,business_id,owner_id,owner_dept_id,status,sla_status,due_at,"
                +"previous_todo_id,version) select "+id+",'"+no+"',t.template_id,v.version_id,'"
                +template+"','"+title+"','LEAD',"+lead+","+owner+","+dept+",'"+status+"','"
                +sla+"',"+due+","+(previous==null?"null":previous)+",0 from todo_template t "
                +"join todo_template_version v on v.template_id=t.template_id and v.version_no=t.current_version "
                +"where t.template_code='"+template+"'";
    }

    private static void cleanup(DataSource dataSource) throws Exception
    {
        try(Connection connection=dataSource.getConnection();Statement sql=connection.createStatement())
        {
            sql.executeUpdate("delete from business_event where aggregate_type='LEAD' and aggregate_id in ("
                    +ACTIVE_LEAD+","+DEAD_LEAD+")");
            sql.executeUpdate("delete from todo_schedule_occurrence where occurrence_id="+OCCURRENCE);
            sql.executeUpdate("delete from todo_schedule_window where window_id="+WINDOW);
            sql.executeUpdate("delete from todo_schedule_plan where plan_id="+PLAN);
            sql.executeUpdate("delete from biz_lead_assignment_policy_candidate where policy_id=9911001");
            sql.executeUpdate("delete from biz_lead_assignment_policy where policy_id=9911001");
            sql.executeUpdate("delete from biz_lead_dead_pool_log where lead_id in ("+ACTIVE_LEAD+","+DEAD_LEAD+")");
            sql.executeUpdate("delete from biz_lead_retry_record where lead_id in ("+ACTIVE_LEAD+","+DEAD_LEAD+")");
            sql.executeUpdate("delete from biz_lead_invalid_review where lead_id in ("+ACTIVE_LEAD+","+DEAD_LEAD+")");
            sql.executeUpdate("delete from biz_lead_call_record where lead_id in ("+ACTIVE_LEAD+","+DEAD_LEAD+")");
            sql.executeUpdate("delete from todo_sla_record where todo_id in ("+SOURCE_TODO+","+REVIEW_TODO+","
                    +DEAD_SOURCE_TODO+","+DEAD_REVIEW_TODO+","+RETRY_TODO+")");
            sql.executeUpdate("delete from todo_candidate where todo_id in ("+SOURCE_TODO+","+REVIEW_TODO+","
                    +DEAD_SOURCE_TODO+","+DEAD_REVIEW_TODO+","+RETRY_TODO+")");
            sql.executeUpdate("delete from todo_instance where todo_id in ("+SOURCE_TODO+","+REVIEW_TODO+","
                    +DEAD_SOURCE_TODO+","+DEAD_REVIEW_TODO+","+RETRY_TODO+")");
            sql.executeUpdate("delete from biz_lead where lead_id in ("+ACTIVE_LEAD+","+DEAD_LEAD+")");
            sql.executeUpdate("delete from sys_user_role where user_id in ("+SELLER+","+SUPERVISOR+","+PEER+")");
            sql.executeUpdate("delete from sys_role where role_id in ("+SELLER_ROLE+","+SUPERVISOR_ROLE+","
                    +PEER_ROLE+")");
            sql.executeUpdate("delete from sys_user where user_id in ("+SELLER+","+SUPERVISOR+","+PEER+")");
        }
    }

    private static String required(String name)
    {
        String value=System.getenv(name);
        if(value==null||value.isBlank())throw new IllegalStateException(name+" is required");
        return value;
    }

    @Intercepts(@Signature(type=Executor.class,method="query",
            args={MappedStatement.class,Object.class,RowBounds.class,
                    ResultHandler.class}))
    private static final class TodoQueryCounter implements Interceptor
    {
        private final java.util.LinkedHashSet<String> statementIds=
                new java.util.LinkedHashSet<>();
        private int todoQueryCount;

        @Override
        public Object intercept(Invocation invocation) throws Throwable
        {
            MappedStatement statement=(MappedStatement)invocation.getArgs()[0];
            if(statement.getId().startsWith("com.law.todo.mapper.TodoMapper."))
            {
                todoQueryCount++;
                statementIds.add(statement.getId());
            }
            return invocation.proceed();
        }

        int todoQueryCount() { return todoQueryCount; }
        Set<String> statementIds() { return Set.copyOf(statementIds); }
    }
}
