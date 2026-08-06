package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.transaction.TransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.security.LeadPermissions;
import com.law.business.security.BusinessActorProvider;
import com.law.file.infrastructure.MyBatisFileObjectRepository;
import com.law.file.mapper.FileObjectMapper;
import com.law.file.security.FileAccessPolicy;
import com.law.todo.application.TodoAssignmentResolver;
import com.law.todo.application.TodoCommandService;
import com.law.todo.application.TodoDodService;
import com.law.todo.application.TodoRoutingService;
import com.law.todo.application.command.TodoActionCommands.ActionCommand;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.domain.DefaultTodoAccessPolicy;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.integration.FileCenterTodoMaterialLookup;
import com.law.todo.integration.TodoEvent;
import com.law.todo.integration.TodoEventService;
import com.law.todo.mapper.TodoConfigurationMapper;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.schedule.TodoScheduleService;
import com.law.todo.spi.MapperTodoOrganizationAdapter;
import com.law.todo.spi.NoOpTodoCompletionLifecyclePort;
import com.law.todo.spi.TodoCompletionLifecyclePort;
import com.law.todo.spi.TodoDictionaryValidationMapperAdapter;
import com.law.todo.spi.TodoOrganizationPort;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.system.mapper.BizLeadMapper;
import com.ruoyi.system.mapper.BusinessEventMapper;
import com.ruoyi.system.mapper.LeadFlowMapper;
import com.ruoyi.system.mapper.SysDictDataMapper;
import com.ruoyi.system.security.SecurityBusinessActorProvider;
import com.ruoyi.system.service.ISysDictTypeService;
import com.ruoyi.system.service.event.LeadFirstContactHandler;
import com.ruoyi.system.service.event.LeadFirstContactValidator;
import com.ruoyi.system.service.event.LeadInvalidReviewTodoHandler;
import com.ruoyi.system.service.event.LeadProgressHandoffTodoHandler;
import com.ruoyi.system.service.event.LeadRetryTodoHandler;
import com.ruoyi.system.service.event.LeadTodoSourceContextService;
import com.ruoyi.system.service.event.OutboxBusinessEventPublisher;
import com.ruoyi.system.service.impl.SysDictTypeServiceImpl;
import com.ruoyi.system.service.lead.LeadAccessPolicy;
import com.ruoyi.system.service.lead.LeadAssignmentPolicyService;
import com.ruoyi.system.service.lead.LeadCallRecordService;
import com.ruoyi.system.service.lead.LeadDeadPoolService;
import com.ruoyi.system.service.lead.LeadFirstContactService;
import com.ruoyi.system.service.lead.LeadInvalidReviewService;
import com.ruoyi.system.service.lead.LeadPermissionPolicy;
import com.ruoyi.system.service.lead.LeadPoolService;
import com.ruoyi.system.service.lead.LeadProgressCycleService;
import com.ruoyi.system.service.lead.LeadRetryService;
import com.ruoyi.system.service.todo.RuoYiTodoBusinessAccessChecker;

/**
 * Production-port proof for the published lead graph. The test deliberately discovers every
 * migrated identity from MySQL: repository JSON IDs are compiler fixtures, never runtime evidence.
 */
