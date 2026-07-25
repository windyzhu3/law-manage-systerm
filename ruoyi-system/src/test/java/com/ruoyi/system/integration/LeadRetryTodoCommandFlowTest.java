package com.ruoyi.system.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.security.BusinessActor;
import com.law.business.security.BusinessActorProvider;
import com.law.file.security.FileAccessPolicy;
import com.law.todo.application.TodoCommandService;
import com.law.todo.application.TodoDodService;
import com.law.todo.application.TodoRoutingService;
import com.law.todo.application.command.TodoActionCommands.ActionCommand;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.domain.TodoAccessPolicy;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.routing.TodoRoutingEngine.RoutingResult;
import com.law.todo.schedule.TodoScheduleService;
import com.law.todo.schedule.TodoScheduleService.ScheduleCompletion;
import com.law.todo.schedule.TodoScheduleService.ScheduleOccurrenceContext;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.domain.BizLeadCallRecord;
import com.ruoyi.system.domain.BizLeadRetryRecord;
import com.ruoyi.system.mapper.BizLeadMapper;
import com.ruoyi.system.mapper.LeadFlowMapper;
import com.ruoyi.system.service.ISysDictTypeService;
import com.ruoyi.system.service.event.LeadRetryTodoHandler;
import com.ruoyi.system.service.lead.LeadAccessPolicy;
import com.ruoyi.system.service.lead.LeadCallRecordService;
import com.ruoyi.system.service.lead.LeadPermissionPolicy;
import com.ruoyi.system.service.lead.LeadPoolService;
import com.ruoyi.system.service.lead.LeadRetryService;

