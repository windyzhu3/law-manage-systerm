package com.ruoyi.web.migration;

import static com.ruoyi.web.migration.LeadTodoProductionPortsExternalMysqlIT.*;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;

import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.law.business.event.BusinessEventCommand;
import com.law.business.event.BusinessEventType;
import com.law.business.security.BusinessActor;
import com.law.business.security.BusinessActorProvider;
import com.law.business.security.LeadPermissions;
import com.law.todo.application.command.TodoActionCommands.ActionCommand;
import com.law.todo.application.TodoAutoActionService;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.spi.TodoAutoActionCapability;
import com.law.todo.spi.TodoCompletionLifecyclePort;
import com.ruoyi.system.mapper.BizLeadMapper;
import com.ruoyi.system.mapper.BusinessEventMapper;
import com.ruoyi.system.mapper.SysUserMapper;
import com.ruoyi.system.security.SecurityBusinessActorProvider;
import com.ruoyi.system.service.event.BusinessEventOutboxProcessor;
import com.ruoyi.system.service.event.OutboxBusinessEventPublisher;
import com.ruoyi.system.service.event.TodoBusinessEventAdapter;
import com.ruoyi.system.service.lead.LeadAccessPolicy;
import com.ruoyi.system.service.lead.LeadAssignmentService;

/**
 * Real MySQL transaction proof for the Lead -> Outbox -> Todo graph.
 * Fixtures and all effects are rolled back after each scenario.
 */
class LeadTodoFlowEndToEndTest extends LeadTodoProductionPortsExternalMysqlIT
{
    @Override
    @Test
    void migratedLeadGraphExecutesThroughProductionPortsWithoutOrphansOrDuplicates() throws Exception
    {
        // The normal Maven suite has no external database. The dedicated Task 11
        // gate supplies one and asserts that this class reports zero skipped tests.
        MigrationTestDatabase.migrate();
        super.migratedLeadGraphExecutesThroughProductionPortsWithoutOrphansOrDuplicates();
    }

    @Test
    void assignedOutboxCreatesOwnedTd001AndValidCompletionCreatesOneTd004() throws Exception
    {
        MigrationTestDatabase.migrate();
        try(SqlSession session=sessions().openSession(false))
        {
            Connection db=session.getConnection();
            try
            {
                PublishedIds published=discoverPublishedIds(db);
                Fixtures fixture=insertFixtures(db,published);
                Ports ports=productionPorts(session);
                authenticate(fixture.ownerId(),fixture.deptId(),fixture.ownerName(),Set.of());

                BusinessActorProvider actors=new SecurityBusinessActorProvider();
                BusinessEventMapper events=session.getMapper(BusinessEventMapper.class);
                LeadAssignmentService assignments=new LeadAssignmentService(
                        session.getMapper(BizLeadMapper.class),
                        new LeadAccessPolicy(session.getMapper(BizLeadMapper.class),actors),
                        actors,new OutboxBusinessEventPublisher(events),
                        session.getMapper(SysUserMapper.class));
                assertEquals(1,assignments.assign(fixture.validLeadId(),fixture.ownerId(),
                        "Task 11 golden assignment"));
                assertEquals(1,count(db,"select count(*) from business_event where aggregate_id=? "
                        +"and event_type='LEAD_ASSIGNED' and event_status='PENDING'",
                        fixture.validLeadId()));

                BusinessEventOutboxProcessor outbox=new BusinessEventOutboxProcessor(events,
                        List.of(new TodoBusinessEventAdapter(ports.events())));
                assertEquals(1,outbox.processBatch(200));
                TodoRow td001=onlyTodo(db,fixture.validLeadId(),published.td001());
                assertEquals(fixture.ownerId(),td001.ownerId());
                assertEquals(1,count(db,"select count(*) from todo_sla_record where todo_id=? "
                        +"and status='RUNNING' and due_at is not null",td001.todoId()));

                prepare(ports.commands(),td001.todoId(),
                        actor(fixture.ownerId(),fixture.ownerName(),fixture.deptId()),"golden");
                ports.commands().complete(td001.todoId(),
                        firstContact("VALID","golden",fixture.validProofId(),true),
                        actor(fixture.ownerId(),fixture.ownerName(),fixture.deptId()));
                outbox.processBatch(200);

                assertEquals("VALID",textScalar(db,
                        "select first_contact_result from biz_lead where lead_id=?",
                        fixture.validLeadId()));
                assertEquals(1,count(db,"select count(*) from biz_lead_call_record where lead_id=? "
                        +"and todo_id=?",fixture.validLeadId(),td001.todoId()));
                assertEquals(1,count(db,"select count(*) from todo_instance where previous_todo_id=? "
                        +"and template_version_id=?",td001.todoId(),published.td004()));
                assertEquals(1,count(db,"select count(*) from todo_action_log where todo_id=? "
                        +"and action_type='COMPLETE'",td001.todoId()));

                assertEquals(0,outbox.processBatch(200));
                assertEquals(1,count(db,"select count(*) from todo_instance where business_type='LEAD' "
                        +"and business_id=? and template_version_id=?",fixture.validLeadId(),
                        published.td004()));
            }
            finally
            {
                SecurityContextHolder.clearContext();
                session.rollback();
            }
        }
    }