class LeadTodoProductionPortsExternalMysqlIT
{
    @Test
    void migratedLeadGraphExecutesThroughProductionPortsWithoutOrphansOrDuplicates() throws Exception
    {
        String url=requiredEnvironment("TODO_MIGRATION_DB_URL");
        String user=requiredEnvironment("TODO_MIGRATION_DB_USER");
        String password=requiredEnvironment("TODO_MIGRATION_DB_PASSWORD");
        DataSource dataSource=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",url,user,password);
        SqlSessionFactory sessions=new SqlSessionFactoryBuilder().build(myBatis(dataSource));

        try(SqlSession session=sessions.openSession(false))
        {
            Connection connection=session.getConnection();
            connection.setAutoCommit(false);
            try
            {
                PublishedIds published=discoverPublishedIds(connection);
                assertPublishedEntryRule(connection,published);
                Fixtures fixtures=insertFixtures(connection,published);
                Ports ports=productionPorts(session);

                // A. The enabled LEAD_ASSIGNED rule creates the dynamically published TD-001.
                TodoInstance validRoot=trigger(ports.events(),published,fixtures.validLeadId(),
                        fixtures.validLeadNo(),fixtures.ownerId(),fixtures.deptId(),"VALID");
                assertTodo(connection,validRoot.getTodoId(),published.td001(),fixtures.ownerId(),null);
                assertGraphIdentity(connection,validRoot.getTodoId(),validRoot.getTodoId(),"td001");

                // B. A real TD-001 handler writes lead facts and routes VALID to dynamic TD-004.
                authenticate(fixtures.ownerId(),fixtures.deptId(),fixtures.ownerName(),Set.of());
                prepare(ports.commands(),validRoot.getTodoId(),
                        actor(fixtures.ownerId(),fixtures.ownerName(),fixtures.deptId()),"B-valid");
                ports.commands().complete(validRoot.getTodoId(),
                        firstContact("VALID","A-valid",fixtures.validProofId(),true),
                        actor(fixtures.ownerId(),fixtures.ownerName(),fixtures.deptId()));
                TodoRow td004=onlyChild(connection,validRoot.getTodoId(),published.td004());
                assertTodo(connection,td004.todoId(),published.td004(),fixtures.ownerId(),fixtures.ownerId());
                assertGraphIdentity(connection,td004.todoId(),validRoot.getTodoId(),"td004");

                // C. SUSPECT persists its supervisor before TD-002 is created; MISJUDGED reopens TD-001.
                TodoInstance suspectRoot=trigger(ports.events(),published,fixtures.suspectLeadId(),
                        fixtures.suspectLeadNo(),fixtures.ownerId(),fixtures.deptId(),"SUSPECT");
                prepare(ports.commands(),suspectRoot.getTodoId(),
                        actor(fixtures.ownerId(),fixtures.ownerName(),fixtures.deptId()),"C-suspect");
                ports.commands().complete(suspectRoot.getTodoId(),
                        firstContact("SUSPECT_INVALID","C-suspect",fixtures.suspectProofId(),false),
                        actor(fixtures.ownerId(),fixtures.ownerName(),fixtures.deptId()));
                TodoRow td002=onlyChild(connection,suspectRoot.getTodoId(),published.td002());
                Long persistedReviewer=longScalar(connection,
                        "select reviewer_id from biz_lead_invalid_review where lead_id=? and todo_id=?",
                        fixtures.suspectLeadId(),suspectRoot.getTodoId());
                assertEquals(fixtures.reviewerId(),persistedReviewer);
                assertTodo(connection,td002.todoId(),published.td002(),persistedReviewer,persistedReviewer);
                assertGraphIdentity(connection,td002.todoId(),suspectRoot.getTodoId(),"td002");

                authenticate(fixtures.reviewerId(),fixtures.deptId(),fixtures.reviewerName(),
                        Set.of(LeadPermissions.INVALID_REVIEW_HANDLE));
                prepare(ports.commands(),td002.todoId(),
                        actor(fixtures.reviewerId(),fixtures.reviewerName(),fixtures.deptId()),"C-review");
                ports.commands().complete(td002.todoId(),
                        new ActionCommand(action("C-review"),"misjudged",
                                Map.of("reviewResult","MISJUDGED_VALID",
                                        "reviewOpinion","The lead is valid after supervisor review"),
                                List.of()),
                        actor(fixtures.reviewerId(),fixtures.reviewerName(),fixtures.deptId()));
                TodoRow reopened=onlyChild(connection,td002.todoId(),published.td001());
                assertTodo(connection,reopened.todoId(),published.td001(),fixtures.ownerId(),fixtures.ownerId());
                assertGraphIdentity(connection,reopened.todoId(),suspectRoot.getTodoId(),"reopenedTd001");

                // D. UNREACHABLE ends graph routing. Only the real due materializer may create TD-003.
                authenticate(fixtures.ownerId(),fixtures.deptId(),fixtures.ownerName(),Set.of());
                TodoInstance retryRoot=trigger(ports.events(),published,fixtures.retryLeadId(),
                        fixtures.retryLeadNo(),fixtures.ownerId(),fixtures.deptId(),"RETRY");
                prepare(ports.commands(),retryRoot.getTodoId(),
                        actor(fixtures.ownerId(),fixtures.ownerName(),fixtures.deptId()),"D-root");
                ports.commands().complete(retryRoot.getTodoId(),
                        firstContact("UNREACHABLE","D-unreachable",fixtures.retryProofId(),false),
                        actor(fixtures.ownerId(),fixtures.ownerName(),fixtures.deptId()));
                assertEquals(0,countTodos(connection,fixtures.retryLeadId(),published.td003()));
                assertEquals(1,ports.schedules().materializeDue(LocalDateTime.now().plusMinutes(2),100));
                TodoRow firstRetry=onlyTodo(connection,fixtures.retryLeadId(),published.td003());
                assertTodo(connection,firstRetry.todoId(),published.td003(),fixtures.ownerId(),null);
                assertGraphIdentity(connection,firstRetry.todoId(),firstRetry.todoId(),"td003");
                assertOccurrenceLink(connection,firstRetry);
                prepare(ports.commands(),firstRetry.todoId(),
                        actor(fixtures.ownerId(),fixtures.ownerName(),fixtures.deptId()),"E-retry");

                // E. Retained attempts never duplicate TD-003; NEXT_WINDOW is materialized only when due.
                for(int attempt=1;attempt<=2;attempt++)
                {
                    TodoInstance retained=ports.commands().complete(firstRetry.todoId(),
                            retryAttempt("E-retain-"+attempt,fixtures.retryProofId(),attempt),
                            actor(fixtures.ownerId(),fixtures.ownerName(),fixtures.deptId()));
                    assertEquals("SUBMITTED",retained.getStatus());
                    assertEquals(1,countTodos(connection,fixtures.retryLeadId(),published.td003()));
                    assertEquals(attempt,count(connection,
                            "select count(*) from todo_action_log where todo_id=? and action_type='COMPLETE_RETAINED'",
                            firstRetry.todoId()));
                }
                ports.commands().complete(firstRetry.todoId(),
                        retryAttempt("E-limit",fixtures.retryProofId(),3),
                        actor(fixtures.ownerId(),fixtures.ownerName(),fixtures.deptId()));
                assertEquals(1,countTodos(connection,fixtures.retryLeadId(),published.td003()));
                assertEquals("COMPLETED",textScalar(connection,
                        "select status from todo_schedule_occurrence where todo_id=?",firstRetry.todoId()));
                assertEquals("T1_AM",textScalar(connection,
                        "select retry_stage from biz_lead where lead_id=?",fixtures.retryLeadId()));

                LocalDateTime nextWindowDue=dateTimeScalar(connection,
                        "select materialize_at from todo_schedule_window where plan_id=("
                        +"select plan_id from todo_schedule_plan where business_type='LEAD' "
                        +"and business_id=?) and window_code='T1_AM'",fixtures.retryLeadId());
                ports.schedules().materializeDue(nextWindowDue.plusSeconds(1),100);
                assertEquals(2,countTodos(connection,fixtures.retryLeadId(),published.td003()));
                TodoRow secondRetry=latestTodo(connection,fixtures.retryLeadId(),published.td003());
                assertNotEquals(firstRetry.todoId(),secondRetry.todoId());
                assertTodo(connection,secondRetry.todoId(),published.td003(),fixtures.ownerId(),null);
                assertGraphIdentity(connection,secondRetry.todoId(),secondRetry.todoId(),"td003");
                assertOccurrenceLink(connection,secondRetry);
                ports.schedules().materializeDue(nextWindowDue.plusSeconds(1),100);
                assertEquals(2,countTodos(connection,fixtures.retryLeadId(),published.td003()));

                assertNoOrphansOrDuplicates(connection,fixtures,published);
            }
            finally
            {
                SecurityContextHolder.clearContext();
                session.rollback();
            }
        }
    }

