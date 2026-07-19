package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.sql.DataSource;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.law.todo.application.HistoricalMigrationExportArchiveWriter;
import com.law.todo.application.TodoAcceptanceReadinessService;
import com.law.todo.application.TodoFileSecurityReadinessService;
import com.law.todo.application.TodoFinanceReadinessService;
import com.law.todo.application.TodoFoundationAdmissionReadinessService;
import com.law.todo.application.TodoFoundationResourceService;
import com.law.todo.application.TodoHistoricalMigrationExportService;
import com.law.todo.application.TodoHistoricalMigrationPreflightService;
import com.law.todo.application.TodoHistoricalMigrationReadinessService;
import com.law.todo.application.view.HistoricalCaseGroupView;
import com.law.todo.application.view.HistoricalMigrationExportArtifact;
import com.law.todo.application.view.TodoFoundationAdmissionReadinessView;
import com.law.todo.application.view.TodoHistoricalMigrationPreflightView;
import com.law.todo.mapper.TodoAcceptanceReadinessMapper;
import com.law.todo.mapper.TodoFileSecurityReadinessMapper;
import com.law.todo.mapper.TodoFinanceReadinessMapper;
import com.law.todo.mapper.TodoFoundationAdmissionReadinessMapper;
import com.law.todo.mapper.TodoFoundationResourceMapper;
import com.law.todo.mapper.TodoHistoricalMigrationReadinessMapper;

class HistoricalMigrationPreflightEndToEndTest
{
    private static final Instant GENERATED_AT=Instant.parse("2026-07-18T12:00:00Z");
    private static final Clock FIXED_CLOCK=Clock.fixed(GENERATED_AT,ZoneOffset.UTC);
    private static final List<String> MAPPERS=List.of(
            "mapper/todo/TodoHistoricalMigrationReadinessMapper.xml",
            "mapper/todo/TodoFoundationAdmissionReadinessMapper.xml",
            "mapper/todo/TodoFoundationResourceMapper.xml",
            "mapper/todo/TodoFileSecurityReadinessMapper.xml",
            "mapper/todo/TodoFinanceReadinessMapper.xml",
            "mapper/todo/TodoAcceptanceReadinessMapper.xml");
    private static final List<String> CSV_COLUMNS=List.of("case_id","case_no","case_name","case_type","case_status",
            "contract_id","main_lawyer_id","dept_id","proposed_business_line","exception_reason","review_status",
            "reviewer_user_id","reviewed_at");