/**
 * Production service path with JDBC-backed mapper ports. Attempts one and two retain TD-003; the
 * configured limit completes it and routes only from the Task 6 server outcome.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes=LeadRetryTodoCommandFlowTest.TestConfiguration.class)
class LeadRetryTodoCommandFlowTest
{
    private static final ScheduleOccurrenceContext CONTEXT=new ScheduleOccurrenceContext(
            91L,81L,82L,"T0",1,31L,"LEAD",7L,"Asia/Shanghai",
            13L,201L,301L,2,3,"MATERIALIZED",null,"ACTIVE");

    @org.springframework.beans.factory.annotation.Autowired JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Autowired TodoCommandService commands;
    @org.springframework.beans.factory.annotation.Autowired TodoMapper todos;
    @org.springframework.beans.factory.annotation.Autowired TodoAccessPolicy todoAccess;
    @org.springframework.beans.factory.annotation.Autowired BizLeadMapper leads;
    @org.springframework.beans.factory.annotation.Autowired LeadFlowMapper facts;
    @org.springframework.beans.factory.annotation.Autowired TodoScheduleService schedules;
    @org.springframework.beans.factory.annotation.Autowired RecordingRoutingService routing;

    @BeforeEach
    void setUp()
    {
        reset(AopTestUtils.getUltimateTargetObject(todos),todoAccess,leads,facts,schedules);
        routing.reset();
        jdbc.execute("drop all objects");
        jdbc.execute("create table todo_instance(todo_id bigint primary key,status varchar(32),template_version_id bigint,next_key varchar(255))");
        jdbc.execute("create table todo_action(action_id varchar(128) primary key,todo_id bigint,action_type varchar(32),from_status varchar(32),to_status varchar(32))");
        jdbc.execute("create table todo_relation(todo_id bigint,business_type varchar(32),business_id bigint)");
        jdbc.execute("create table lead_state(lead_id bigint primary key,retry_stage varchar(32),attempt_count int,row_version int,disposition varchar(32),first_result varchar(32),owner_id bigint,dept_id bigint)");
        jdbc.execute("create table call_fact(call_record_id bigint primary key,lead_id bigint,todo_id bigint,occurrence_key varchar(255) unique)");
        jdbc.execute("create table retry_fact(retry_record_id bigint primary key,lead_id bigint,todo_id bigint,plan_id bigint,window_code varchar(32),attempt_no int,result_code varchar(32),next_stage varchar(32),idempotency_key varchar(255) unique)");
        jdbc.execute("create table schedule_occurrence(occurrence_id bigint primary key,todo_id bigint,occurrence_key varchar(128),status varchar(32),result_code varchar(32))");
        jdbc.update("insert into todo_instance(todo_id,status,template_version_id,next_key) values(31,'SUBMITTED',13,null)");
        jdbc.update("insert into lead_state values(7,'T0',0,0,'ACTIVE','UNREACHABLE',8,3)");
        jdbc.update("insert into schedule_occurrence values(91,31,'81:T0:1','MATERIALIZED',null)");

        when(todoAccess.canOperate(any(),eq(8L))).thenReturn(true);
        when(todos.selectById(anyLong())).thenAnswer(invocation->todo(invocation.getArgument(0)));
        when(todos.selectActionById(anyString())).thenAnswer(invocation->row(
                "select todo_id todoId,action_type actionType from todo_action where action_id=?",
                (Object)invocation.getArgument(0)));
        when(todos.selectTemplateVersionById(any())).thenAnswer(invocation->
                version(invocation.getArgument(0)));
        when(todos.updateStatusConditionally(anyLong(),anyString(),anyString(),any(),anyString()))
                .thenAnswer(invocation->jdbc.update(
                        "update todo_instance set status=? where todo_id=? and status=?",
                        invocation.getArgument(2),invocation.getArgument(0),
                        invocation.getArgument(1)));
        when(todos.insertActionIfAbsent(anyMap())).thenAnswer(invocation->{
            Map<String,Object> value=invocation.getArgument(0);
            return jdbc.update("insert into todo_action values(?,?,?,?,?)",value.get("actionId"),
                    value.get("todoId"),value.get("actionType"),value.get("fromStatus"),
                    value.get("toStatus"));
        });
        when(todos.selectByNextKey(anyString())).thenAnswer(invocation->{
            Map<String,Object> row=row(
                    "select todo_id todoId from todo_instance where next_key=?",
                    (Object)invocation.getArgument(0));
            return row==null?null:todo(Long.valueOf(String.valueOf(row.get("TODOID"))));
        });
        when(todos.insertInstance(any(TodoInstance.class))).thenAnswer(invocation->{
            TodoInstance next=invocation.getArgument(0);
            next.setTodoId(41L);
            return jdbc.update("insert into todo_instance values(?,?,?,?)",next.getTodoId(),
                    next.getStatus(),next.getTemplateVersionId(),next.getNextIdempotencyKey());
        });
        when(todos.insertRelation(anyMap())).thenAnswer(invocation->{
            Map<String,Object> value=invocation.getArgument(0);
            return jdbc.update("insert into todo_relation values(?,?,?)",value.get("todoId"),
                    value.get("businessType"),value.get("businessId"));
        });

        when(leads.selectLeadById(7L)).thenAnswer(invocation->lead());
        when(leads.countLeadInDataScope(7L,8L,3L,false)).thenReturn(1);
        when(leads.advanceRetryStage(eq(7L),anyString(),anyString(),anyInt(),any(Date.class),
                anyInt(),anyString())).thenAnswer(this::advanceLead);
        when(leads.advanceRetryStage(eq(7L),anyString(),anyString(),anyInt(),eq(null),
                anyInt(),anyString())).thenAnswer(this::advanceLead);

        when(facts.insertCallRecordIfAbsent(any(BizLeadCallRecord.class))).thenAnswer(invocation->{
            BizLeadCallRecord value=invocation.getArgument(0);
            long id=count("call_fact")+1L;value.setCallRecordId(id);
            return jdbc.update("insert into call_fact values(?,?,?,?)",id,value.getLeadId(),
                    value.getTodoId(),value.getIdempotencyKey());
        });
        when(facts.selectCallRecordByIdempotencyKey(anyString())).thenReturn(null);
        when(facts.countCallRecordsForLeadTodo(7L,31L)).thenAnswer(invocation->count("call_fact"));
        when(facts.selectRetryRecordByIdempotencyKey(anyString())).thenReturn(null);
        when(facts.insertRetryRecordIfAbsent(any(BizLeadRetryRecord.class))).thenAnswer(invocation->{
            BizLeadRetryRecord value=invocation.getArgument(0);
            long id=count("retry_fact")+1L;value.setRetryRecordId(id);
            return jdbc.update("insert into retry_fact values(?,?,?,?,?,?,?,?,?)",id,
                    value.getLeadId(),value.getTodoId(),value.getPlanId(),value.getWindowCode(),
                    value.getAttemptNo(),value.getContactResult(),value.getNextWindowCode(),
                    value.getIdempotencyKey());
        });

        when(schedules.requireOccurrenceIdForTodo("81:T0:1",31L)).thenReturn(91L);
        when(schedules.lockOccurrenceContext(91L)).thenReturn(CONTEXT);
        when(schedules.completeAttemptLimit(eq(CONTEXT),any(LocalDateTime.class)))
                .thenAnswer(invocation->{
                    jdbc.update("update schedule_occurrence set status='COMPLETED',result_code='NEXT_WINDOW' where occurrence_id=91");
                    return new ScheduleCompletion(81L,"T0",1,"T1_AM",
                            LocalDateTime.of(2026,7,27,9,0),false,"LEAD",7L,31L,
                            "Asia/Shanghai",13L,201L,301L,2,3);
                });
    }

    @Test
    void attempts_one_two_and_limit_follow_server_terminality_and_exact_route()
    {
        TodoInstance first=commands.complete(31L,attempt("retry-1","NEXT_WINDOW",1),
                new Actor(8L,"alice",3L));
        assertEquals("SUBMITTED",first.getStatus());
        assertEquals(0,routing.payloads().size());
        assertEquals("MATERIALIZED",scalar(
                "select status from schedule_occurrence where occurrence_id=91",String.class));

        TodoInstance second=commands.complete(31L,attempt("retry-2","NEXT_WINDOW",2),
                new Actor(8L,"alice",3L));
        assertEquals("SUBMITTED",second.getStatus());
        assertEquals(0,routing.payloads().size());

        TodoInstance limit=commands.complete(31L,attempt("retry-3","EXHAUSTED",3),
                new Actor(8L,"alice",3L));

        assertEquals("COMPLETED",limit.getStatus());
        assertEquals(List.of("CONTINUE_CURRENT_WINDOW","CONTINUE_CURRENT_WINDOW","NEXT_WINDOW"),
                jdbc.queryForList("select result_code from retry_fact order by retry_record_id",
                        String.class));
        assertEquals(List.of("COMPLETE_RETAINED","COMPLETE_RETAINED","COMPLETE"),
                jdbc.queryForList("select action_type from todo_action order by action_id",
                        String.class));
        assertEquals(3,count("call_fact"));
        assertEquals(3,count("retry_fact"));
        assertEquals("COMPLETED",scalar(
                "select status from schedule_occurrence where occurrence_id=91",String.class));
        assertEquals("NEXT_WINDOW",scalar(
                "select result_code from schedule_occurrence where occurrence_id=91",String.class));
        assertEquals("T1_AM",scalar(
                "select retry_stage from lead_state where lead_id=7",String.class));
        assertEquals(1,routing.payloads().size());
        assertEquals(Map.of("result","NEXT_WINDOW","retryRecordId",3L,
                "nextStage","T1_AM","attemptNo",3,"replayed",false),
                routing.payloads().get(0));
        assertEquals(14L,scalar(
                "select template_version_id from todo_instance where todo_id=41",Long.class));
        assertEquals(1,count("todo_relation"));
    }

    private ActionCommand attempt(String actionId,String clientResult,int attempt)
    {
        return new ActionCommand(actionId,"retry",Map.of(
                "result",clientResult,
                "planId",999L,
                "nextWindowCode","CLIENT_CHOICE",
                "attemptCount",attempt,
                "contactedAt",String.format("2026-07-26T%02d:00:00",8+attempt)),List.of());
    }

    private int advanceLead(org.mockito.invocation.InvocationOnMock invocation)
    {
        String expected=invocation.getArgument(1);String next=invocation.getArgument(2);
        Integer attempts=invocation.getArgument(3);Integer version=invocation.getArgument(5);
        return jdbc.update("update lead_state set retry_stage=?,attempt_count=?,row_version=row_version+1 where lead_id=7 and retry_stage=? and row_version=?",
                next,attempts,expected,version);
    }

    private TodoInstance todo(Long id)
    {
        Map<String,Object> row=row(
                "select todo_id todoId,status,template_version_id templateVersionId,next_key nextKey from todo_instance where todo_id=?",
                (Object)id);
        if(row==null)return null;
        TodoInstance value=new TodoInstance();value.setTodoId(id);
        value.setStatus(String.valueOf(row.get("STATUS")));
        value.setTemplateVersionId(Long.valueOf(String.valueOf(row.get("TEMPLATEVERSIONID"))));
        value.setTemplateCode(id.equals(31L)?"TD-003":"TD-004");
        value.setBusinessType("LEAD");value.setBusinessId(7L);value.setBusinessNo("LEAD-7");
        value.setOwnerId(8L);value.setOwnerDeptId(3L);value.setRootTodoId(31L);
        value.setDefinitionHash("retry-hash");value.setRouteDefinitionVersionId(13L);
        value.setRouteNodeKey(id.equals(31L)?"retry":"next");
        value.setOccurrenceKey(id.equals(31L)?"81:T0:1":String.valueOf(row.get("NEXTKEY")));
        return value;
    }

    private BizLead lead()
    {
        Map<String,Object> row=row(
                "select retry_stage retryStage,attempt_count attemptCount,row_version rowVersion,disposition,first_result firstResult,owner_id ownerId,dept_id deptId from lead_state where lead_id=7");
        BizLead value=new BizLead();value.setLeadId(7L);value.setLeadNo("LEAD-7");
        value.setDelFlag("0");value.setPoolStatus("0");
        value.setDisposition(String.valueOf(row.get("DISPOSITION")));
        value.setFirstContactResult(String.valueOf(row.get("FIRSTRESULT")));
        value.setRetryStage(String.valueOf(row.get("RETRYSTAGE")));
        value.setRetryAttemptCount(Integer.valueOf(String.valueOf(row.get("ATTEMPTCOUNT"))));
        value.setRowVersion(Integer.valueOf(String.valueOf(row.get("ROWVERSION"))));
        value.setOwnerId(Long.valueOf(String.valueOf(row.get("OWNERID"))));
        value.setDeptId(Long.valueOf(String.valueOf(row.get("DEPTID"))));
        return value;
    }

    private Map<String,Object> version(Long id)
    {
        if(Long.valueOf(13L).equals(id))return Map.of(
                "compiled_json",routeDefinition(),"definition_hash","retry-hash");
        if(Long.valueOf(14L).equals(id))return Map.of(
                "status","PUBLISHED","template_id",14L,"template_code","TD-003",
                "template_name","Next retry","business_type","LEAD","owner_rule_json","OWNER");
        if(Long.valueOf(15L).equals(id))return Map.of(
                "status","PUBLISHED","template_id",15L,"template_code","TD-004",
                "template_name","Progress","business_type","LEAD","owner_rule_json","OWNER");
        return Map.of();
    }

    private String routeDefinition()
    {
        return """
                {"schemaVersion":1,"templateCode":"TD-003",
                 "dod":{"config":{}},"ui":{"config":{}},
                 "routing":{"config":{"start":"retry","nodes":[
                   {"key":"retry","type":"TASK","templateVersionId":13},
                   {"key":"result","type":"DECISION"},
                   {"key":"next","type":"TASK","templateVersionId":14},
                   {"key":"connected","type":"TASK","templateVersionId":15},
                   {"key":"end","type":"END"}],
                 "edges":[
                   {"key":"retry-result","from":"retry","to":"result","priority":0},
                   {"key":"next-window","from":"result","to":"next","priority":20,
                    "condition":{"$expression":{"version":1,"root":{"field":"result","operator":"EQ","value":"NEXT_WINDOW"}}}},
                   {"key":"connected-result","from":"result","to":"connected","priority":10,
                    "condition":{"$expression":{"version":1,"root":{"field":"result","operator":"EQ","value":"CONNECTED"}}}},
                   {"key":"exhausted","from":"result","to":"end","priority":0,"default":true},
                   {"key":"next-end","from":"next","to":"end","priority":0},
                   {"key":"connected-end","from":"connected","to":"end","priority":0}]}} ,
                 "autoActions":[],"decisionRefs":[],"acceptanceRefs":[]}
                """;
    }

    private Map<String,Object> row(String sql,Object... args)
    {
        List<Map<String,Object>> rows=jdbc.queryForList(sql,args);
        return rows.isEmpty()?null:rows.get(0);
    }
    private int count(String table)
    {return jdbc.queryForObject("select count(*) from "+table,Integer.class);}
    private <T> T scalar(String sql,Class<T> type)
    {return jdbc.queryForObject(sql,type);}

    @Configuration
    @EnableTransactionManagement(proxyTargetClass=true)
    static class TestConfiguration
    {
        @Bean DataSource dataSource()
        {
            JdbcDataSource value=new JdbcDataSource();
            value.setURL("jdbc:h2:mem:lead_retry_todo_flow;MODE=MySQL;DB_CLOSE_DELAY=-1");
            value.setUser("sa");return value;
        }
        @Bean JdbcTemplate jdbcTemplate(DataSource source){return new JdbcTemplate(source);}
        @Bean PlatformTransactionManager transactionManager(DataSource source)
        {return new DataSourceTransactionManager(source);}
        @Bean TodoMapper todoMapper(){return mock(TodoMapper.class);}
        @Bean TodoAccessPolicy todoAccessPolicy(){return mock(TodoAccessPolicy.class);}
        @Bean BizLeadMapper bizLeadMapper(){return mock(BizLeadMapper.class);}
        @Bean LeadFlowMapper leadFlowMapper(){return mock(LeadFlowMapper.class);}
        @Bean TodoScheduleService schedules(){return mock(TodoScheduleService.class);}
        @Bean BusinessActorProvider actors()
        {return ()->new BusinessActor(8L,"alice","Alice",3L,false);}
        @Bean ISysDictTypeService dictionaries()
        {
            ISysDictTypeService service=mock(ISysDictTypeService.class);
            when(service.selectDictDataByType(anyString())).thenAnswer(invocation->{
                String type=invocation.getArgument(0);
                List<String> values="law_call_channel".equals(type)
                        ?List.of("MANUAL")
                        :List.of("CONNECTED","NEXT_WINDOW","EXHAUSTED");
                return values.stream().map(value->{
                    SysDictData item=new SysDictData();item.setDictValue(value);return item;
                }).toList();
            });
            return service;
        }
        @Bean FileAccessPolicy files(){return mock(FileAccessPolicy.class);}
        @Bean LeadPermissionPolicy permissions(){return mock(LeadPermissionPolicy.class);}
        @Bean BusinessEventPublisher events(){return mock(BusinessEventPublisher.class);}
        @Bean LeadPoolService pool(){return mock(LeadPoolService.class);}
        @Bean LeadAccessPolicy leadAccess(BizLeadMapper mapper,BusinessActorProvider actors)
        {return new LeadAccessPolicy(mapper,actors);}
        @Bean LeadCallRecordService calls(LeadFlowMapper facts,BizLeadMapper leads,
                LeadAccessPolicy access,BusinessActorProvider actors,ISysDictTypeService dictionaries,
                FileAccessPolicy files,LeadPermissionPolicy permissions)
        {return new LeadCallRecordService(facts,leads,access,actors,dictionaries,files,permissions,List.of());}
        @Bean LeadRetryService retryService(BizLeadMapper leads,LeadFlowMapper facts,
                LeadAccessPolicy access,LeadCallRecordService calls,BusinessActorProvider actors,
                ISysDictTypeService dictionaries,TodoScheduleService schedules,LeadPoolService pool,
                BusinessEventPublisher events)
        {return new LeadRetryService(leads,facts,access,calls,actors,dictionaries,schedules,pool,events);}
        @Bean LeadRetryTodoHandler retryHandler(LeadRetryService retries,TodoScheduleService schedules)
        {return new LeadRetryTodoHandler(retries,schedules);}
        @Bean RecordingRoutingService routing(TodoMapper mapper)
        {return new RecordingRoutingService(mapper);}
        @Bean TodoCommandService commands(TodoMapper mapper,TodoAccessPolicy access,
                LeadRetryTodoHandler handler,RecordingRoutingService routing)
        {return new TodoCommandService(mapper,access,new TodoDodService(List.of()),List.of(handler),routing);}
    }

    static class RecordingRoutingService extends TodoRoutingService
    {
        final List<Map<String,Object>> payloads=new ArrayList<>();
        RecordingRoutingService(TodoMapper mapper){super(mapper);}
        @Override public RoutingResult advance(TodoInstance previous,Map<String,Object> payload)
        {
            payloads.add(Map.copyOf(new LinkedHashMap<>(payload)));
            return super.advance(previous,payload);
        }
        List<Map<String,Object>> payloads(){return List.copyOf(payloads);}
        void reset(){payloads.clear();}
    }
}
