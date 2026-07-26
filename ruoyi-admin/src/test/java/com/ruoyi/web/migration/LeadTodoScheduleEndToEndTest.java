package com.ruoyi.web.migration;

import static com.ruoyi.web.migration.LeadTodoProductionPortsExternalMysqlIT.*;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import com.law.todo.application.TodoSlaService;
import com.law.todo.application.command.TodoActionCommands.ActionCommand;
import com.law.todo.assignment.CompositeOwnerResolver;
import com.law.todo.assignment.OwnerResolutionContext;
import com.law.todo.definition.model.TodoDefinitionDocument.OwnerRule;
import com.law.todo.domain.DefaultTodoAccessPolicy;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.spi.MapperTodoOrganizationAdapter;

/** Real MySQL proof for retry windows, owner routing and SLA thresholds. */
class LeadTodoScheduleEndToEndTest
{
    @Test
    void unreachableAdvancesToT1AndConnectedCancelsFutureWindowsAndCreatesTd004()
            throws Exception
    {
        MigrationTestDatabase.migrate();
        try(SqlSession session=LeadTodoFlowEndToEndTest.sessions().openSession(false))
        {
            Connection db=session.getConnection();
            try
            {
                PublishedIds published=discoverPublishedIds(db);
                Fixtures fixture=insertFixtures(db,published);
                Ports ports=productionPorts(session);
                authenticate(fixture.ownerId(),fixture.deptId(),fixture.ownerName(),Set.of());
                TodoInstance root=unreachableRoot(ports,published,fixture,"connected");
                TodoRow t0=materialize(db,ports,fixture.retryLeadId(),published.td003(),"T0");
                exhaustCurrentTodo(ports,t0,fixture,3,"connected-t0");
                TodoRow t1=materialize(db,ports,fixture.retryLeadId(),published.td003(),"T1_AM");

                prepare(ports.commands(),t1.todoId(),
                        actor(fixture.ownerId(),fixture.ownerName(),fixture.deptId()),"connected-t1");
                ports.commands().complete(t1.todoId(),connected("connected-t1",fixture.retryProofId()),
                        actor(fixture.ownerId(),fixture.ownerName(),fixture.deptId()));

                assertEquals("VALID",textScalar(db,
                        "select first_contact_result from biz_lead where lead_id=?",
                        fixture.retryLeadId()));
                assertEquals("CONTACTED",textScalar(db,
                        "select status from todo_schedule_plan where business_id=?",
                        fixture.retryLeadId()));
                assertEquals(1,count(db,"select count(*) from todo_instance where business_id=? "
                        +"and template_version_id=?",fixture.retryLeadId(),published.td004()));
                assertEquals(5,count(db,"select count(*) from todo_schedule_window where plan_id=("
                        +"select plan_id from todo_schedule_plan where business_id=?) "
                        +"and window_order>1 and status='CANCELLED'",fixture.retryLeadId()));
                assertEquals("COMPLETED",textScalar(db,
                        "select status from todo_instance where todo_id=?",root.getTodoId()));
            }
            finally
            {
                SecurityContextHolder.clearContext();
                session.rollback();
            }
        }
    }

    @Test
    void allT0T1T2WindowsExhaustedReturnsLeadToPublicPoolExactlyOnce() throws Exception
    {
        MigrationTestDatabase.migrate();
        try(SqlSession session=LeadTodoFlowEndToEndTest.sessions().openSession(false))
        {
            Connection db=session.getConnection();
            try
            {
                PublishedIds published=discoverPublishedIds(db);
                Fixtures fixture=insertFixtures(db,published);
                Ports ports=productionPorts(session);
                authenticate(fixture.ownerId(),fixture.deptId(),fixture.ownerName(),Set.of());
                unreachableRoot(ports,published,fixture,"exhausted");

                String[] windows={"T0","T1_AM","T1_NOON","T1_PM","T2_AM","T2_NOON","T2_PM"};
                for(String window:windows)
                {
                    TodoRow todo=materialize(db,ports,fixture.retryLeadId(),published.td003(),window);
                    exhaustCurrentTodo(ports,todo,fixture,"T0".equals(window)?3:1,
                            "all-"+window);
                }

                assertEquals("PUBLIC_POOL",textScalar(db,
                        "select disposition from biz_lead where lead_id=?",fixture.retryLeadId()));
                assertEquals("1",textScalar(db,
                        "select pool_status from biz_lead where lead_id=?",fixture.retryLeadId()));
                assertEquals("EXHAUSTED",textScalar(db,
                        "select retry_stage from biz_lead where lead_id=?",fixture.retryLeadId()));
                assertEquals("EXHAUSTED",textScalar(db,
                        "select status from todo_schedule_plan where business_id=?",
                        fixture.retryLeadId()));
                assertEquals(1,count(db,"select count(*) from business_event where aggregate_id=? "
                        +"and event_type='LEAD_RETRY_EXHAUSTED'",fixture.retryLeadId()));
                assertEquals(0,count(db,"select count(*) from todo_instance where business_id=? "
                        +"and template_version_id=? and status not in ('COMPLETED','CANCELLED')",
                        fixture.retryLeadId(),published.td003()));
            }
            finally
            {
                SecurityContextHolder.clearContext();
                session.rollback();
            }
        }
    }