    static Ports productionPorts(SqlSession session) throws Exception
    {
        return productionPorts(session,new NoOpTodoCompletionLifecyclePort());
    }

    static Ports productionPorts(SqlSession session,TodoCompletionLifecyclePort completionLifecycle)
            throws Exception
    {
        TodoMapper todos=session.getMapper(TodoMapper.class);
        BizLeadMapper leads=session.getMapper(BizLeadMapper.class);
        LeadFlowMapper facts=session.getMapper(LeadFlowMapper.class);
        BusinessEventMapper outboxMapper=session.getMapper(BusinessEventMapper.class);
        TodoConfigurationMapper configuration=session.getMapper(TodoConfigurationMapper.class);
        TodoOrganizationPort organization=new MapperTodoOrganizationAdapter(todos);
        TodoAssignmentResolver assignments=new TodoAssignmentResolver(organization);
        TodoRoutingService routing=new TodoRoutingService(todos,assignments);
        TodoScheduleService schedules=new TodoScheduleService(todos,routing);
        TodoEventService events=new TodoEventService(todos,assignments);

        BusinessActorProvider actors=new SecurityBusinessActorProvider();
        LeadAccessPolicy leadAccess=new LeadAccessPolicy(leads,actors);
        ISysDictTypeService dictionaries=dictionaries(session.getMapper(SysDictDataMapper.class));
        BusinessEventPublisher outbox=new OutboxBusinessEventPublisher(outboxMapper);
        RuoYiTodoBusinessAccessChecker businessAccess=
                new RuoYiTodoBusinessAccessChecker(leads,null,null,null,null);
        FileAccessPolicy fileAccess=new FileAccessPolicy(
                new MyBatisFileObjectRepository(session.getMapper(FileObjectMapper.class)),
                List.of(businessAccess));
        LeadPermissionPolicy permissions=new LeadPermissionPolicy();
        LeadCallRecordService calls=new LeadCallRecordService(facts,leads,leadAccess,actors,dictionaries,
                fileAccess,permissions,List.of());
        LeadAssignmentPolicyService policies=new LeadAssignmentPolicyService(facts);
        LeadProgressCycleService progressCycles=new LeadProgressCycleService(leads,actors,
                dictionaries,todos,schedules,policies);
        LeadPoolService pool=new LeadPoolService(leads,leadAccess,actors,outbox);
        LeadDeadPoolService deadPool=new LeadDeadPoolService(
                leads,facts,leadAccess,actors,schedules,outbox);
        LeadFirstContactService firstContacts=new LeadFirstContactService(leads,facts,leadAccess,calls,
                actors,dictionaries,organization,schedules,policies,outbox);
        LeadInvalidReviewService reviews=new LeadInvalidReviewService(leads,facts,leadAccess,actors,
                dictionaries,permissions,deadPool,outbox,organization);
        LeadRetryService retries=new LeadRetryService(leads,facts,leadAccess,calls,actors,dictionaries,
                schedules,pool,outbox);

        TodoDodService dod=new TodoDodService(
                List.of(new LeadFirstContactValidator(leads,schedules)),
                List.of(new FileCenterTodoMaterialLookup(fileAccess)),
                new TodoDictionaryValidationMapperAdapter(configuration));
        TodoCommandService commands=new TodoCommandService(todos,new DefaultTodoAccessPolicy(todos),dod,
                List.of(new LeadFirstContactHandler(firstContacts),
                        new LeadInvalidReviewTodoHandler(reviews,
                                new LeadTodoSourceContextService(outboxMapper,facts)),
                        new LeadRetryTodoHandler(retries,schedules),
                        new LeadProgressHandoffTodoHandler(progressCycles)),
                routing,completionLifecycle);
        return new Ports(events,commands,schedules);
    }