    @Test
    void suspectInvalidManualConfirmationMovesLeadToDeadPoolWithAudit() throws Exception
    {
        MigrationTestDatabase.migrate();
        try(SqlSession session=sessions().openSession(false))
        {
            Connection db=session.getConnection();
            try
            {
                PublishedIds published=discoverPublishedIds(db);
                Fixtures fixture=insertFixtures(db,published);
                Ports ports=productionPorts(session);
                TodoInstance td001=trigger(ports.events(),published,fixture.suspectLeadId(),
                        fixture.suspectLeadNo(),fixture.ownerId(),fixture.deptId(),"true-invalid");

                authenticate(fixture.ownerId(),fixture.deptId(),fixture.ownerName(),Set.of());
                prepare(ports.commands(),td001.getTodoId(),
                        actor(fixture.ownerId(),fixture.ownerName(),fixture.deptId()),"invalid-root");
                ports.commands().complete(td001.getTodoId(),
                        firstContact("SUSPECT_INVALID","invalid-root",fixture.suspectProofId(),false),
                        actor(fixture.ownerId(),fixture.ownerName(),fixture.deptId()));
                TodoRow td002=onlyChild(db,td001.getTodoId(),published.td002());

                authenticate(fixture.reviewerId(),fixture.deptId(),fixture.reviewerName(),
                        Set.of(LeadPermissions.INVALID_REVIEW_HANDLE));
                prepare(ports.commands(),td002.todoId(),
                        actor(fixture.reviewerId(),fixture.reviewerName(),fixture.deptId()),"invalid-review");
                ports.commands().complete(td002.todoId(),new ActionCommand(action("invalid-review"),
                        "confirmed invalid",
                        Map.of("reviewResult","TRUE_INVALID",
                                "reviewOpinion","Real MySQL supervisor confirmation"),
                        List.of()),actor(fixture.reviewerId(),fixture.reviewerName(),fixture.deptId()));

                assertEquals("DEAD_POOL",textScalar(db,
                        "select disposition from biz_lead where lead_id=?",fixture.suspectLeadId()));
                assertEquals(1,count(db,"select count(*) from biz_lead_dead_pool_log where lead_id=? "
                        +"and source_todo_id=?",fixture.suspectLeadId(),td001.getTodoId()));
                assertEquals(1,count(db,"select count(*) from todo_action_log where todo_id=? "
                        +"and action_type='COMPLETE'",td002.todoId()));
                assertEquals(0,count(db,"select count(*) from todo_instance where previous_todo_id=?",
                        td002.todoId()));
            }
            finally
            {
                SecurityContextHolder.clearContext();
                session.rollback();
            }
        }
    }