    @Test
    void leaveUsesDelegateAndRoundRobinSkipsUnavailableOwner() throws Exception
    {
        MigrationTestDatabase.migrate();
        try(SqlSession session=LeadTodoFlowEndToEndTest.sessions().openSession(false))
        {
            Connection db=session.getConnection();
            try
            {
                PublishedIds published=discoverPublishedIds(db);
                Fixtures fixture=insertFixtures(db,published);
                long third=generated(db,"insert into sys_user(dept_id,user_name,nick_name,user_type,"
                        +"password,status,del_flag,create_by,create_time) "
                        +"values(?,?,?,'00','task11','0','0','task11',sysdate())",
                        fixture.deptId(),"task11_rr_"+fixture.ownerId(),"round robin third");
                LocalDateTime now=LocalDateTime.now().withNano(0);
                update(db,"insert into sys_user_availability(user_id,status,effective_from,effective_to,"
                        +"reason,create_by,create_time,update_time) values(?,'UNAVAILABLE',?,?,"
                        +"'Task 11 leave','task11',sysdate(),sysdate())",fixture.ownerId(),
                        now.minusHours(1),now.plusHours(1));
                update(db,"insert into sys_user_delegation(from_user_id,to_user_id,status,effective_from,"
                        +"effective_to,reason,create_by,create_time,update_time) values(?,?,'ACTIVE',?,?,"
                        +"'Task 11 delegation','task11',sysdate(),sysdate())",fixture.ownerId(),
                        fixture.reviewerId(),now.minusHours(1),now.plusHours(1));

                MapperTodoOrganizationAdapter organization=
                        new MapperTodoOrganizationAdapter(session.getMapper(TodoMapper.class));
                CompositeOwnerResolver resolver=new CompositeOwnerResolver(organization);
                OwnerResolutionContext context=new OwnerResolutionContext(Map.of(),"LEAD",
                        fixture.validLeadId(),now);
                var delegated=resolver.resolve(new OwnerRule(Map.of("type","BUSINESS_OWNER",
                        "skipUnavailable",true,"useDelegation",true)),context);
                assertEquals(fixture.reviewerId(),delegated.ownerId());

                OwnerRule roundRobin=new OwnerRule(Map.of("type","ROUND_ROBIN",
                        "strategyKey","TASK11:"+fixture.deptId(),
                        "source",Map.of("type","DEPT","value",fixture.deptId()),
                        "skipUnavailable",true));
                Long first=resolver.resolve(roundRobin,context).ownerId();
                Long second=resolver.resolve(roundRobin,context).ownerId();
                assertEquals(Set.of(fixture.reviewerId(),third),Set.of(first,second));
                assertEquals(1,count(db,"select count(*) from todo_round_robin_cursor "
                        +"where strategy_key=?","TASK11:"+fixture.deptId()));
            }
            finally
            {
                SecurityContextHolder.clearContext();
                session.rollback();
            }
        }
    }