    @Test
    void preflightAndExportAreReadOnlyAndDeterministic(@TempDir Path artifactRoot) throws Exception
    {
        String url=System.getenv("TODO_MIGRATION_DB_URL");
        assumeTrue(url!=null&&!url.isBlank(),"Migration database is provided by the CI quality gate");
        String user=System.getenv("TODO_MIGRATION_DB_USER"),password=System.getenv("TODO_MIGRATION_DB_PASSWORD");
        Flyway flyway=Flyway.configure().dataSource(url,user,password).baselineOnMigrate(true).baselineVersion("0.15.0")
                .locations("classpath:db/migration").load();
        assertTrue(flyway.migrate().success);
        assertEquals("0.20.28",flyway.info().current().getVersion().getVersion());

        DataSource dataSource=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",url,user,password);
        Configuration configuration=myBatis(dataSource);
        try {
            try(SqlSession session=new SqlSessionFactoryBuilder().build(configuration).openSession(false)) {
                withRollback(session.getConnection(),connection->{
                    connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                    insertCaseFixtures(connection);
                    Snapshot before=snapshot(connection);
                    List<Map<String,Object>> candidates=rows(connection,"select case_id,case_no,case_name,case_type,"
                            +"case_status,contract_id,main_lawyer_id,dept_id from biz_case where del_flag='0' order by case_id");
                    long deleted=count(connection,"select count(*) from biz_case where del_flag<>'0'");
                    assertBaselineTruth(connection,before);

                    TodoHistoricalMigrationReadinessMapper migrationMapper=
                            session.getMapper(TodoHistoricalMigrationReadinessMapper.class);
                    TodoHistoricalMigrationPreflightView preflight=preflight(migrationMapper).preflight("G-04");
                    assertEquals(GENERATED_AT,preflight.generatedAt());
                    assertEquals(candidates.size(),preflight.activeCaseCount());
                    assertEquals(candidates.size(),preflight.exceptionCandidateCount());
                    assertEquals(deleted,preflight.deletedCaseCount());
                    assertEquals(before.todoInstances().size(),preflight.historicalTodoCount());
                    assertEquals(before.orphanTodoVersionCount(),preflight.orphanTodoVersionCount());
                    assertEquals(expectedGroups(connection),preflight.groups());

                    HistoricalMigrationExportArchiveWriter writer=writer(artifactRoot);
                    TodoHistoricalMigrationExportService exports=
                            new TodoHistoricalMigrationExportService(migrationMapper,writer);
                    byte[] archive;
                    try(HistoricalMigrationExportArtifact artifact=exports.export("G-04")) {
                        archive=artifact.input().readAllBytes();
                        assertEquals(candidates.size(),artifact.rowCount());
                        assertEquals(GENERATED_AT,artifact.generatedAt());
                        assertEquals(archive.length,artifact.sizeBytes());
                        validateArchive(archive,artifact.csvSha256(),candidates);
                    }
                    assertArtifactRootEmpty(artifactRoot);

                    Snapshot after=snapshot(connection);
                    assertEquals(before,after,"Preflight and export must not mutate business, Todo or governance rows");
                    assertBaselineTruth(connection,after);
                    assertFoundationNotAdmitted(session,migrationMapper);
                });
            }
        }
        finally {
            assertFixtureCount(dataSource,"g04-e2e",0);
        }
        assertArtifactRootEmpty(artifactRoot);
        assertRollbackGuardRemovesPartiallyInsertedBatchAfterFailure(dataSource);
    }

    private static void assertRollbackGuardRemovesPartiallyInsertedBatchAfterFailure(DataSource dataSource)
            throws Exception
    {
        BatchUpdateException failure;
        try(Connection connection=dataSource.getConnection()) {
            failure=assertThrows(BatchUpdateException.class,()->withRollback(connection,transaction->{
                insertPartiallyFailingFixtures(transaction);
            }));
            connection.commit();
        }
        assertTrue(Arrays.stream(failure.getUpdateCounts()).anyMatch(value->value==1));
        assertFixtureCount(dataSource,"g04-e2e-batch-failure",0);
    }

    private static void withRollback(Connection connection,SqlWork work) throws Exception
    {
        connection.setAutoCommit(false);
        try {
            work.run(connection);
        }
        finally {
            connection.rollback();
        }
    }

    private static void insertPartiallyFailingFixtures(Connection connection) throws Exception
    {
        String sql="insert into biz_case(case_no,case_name,contract_id,case_type,case_status,main_lawyer_id,dept_id,"
                +"del_flag,create_by,create_time) values(?,?,?,?,?,?,?,?,?,?)";
        try(PreparedStatement insert=connection.prepareStatement(sql)) {
            addCase(insert,"G04-E2E-BATCH-FAILURE","partial batch fixture",984101L,
                    "CIVIL","OPEN",null,null,"0","g04-e2e-batch-failure");
            addCase(insert,"G04-E2E-BATCH-FAILURE","duplicate batch fixture",984102L,
                    "CIVIL","OPEN",null,null,"0","g04-e2e-batch-failure");
            try {
                insert.executeBatch();
            }
            catch(BatchUpdateException failure) {
                assertEquals(1,count(connection,
                        "select count(*) from biz_case where create_by='g04-e2e-batch-failure'"));
                throw failure;
            }
        }
    }

    private static void assertFixtureCount(DataSource dataSource,String createBy,long expected) throws Exception
    {
        try(Connection cleanupProof=dataSource.getConnection();
                PreparedStatement select=cleanupProof.prepareStatement(
                        "select count(*) from biz_case where create_by=?")) {
            select.setString(1,createBy);
            try(ResultSet result=select.executeQuery()) {
                assertTrue(result.next());
                assertEquals(expected,result.getLong(1));
            }
        }
    }