    @Test
    void duplicateEventAndCompletionActionReplayAreIdempotentAndConflictIsRejected()
            throws Exception
    {
        MigrationTestDatabase.migrate();
        try(SqlSession session=sessions().openSession(false))
        {
            Connection db=session.getConnection();
            try
            {
                PublishedIds published=discoverPublishedIds(db);
                Fixtures fixture=insertFixtures(db,published);
                Ports ports=productionPorts(session);
                BusinessEventMapper mapper=session.getMapper(BusinessEventMapper.class);
                OutboxBusinessEventPublisher publisher=new OutboxBusinessEventPublisher(mapper);
                BusinessEventCommand event=new BusinessEventCommand(BusinessEventType.LEAD_ASSIGNED,
                        "LEAD",fixture.validLeadId(),fixture.validLeadNo(),
                        "TASK11:DUPLICATE:"+fixture.validLeadId(),
                        Map.of("schemaVersion",1,"assignmentId",fixture.validLeadId(),
                                "ownerId",fixture.ownerId(),"ownerDeptId",fixture.deptId(),
                                "operatorId",fixture.ownerId()));
                BusinessActor system=new BusinessActor(fixture.ownerId(),fixture.ownerName(),
                        fixture.ownerName(),fixture.deptId(),false);
                publisher.publish(event,system);
                assertThrows(org.apache.ibatis.exceptions.PersistenceException.class,
                        ()->publisher.publish(event,system));
                BusinessEventOutboxProcessor outbox=new BusinessEventOutboxProcessor(mapper,
                        List.of(new TodoBusinessEventAdapter(ports.events())));
                outbox.processBatch(200);
                TodoRow td001=onlyTodo(db,fixture.validLeadId(),published.td001());
                assertEquals(1,count(db,"select count(*) from todo_instance where business_type='LEAD' "
                        +"and business_id=? and template_version_id=?",fixture.validLeadId(),
                        published.td001()));
                assertEquals(1,count(db,"select count(*) from business_event where aggregate_id=? "
                        +"and event_type='LEAD_ASSIGNED' and event_status='PROCESSED'",
                        fixture.validLeadId()));

                authenticate(fixture.ownerId(),fixture.deptId(),fixture.ownerName(),Set.of());
                prepare(ports.commands(),td001.todoId(),
                        actor(fixture.ownerId(),fixture.ownerName(),fixture.deptId()),"replay");
                ActionCommand completion=firstContact("VALID","stable-completion",
                        fixture.validProofId(),true);
                TodoInstance first=ports.commands().complete(td001.todoId(),completion,
                        actor(fixture.ownerId(),fixture.ownerName(),fixture.deptId()));
                assertEquals("COMPLETED",first.getStatus());
                CompletionCounts afterFirst=completionCounts(db,fixture.validLeadId(),
                        td001.todoId(),published.td004());

                TodoInstance replay=ports.commands().complete(td001.todoId(),completion,
                        actor(fixture.ownerId(),fixture.ownerName(),fixture.deptId()));
                assertEquals(first.getTodoId(),replay.getTodoId());
                assertEquals("COMPLETED",replay.getStatus());
                assertEquals(afterFirst,completionCounts(db,fixture.validLeadId(),
                        td001.todoId(),published.td004()));
                assertEquals(1,afterFirst.completeActions());
                assertEquals(1,afterFirst.followups());
                assertEquals(1,afterFirst.callFacts());
                assertEquals(1,afterFirst.branchEvents());
                assertEquals(1,afterFirst.nextTodos());

                Map<String,Object> conflictingFields=new LinkedHashMap<>(completion.fields());
                conflictingFields.put("city","conflicting-city");
                ActionCommand conflict=new ActionCommand(completion.actionId(),completion.opinion(),
                        conflictingFields,completion.fileObjectIds());
                com.law.todo.domain.TodoException rejected=assertThrows(
                        com.law.todo.domain.TodoException.class,
                        ()->ports.commands().complete(td001.todoId(),conflict,
                                actor(fixture.ownerId(),fixture.ownerName(),fixture.deptId())));
                assertEquals("TODO_ACTION_ID_CONFLICT",rejected.getBusinessCode());
                assertEquals(afterFirst,completionCounts(db,fixture.validLeadId(),
                        td001.todoId(),published.td004()));
            }
            finally
            {
                SecurityContextHolder.clearContext();
                session.rollback();
            }
        }
    }