    @Test
    void sla80_100_150FireOnceAndSupervisorReceivesEscalation() throws Exception
    {
        MigrationTestDatabase.migrate();
        try(SqlSession session=LeadTodoFlowEndToEndTest.sessions().openSession(false))
        {
            Connection db=session.getConnection();
            try
            {
                PublishedIds published=discoverPublishedIds(db);
                Fixtures fixture=insertFixtures(db,published);
                TodoInstance todo=trigger(productionPorts(session).events(),published,
                        fixture.validLeadId(),fixture.validLeadNo(),fixture.ownerId(),
                        fixture.deptId(),"sla");
                LocalDateTime now=LocalDateTime.now().withNano(0);
                update(db,"update todo_sla_record set due_at=?,original_due_at=?,remind80_due_at=?,"
                        +"overdue100_due_at=?,escalate150_due_at=?,remind80_at=null,overdue100_at=null,"
                        +"escalate150_at=null,version=0 where todo_id=?",now.minusMinutes(2),
                        now.minusMinutes(2),now.minusMinutes(4),now.minusMinutes(3),
                        now.minusMinutes(1),todo.getTodoId());
                TodoMapper mapper=session.getMapper(TodoMapper.class);
                TodoSlaService sla=new TodoSlaService(mapper,new DefaultTodoAccessPolicy(mapper));
                int fired=sla.scanAndEscalate(now);
                assertEquals(3,fired,()->{
                    try
                    {
                        return textScalar(db,"select concat(coalesce(cast(remind80_at as char),'null'),"
                                +"'|',coalesce(cast(overdue100_at as char),'null'),'|',"
                                +"coalesce(cast(escalate150_at as char),'null'),'|v',version) "
                                +"from todo_sla_record where todo_id=?",todo.getTodoId());
                    }
                    catch(Exception failure){return failure.toString();}
                });
                assertEquals(0,sla.scanAndEscalate(now.plusMinutes(1)));
                assertEquals("ESCALATED",textScalar(db,
                        "select sla_status from todo_instance where todo_id=?",todo.getTodoId()));
                assertEquals(4,count(db,"select count(*) from todo_notification where todo_id=? "
                        +"and notification_type in ('REMINDED_80','OVERDUE_100','ESCALATED_150')",
                        todo.getTodoId()));
                assertEquals(1,count(db,"select count(*) from todo_notification where todo_id=? "
                        +"and user_id=? and notification_type='ESCALATED_150'",todo.getTodoId(),
                        fixture.reviewerId()));
            }
            finally
            {
                SecurityContextHolder.clearContext();
                session.rollback();
            }
        }
    }

    private static TodoInstance unreachableRoot(Ports ports,PublishedIds published,Fixtures fixture,
            String key)
    {
        TodoInstance root=trigger(ports.events(),published,fixture.retryLeadId(),
                fixture.retryLeadNo(),fixture.ownerId(),fixture.deptId(),key);
        prepare(ports.commands(),root.getTodoId(),
                actor(fixture.ownerId(),fixture.ownerName(),fixture.deptId()),key);
        ports.commands().complete(root.getTodoId(),
                firstContact("UNREACHABLE",key,fixture.retryProofId(),false),
                actor(fixture.ownerId(),fixture.ownerName(),fixture.deptId()));
        return root;
    }

    private static TodoRow materialize(Connection db,Ports ports,long leadId,long td003,String window)
            throws Exception
    {
        LocalDateTime at=dateTimeScalar(db,"select materialize_at from todo_schedule_window "
                +"where plan_id=(select plan_id from todo_schedule_plan where business_id=?) "
                +"and window_code=?",leadId,window);
        ports.schedules().materializeDue(at.plusSeconds(1),100);
        TodoRow result=latestTodo(db,leadId,td003);
        assertEquals(window,textScalar(db,"select window_code from todo_schedule_occurrence "
                +"where todo_id=?",result.todoId()));
        return result;
    }

    private static void exhaustCurrentTodo(Ports ports,TodoRow todo,Fixtures fixture,int attempts,
            String key)
    {
        prepare(ports.commands(),todo.todoId(),
                actor(fixture.ownerId(),fixture.ownerName(),fixture.deptId()),key);
        for(int attempt=1;attempt<=attempts;attempt++)
            ports.commands().complete(todo.todoId(),
                    retryAttempt(key+"-"+attempt,fixture.retryProofId(),attempt),
                    actor(fixture.ownerId(),fixture.ownerName(),fixture.deptId()));
    }

    private static ActionCommand connected(String key,long proofId)
    {
        Map<String,Object> fields=new LinkedHashMap<>();
        fields.put("contactResult","CONNECTED");
        fields.put("name","Connected production client");
        fields.put("city","Shanghai");
        fields.put("demand","Connected during the governed retry window");
        fields.put("visited","1");
        fields.put("callRecord",call(key,"CONNECTED",1));
        return new ActionCommand(action(key),"connected retry",fields,List.of(proofId));
    }
}
