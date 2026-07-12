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
        assertEquals("0.17.3", current.getVersion().getVersion());
        verifyDatabaseInvariants(url);
    }

    private void verifyDatabaseInvariants(String url)
    {
        try(Connection connection=DriverManager.getConnection(url,System.getenv("TODO_MIGRATION_DB_USER"),System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            long templateId;long versionId;
            try(Statement statement=connection.createStatement();ResultSet rows=statement.executeQuery("select t.template_id,v.version_id from todo_template t join todo_template_version v on v.template_id=t.template_id where t.template_code='LEAD_FIRST_CONTACT' and v.version_no=1")){assertTrue(rows.next());templateId=rows.getLong(1);versionId=rows.getLong(2);}
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