    @Test
    void td004DuplicateCompletionCreatesOneProgressPlanOccurrenceAndNextTodo() throws Exception
    {
        MigrationTestDatabase.migrate();
        try(SqlSession session=sessions().openSession(false))
        {
            Connection db=session.getConnection();
            try
            {
                PublishedIds published=discoverPublishedIds(db);
                Fixtures fixture=insertFixtures(db,published);
                Ports ports=productionPorts(session);
                authenticate(fixture.ownerId(),fixture.deptId(),fixture.ownerName(),Set.of());

                TodoInstance root=trigger(ports.events(),published,fixture.validLeadId(),
                        fixture.validLeadNo(),fixture.ownerId(),fixture.deptId(),"progress-cycle");
                prepare(ports.commands(),root.getTodoId(),
                        actor(fixture.ownerId(),fixture.ownerName(),fixture.deptId()),"progress-root");
                ports.commands().complete(root.getTodoId(),
                        firstContact("VALID","progress-root",fixture.validProofId(),true),
                        actor(fixture.ownerId(),fixture.ownerName(),fixture.deptId()));
                TodoRow td004=onlyChild(db,root.getTodoId(),published.td004());

                long proofId=generated(db,
                        "insert into file_object(logical_name,status,created_by,create_time) "
                        +"values(?,'ACTIVE',?,sysdate())","progress-proof.txt",fixture.ownerId());
                assertEquals(1,update(db,
                        "insert into file_business_relation(file_object_id,business_type,business_id,"
                        +"material_type,visibility,scope_dept_id,scope_user_id,created_by,created_dept_id,"
                        +"active,create_time) values(?,'LEAD',?,'FOLLOWUP_PROOF','BUSINESS',0,0,?,?,1,sysdate())",
                        proofId,fixture.validLeadId(),fixture.ownerId(),fixture.deptId()));

                prepare(ports.commands(),td004.todoId(),
                        actor(fixture.ownerId(),fixture.ownerName(),fixture.deptId()),"progress");
                LocalDateTime progressAt=LocalDateTime.now().minusMinutes(1).withNano(0);
                ActionCommand completion=new ActionCommand(action("progress-complete"),
                        "Task 11 recurring progress",Map.of(
                                "progressType","PHONE",
                                "progressAt",progressAt.toString(),
                                "remark","Real MySQL five-day cycle"),List.of(proofId));
                TodoInstance first=ports.commands().complete(td004.todoId(),completion,
                        actor(fixture.ownerId(),fixture.ownerName(),fixture.deptId()));
                TodoInstance replay=ports.commands().complete(td004.todoId(),completion,
                        actor(fixture.ownerId(),fixture.ownerName(),fixture.deptId()));
                assertEquals(first.getTodoId(),replay.getTodoId());
                assertEquals("COMPLETED",replay.getStatus());

                assertEquals(1,count(db,"select count(*) from biz_lead_followup "
                        +"where lead_id=? and source_todo_id=? and follow_result='SUBSTANTIVE_PROGRESS'",
                        fixture.validLeadId(),td004.todoId()));
                assertEquals(1,count(db,"select count(*) from todo_schedule_plan "
                        +"where previous_todo_id=? and schedule_purpose='LEAD_PROGRESS_5D'",
                        td004.todoId()));

                assertEquals(1,ports.schedules().materializeDue(
                        LocalDateTime.now().plusSeconds(1),100));
                assertEquals(0,ports.schedules().materializeDue(
                        LocalDateTime.now().plusSeconds(1),100));
                assertEquals(1,count(db,"select count(*) from todo_schedule_occurrence occurrence "
                        +"join todo_schedule_plan plan on plan.plan_id=occurrence.plan_id "
                        +"where plan.previous_todo_id=?",td004.todoId()));
                assertEquals(1,count(db,"select count(*) from todo_instance "
                        +"where previous_todo_id=? and template_version_id=?",td004.todoId(),published.td004()));
            }
            finally
            {
                SecurityContextHolder.clearContext();
                session.rollback();
            }
        }
    }