    private static Configuration myBatis(DataSource dataSource) throws Exception
    {
        Configuration configuration=new Configuration(new Environment("historical-migration-e2e",
                new JdbcTransactionFactory(),dataSource));
        for(String mapper:MAPPERS)try(InputStream input=Resources.getResourceAsStream(mapper)) {
            new XMLMapperBuilder(input,configuration,mapper,configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    private static void insertCaseFixtures(Connection connection) throws Exception
    {
        String sql="insert into biz_case(case_no,case_name,contract_id,case_type,case_status,main_lawyer_id,dept_id,"
                +"del_flag,create_by,create_time) values(?,?,?,?,?,?,?,?,?,?)";
        try(PreparedStatement insert=connection.prepareStatement(sql)) {
            addCase(insert,"G04-E2E-ACTIVE-001","=HYPERLINK(\"https://例.example\")",984001L,
                    "民事","OPEN",null,701L,"0");
            addCase(insert,"G04-E2E-ACTIVE-002","中文案件 \"quoted\"\n第二行",984002L,
                    null,null,801L,null,"0");
            addCase(insert,"G04-E2E-DELETED-001","已删除案件",984003L,
                    "CIVIL","CLOSED",802L,702L,"2");
            assertEquals(3,Arrays.stream(insert.executeBatch()).filter(value->value==1||value==Statement.SUCCESS_NO_INFO).count());
        }
    }

    private static void addCase(PreparedStatement insert,String caseNo,String caseName,long contractId,String caseType,
            String caseStatus,Long lawyerId,Long deptId,String delFlag) throws Exception
    {addCase(insert,caseNo,caseName,contractId,caseType,caseStatus,lawyerId,deptId,delFlag,"g04-e2e");}

    private static void addCase(PreparedStatement insert,String caseNo,String caseName,long contractId,String caseType,
            String caseStatus,Long lawyerId,Long deptId,String delFlag,String createBy) throws Exception
    {
        insert.setString(1,caseNo);insert.setString(2,caseName);insert.setLong(3,contractId);
        insert.setString(4,caseType);insert.setString(5,caseStatus);
        if(lawyerId==null)insert.setNull(6,java.sql.Types.BIGINT);else insert.setLong(6,lawyerId);
        if(deptId==null)insert.setNull(7,java.sql.Types.BIGINT);else insert.setLong(7,deptId);
        insert.setString(8,delFlag);insert.setString(9,createBy);
        insert.setTimestamp(10,java.sql.Timestamp.from(GENERATED_AT));insert.addBatch();
    }

    private static Snapshot snapshot(Connection connection) throws Exception
    {
        return new Snapshot(
                rows(connection,"select * from biz_case order by case_id"),
                rows(connection,"select * from todo_instance order by todo_id"),
                rows(connection,"select * from todo_foundation_migration_requirement where gate_code='G-04' "
                        +"order by sort_order,requirement_id"),
                rows(connection,"select * from todo_admission_evidence where evidence_code='G04-HISTORICAL-MIGRATION' "
                        +"order by evidence_id"),
                rows(connection,"select * from todo_decision where decision_code='Q-001' order by decision_id"),
                count(connection,"select count(*) from todo_instance i left join todo_template_version v "
                        +"on v.version_id=i.template_version_id where v.version_id is null"));
    }

    private static void assertBaselineTruth(Connection connection,Snapshot snapshot) throws Exception
    {
        assertEquals(0,count(connection,"select count(*) from information_schema.columns where table_schema=database() "
                +"and table_name='biz_case' and column_name='business_line'"));
        assertEquals(8,snapshot.g04Requirements().size());
        assertEquals(3,sourceCount(snapshot,"CONFIRMED"));
        assertEquals(1,sourceCount(snapshot,"NEEDS_DECISION"));
        assertEquals(4,sourceCount(snapshot,"NEEDS_EVIDENCE"));
        assertEquals(5,snapshot.g04Requirements().stream()
                .filter(row->!"CONFIRMED".equals(row.get("source_status"))).count());
        assertEquals(1,snapshot.g04Evidence().size());
        assertEquals("OPEN",snapshot.g04Evidence().get(0).get("status"));
        assertEquals(1,snapshot.q001().size());
        assertEquals("OPEN",snapshot.q001().get(0).get("status"));
        assertEquals(0,snapshot.orphanTodoVersionCount());
    }

    private static long sourceCount(Snapshot snapshot,String status)
    {return snapshot.g04Requirements().stream().filter(row->status.equals(row.get("source_status"))).count();}

    private static List<HistoricalCaseGroupView> expectedGroups(Connection connection) throws Exception
    {
        return rows(connection,"select coalesce(case_status,'<NULL>') case_status,"
                +"coalesce(case_type,'<NULL>') case_type,count(*) case_count from biz_case where del_flag='0' "
                +"group by coalesce(case_status,'<NULL>'),coalesce(case_type,'<NULL>') "
                +"order by case_count desc,case_status,case_type").stream()
                .map(row->new HistoricalCaseGroupView(String.valueOf(row.get("case_status")),
                        String.valueOf(row.get("case_type")),((Number)row.get("case_count")).longValue()))
                .toList();
    }

    private static void assertFoundationNotAdmitted(SqlSession session,
            TodoHistoricalMigrationReadinessMapper migrationMapper)
    {
        TodoFoundationAdmissionReadinessService service=new TodoFoundationAdmissionReadinessService(
                session.getMapper(TodoFoundationAdmissionReadinessMapper.class),
                new TodoFoundationResourceService(session.getMapper(TodoFoundationResourceMapper.class)),
                new TodoHistoricalMigrationReadinessService(migrationMapper),
                new TodoFileSecurityReadinessService(session.getMapper(TodoFileSecurityReadinessMapper.class)),
                new TodoFinanceReadinessService(session.getMapper(TodoFinanceReadinessMapper.class)),
                new TodoAcceptanceReadinessService(session.getMapper(TodoAcceptanceReadinessMapper.class)));
        TodoFoundationAdmissionReadinessView readiness=service.readiness();
        assertEquals("NOT_ADMITTED",readiness.overallStatus());
        assertFalse(readiness.admitted());
        assertEquals(2,readiness.readyGateCount());
        assertEquals(8,readiness.totalGateCount());
        assertFalse(readiness.gates().stream().filter(gate->"G-04".equals(gate.gateCode())).findFirst().orElseThrow().ready());
    }

    private static TodoHistoricalMigrationPreflightService preflight(TodoHistoricalMigrationReadinessMapper mapper)
            throws Exception
    {
        Constructor<TodoHistoricalMigrationPreflightService> constructor=TodoHistoricalMigrationPreflightService.class
                .getDeclaredConstructor(TodoHistoricalMigrationReadinessMapper.class,Clock.class);
        constructor.setAccessible(true);
        return constructor.newInstance(mapper,FIXED_CLOCK);
    }

    private static HistoricalMigrationExportArchiveWriter writer(Path root) throws Exception
    {
        Constructor<HistoricalMigrationExportArchiveWriter> constructor=HistoricalMigrationExportArchiveWriter.class
                .getDeclaredConstructor(Path.class,Clock.class);
        constructor.setAccessible(true);
        return constructor.newInstance(root,FIXED_CLOCK);
    }

    private static void validateArchive(byte[] archive,String reportedSha,List<Map<String,Object>> candidates)
            throws Exception
    {
        Map<String,byte[]> entries=zipEntries(archive);
        assertEquals(List.of("historical-case-exceptions.csv","manifest.json"),new ArrayList<>(entries.keySet()));
        byte[] csvBytes=entries.get("historical-case-exceptions.csv");
        assertNotNull(csvBytes);
        assertEquals(expectedCsv(candidates),new String(csvBytes,StandardCharsets.UTF_8));
        assertEquals(sha256(csvBytes),reportedSha);
        String csv=new String(csvBytes,StandardCharsets.UTF_8);
        assertTrue(csv.contains("\"'=HYPERLINK(\"\"https://例.example\"\")\""));
        assertTrue(csv.contains("\"中文案件 \"\"quoted\"\"\n第二行\""));
        assertFalse(csv.contains("G04-E2E-DELETED-001"));

        JSONObject manifest=JSON.parseObject(entries.get("manifest.json"));
        assertEquals(List.of("schemaVersion","gateCode","fileName","rowCount","csvSha256","generatedAt",
                "classificationState","allowedBusinessLines"),new ArrayList<>(manifest.keySet()));
        assertEquals(1,manifest.getIntValue("schemaVersion"));
        assertEquals("G-04",manifest.getString("gateCode"));
        assertEquals("historical-case-exceptions.csv",manifest.getString("fileName"));
        assertEquals(GENERATED_AT.toString(),manifest.getString("generatedAt"));
        assertEquals(candidates.size(),manifest.getLongValue("rowCount"));
        assertEquals(reportedSha,manifest.getString("csvSha256"));
        assertEquals("UNREVIEWED",manifest.getString("classificationState"));
        assertEquals(List.of("NON_LITIGATION","COMPREHENSIVE","EXECUTION"),
                manifest.getList("allowedBusinessLines",String.class));
    }

    private static String expectedCsv(List<Map<String,Object>> candidates)
    {
        StringBuilder csv=new StringBuilder("\ufeff");
        appendCsvRow(csv,new ArrayList<>(CSV_COLUMNS));
        for(Map<String,Object> candidate:candidates)appendCsvRow(csv,Arrays.asList(
                candidate.get("case_id"),candidate.get("case_no"),candidate.get("case_name"),
                candidate.get("case_type"),candidate.get("case_status"),candidate.get("contract_id"),
                candidate.get("main_lawyer_id"),candidate.get("dept_id"),"","NO_REVIEWED_CLASSIFICATION","PENDING","",""));
        return csv.toString();
    }

    private static void appendCsvRow(StringBuilder csv,List<?> values)
    {
        for(int index=0;index<values.size();index++) {
            if(index>0)csv.append(',');
            csv.append('"').append(csvEscape(values.get(index))).append('"');
        }
        csv.append("\r\n");
    }

    private static String csvEscape(Object value)
    {
        String text=value==null?"":String.valueOf(value);
        if(!text.isEmpty()&&"=+-@\t\r".indexOf(text.charAt(0))>=0)text="'"+text;
        return text.replace("\"","\"\"");
    }

    private static Map<String,byte[]> zipEntries(byte[] archive) throws Exception
    {
        Map<String,byte[]> entries=new LinkedHashMap<>();
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(archive),StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while((entry=zip.getNextEntry())!=null)entries.put(entry.getName(),zip.readAllBytes());
        }
        return entries;
    }

    private static String sha256(byte[] value) throws Exception
    {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}

    private static List<Map<String,Object>> rows(Connection connection,String sql) throws Exception
    {
        List<Map<String,Object>> result=new ArrayList<>();
        try(Statement statement=connection.createStatement();ResultSet rows=statement.executeQuery(sql)) {
            ResultSetMetaData metadata=rows.getMetaData();
            while(rows.next()) {
                Map<String,Object> row=new LinkedHashMap<>();
                for(int index=1;index<=metadata.getColumnCount();index++) {
                    Object value=rows.getObject(index);
                    if(value instanceof byte[] bytes)value=HexFormat.of().formatHex(bytes);
                    row.put(metadata.getColumnLabel(index).toLowerCase(java.util.Locale.ROOT),value);
                }
                result.add(Collections.unmodifiableMap(new LinkedHashMap<>(row)));
            }
        }
        return List.copyOf(result);
    }

    private static long count(Connection connection,String sql) throws Exception
    {
        try(Statement statement=connection.createStatement();ResultSet rows=statement.executeQuery(sql)) {
            assertTrue(rows.next());return rows.getLong(1);
        }
    }

    private static void assertArtifactRootEmpty(Path root) throws Exception
    {try(var files=Files.list(root)){assertTrue(files.findAny().isEmpty(),"Export artifacts must be deleted on close");}}

    private record Snapshot(List<Map<String,Object>> cases,List<Map<String,Object>> todoInstances,
            List<Map<String,Object>> g04Requirements,List<Map<String,Object>> g04Evidence,
            List<Map<String,Object>> q001,long orphanTodoVersionCount) { }

    @FunctionalInterface
    private interface SqlWork
    {void run(Connection connection) throws Exception;}
}
