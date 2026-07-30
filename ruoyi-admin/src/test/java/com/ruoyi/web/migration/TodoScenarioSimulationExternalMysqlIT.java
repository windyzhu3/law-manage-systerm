package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;

import com.law.todo.application.TodoAssignmentResolver;
import com.law.todo.application.TodoDefinitionSimulationService;
import com.law.todo.application.TodoSimulationEvidenceService;
import com.law.todo.application.TodoSimulationScenarioCatalog;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoConfigurationCommands.ScenarioSimulationCommand;
import com.law.todo.application.command.TodoDefinitionCommands.SimulateDefinitionCommand;
import com.law.todo.application.command.TodoDefinitionCommands.VirtualTaskCompletionSample;
import com.law.todo.application.view.TodoSimulationScenarioViews.SimulationScenario;
import com.law.todo.application.view.TodoSimulationView;
import com.law.todo.definition.catalog.TodoDecisionService;
import com.law.todo.definition.catalog.TodoEventCatalogService;
import com.law.todo.definition.codec.TodoDefinitionCodec;
import com.law.todo.definition.compiler.TodoDefinitionCompiler;
import com.law.todo.definition.model.TodoDefinitionDocument;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.expression.ConditionValidator;
import com.law.todo.mapper.TodoConfigurationMapper;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.spi.TodoAutoActionCapability;
import com.law.todo.spi.TodoAutoActionCapability.AutoActionResult;
import com.law.todo.spi.TodoAutoActionCapabilityRegistry;
import com.ruoyi.system.service.event.LeadFirstContactHandler;

/**
 * External-MySQL proof for TD-001's governed scenarios. The entire proof runs in one transaction
 * and rolls back, while still asserting that simulation changes only evidence rows.
 */
class TodoScenarioSimulationExternalMysqlIT
{
    private static final List<String> RUNTIME_TABLES=List.of(
            "todo_instance","todo_action_log","business_event","biz_lead","biz_lead_followup",
            "biz_lead_assignment_log","biz_lead_call_record","biz_lead_invalid_review",
            "todo_schedule_plan");
    private static final LocalDateTime NOW=LocalDateTime.of(2026,7,28,9,0);