    @Test
    void overdueTd002RunsCompleteDefaultAndMovesLeadToDeadPool() throws Exception
    {
        MigrationTestDatabase.migrate();
        try(SqlSession session=sessions().openSession(false))
        {
            Connection db=session.getConnection();
            try
            {
                PublishedIds published=discoverPublishedIds(db);
                Fixtures fixture=insertFixtures(db,published);
                Ports ports=productionPorts(session);
                authenticate(fixture.ownerId(),fixture.deptId(),fixture.ownerName(),Set.of());
                TodoInstance root=trigger(ports.events(),published,fixture.suspectLeadId(),
                        fixture.suspectLeadNo(),fixture.ownerId(),fixture.deptId(),"auto-review");
                prepare(ports.commands(),root.getTodoId(),
                        actor(fixture.ownerId(),fixture.ownerName(),fixture.deptId()),"auto-review-root");
                ports.commands().complete(root.getTodoId(),
                        firstContact("SUSPECT_INVALID","auto-review",fixture.suspectProofId(),false),
                        actor(fixture.ownerId(),fixture.ownerName(),fixture.deptId()));
                TodoRow td002=onlyChild(db,root.getTodoId(),published.td002());
                LocalDateTime now=LocalDateTime.now().withNano(0);
                update(db,"update todo_instance set due_at=? where todo_id=?",
                        now.minusMinutes(1),td002.todoId());
                update(db,"update todo_sla_record set due_at=?,original_due_at=?,"
                        +"remind80_due_at=?,overdue100_due_at=?,escalate150_due_at=? where todo_id=?",
                        now.minusMinutes(1),now.minusMinutes(1),now.minusMinutes(3),
                        now.minusMinutes(2),now.minusMinutes(1),td002.todoId());

                TodoMapper todos=session.getMapper(TodoMapper.class);
                TodoAutoActionCapability completeDefault=new TodoAutoActionCapability()
                {
                    @Override public String actionType(){return "COMPLETE_DEFAULT";}
                    @Override public Descriptor descriptor()
                    {
                        return new Descriptor("COMPLETE_DEFAULT",
                                List.of("DUE","SLA_80","SLA_100","SLA_150"),
                                Descriptor.commonRetryFields(),List.of());
                    }
                    @SuppressWarnings("unchecked")
                    @Override public AutoActionResult execute(TodoInstance todo,
                            com.law.todo.definition.model.TodoDefinitionDocument.AutoActionRule rule,
                            com.law.todo.application.command.TodoActionCommands.Actor serviceActor)
                    {
                        Map<String,Object> config=rule.config();
                        Map<String,Object> fields=config.get("fields") instanceof Map<?,?> value
                                ?new LinkedHashMap<>((Map<String,Object>)value):Map.of();
                        ports.commands().autoComplete(todo.getTodoId(),new ActionCommand(
                                "AUTO:"+todo.getTodoId()+":"+config.get("ruleKey"),
                                String.valueOf(config.getOrDefault("reason","Task 11 default review")),
                                fields,List.of()),serviceActor);
                        return AutoActionResult.success();
                    }
                };
                TodoAutoActionService autoActions=new TodoAutoActionService(todos,
                        List.of(completeDefault));
                assertEquals(1,autoActions.scanDue(now));

                assertEquals("SUCCESS",textScalar(db,
                        "select status from todo_auto_action_execution where todo_id=?",
                        td002.todoId()),()->{
                            try{return textScalar(db,"select concat(coalesce(last_error_code,''),'|',"
                                    +"coalesce(last_error_message,'')) from todo_auto_action_execution "
                                    +"where todo_id=?",td002.todoId());}
                            catch(Exception failure){return failure.toString();}
                        });
                assertEquals("COMPLETED",textScalar(db,
                        "select status from todo_instance where todo_id=?",td002.todoId()));
                assertEquals("DEAD_POOL",textScalar(db,
                        "select disposition from biz_lead where lead_id=?",fixture.suspectLeadId()));
                assertEquals("Y",textScalar(db,
                        "select system_default from biz_lead_invalid_review where lead_id=?",
                        fixture.suspectLeadId()));
                assertEquals(1,count(db,"select count(*) from todo_auto_action_audit where todo_id=? "
                        +"and action_type='COMPLETE_DEFAULT' and status='SUCCESS'",td002.todoId()));
                assertEquals(1,count(db,"select count(*) from todo_action_log where todo_id=? "
                        +"and action_type='COMPLETE_DEFAULT'",td002.todoId()));
            }
            finally
            {
                SecurityContextHolder.clearContext();
                session.rollback();
            }
        }
    }

