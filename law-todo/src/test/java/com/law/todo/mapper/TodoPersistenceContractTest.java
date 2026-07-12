package com.law.todo.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TodoPersistenceContractTest
{
    @Test void migrationDefinesCompleteTodoModel() throws Exception
    {
        Path migration = Path.of("..", "ruoyi-admin", "src", "main", "resources", "db", "migration",
                "V0_16_1__todo_engine.sql");
        String sql = Files.readString(migration).toLowerCase();
        for (String table : new String[] { "todo_template", "todo_template_version", "todo_trigger_rule",
                "todo_instance", "todo_candidate", "todo_cc", "todo_relation", "todo_action_log",
                "todo_attachment", "todo_sla_record", "todo_work_calendar" })
            assertTrue(sql.contains("create table if not exists " + table), table);
        assertTrue(sql.contains("uk_todo_trigger_idempotency"));
        assertTrue(sql.contains("uk_todo_action_idempotency"));
        assertTrue(sql.contains("uk_todo_next_idempotency"));
        assertTrue(sql.contains("idx_todo_owner_queue"));
        assertTrue(sql.contains("idx_todo_candidate_queue"));
        assertTrue(sql.contains("idx_todo_sla_queue"));
        assertTrue(sql.contains("idx_todo_business_relation"));
    }
}
