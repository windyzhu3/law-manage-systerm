package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class TodoTriggerRuleMetadataMigrationContractTest
{
    @Test
    void externalMysqlMigratesExactPreMetadataTriggerRowsFrom02034To02035() throws Exception
    {
        String url=System.getenv("TODO_MIGRATION_DB_URL");
        assumeTrue(url!=null&&!url.isBlank(),"Migration database is provided by the CI quality gate");
        String user=System.getenv("TODO_MIGRATION_DB_USER"),password=System.getenv("TODO_MIGRATION_DB_PASSWORD");
        Flyway before=Flyway.configure().dataSource(url,user,password).baselineOnMigrate(true).baselineVersion("0.15.0")
                .locations("classpath:db/migration").target("0.20.34").load();
        assertTrue(before.migrate().success);
        assertEquals("0.20.34",before.info().current().getVersion().getVersion());

        try(Connection connection=DriverManager.getConnection(url,user,password)) {
            Seed seed=seedPreMetadataTriggerRows(connection);
            Flyway after=Flyway.configure().dataSource(url,user,password).baselineOnMigrate(true).baselineVersion("0.15.0")
                    .locations("classpath:db/migration").target("0.20.35").load();
            assertTrue(after.migrate().success);
            assertEquals("0.20.35",after.info().current().getVersion().getVersion());
            assertBackfill(connection,seed.firstId());assertBackfill(connection,seed.secondId());
            assertEquals(2,count(connection,"select count(*) from information_schema.columns where table_schema=database() and table_name='todo_trigger_rule' and column_name in ('rule_code','rule_name') and is_nullable='NO'"));
            assertEquals(1,count(connection,"select count(*) from information_schema.statistics where table_schema=database() and table_name='todo_trigger_rule' and index_name='uk_todo_trigger_rule_code' and non_unique=0"));
            assertThrows(SQLException.class,()->insert(connection,"TRIGGER_METADATA_E2E_DUPLICATE",seed.versionId(),"TRIGGER_"+seed.firstId(),"duplicate",seed.templateId()));
            try(PreparedStatement cleanup=connection.prepareStatement("delete from todo_trigger_rule where event_type like 'TRIGGER_METADATA_E2E_%'")){cleanup.executeUpdate();}
        }
    }

    private Seed seedPreMetadataTriggerRows(Connection connection) throws Exception
    {
        long templateId,versionId;
        try(PreparedStatement select=connection.prepareStatement("select template_id,version_id from todo_template_version order by version_id limit 1");ResultSet rows=select.executeQuery())
        {assertTrue(rows.next(),"0.20.34 migration must provide a valid template version fixture");templateId=rows.getLong(1);versionId=rows.getLong(2);}
        return new Seed(insert(connection,"TRIGGER_METADATA_E2E_A",versionId,null,null,templateId),insert(connection,"TRIGGER_METADATA_E2E_B",versionId,null,null,templateId),templateId,versionId);
    }

    private long insert(Connection connection,String event,long versionId,String code,String name,long templateId) throws Exception
    {
        String sql=code==null?"insert into todo_trigger_rule(event_type,template_id,template_version_id,business_type,enabled) values(?,?,?,'LEAD','N')":
                "insert into todo_trigger_rule(event_type,template_id,template_version_id,business_type,enabled,rule_code,rule_name) values(?,?,?,'LEAD','N',?,?)";
        try(PreparedStatement insert=connection.prepareStatement(sql,java.sql.Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1,event);insert.setLong(2,templateId);insert.setLong(3,versionId);
            if(code!=null){insert.setString(4,code);insert.setString(5,name);}insert.executeUpdate();
            try(ResultSet keys=insert.getGeneratedKeys()){assertTrue(keys.next());return keys.getLong(1);}
        }
    }

    private void assertBackfill(Connection connection,long id) throws Exception
    {
        try(PreparedStatement select=connection.prepareStatement("select rule_code,rule_name from todo_trigger_rule where trigger_rule_id=?")) {
            select.setLong(1,id);try(ResultSet row=select.executeQuery()){assertTrue(row.next());assertEquals("TRIGGER_"+id,row.getString(1));assertEquals("Trigger rule "+id,row.getString(2));}
        }
    }

    private long count(Connection connection,String sql) throws Exception
    {try(PreparedStatement select=connection.prepareStatement(sql);ResultSet row=select.executeQuery()){assertTrue(row.next());return row.getLong(1);}}

    private record Seed(long firstId,long secondId,long templateId,long versionId) { }

    @Test
    void addsUniqueNonNullRuleIdentityWithDeterministicBackfill()
    {
        String sql = resource("db/migration/V0_20_35__todo_trigger_rule_metadata.sql").replaceAll("\\s+", " ").toLowerCase();
        assertTrue(sql.contains("add column rule_code varchar(64)"));
        assertTrue(sql.contains("add column rule_name varchar(128)"));
        assertTrue(sql.contains("concat('trigger_',trigger_rule_id)"));
        assertTrue(sql.contains("modify column rule_code varchar(64) not null"));
        assertTrue(sql.contains("modify column rule_name varchar(128) not null"));
        assertTrue(sql.contains("unique key uk_todo_trigger_rule_code (rule_code)"));
    }

    private String resource(String path)
    {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path))
        {
            assertNotNull(input, () -> "Migration resource must exist: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException exception)
        {
            throw new AssertionError("Unable to read migration resource: " + path, exception);
        }
    }
}