    @Test
    void productionCompletionTransactionRollsBackHandlerAndRoutingFailuresThenRecovers()
            throws Exception
    {
        MigrationTestDatabase.migrate();
        DataSource source=dataSource();
        SqlSessionFactory springSessions=new SqlSessionFactoryBuilder().build(
                myBatis(source,new SpringManagedTransactionFactory()));
        SqlSessionTemplate session=new SqlSessionTemplate(springSessions);
        DataSourceTransactionManager transactions=new DataSourceTransactionManager(source);
        TransactionTemplate outer=new TransactionTemplate(transactions);
        outer.executeWithoutResult(outerStatus->
        {
            try
            {
                Connection db=DataSourceUtils.getConnection(source);
                PublishedIds published=discoverPublishedIds(db);
                Fixtures fixture=insertFixtures(db,published);
                Ports production=productionPorts(session);
                TodoInstance td001=trigger(production.events(),published,
                        fixture.validLeadId(),fixture.validLeadNo(),fixture.ownerId(),
                        fixture.deptId(),"rollback");
                authenticate(fixture.ownerId(),fixture.deptId(),fixture.ownerName(),Set.of());
                prepare(production.commands(),td001.getTodoId(),
                        actor(fixture.ownerId(),fixture.ownerName(),fixture.deptId()),"tx-fixture");
                CompletionSnapshot before=completionSnapshot(db,fixture.validLeadId(),
                        td001.getTodoId(),published.td004());
                ActionCommand completion=firstContact("VALID","tx-recovery",
                        fixture.validProofId(),true);

                TodoCompletionLifecyclePort failAfterBusiness=new FailingCompletionLifecycle(
                        "BUSINESS",db,before,fixture.validLeadId(),td001.getTodoId(),
                        published.td004());
                assertThrows(InjectedCompletionFailure.class,()->nested(transactions)
                        .executeWithoutResult(ignored->uncheckedComplete(session,failAfterBusiness,
                                td001.getTodoId(),completion,fixture)));
                assertEquals(before,completionSnapshot(db,fixture.validLeadId(),
                        td001.getTodoId(),published.td004()));

                TodoCompletionLifecyclePort failAfterRouting=new FailingCompletionLifecycle(
                        "ROUTING",db,before,fixture.validLeadId(),td001.getTodoId(),
                        published.td004());
                assertThrows(InjectedCompletionFailure.class,()->nested(transactions)
                        .executeWithoutResult(ignored->uncheckedComplete(session,failAfterRouting,
                                td001.getTodoId(),completion,fixture)));
                assertEquals(before,completionSnapshot(db,fixture.validLeadId(),
                        td001.getTodoId(),published.td004()));

                TodoInstance recovered=nested(transactions).execute(ignored->
                        production.commands().complete(td001.getTodoId(),completion,
                                actor(fixture.ownerId(),fixture.ownerName(),fixture.deptId())));
                assertNotNull(recovered);
                assertEquals("COMPLETED",recovered.getStatus());
                CompletionSnapshot after=completionSnapshot(db,fixture.validLeadId(),
                        td001.getTodoId(),published.td004());
                assertEquals("COMPLETED",after.todoStatus());
                assertEquals(before.todoVersion()+1,after.todoVersion());
                assertEquals("VALID",after.firstContactResult());
                assertEquals(before.actionLogs()+1,after.actionLogs());
                assertEquals(before.followups()+1,after.followups());
                assertEquals(before.callFacts()+1,after.callFacts());
                assertEquals(before.branchEvents()+1,after.branchEvents());
                assertEquals(before.nextTodos()+1,after.nextTodos());
            }
            catch(RuntimeException failure){throw failure;}
            catch(Exception failure){throw new IllegalStateException(failure);}
            finally
            {
                SecurityContextHolder.clearContext();
                outerStatus.setRollbackOnly();
            }
        });
    }