    private static ISysDictTypeService dictionaries(SysDictDataMapper mapper)
    {
        return new SysDictTypeServiceImpl()
        {
            @Override public List<SysDictData> selectDictDataByType(String dictType)
            {
                return mapper.selectDictDataByType(dictType);
            }
            @Override public void resetDictCache(){ }
        };
    }

    static PublishedIds discoverPublishedIds(Connection connection) throws Exception
    {
        Map<String,Long> ids=new LinkedHashMap<>();
        try(PreparedStatement query=connection.prepareStatement(
                "select t.template_code,v.version_id from todo_template t "
                +"join todo_template_version v on v.template_id=t.template_id "
                +"where t.template_code in ('TD-001','TD-002','TD-003','TD-004') "
                +"and v.status='PUBLISHED' and not exists(select 1 from todo_template_version newer "
                +"where newer.template_id=v.template_id and newer.status='PUBLISHED' "
                +"and (newer.version_no>v.version_no or "
                +"(newer.version_no=v.version_no and newer.version_id>v.version_id)))"))
        {
            try(ResultSet rows=query.executeQuery())
            {
                while(rows.next())ids.put(rows.getString(1),rows.getLong(2));
            }
        }
        assertEquals(Set.of("TD-001","TD-002","TD-003","TD-004"),ids.keySet());
        assertEquals(4,Set.copyOf(ids.values()).size());
        ids.values().forEach(id->assertTrue(id>4,
                "Migrated production identities must not be repository fixture IDs 1..4"));
        return new PublishedIds(ids.get("TD-001"),ids.get("TD-002"),ids.get("TD-003"),
                ids.get("TD-004"));
    }

    private static void assertPublishedEntryRule(Connection connection,PublishedIds ids) throws Exception
    {
        try(PreparedStatement query=connection.prepareStatement(
                "select r.template_version_id,t.template_code,v.status "
                +"from todo_trigger_rule r join todo_template t on t.template_id=r.template_id "
                +"join todo_template_version v on v.version_id=r.template_version_id "
                +"where r.event_type='LEAD_ASSIGNED' and r.business_type='LEAD' and r.enabled='Y'"))
        {
            try(ResultSet row=query.executeQuery())
            {
                assertTrue(row.next(),"An enabled LEAD_ASSIGNED rule is required");
                assertEquals(ids.td001(),row.getLong(1));
                assertEquals("TD-001",row.getString(2));
                assertEquals("PUBLISHED",row.getString(3));
                assertFalse(row.next(),"LEAD_ASSIGNED must have one enabled production rule");
            }
        }
    }

    static Fixtures insertFixtures(Connection connection,PublishedIds published) throws Exception
    {
        String marker=UUID.randomUUID().toString().replace("-","").substring(0,12);
        String ownerName="i3o_"+marker;
        String reviewerName="i3r_"+marker;
        long deptId=generated(connection,
                "insert into sys_dept(parent_id,ancestors,dept_name,dept_code,order_num,leader,status,"
                +"del_flag,create_by,create_time) values(0,'0',?,?,1,?,'0','0','task8',sysdate())",
                "I3 "+marker,"I3_"+marker,reviewerName);
        long ownerId=insertUser(connection,deptId,ownerName,"I3 owner");
        long reviewerId=insertUser(connection,deptId,reviewerName,"I3 reviewer");
        long fullScopeRole=longScalar(connection,
                "select role_id from sys_role where data_scope='1' and status='0' and del_flag='0' "
                +"order by role_id limit 1");
        update(connection,"insert into sys_user_role(user_id,role_id) values(?,?),(?,?)",
                ownerId,fullScopeRole,reviewerId,fullScopeRole);

        String source="I3_"+marker;
        long valid=insertLead(connection,"I3V"+marker,"I3 valid",source,ownerId,deptId);
        long suspect=insertLead(connection,"I3S"+marker,"I3 suspect",source,ownerId,deptId);
        long retry=insertLead(connection,"I3R"+marker,"I3 retry",source,ownerId,deptId);
        insertRetryPolicy(connection,"I3_POLICY_"+marker,deptId,source,published.td003());
        return new Fixtures(deptId,ownerId,reviewerId,ownerName,reviewerName,
                valid,"I3V"+marker,proof(connection,valid,ownerId,deptId,"valid-"+marker),
                suspect,"I3S"+marker,proof(connection,suspect,ownerId,deptId,"suspect-"+marker),
                retry,"I3R"+marker,proof(connection,retry,ownerId,deptId,"retry-"+marker));
    }

