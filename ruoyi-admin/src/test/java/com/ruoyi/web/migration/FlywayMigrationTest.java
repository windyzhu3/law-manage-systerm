package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

class FlywayMigrationTest
{
    @Test
    void migratesV015BaselineToTodoPhaseOneSchema()
    {
        String url = System.getenv("TODO_MIGRATION_DB_URL");
        assumeTrue(url != null && !url.isBlank(), "Migration database is provided by the CI quality gate");
        Flyway flyway = Flyway.configure()
            .dataSource(url, System.getenv("TODO_MIGRATION_DB_USER"), System.getenv("TODO_MIGRATION_DB_PASSWORD"))
            .baselineOnMigrate(true)
            .baselineVersion("0.15.0")
            .locations("classpath:db/migration")
            .load();

        MigrateResult result = flyway.migrate();
        MigrationInfo current = flyway.info().current();

        assertTrue(result.success);
        assertEquals("0.20.16", current.getVersion().getVersion());
        verifyDatabaseInvariants(url);
        verifyV02PrdCatalogue(url);
        verifyDecisionAccountabilitySchema(url);
        verifyAdmissionEvidenceSchema(url);
    }

    private void verifyAdmissionEvidenceSchema(String url)
    {
        try (Connection connection = DriverManager.getConnection(url, System.getenv("TODO_MIGRATION_DB_USER"),
            System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            assertEquals(5L, count(connection,"select count(*) from todo_admission_evidence"));
            assertEquals(5L, count(connection,"select count(*) from todo_admission_evidence where status='OPEN' "
                + "and owner_user_id is null and reviewer_user_id is null and due_at is null and artifact_ref is null"));
            assertEquals(5L, count(connection,"select count(distinct gate_code) from todo_admission_evidence "
                + "where gate_code in ('G-02','G-04','G-05','G-06','G-07')"));
            assertEquals(2L, count(connection,"select count(*) from sys_menu where perms in ('todo:admission:view','todo:admission:edit')"));
        }
        catch (SQLException exception)
        {
            throw new AssertionError("Admission evidence database invariants failed", exception);
        }
    }

    private void verifyDecisionAccountabilitySchema(String url)
    {
        try (Connection connection = DriverManager.getConnection(url, System.getenv("TODO_MIGRATION_DB_USER"),
            System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            assertEquals(4L, count(connection,
                "select count(*) from information_schema.columns where table_schema=database() "
                    + "and table_name='todo_decision' and column_name in ('owner_user_id','owner_role_key','due_at','delivery_phase')"));
            assertEquals(12L, count(connection,
                "select count(*) from todo_decision where decision_code between 'Q-001' and 'Q-012' "
                    + "and status='OPEN' and owner_user_id is null and due_at is null"));
            assertEquals(8L, count(connection,
                "select count(*) from todo_decision where decision_code between 'Q-001' and 'Q-012' and delivery_phase='PHASE_ONE'"));
            assertEquals(4L, count(connection,
                "select count(*) from todo_decision where decision_code between 'Q-001' and 'Q-012' and delivery_phase='PHASE_TWO'"));
        }
        catch (SQLException exception)
        {
            throw new AssertionError("Decision accountability database invariants failed", exception);
        }
    }

    private void verifyV02PrdCatalogue(String url)
    {
        try (Connection connection = DriverManager.getConnection(url, System.getenv("TODO_MIGRATION_DB_USER"),
            System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            assertEquals(25L, count(connection, "select count(*) from todo_prd_definition_catalog"));
            assertEquals(25L, count(connection,
                "select count(*) from todo_prd_definition_catalog where definition_package_state='READY'"));
            assertEquals(25L, count(connection,
                "select count(*) from todo_prd_definition_catalog where production_state='BLOCKED'"));
            assertEquals(25L, count(connection,
                "select count(*) from todo_prd_definition_catalog c join todo_template t on t.template_code=c.template_code "
                    + "join todo_template_version v on v.template_id=t.template_id "
                    + "where v.status='DRAFT' and cast(v.definition_json as char)=cast(c.definition_json as char)"));
            assertEquals(0L, count(connection,
                "select count(*) from todo_trigger_rule r join todo_template t on t.template_id=r.template_id "
                    + "where t.template_code between 'TD-001' and 'TD-025' and r.enabled='Y'"));
            assertEquals(12L, count(connection,
                "select count(*) from todo_decision where decision_code between 'Q-001' and 'Q-012' "
                    + "and status='OPEN' and blocking='Y'"));
        }
        catch (SQLException exception)
        {
            throw new AssertionError("v0.2 PRD catalogue database invariants failed", exception);
        }
    }

    private long count(Connection connection, String sql) throws SQLException
    {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql))
        {
            assertTrue(rows.next());
            return rows.getLong(1);
        }
    }

    private void verifyDatabaseInvariants(String url)
    {
        try(Connection connection=DriverManager.getConnection(url,System.getenv("TODO_MIGRATION_DB_USER"),System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            long templateId;long versionId;
            try(Statement statement=connection.createStatement();ResultSet rows=statement.executeQuery("select t.template_id,v.version_id from todo_template t join todo_template_version v on v.template_id=t.template_id where t.template_code='LEAD_FIRST_CONTACT' and v.version_no=1")){assertTrue(rows.next());templateId=rows.getLong(1);versionId=rows.getLong(2);}
            try(Statement cleanup=connection.createStatement()){cleanup.executeUpdate("delete from todo_instance where trigger_idempotency_key='migration-invariant-key'");}
            long todoId=insertTodo(connection,templateId,versionId,"migration-invariant-key");
            assertThrows(SQLException.class,()->insertTodo(connection,templateId,versionId,"migration-invariant-key"));
            try(PreparedStatement update=connection.prepareStatement("update todo_instance set status='CLAIMED' where todo_id=? and status='CREATED'")){update.setLong(1,todoId);assertEquals(1,update.executeUpdate());assertEquals(0,update.executeUpdate());}
        }
        catch(SQLException exception){throw new AssertionError("Todo database invariants failed",exception);}
    }

    private long insertTodo(Connection connection,long templateId,long versionId,String key) throws SQLException
    {
        try(PreparedStatement insert=connection.prepareStatement("insert into todo_instance(todo_no,template_id,template_version_id,title,business_type,business_id,status,trigger_idempotency_key) values(?,?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS))
        {
            insert.setString(1,"IT"+System.nanoTime());insert.setLong(2,templateId);insert.setLong(3,versionId);insert.setString(4,"迁移验收待办");insert.setString(5,"LEAD");insert.setLong(6,999999L);insert.setString(7,"CREATED");insert.setString(8,key);insert.executeUpdate();try(ResultSet keys=insert.getGeneratedKeys()){assertTrue(keys.next());return keys.getLong(1);}
        }
    }
}