    private static CompletionCounts completionCounts(Connection db,long leadId,long rootTodoId,
            long nextVersionId) throws Exception
    {
        return new CompletionCounts(
                count(db,"select count(*) from todo_action_log where todo_id=? "
                        +"and action_type='COMPLETE'",rootTodoId),
                count(db,"select count(*) from biz_lead_followup where lead_id=? "
                        +"and follow_result='VALID'",leadId),
                count(db,"select count(*) from biz_lead_call_record where lead_id=? "
                        +"and todo_id=?",leadId,rootTodoId),
                count(db,"select count(*) from business_event where aggregate_type='LEAD' "
                        +"and aggregate_id=? and event_type='LEAD_FIRST_CONTACT_VALID'",leadId),
                count(db,"select count(*) from todo_instance where business_type='LEAD' "
                        +"and business_id=? and template_version_id=?",leadId,nextVersionId),
                count(db,"select count(*) from todo_route_token where root_todo_id=?",rootTodoId),
                count(db,"select count(*) from todo_route_join where root_todo_id=?",rootTodoId),
                count(db,"select count(*) from todo_relation where business_type='LEAD' "
                        +"and business_id=?",leadId));
    }

    private static CompletionSnapshot completionSnapshot(Connection db,long leadId,long todoId,
            long nextVersionId) throws Exception
    {
        return new CompletionSnapshot(
                textScalar(db,"select status from todo_instance where todo_id=?",todoId),
                Math.toIntExact(longScalar(db,
                        "select version from todo_instance where todo_id=?",todoId)),
                textScalar(db,"select first_contact_result from biz_lead where lead_id=?",leadId),
                textScalar(db,"select first_contact_status from biz_lead where lead_id=?",leadId),
                Math.toIntExact(longScalar(db,"select row_version from biz_lead where lead_id=?",
                        leadId)),
                count(db,"select count(*) from todo_action_log where todo_id=?",todoId),
                count(db,"select count(*) from biz_lead_followup where lead_id=?",leadId),
                count(db,"select count(*) from biz_lead_call_record where lead_id=?",leadId),
                count(db,"select count(*) from business_event where aggregate_type='LEAD' "
                        +"and aggregate_id=? and event_type='LEAD_FIRST_CONTACT_VALID'",leadId),
                count(db,"select count(*) from todo_instance where business_type='LEAD' "
                        +"and business_id=? and template_version_id=?",leadId,nextVersionId),
                count(db,"select count(*) from todo_route_token where root_todo_id=?",todoId),
                count(db,"select count(*) from todo_route_join where root_todo_id=?",todoId),
                count(db,"select count(*) from todo_relation where business_type='LEAD' "
                        +"and business_id=?",leadId));
    }