    private static long insertUser(Connection connection,long deptId,String userName,String nickName)
            throws Exception
    {
        return generated(connection,
                "insert into sys_user(dept_id,user_name,nick_name,user_type,password,status,del_flag,"
                +"create_by,create_time) values(?,?,?,'00','external-it','0','0','task8',sysdate())",
                deptId,userName,nickName);
    }

    private static long insertLead(Connection connection,String leadNo,String leadName,String source,
            long ownerId,long deptId) throws Exception
    {
        return generated(connection,
                "insert into biz_lead(lead_no,lead_name,contact_name,mobile,source_code,"
                +"first_contact_status,status,pool_status,disposition,priority,owner_id,dept_id,"
                +"del_flag,row_version,create_by,create_time) "
                +"values(?,?,?,'13800000000',?,'PENDING','1','0','ACTIVE','2',?,?,'0',0,"
                +"'task8',sysdate())",
                leadNo,leadName,leadName,source,ownerId,deptId);
    }

    private static void insertRetryPolicy(Connection connection,String code,long deptId,String source,
            long td003Version) throws Exception
    {
        String json="{\"templateVersionId\":"+td003Version+",\"ruleVersionId\":"
                +td003Version+",\"timezone\":\"Asia/Shanghai\",\"windows\":["
                +"{\"windowCode\":\"T0\",\"windowOrder\":0,\"dayOffset\":0,"
                +"\"startOffsetMinutes\":0,\"durationMinutes\":120,\"maxAttempts\":3,"
                +"\"occurrenceNo\":1},"
                +"{\"windowCode\":\"T1_AM\",\"windowOrder\":1,\"dayOffset\":1,"
                +"\"startTime\":\"09:00:00\",\"endTime\":\"11:00:00\","
                +"\"maxAttempts\":1,\"occurrenceNo\":1},"
                +"{\"windowCode\":\"T1_NOON\",\"windowOrder\":2,\"dayOffset\":1,"
                +"\"startTime\":\"11:00:00\",\"endTime\":\"14:00:00\","
                +"\"maxAttempts\":1,\"occurrenceNo\":1},"
                +"{\"windowCode\":\"T1_PM\",\"windowOrder\":3,\"dayOffset\":1,"
                +"\"startTime\":\"14:00:00\",\"endTime\":\"18:00:00\","
                +"\"maxAttempts\":1,\"occurrenceNo\":1},"
                +"{\"windowCode\":\"T2_AM\",\"windowOrder\":4,\"dayOffset\":2,"
                +"\"startTime\":\"09:00:00\",\"endTime\":\"11:00:00\","
                +"\"maxAttempts\":1,\"occurrenceNo\":1},"
                +"{\"windowCode\":\"T2_NOON\",\"windowOrder\":5,\"dayOffset\":2,"
                +"\"startTime\":\"11:00:00\",\"endTime\":\"14:00:00\","
                +"\"maxAttempts\":1,\"occurrenceNo\":1},"
                +"{\"windowCode\":\"T2_PM\",\"windowOrder\":6,\"dayOffset\":2,"
                +"\"startTime\":\"14:00:00\",\"endTime\":\"18:00:00\","
                +"\"maxAttempts\":1,\"occurrenceNo\":1}]}";
        update(connection,
                "insert into biz_lead_assignment_policy(policy_code,policy_name,sales_dept_id,"
                +"source_code,business_type,retry_rule_json,status,row_version,create_by) "
                +"values(?,?,?,?, 'LEAD',cast(? as json),'ACTIVE',0,'task8')",
                code,code,deptId,source,json);
    }

    private static long proof(Connection connection,long leadId,long ownerId,long deptId,String name)
            throws Exception
    {
        long fileId=generated(connection,
                "insert into file_object(logical_name,status,created_by,create_time) "
                +"values(?,'ACTIVE',?,sysdate())",name+".txt",ownerId);
        update(connection,
                "insert into file_business_relation(file_object_id,business_type,business_id,"
                +"material_type,visibility,scope_dept_id,scope_user_id,created_by,created_dept_id,"
                +"active,create_time) values(?,'LEAD',?,'CONTACT_PROOF','BUSINESS',0,0,?,?,1,sysdate())",
                fileId,leadId,ownerId,deptId);
        return fileId;
    }

