package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.LocalDateTime;
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

import com.law.todo.application.TodoSimulationEvidenceService;
import com.law.todo.application.TodoSimulationReadinessService;
import com.law.todo.application.TodoSimulationReadinessService.BatchRequest;
import com.law.todo.application.TodoSimulationScenarioCatalog;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoConfigurationCommands.JourneySimulationCommand;
import com.law.todo.application.command.TodoConfigurationCommands.ScenarioSimulationCommand;
import com.law.todo.application.view.TodoSimulationReadinessView;
import com.law.todo.application.view.TodoSimulationScenarioViews.SimulationScenario;
import com.law.todo.definition.compiler.DefinitionValidationReport;
import com.law.todo.mapper.TodoConfigurationMapper;

/**
 * Proves the simulation double gate against the real MySQL mapper SQL. The test
 * rolls back all evidence rows and asserts that no runtime business table changes.
 */
class TodoSimulationReadinessExternalMysqlIT
{
    private static final LocalDateTime NOW=LocalDateTime.of(2026,7,30,9,0);
    private static final List<String> RUNTIME_TABLES=List.of(
            "biz_lead","todo_instance","todo_action_log","business_event");

    @Test
    void publicationRequiresCurrentScenarioAndFullSimulationEvidence() throws Exception
    {
        String url=MigrationTestDatabase.migrate();
        DataSource dataSource=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",url,
                MigrationTestDatabase.user(),MigrationTestDatabase.password());
        Configuration configuration=myBatis(dataSource);
        try(SqlSession session=new SqlSessionFactoryBuilder().build(configuration).openSession(false))
        {
            Connection connection=session.getConnection();
            GovernedDefinition governed=governedTd001(connection);
            clearEvidence(connection,governed);
            Map<String,TableFingerprint> before=snapshot(connection);
            long evidenceBefore=count(connection,"todo_simulation_evidence");

            TodoConfigurationMapper mapper=session.getMapper(TodoConfigurationMapper.class);
            TodoSimulationScenarioCatalog catalog=new TodoSimulationScenarioCatalog(mapper);
            TodoSimulationEvidenceService evidence=new TodoSimulationEvidenceService(mapper,catalog);
            TodoSimulationReadinessService readiness=
                    new TodoSimulationReadinessService(catalog,evidence,mapper);
            List<SimulationScenario> scenarios=catalog.scenarios("TD-001","LEAD");
            Actor actor=new Actor(113L,"todo_config_admin",103L);

            TodoSimulationReadinessView initial=readiness.readiness(
                    governed.templateId(),governed.versionId(),governed.definitionHash(),
                    "TD-001","LEAD");
            assertFalse(initial.publicationReady());
            assertFalse(initial.fullSimulationPassed());

            for(SimulationScenario scenario:scenarios.stream()
                    .filter(SimulationScenario::requiredForPublish).toList())
            {
                evidence.record(governed.templateId(),scenario,
                        new ScenarioSimulationCommand(governed.versionId(),
                                governed.definitionHash(),"LEAD",575L,Map.of(),NOW,
                                "readiness-mysql-"+scenario.scenarioCode()),
                        scenario.expectedNextTemplateCode(),true,
                        List.of("EVENT:MATCHED","ROUTING:EVALUATED"),actor);
            }

            TodoSimulationReadinessView scenariosPassed=readiness.readiness(
                    governed.templateId(),governed.versionId(),governed.definitionHash(),
                    "TD-001","LEAD");
            assertEquals(3,scenariosPassed.requiredScenarioCount());
            assertEquals(3,scenariosPassed.passedScenarioCount());
            assertFalse(scenariosPassed.fullSimulationPassed());
            assertFalse(scenariosPassed.publicationReady());
            assertTrue(preflight(readiness,governed).errors().stream()
                    .anyMatch(issue->"TODO_FULL_SIMULATION_REQUIRED".equals(issue.code())));

            JourneySimulationCommand fullCommand=new JourneySimulationCommand(
                    governed.templateId(),governed.versionId(),"LEAD_ASSIGNED",1,
                    "LEAD",575L,Map.of(),NOW,List.of(),governed.definitionHash());
            evidence.recordFull(governed.templateId(),fullCommand,true,
                    List.of("EVENT","TRIGGER","OWNER","DOD","SLA","ROUTING"),actor);

            TodoSimulationReadinessView ready=readiness.readiness(
                    governed.templateId(),governed.versionId(),governed.definitionHash(),
                    "TD-001","LEAD");
            assertTrue(ready.fullSimulationPassed());
            assertTrue(ready.publicationReady());
            assertTrue(preflight(readiness,governed).errors().isEmpty());

            TodoSimulationReadinessView batch=readiness.readinessBatch(List.of(
                    new BatchRequest(governed.templateId(),governed.versionId(),
                            governed.definitionHash(),"DRAFT"))).get(governed.versionId());
            assertTrue(batch.publicationReady());
            assertEquals(before,snapshot(connection));
            assertEquals(evidenceBefore+4,count(connection,"todo_simulation_evidence"));
            assertEquals(4,count(connection,"todo_simulation_evidence",
                    "template_id="+governed.templateId()+" and version_id="+governed.versionId()
                            +" and definition_hash='"+governed.definitionHash()+"'"
                            +" and result_status='PASSED'"));
            session.rollback();
        }
    }

    private static DefinitionValidationReport preflight(
            TodoSimulationReadinessService readiness,GovernedDefinition governed)
    {
        DefinitionValidationReport base=new DefinitionValidationReport(
                List.of(),List.of(),governed.compiledJson(),governed.definitionHash());
        return readiness.applyPreflightGate(governed.versionId(),base);
    }

    private static Configuration myBatis(DataSource dataSource) throws Exception
    {
        Configuration configuration=new Configuration(new Environment(
                "todo-readiness-external-mysql",new JdbcTransactionFactory(),dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        try(InputStream input=Resources.getResourceAsStream(
                "mapper/todo/TodoConfigurationMapper.xml"))
        {
            new XMLMapperBuilder(input,configuration,
                    "mapper/todo/TodoConfigurationMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    private static GovernedDefinition governedTd001(Connection connection) throws Exception
    {
        try(Statement statement=connection.createStatement();ResultSet rows=statement.executeQuery("""
                select t.template_id,v.version_id,v.definition_hash,cast(v.compiled_json as char)
                from todo_template t
                join todo_template_version v on v.template_id=t.template_id
                where t.template_code='TD-001'
                  and v.status in ('DRAFT','BLOCKED','PUBLISHED')
                  and v.compiled_json is not null
                  and v.definition_hash is not null
                order by case when v.status in ('DRAFT','BLOCKED') then 0 else 1 end,
                         v.version_no desc,v.version_id desc
                limit 1
                """))
        {
            assertTrue(rows.next(),"A compiled governed TD-001 definition is required");
            return new GovernedDefinition(rows.getLong(1),rows.getLong(2),
                    rows.getString(3),rows.getString(4));
        }
    }

    private static void clearEvidence(Connection connection,GovernedDefinition governed)
            throws Exception
    {
        try(PreparedStatement statement=connection.prepareStatement("""
                delete from todo_simulation_evidence
                where template_id=? and version_id=? and definition_hash=?
                """))
        {
            statement.setLong(1,governed.templateId());
            statement.setLong(2,governed.versionId());
            statement.setString(3,governed.definitionHash());
            statement.executeUpdate();
        }
    }

    private static Map<String,TableFingerprint> snapshot(Connection connection) throws Exception
    {
        Map<String,TableFingerprint> result=new LinkedHashMap<>();
        for(String table:RUNTIME_TABLES)result.put(table,fingerprint(connection,table));
        return Map.copyOf(result);
    }

    private static TableFingerprint fingerprint(Connection connection,String table) throws Exception
    {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        long count=0;
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
                    digest.update((value==null?"<NULL>":value)
                            .getBytes(StandardCharsets.UTF_8));
                    digest.update((byte)0);
                }
                digest.update((byte)'\n');
            }
        }
        return new TableFingerprint(count,HexFormat.of().formatHex(digest.digest()));
    }

    private static long count(Connection connection,String table) throws Exception
    {
        return count(connection,table,"1=1");
    }

    private static long count(Connection connection,String table,String where) throws Exception
    {
        try(Statement statement=connection.createStatement();
                ResultSet rows=statement.executeQuery(
                        "select count(*) from "+table+" where "+where))
        {
            rows.next();
            return rows.getLong(1);
        }
    }

    private record GovernedDefinition(long templateId,long versionId,
            String definitionHash,String compiledJson) { }
    private record TableFingerprint(long rows,String sha256) { }
}