    private static TransactionTemplate nested(DataSourceTransactionManager transactions)
    {
        TransactionTemplate nested=new TransactionTemplate(transactions);
        nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
        return nested;
    }

    private static void uncheckedComplete(SqlSession session,
            TodoCompletionLifecyclePort lifecycle,long todoId,ActionCommand command,Fixtures fixture)
    {
        try
        {
            productionPorts(session,lifecycle).commands().complete(todoId,command,
                    actor(fixture.ownerId(),fixture.ownerName(),fixture.deptId()));
        }
        catch(RuntimeException failure){throw failure;}
        catch(Exception failure){throw new IllegalStateException(failure);}
    }

    private record CompletionCounts(int completeActions,int followups,int callFacts,
            int branchEvents,int nextTodos,int routeTokens,int routeJoins,int relations) { }

    private record CompletionSnapshot(String todoStatus,int todoVersion,
            String firstContactResult,String firstContactStatus,int leadVersion,int actionLogs,
            int followups,int callFacts,int branchEvents,int nextTodos,int routeTokens,
            int routeJoins,int relations) { }

    private static final class InjectedCompletionFailure extends RuntimeException
    {
        private static final long serialVersionUID=1L;
        private InjectedCompletionFailure(String checkpoint)
        {
            super("Injected completion lifecycle failure after "+checkpoint);
        }
    }

    private static final class FailingCompletionLifecycle
            implements TodoCompletionLifecyclePort
    {
        private final String checkpoint;
        private final Connection db;
        private final CompletionSnapshot before;
        private final long leadId;
        private final long todoId;
        private final long nextVersionId;

        private FailingCompletionLifecycle(String checkpoint,Connection db,
                CompletionSnapshot before,long leadId,long todoId,long nextVersionId)
        {
            this.checkpoint=checkpoint;this.db=db;this.before=before;this.leadId=leadId;
            this.todoId=todoId;this.nextVersionId=nextVersionId;
        }

        @Override
        public void afterBusinessCompletion(TodoInstance todo,Map<String,Object> routingPayload)
        {
            CompletionSnapshot visible=current();
            assertEquals(before.followups()+1,visible.followups(),
                    "The real handler must write a fact before the injected failure");
            assertEquals(before.callFacts()+1,visible.callFacts());
            assertEquals(before.branchEvents()+1,visible.branchEvents(),
                    "The real Outbox publisher must run before the lifecycle checkpoint");
            if("BUSINESS".equals(checkpoint))throw new InjectedCompletionFailure(checkpoint);
        }

        @Override
        public void afterRouting(TodoInstance todo,Map<String,Object> routingPayload)
        {
            CompletionSnapshot visible=current();
            assertEquals(before.actionLogs()+1,visible.actionLogs());
            assertEquals(before.nextTodos()+1,visible.nextTodos(),
                    "The real routing service must create the next Todo before failure");
            assertTrue(visible.relations()>before.relations());
            if("ROUTING".equals(checkpoint))throw new InjectedCompletionFailure(checkpoint);
        }

        private CompletionSnapshot current()
        {
            try{return completionSnapshot(db,leadId,todoId,nextVersionId);}
            catch(Exception failure){throw new IllegalStateException(failure);}
        }
    }

    static DataSource dataSource()
    {
        return new UnpooledDataSource("com.mysql.cj.jdbc.Driver",
                required("TODO_MIGRATION_DB_URL"),required("TODO_MIGRATION_DB_USER"),
                required("TODO_MIGRATION_DB_PASSWORD"));
    }

    static SqlSessionFactory sessions() throws Exception
    {
        return new SqlSessionFactoryBuilder().build(myBatis(dataSource()));
    }

    static String required(String name)
    {
        String value=System.getenv(name);
        if(value==null||value.isBlank())
            throw new AssertionError(name+" is required; Task 11 real MySQL tests never skip");
        return value;
    }
}
