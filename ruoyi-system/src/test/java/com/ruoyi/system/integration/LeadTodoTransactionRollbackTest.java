package com.ruoyi.system.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
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
import com.law.business.event.BusinessEventCommand;
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
import com.law.todo.schedule.TodoScheduleService;
import com.law.todo.spi.TodoOrganizationPort;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.domain.BizLeadCallRecord;
import com.ruoyi.system.domain.BizLeadFollowup;
import com.ruoyi.system.mapper.BizLeadMapper;
import com.ruoyi.system.mapper.LeadFlowMapper;
import com.ruoyi.system.service.ISysDictTypeService;
import com.ruoyi.system.service.event.LeadFirstContactHandler;
import com.ruoyi.system.service.lead.LeadAccessPolicy;
import com.ruoyi.system.service.lead.LeadAssignmentPolicyService;
import com.ruoyi.system.service.lead.LeadCallRecordService;
import com.ruoyi.system.service.lead.LeadFirstContactService;
import com.ruoyi.system.service.lead.LeadPermissionPolicy;

/**
 * Uses Spring's real transactional proxy plus a real JDBC transaction. Mapper mocks only adapt
 * production ports to the embedded database so a rollback is observed across Todo and lead modules.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes=LeadTodoTransactionRollbackTest.TestConfiguration.class)
class LeadTodoTransactionRollbackTest
{
    @org.springframework.beans.factory.annotation.Autowired JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Autowired TodoCommandService commands;
    @org.springframework.beans.factory.annotation.Autowired TodoMapper todos;
    @org.springframework.beans.factory.annotation.Autowired BizLeadMapper leads;
    @org.springframework.beans.factory.annotation.Autowired LeadFlowMapper facts;
    @org.springframework.beans.factory.annotation.Autowired TodoAccessPolicy todoAccess;
    @org.springframework.beans.factory.annotation.Autowired TodoOrganizationPort organization;
    @org.springframework.beans.factory.annotation.Autowired MutableOutboxPublisher outbox;

    @BeforeEach
    void setUp()
    {
        org.mockito.Mockito.reset(AopTestUtils.getUltimateTargetObject(todos),leads,facts,todoAccess,
                organization);
        jdbc.execute("drop all objects");
        jdbc.execute("create table todo_instance(todo_id bigint primary key,status varchar(32))");
        jdbc.execute("create table todo_action(action_id varchar(128) primary key,todo_id bigint)");
        jdbc.execute("create table lead_call(call_record_id bigint primary key,lead_id bigint,todo_id bigint)");
        jdbc.execute("create table lead_followup(followup_id bigint primary key,lead_id bigint)");
        jdbc.execute("create table lead_transition(id bigint auto_increment primary key,lead_id bigint,result varchar(32))");
        jdbc.execute("create table business_event(id bigint auto_increment primary key,event_key varchar(255))");
        jdbc.execute("create table route_join(root_todo_id bigint,node_key varchar(64),occurrence int,status varchar(32),primary key(root_todo_id,node_key,occurrence))");
        jdbc.execute("create table route_token(root_todo_id bigint,node_key varchar(64),branch_key varchar(64),occurrence int,status varchar(32))");
        jdbc.execute("create table todo_relation(todo_id bigint,business_type varchar(32),business_id bigint)");
        jdbc.update("insert into todo_instance(todo_id,status) values(21,'SUBMITTED')");
        outbox.failAfterInsert=false;
        outbox.businessFactFailure=false;
        outbox.routingFailure=false;

        when(todoAccess.canOperate(any(),anyLong())).thenReturn(true);
        when(organization.isAvailable(anyLong(),any(LocalDateTime.class))).thenReturn(true);
        when(todos.selectById(21L)).thenAnswer(invocation->todo());
        when(todos.selectActionById(anyString())).thenAnswer(invocation->{
            List<Map<String,Object>> rows=jdbc.queryForList(
                    "select todo_id todoId from todo_action where action_id=?",
                    new Object[]{invocation.getArgument(0)});
            return rows.isEmpty()?null:rows.get(0);
        });
        when(todos.selectTemplateVersionById(any())).thenAnswer(invocation->{
            Long versionId=invocation.getArgument(0);
            if(Long.valueOf(11L).equals(versionId))return Map.of(
                    "compiled_json",routeDefinition(),
                    "definition_hash","route-hash");
            if(Long.valueOf(12L).equals(versionId))return Map.of(
                    "status","PUBLISHED","template_id",12L,"template_code","TD-004",
                    "template_name","Progress handoff","business_type","LEAD",
                    "owner_rule_json","OWNER");
            return Map.of();
        });
        when(todos.updateStatusConditionally(anyLong(),anyString(),anyString(),any(),anyString()))
                .thenAnswer(invocation->jdbc.update(
                        "update todo_instance set status=? where todo_id=? and status=?",
                        invocation.getArgument(2),invocation.getArgument(0),invocation.getArgument(1)));
        when(todos.insertActionIfAbsent(anyMap())).thenAnswer(invocation->{
            Map<String,Object> value=invocation.getArgument(0);
            return jdbc.update("insert into todo_action(action_id,todo_id) values(?,?)",
                    value.get("actionId"),value.get("todoId"));
        });
        when(todos.insertRouteJoinIfAbsent(anyMap())).thenAnswer(invocation->{
            Map<String,Object> value=invocation.getArgument(0);
            return jdbc.update("insert into route_join(root_todo_id,node_key,occurrence,status) values(?,?,?,'WAITING')",
                    value.get("rootTodoId"),value.get("nodeKey"),value.get("occurrence"));
        });
        when(todos.selectRouteJoinForUpdate(anyLong(),anyString(),anyInt())).thenAnswer(invocation->{
            Long root=invocation.getArgument(0);String node=invocation.getArgument(1);
            Integer occurrence=invocation.getArgument(2);
            List<Map<String,Object>> rows=jdbc.queryForList(
                    "select root_todo_id rootTodoId,node_key nodeKey,occurrence,status from route_join where root_todo_id=? and node_key=? and occurrence=?",
                    root,node,occurrence);
            return rows.isEmpty()?null:rows.get(0);
        });
        when(todos.insertRouteTokenIfAbsent(anyMap())).thenAnswer(invocation->{
            Map<String,Object> value=invocation.getArgument(0);
            return jdbc.update("insert into route_token(root_todo_id,node_key,branch_key,occurrence,status) values(?,?,?,?,?)",
                    value.get("rootTodoId"),value.get("nodeKey"),value.get("branchKey"),
                    value.get("occurrence"),value.get("status"));
        });
        when(todos.selectRouteTokenArrivalsForUpdate(anyLong(),anyString(),anyInt()))
                .thenAnswer(invocation->{
                    Long root=invocation.getArgument(0);String node=invocation.getArgument(1);
                    Integer occurrence=invocation.getArgument(2);
                    return jdbc.queryForList(
                            "select branch_key from route_token where root_todo_id=? and node_key=? and occurrence=?",
                            String.class,root,node,occurrence);
                });
        when(todos.advanceRouteJoinConditionally(anyLong(),anyString(),anyInt()))
                .thenAnswer(invocation->jdbc.update(
                        "update route_join set status='ADVANCED' where root_todo_id=? and node_key=? and occurrence=? and status='WAITING'",
                        invocation.getArgument(0),invocation.getArgument(1),invocation.getArgument(2)));
        when(todos.selectByNextKey(anyString())).thenReturn(null);
        when(todos.insertInstance(any(TodoInstance.class))).thenAnswer(invocation->{
            TodoInstance next=invocation.getArgument(0);
            next.setTodoId(22L);
            return jdbc.update("insert into todo_instance(todo_id,status) values(?,?)",
                    next.getTodoId(),next.getStatus());
        });
        when(todos.insertRelation(anyMap())).thenAnswer(invocation->{
            Map<String,Object> value=invocation.getArgument(0);
            int inserted=jdbc.update(
                    "insert into todo_relation(todo_id,business_type,business_id) values(?,?,?)",
                    value.get("todoId"),value.get("businessType"),value.get("businessId"));
            if(outbox.routingFailure)throw new IllegalStateException("routing relation failure");
            return inserted;
        });

        when(leads.selectLeadById(7L)).thenAnswer(invocation->lead());
        when(leads.countLeadInDataScope(7L,8L,3L,false)).thenReturn(1);
        when(leads.insertFollowup(any(BizLeadFollowup.class))).thenAnswer(invocation->{
            BizLeadFollowup value=invocation.getArgument(0);value.setFollowupId(51L);
            return jdbc.update("insert into lead_followup(followup_id,lead_id) values(?,?)",
                    value.getFollowupId(),value.getLeadId());
        });
        when(leads.completeFirstContact(anyLong(),anyString(),anyString(),any(),any(),any(),any(),
                any(),any(),anyInt(),anyString())).thenAnswer(invocation->{
            Long leadId=invocation.getArgument(0);
            String result=invocation.getArgument(2);
            int rows=jdbc.update("insert into lead_transition(lead_id,result) values(?,?)",
                    leadId,result);
            if(outbox.businessFactFailure)throw new IllegalStateException("business fact failure");
            return rows;
        });
        when(facts.insertCallRecordIfAbsent(any(BizLeadCallRecord.class))).thenAnswer(invocation->{
            BizLeadCallRecord value=invocation.getArgument(0);value.setCallRecordId(41L);
            return jdbc.update("insert into lead_call(call_record_id,lead_id,todo_id) values(?,?,?)",
                    value.getCallRecordId(),value.getLeadId(),value.getTodoId());
        });
    }

    @Test
    void outbox_failure_rolls_back_todo_action_business_facts_and_outbox()
    {
        outbox.failAfterInsert=true;

        assertThrows(IllegalStateException.class,()->commands.complete(21L,action("tx-outbox"),
                new Actor(8L,"alice",3L)));

        assertRolledBack();
    }

    @Test
    void branch_persistence_failure_rolls_back_todo_action_and_all_prior_facts()
    {
        outbox.businessFactFailure=true;

        assertThrows(IllegalStateException.class,()->commands.complete(21L,action("tx-business"),
                new Actor(8L,"alice",3L)));

        assertRolledBack();
    }

    @Test
    void routing_failure_after_token_next_todo_and_relation_rolls_back_the_full_completion()
    {
        outbox.routingFailure=true;

        assertThrows(IllegalStateException.class,()->commands.complete(21L,action("tx-route"),
                new Actor(8L,"alice",3L)));

        assertRolledBack();
    }

    private void assertRolledBack()
    {
        assertEquals("SUBMITTED",jdbc.queryForObject(
                "select status from todo_instance where todo_id=21",String.class));
        assertEquals(0,count("todo_action"));
        assertEquals(0,count("lead_call"));
        assertEquals(0,count("lead_followup"));
        assertEquals(0,count("lead_transition"));
        assertEquals(0,count("business_event"));
        assertEquals(0,count("route_join"));
        assertEquals(0,count("route_token"));
        assertEquals(0,count("todo_relation"));
        assertEquals(1,count("todo_instance"));
    }

    private int count(String table)
    {return jdbc.queryForObject("select count(*) from "+table,Integer.class);}

    private ActionCommand action(String id)
    {
        return new ActionCommand(id,"done",Map.of(
                "contactResult","VALID",
                "contactName","张三",
                "city","上海",
                "legalDemand","合同争议",
                "visited","0",
                "callRecord",Map.of(
                        "callChannel","MANUAL",
                        "businessOccurrenceKey",id+":call",
                        "startedAt","2026-07-26T09:00:00",
                        "callResult","CONNECTED")),List.of());
    }

    private TodoInstance todo()
    {
        String status=jdbc.queryForObject(
                "select status from todo_instance where todo_id=21",String.class);
        TodoInstance todo=new TodoInstance();todo.setTodoId(21L);todo.setTemplateVersionId(11L);
        todo.setTemplateCode("TD-001");todo.setBusinessType("LEAD");todo.setBusinessId(7L);
        todo.setBusinessNo("LEAD-7");todo.setOwnerId(8L);todo.setOwnerDeptId(3L);
        todo.setDefinitionHash("route-hash");todo.setRouteDefinitionVersionId(11L);
        todo.setRouteNodeKey("source");todo.setRootTodoId(21L);
        todo.setStatus(status);return todo;
    }

    private String routeDefinition()
    {
        return """
                {"schemaVersion":1,"templateCode":"TD-001",
                 "dod":{"config":{}},"ui":{"config":{}},
                 "routing":{"config":{"start":"source","nodes":[
                   {"key":"source","type":"TASK","templateVersionId":11},
                   {"key":"fork","type":"FORK"},
                   {"key":"join","type":"JOIN","joinMode":"ALL","branches":["branchA"]},
                   {"key":"next","type":"TASK","templateVersionId":12},
                   {"key":"end","type":"END"}],
                 "edges":[
                   {"key":"source-fork","from":"source","to":"fork","priority":0},
                   {"key":"fork-join","from":"fork","to":"join","branchKey":"branchA","priority":0},
                   {"key":"join-next","from":"join","to":"next","priority":0},
                   {"key":"next-end","from":"next","to":"end","priority":0}]}} ,
                 "autoActions":[],"decisionRefs":[],"acceptanceRefs":[]}
                """;
    }

    private BizLead lead()
    {
        BizLead lead=new BizLead();lead.setLeadId(7L);lead.setLeadNo("LEAD-7");lead.setStatus("1");
        lead.setDelFlag("0");lead.setDisposition("ACTIVE");lead.setOwnerId(8L);lead.setDeptId(3L);
        lead.setFirstContactStatus("PENDING");lead.setRowVersion(0);return lead;
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass=true)
    static class TestConfiguration
    {
        @Bean DataSource dataSource()
        {
            JdbcDataSource value=new JdbcDataSource();
            value.setURL("jdbc:h2:mem:lead_todo_tx;MODE=MySQL;DB_CLOSE_DELAY=-1");
            value.setUser("sa");return value;
        }
        @Bean JdbcTemplate jdbcTemplate(DataSource source){return new JdbcTemplate(source);}
        @Bean PlatformTransactionManager transactionManager(DataSource source)
        {return new DataSourceTransactionManager(source);}
        @Bean TodoMapper todoMapper(){return mock(TodoMapper.class);}
        @Bean BizLeadMapper bizLeadMapper(){return mock(BizLeadMapper.class);}
        @Bean LeadFlowMapper leadFlowMapper(){return mock(LeadFlowMapper.class);}
        @Bean TodoAccessPolicy todoAccessPolicy(){return mock(TodoAccessPolicy.class);}
        @Bean BusinessActorProvider actors()
        {return ()->new BusinessActor(8L,"alice","Alice",3L,false);}
        @Bean ISysDictTypeService dictionaries()
        {
            ISysDictTypeService value=mock(ISysDictTypeService.class);
            when(value.selectDictDataByType(anyString())).thenAnswer(invocation->{
                SysDictData item=new SysDictData();String type=invocation.getArgument(0);
                item.setDictValue("law_call_channel".equals(type)?"MANUAL":"VALID");
                return List.of(item);
            });
            return value;
        }
        @Bean FileAccessPolicy files(){return mock(FileAccessPolicy.class);}
        @Bean LeadPermissionPolicy permissions(){return mock(LeadPermissionPolicy.class);}
        @Bean TodoOrganizationPort organization(){return mock(TodoOrganizationPort.class);}
        @Bean TodoScheduleService schedules(){return mock(TodoScheduleService.class);}
        @Bean LeadAssignmentPolicyService policies(){return mock(LeadAssignmentPolicyService.class);}
        @Bean LeadAccessPolicy leadAccess(BizLeadMapper mapper,BusinessActorProvider actors)
        {return new LeadAccessPolicy(mapper,actors);}
        @Bean LeadCallRecordService calls(LeadFlowMapper facts,BizLeadMapper leads,
                LeadAccessPolicy access,BusinessActorProvider actors,ISysDictTypeService dictionaries,
                FileAccessPolicy files,LeadPermissionPolicy permissions)
        {return new LeadCallRecordService(facts,leads,access,actors,dictionaries,files,permissions,List.of());}
        @Bean MutableOutboxPublisher outbox(JdbcTemplate jdbc){return new MutableOutboxPublisher(jdbc);}
        @Bean LeadFirstContactService firstContacts(BizLeadMapper leads,LeadFlowMapper facts,
                LeadAccessPolicy access,LeadCallRecordService calls,BusinessActorProvider actors,
                ISysDictTypeService dictionaries,TodoOrganizationPort organization,
                TodoScheduleService schedules,LeadAssignmentPolicyService policies,
                MutableOutboxPublisher events)
        {return new LeadFirstContactService(leads,facts,access,calls,actors,dictionaries,
                organization,schedules,policies,events);}
        @Bean LeadFirstContactHandler handler(LeadFirstContactService firstContacts)
        {return new LeadFirstContactHandler(firstContacts);}
        @Bean TodoRoutingService routing(TodoMapper mapper){return new TodoRoutingService(mapper);}
        @Bean TodoCommandService commands(TodoMapper mapper,TodoAccessPolicy access,
                LeadFirstContactHandler handler,TodoRoutingService routing)
        {return new TodoCommandService(mapper,access,new TodoDodService(List.of()),List.of(handler),routing);}
    }

    static final class MutableOutboxPublisher implements BusinessEventPublisher
    {
        private final JdbcTemplate jdbc;
        volatile boolean failAfterInsert;
        volatile boolean businessFactFailure;
        volatile boolean routingFailure;
        MutableOutboxPublisher(JdbcTemplate jdbc){this.jdbc=jdbc;}
        @Override public void publish(BusinessEventCommand command)
        {publish(command,null);}
        @Override public void publish(BusinessEventCommand command,BusinessActor actor)
        {
            jdbc.update("insert into business_event(event_key) values(?)",command.getIdempotencyKey());
            if(failAfterInsert)throw new IllegalStateException("outbox failure");
        }
    }
}