    static TodoInstance trigger(TodoEventService events,PublishedIds published,long leadId,
            String leadNo,long ownerId,long ownerDeptId,String suffix)
    {
        List<TodoInstance> created=events.handle(new TodoEvent(
                "I3-"+suffix+"-"+UUID.randomUUID(),"LEAD_ASSIGNED","LEAD",leadId,leadNo,
                Map.of("schemaVersion",1,"assignmentId",leadId,"ownerId",ownerId,
                        "ownerDeptId",ownerDeptId,"operatorId",ownerId),1));
        assertEquals(1,created.size());
        assertEquals(published.td001(),created.get(0).getTemplateVersionId());
        assertEquals(ownerId,created.get(0).getOwnerId());
        return created.get(0);
    }

    static ActionCommand firstContact(String result,String key,long proofId,boolean valid)
    {
        Map<String,Object> fields=new LinkedHashMap<>();
        fields.put("contactResult",result);
        fields.put("contactedAt",LocalDateTime.now().withNano(0).toString());
        if(valid)
        {
            fields.put("name","Production client");
            fields.put("city","Shanghai");
            fields.put("demand","Production-port legal demand");
            fields.put("visited","1");
        }
        if("SUSPECT_INVALID".equals(result))
        {
            fields.put("invalidReasonCode","NO_DEMAND");
            fields.put("salesExplanation","Supervisor review is required");
        }
        fields.put("callRecord",call(key,result,null));
        return new ActionCommand(action(key),"production-port first contact",fields,List.of(proofId));
    }

    static void prepare(TodoCommandService commands,long todoId,Actor actor,String key)
    {
        commands.claim(todoId,new ActionCommand(action(key+"-claim"),null,Map.of(),List.of()),actor);
        commands.start(todoId,new ActionCommand(action(key+"-start"),null,Map.of(),List.of()),actor);
        commands.submit(todoId,new ActionCommand(action(key+"-submit"),null,Map.of(),List.of()),actor);
    }

    static ActionCommand retryAttempt(String key,long proofId,int attempt)
    {
        Map<String,Object> fields=new LinkedHashMap<>();
        fields.put("contactResult","NEXT_WINDOW");
        fields.put("contactedAt",LocalDateTime.now().withNano(0).toString());
        fields.put("attemptCount",attempt);
        fields.put("callRecord",call(key,"NEXT_WINDOW",attempt));
        return new ActionCommand(action(key),"production-port retry",fields,List.of(proofId));
    }

    static Map<String,Object> call(String key,String result,Integer attempt)
    {
        Map<String,Object> call=new LinkedHashMap<>();
        call.put("callChannel","MANUAL");
        call.put("businessOccurrenceKey","I3-CALL-"+key+"-"+UUID.randomUUID());
        call.put("startedAt",LocalDateTime.now().minusMinutes(1).withNano(0).toString());
        call.put("endedAt",LocalDateTime.now().withNano(0).toString());
        call.put("durationSeconds",60);
        call.put("callResult",result);
        call.put("manualNotes","External MySQL production-port proof");
        if(attempt!=null)call.put("attemptCount",attempt);
        return call;
    }