    @Test
    void threeGovernedScenariosResolveExpectedTargetsWithoutRuntimeWrites() throws Exception
    {
        DataSource dataSource=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",
                required("TODO_MIGRATION_DB_URL"),required("TODO_MIGRATION_DB_USER"),
                required("TODO_MIGRATION_DB_PASSWORD"));
        Configuration configuration=myBatis(dataSource);
        try(SqlSession session=new SqlSessionFactoryBuilder().build(configuration).openSession(false))
        {
            Connection connection=session.getConnection();
            Map<String,TableFingerprint> before=snapshot(connection);
            long evidenceBefore=count(connection,"todo_simulation_evidence");
            TodoMapper todo=session.getMapper(TodoMapper.class);
            TodoConfigurationMapper config=session.getMapper(TodoConfigurationMapper.class);
            GovernedDefinition governed=governedTd001(connection);
            TodoSimulationScenarioCatalog catalog=new TodoSimulationScenarioCatalog(config);
            List<SimulationScenario> scenarios=catalog.scenarios("TD-001","LEAD");
            assertEquals(List.of("TD001_VALID","TD001_SUSPECT_INVALID","TD001_UNREACHABLE"),
                    scenarios.stream().map(SimulationScenario::scenarioCode).toList());

            TodoDefinitionCompiler compiler=new TodoDefinitionCompiler(new TodoDefinitionCodec(),
                    new TodoEventCatalogService(todo),new TodoDecisionService(todo),new ConditionValidator(),
                    new TodoAutoActionCapabilityRegistry(List.of(capability("ESCALATE"))));
            TodoDefinitionSimulationService simulator=new TodoDefinitionSimulationService(todo,
                    new TodoAssignmentResolver(),List.of(new LeadFirstContactHandler(null)),compiler);
            TodoSimulationEvidenceService evidence=new TodoSimulationEvidenceService(config,catalog);
            Map<String,Object> eventPayload=Map.of(
                    "assignmentId",9001L,"operatorId",113L,"ownerId",113L,"ownerDeptId",103L,
                    "schemaVersion",1,"leadId",575L,"businessNo","SENSITIVE-LEAD-NO",
                    "businessName","SENSITIVE-CUSTOMER-NAME");
            Map<String,String> actuals=new LinkedHashMap<>();
            Actor actor=new Actor(113L,"todo_config_admin",103L);

            for(SimulationScenario scenario:scenarios)
            {
                Map<String,Object> completion=completion(scenario.completionPayload());
                TodoSimulationView result=simulator.simulate(governed.versionId(),
                        new SimulateDefinitionCommand(eventPayload,"LEAD",575L,NOW,
                                List.of(new VirtualTaskCompletionSample(
                                        completionNodeKey(governed.compiledJson(),
                                                scenario.completionNodeKey(),config),
                                        Math.max(0,scenario.occurrence()-1),completion,NOW))),
                        "LEAD_ASSIGNED",1,"LEAD");
                String actual=result.routes().stream()
                        .filter(route->route.templateVersionId()!=null
                                &&"PENDING_COMPLETION".equals(route.status()))
                        .map(route->config.selectTemplateCodeByVersionId(route.templateVersionId()))
                        .filter(code->code!=null&&!code.isBlank()).findFirst().orElse(null);
                if(actual==null)
                {
                    TodoInstance todoInstance=new TodoInstance();
                    todoInstance.setTemplateCode("TD-001");todoInstance.setBusinessType("LEAD");
                    todoInstance.setBusinessId(575L);
                    actual=new LeadFirstContactHandler(null).simulate(todoInstance,completion)
                            .producedTemplateCodes().stream().findFirst().orElse(null);
                }
                assertFalse(result.issues().stream().anyMatch(issue->"ERROR".equals(issue.severity())),
                        scenario.scenarioCode()+" routes="+result.routes()+" issues="+result.issues());
                assertEquals(scenario.expectedNextTemplateCode(),actual,
                        scenario.scenarioCode()+" routes="+result.routes()+" issues="+result.issues());
                actuals.put(scenario.scenarioCode(),actual);
                ScenarioSimulationCommand command=new ScenarioSimulationCommand(governed.versionId(),
                        governed.definitionHash(),"LEAD",575L,eventPayload,NOW,
                        "external-mysql-"+scenario.scenarioCode());
                evidence.record(governed.templateId(),scenario,command,actual,true,
                        List.of("EVENT:MATCHED","ROUTING:EVALUATED"),actor);
            }

            assertEquals(Map.of("TD001_VALID","TD-004","TD001_SUSPECT_INVALID","TD-002",
                    "TD001_UNREACHABLE","TD-003"),actuals);
            assertEquals(before,snapshot(connection));
            assertEquals(evidenceBefore+3,count(connection,"todo_simulation_evidence"));
            assertEquals(0,count(connection,"todo_simulation_evidence",
                    "trace_summary_json like '%SENSITIVE-LEAD-NO%'"
                            +" or trace_summary_json like '%SENSITIVE-CUSTOMER-NAME%'"));
            session.rollback();
        }
    }

    private static Configuration myBatis(DataSource dataSource) throws Exception
    {
        Configuration configuration=new Configuration(new Environment("todo-scenario-external-mysql",
                new JdbcTransactionFactory(),dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        parse(configuration,"mapper/todo/TodoMapper.xml");
        parse(configuration,"mapper/todo/TodoConfigurationMapper.xml");
        return configuration;
    }

    private static void parse(Configuration configuration,String resource) throws Exception
    {
        try(InputStream input=Resources.getResourceAsStream(resource))
        {
            new XMLMapperBuilder(input,configuration,resource,configuration.getSqlFragments()).parse();
        }
    }

    private static GovernedDefinition governedTd001(Connection connection) throws Exception
    {
        try(Statement statement=connection.createStatement();ResultSet rows=statement.executeQuery("""
                select t.template_id,v.version_id,v.definition_hash,cast(v.compiled_json as char)
                from todo_template t join todo_template_version v on v.template_id=t.template_id
                where t.template_code='TD-001' and v.status in ('DRAFT','BLOCKED','PUBLISHED')
                  and v.compiled_json is not null and v.definition_hash is not null
                order by case when v.status in ('DRAFT','BLOCKED') then 0 else 1 end,
                         v.version_no desc,v.version_id desc limit 1
                """))
        {
            assertTrue(rows.next(),"A compiled governed TD-001 definition is required");
            return new GovernedDefinition(rows.getLong(1),rows.getLong(2),rows.getString(3),
                    rows.getString(4));
        }
    }

    private static String completionNodeKey(String compiledJson,String reference,
            TodoConfigurationMapper config)
    {
        TodoDefinitionDocument definition=new TodoDefinitionCodec().read(compiledJson);
        Object rawNodes=definition.routing().config().get("nodes");
        assertTrue(rawNodes instanceof List<?>,"The governed route must define nodes");
        List<?> nodes=(List<?>)rawNodes;
        List<Map<?,?>> taskNodes=new ArrayList<>();
        for(Object value:nodes)
            if(value instanceof Map<?,?> node
                    &&"TASK".equals(String.valueOf(node.get("type"))))
                taskNodes.add(node);
        String exact=taskNodes.stream()
                .filter(node->reference.equals(String.valueOf(node.get("key"))))
                .map(node->String.valueOf(node.get("key")))
                .findFirst().orElse(null);
        if(exact!=null)return exact;
        String start=String.valueOf(definition.routing().config().get("start"));
        String startMatch=taskNodes.stream()
                .filter(node->start.equals(String.valueOf(node.get("key")))
                        &&referencesTemplate(node,reference,config))
                .map(node->String.valueOf(node.get("key")))
                .findFirst().orElse(null);
        if(startMatch!=null)return startMatch;
        List<String> matches=taskNodes.stream()
                .filter(node->referencesTemplate(node,reference,config))
                .map(node->String.valueOf(node.get("key")))
                .distinct().toList();
        assertEquals(1,matches.size(),
                "The governed completion reference must resolve to exactly one task node");
        return matches.get(0);
    }

    private static boolean referencesTemplate(Map<?,?> node,String reference,
            TodoConfigurationMapper config)
    {
        Object templateCode=node.get("templateCode");
        if(templateCode!=null&&reference.equals(String.valueOf(templateCode)))return true;
        Object templateVersionId=node.get("templateVersionId");
        if(templateVersionId==null)return false;
        try
        {
            return reference.equals(config.selectTemplateCodeByVersionId(
                    Long.parseLong(String.valueOf(templateVersionId))));
        }
        catch(NumberFormatException ignored){return false;}
    }

    private static Map<String,Object> completion(Map<String,Object> source)
    {
        Map<String,Object> result=new LinkedHashMap<>();
        source.forEach((key,value)->result.put(key,"${SIMULATION_NOW}".equals(value)?NOW.toString():value));
        return result;
    }

    private static Map<String,TableFingerprint> snapshot(Connection connection) throws Exception
    {
        Map<String,TableFingerprint> result=new LinkedHashMap<>();
        for(String table:RUNTIME_TABLES)result.put(table,fingerprint(connection,table));
        return Map.copyOf(result);
    }

    private static TableFingerprint fingerprint(Connection connection,String table) throws Exception
    {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");long count=0;
        try(Statement statement=connection.createStatement();
                ResultSet rows=statement.executeQuery("select * from "+table+" order by 1"))
        {
            ResultSetMetaData metadata=rows.getMetaData();
            while(rows.next())
            {
                count++;
                for(int column=1;column<=metadata.getColumnCount();column++)
                {
                    String value=rows.getString(column);
                    digest.update((value==null?"<NULL>":value).getBytes(StandardCharsets.UTF_8));
                    digest.update((byte)0);
                }
                digest.update((byte)'\n');
            }
        }
        return new TableFingerprint(count,HexFormat.of().formatHex(digest.digest()));
    }

    private static long count(Connection connection,String table) throws Exception
    {return count(connection,table,"1=1");}

    private static long count(Connection connection,String table,String where) throws Exception
    {
        try(Statement statement=connection.createStatement();
                ResultSet rows=statement.executeQuery("select count(*) from "+table+" where "+where))
        {rows.next();return rows.getLong(1);}
    }

    private static String required(String name)
    {
        String value=System.getenv(name);
        if(value==null||value.isBlank())throw new IllegalStateException(name+" is required");
        return value;
    }

    private static TodoAutoActionCapability capability(String actionType)
    {
        return new TodoAutoActionCapability()
        {
            @Override public String actionType(){return actionType;}
            @Override public Descriptor descriptor()
            {
                return new Descriptor(actionType,List.of("DUE","SLA_80","SLA_100","SLA_150"),
                        Descriptor.commonRetryFields(),List.of());
            }
            @Override public AutoActionResult execute(TodoInstance todo,
                    com.law.todo.definition.model.TodoDefinitionDocument.AutoActionRule rule,Actor actor)
            {return AutoActionResult.success();}
        };
    }

    private record GovernedDefinition(long templateId,long versionId,String definitionHash,
            String compiledJson) { }
    private record TableFingerprint(long rows,String sha256) { }
}