    static void authenticate(long userId,long deptId,String userName,Set<String> permissions)
    {
        SysUser user=new SysUser();
        user.setUserId(userId);user.setDeptId(deptId);user.setUserName(userName);
        user.setNickName(userName);user.setPassword("external-it");user.setStatus("0");
        LoginUser login=new LoginUser(userId,deptId,user,permissions);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(login,null,login.getAuthorities()));
    }

    static Actor actor(long userId,String userName,long deptId)
    {
        return new Actor(userId,userName,deptId);
    }

    static String action(String suffix)
    {
        return "I3-"+suffix+"-"+UUID.randomUUID();
    }

    private static void assertTodo(Connection connection,long todoId,long versionId,long ownerId,
            Long candidateOwner) throws Exception
    {
        try(PreparedStatement query=connection.prepareStatement(
                "select template_version_id,owner_id,route_token,occurrence_key from todo_instance "
                +"where todo_id=?"))
        {
            query.setLong(1,todoId);
            try(ResultSet row=query.executeQuery())
            {
                assertTrue(row.next());
                assertEquals(versionId,row.getLong("template_version_id"));
                assertEquals(ownerId,row.getLong("owner_id"));
                assertNotNull(row.getString("occurrence_key"));
                assertFalse(row.next());
            }
        }
        assertEquals(1,count(connection,
                "select count(*) from todo_relation where todo_id=? and relation_type='PRIMARY'",
                todoId));
        if(candidateOwner!=null)
            assertEquals(1,count(connection,
                    "select count(*) from todo_candidate where todo_id=? and candidate_type='USER' "
                    +"and candidate_value=?",todoId,candidateOwner));
    }

    private static void assertGraphIdentity(Connection connection,long todoId,long rootId,String node)
            throws Exception
    {
        assertEquals(rootId,longScalar(connection,
                "select root_todo_id from todo_instance where todo_id=?",todoId));
        assertEquals(node,textScalar(connection,
                "select route_node_key from todo_instance where todo_id=?",todoId));
        JSONObject token=JSON.parseObject(textScalar(connection,
                "select route_token from todo_instance where todo_id=?",todoId));
        assertEquals(rootId,token.getLongValue("rootTodoId"));
        assertEquals(node,token.getString("nodeKey"));
        assertNull(token.getString("branchKey"));
        assertEquals(0,token.getIntValue("occurrence"));
        assertEquals("ACTIVE",token.getString("status"));
    }

    private static void assertOccurrenceLink(Connection connection,TodoRow todo) throws Exception
    {
        assertNotNull(todo.occurrenceKey());
        assertEquals(todo.todoId(),longScalar(connection,
                "select todo_id from todo_schedule_occurrence where occurrence_key=? and status='MATERIALIZED'",
                todo.occurrenceKey()));
        assertEquals(1,count(connection,
                "select count(*) from todo_schedule_occurrence where occurrence_key=?",
                todo.occurrenceKey()));
    }

    static TodoRow onlyChild(Connection connection,long previousId,long versionId)
            throws Exception
    {
        List<TodoRow> rows=todos(connection,
                "select todo_id,template_version_id,owner_id,root_todo_id,route_node_key,"
                +"occurrence_key,status from todo_instance where previous_todo_id=? "
                +"and template_version_id=? order by todo_id",previousId,versionId);
        assertEquals(1,rows.size());
        return rows.get(0);
    }

    static TodoRow onlyTodo(Connection connection,long leadId,long versionId) throws Exception
    {
        List<TodoRow> rows=todos(connection,
                "select todo_id,template_version_id,owner_id,root_todo_id,route_node_key,"
                +"occurrence_key,status from todo_instance where business_type='LEAD' "
                +"and business_id=? and template_version_id=? order by todo_id",leadId,versionId);
        assertEquals(1,rows.size());
        return rows.get(0);
    }

    static TodoRow latestTodo(Connection connection,long leadId,long versionId) throws Exception
    {
        List<TodoRow> rows=todos(connection,
                "select todo_id,template_version_id,owner_id,root_todo_id,route_node_key,"
                +"occurrence_key,status from todo_instance where business_type='LEAD' "
                +"and business_id=? and template_version_id=? order by todo_id desc limit 1",
                leadId,versionId);
        assertEquals(1,rows.size());
        return rows.get(0);
    }

    private static List<TodoRow> todos(Connection connection,String sql,Object... values)
            throws Exception
    {
        try(PreparedStatement query=connection.prepareStatement(sql))
        {
            bind(query,values);
            try(ResultSet rows=query.executeQuery())
            {
                java.util.ArrayList<TodoRow> result=new java.util.ArrayList<>();
                while(rows.next())result.add(new TodoRow(rows.getLong("todo_id"),
                        rows.getLong("template_version_id"),nullableLong(rows,"owner_id"),
                        nullableLong(rows,"root_todo_id"),rows.getString("route_node_key"),
                        rows.getString("occurrence_key"),rows.getString("status")));
                return List.copyOf(result);
            }
        }
    }

    static int countTodos(Connection connection,long leadId,long versionId) throws Exception
    {
        return count(connection,"select count(*) from todo_instance where business_type='LEAD' "
                +"and business_id=? and template_version_id=?",leadId,versionId);
    }

    private static void assertNoOrphansOrDuplicates(Connection connection,Fixtures fixtures,
            PublishedIds published) throws Exception
    {
        String leads=fixtures.validLeadId()+","+fixtures.suspectLeadId()+","+fixtures.retryLeadId();
        assertEquals(0,count(connection,
                "select count(*) from todo_instance t left join todo_relation r on r.todo_id=t.todo_id "
                +"and r.relation_type='PRIMARY' where t.business_type='LEAD' and t.business_id in ("
                +leads+") and r.todo_id is null"));
        assertEquals(0,count(connection,
                "select count(*) from (select trigger_idempotency_key from todo_instance "
                +"where business_type='LEAD' and business_id in ("+leads+") "
                +"and trigger_idempotency_key is not null group by trigger_idempotency_key "
                +"having count(*)>1) duplicate_triggers"));
        assertEquals(0,count(connection,
                "select count(*) from (select next_idempotency_key from todo_instance "
                +"where business_type='LEAD' and business_id in ("+leads+") "
                +"and next_idempotency_key is not null group by next_idempotency_key "
                +"having count(*)>1) duplicate_routes"));
        assertEquals(0,count(connection,
                "select count(*) from (select occurrence_key from todo_schedule_occurrence "
                +"where plan_id in (select plan_id from todo_schedule_plan where business_type='LEAD' "
                +"and business_id=?) group by occurrence_key having count(*)>1) duplicate_occurrences",
                fixtures.retryLeadId()));
        assertEquals(0,count(connection,
                "select count(*) from todo_instance where business_type='LEAD' and business_id in ("
                +leads+") and template_version_id not in (?,?,?,?)",
                published.td001(),published.td002(),published.td003(),published.td004()));
    }

    static Configuration myBatis(DataSource dataSource) throws Exception
    {
        return myBatis(dataSource,new JdbcTransactionFactory());
    }

    static Configuration myBatis(DataSource dataSource,TransactionFactory transactions) throws Exception
    {
        Configuration configuration=new Configuration(new Environment("lead-todo-production-it",
                transactions,dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
        configuration.getTypeAliasRegistry().registerAliases("com.ruoyi.system.domain");
        configuration.getTypeAliasRegistry().registerAliases("com.ruoyi.common.core.domain.entity");
        for(String resource:new String[]{
                "mapper/todo/TodoMapper.xml",
                "mapper/todo/TodoConfigurationMapper.xml",
                "mapper/system/BizLeadMapper.xml",
                "mapper/system/LeadFlowMapper.xml",
                "mapper/system/BusinessEventMapper.xml",
                "mapper/system/SysDictDataMapper.xml",
                "mapper/system/SysUserMapper.xml",
                "mapper/file/FileObjectMapper.xml"})
        {
            try(InputStream input=Resources.getResourceAsStream(resource))
            {
                new XMLMapperBuilder(input,configuration,resource,
                        configuration.getSqlFragments()).parse();
            }
        }
        return configuration;
    }

    static long generated(Connection connection,String sql,Object... values) throws Exception
    {
        try(PreparedStatement insert=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS))
        {
            bind(insert,values);
            assertEquals(1,insert.executeUpdate());
            try(ResultSet keys=insert.getGeneratedKeys())
            {
                assertTrue(keys.next());
                return keys.getLong(1);
            }
        }
    }

    static int update(Connection connection,String sql,Object... values) throws Exception
    {
        try(PreparedStatement update=connection.prepareStatement(sql))
        {
            bind(update,values);
            return update.executeUpdate();
        }
    }

    static int count(Connection connection,String sql,Object... values) throws Exception
    {
        return Math.toIntExact(longScalar(connection,sql,values));
    }

    static long longScalar(Connection connection,String sql,Object... values) throws Exception
    {
        try(PreparedStatement query=connection.prepareStatement(sql))
        {
            bind(query,values);
            try(ResultSet row=query.executeQuery())
            {
                assertTrue(row.next(),sql);
                long result=row.getLong(1);
                assertFalse(row.wasNull(),sql);
                assertFalse(row.next(),sql);
                return result;
            }
        }
    }

    static String textScalar(Connection connection,String sql,Object... values) throws Exception
    {
        try(PreparedStatement query=connection.prepareStatement(sql))
        {
            bind(query,values);
            try(ResultSet row=query.executeQuery())
            {
                assertTrue(row.next(),sql);
                String result=row.getString(1);
                assertFalse(row.next(),sql);
                return result;
            }
        }
    }

    static LocalDateTime dateTimeScalar(Connection connection,String sql,Object... values)
            throws Exception
    {
        try(PreparedStatement query=connection.prepareStatement(sql))
        {
            bind(query,values);
            try(ResultSet row=query.executeQuery())
            {
                assertTrue(row.next(),sql);
                LocalDateTime result=row.getObject(1,LocalDateTime.class);
                assertFalse(row.next(),sql);
                return result;
            }
        }
    }

    private static void bind(PreparedStatement statement,Object... values) throws Exception
    {
        for(int index=0;index<values.length;index++)statement.setObject(index+1,values[index]);
    }

    private static Long nullableLong(ResultSet row,String column) throws Exception
    {
        long value=row.getLong(column);
        return row.wasNull()?null:value;
    }

    private static String requiredEnvironment(String name)
    {
        String value=System.getenv(name);
        if(value==null||value.isBlank())
            throw new AssertionError(name+" is required; external MySQL tests must never skip");
        return value;
    }

    record PublishedIds(long td001,long td002,long td003,long td004) { }
    record Fixtures(long deptId,long ownerId,long reviewerId,String ownerName,
            String reviewerName,long validLeadId,String validLeadNo,long validProofId,
            long suspectLeadId,String suspectLeadNo,long suspectProofId,
            long retryLeadId,String retryLeadNo,long retryProofId) { }
    record Ports(TodoEventService events,TodoCommandService commands,
            TodoScheduleService schedules) { }
    record TodoRow(long todoId,long templateVersionId,Long ownerId,Long rootTodoId,
            String routeNodeKey,String occurrenceKey,String status) { }
}
