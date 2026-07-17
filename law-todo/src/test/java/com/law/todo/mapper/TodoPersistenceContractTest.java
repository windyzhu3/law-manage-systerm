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

    @Test void eventCatalogMigrationLimitsSupportedStatuses() throws Exception
    {
        Path migration = Path.of("..", "ruoyi-admin", "src", "main", "resources", "db", "migration",
                "V0_20_2__todo_catalog_and_decisions.sql");
        String sql = Files.readString(migration).toLowerCase().replaceAll("\\s+", " ");
        assertTrue(sql.contains("check (status in ('active','draft','disabled','retired'))"));
    }

    @Test void extensionMigrationPreservesLegacySchedulesAndUsesOneActionNamespace() throws Exception
    {
        Path migration=Path.of("..","ruoyi-admin","src","main","resources","db","migration","V0_20_3__todo_extension_and_notification.sql");
        String sql=Files.readString(migration).toLowerCase().replaceAll("\\s+"," ");
        assertTrue(sql.contains("update todo_sla_record set original_due_at=due_at"));
        assertTrue(!sql.contains("timestampdiff(second,start_at,due_at)"));
        assertTrue(!sql.contains("remind80_due_at=date_add"));assertTrue(!sql.contains("overdue100_due_at=due_at"));assertTrue(!sql.contains("escalate150_due_at=date_add"));
        assertTrue(sql.contains("create table todo_extension_action"));
        assertTrue(sql.contains("primary key (action_id)"));assertTrue(sql.contains("action_type"));
        assertTrue(sql.indexOf("update todo_notification set delivery_key=")<sql.indexOf("modify column delivery_key"));
        assertTrue(sql.contains("uk_todo_notification_source"));
    }

    @Test void routingMigrationFreezesOwningDefinitionAndConcurrencyIdentity() throws Exception
    {
        Path migration=Path.of("..","ruoyi-admin","src","main","resources","db","migration","V0_20_4__todo_routing_runtime.sql");
        String sql=Files.readString(migration).toLowerCase().replaceAll("\\s+"," ");
        assertTrue(sql.contains("route_definition_version_id bigint"));
        assertTrue(sql.contains("uk_todo_route_occurrence"));
        assertTrue(sql.contains("uk_todo_route_token_arrival"));
        assertTrue(sql.contains("primary key (root_todo_id,node_key,occurrence)"));
    }

    @Test void autoActionMigrationSeparatesMutableClaimFromImmutableResultAudit() throws Exception
    {
        Path migration=Path.of("..","ruoyi-admin","src","main","resources","db","migration","V0_20_5__todo_auto_actions.sql");
        String sql=Files.readString(migration).toLowerCase().replaceAll("\\s+"," ");
        assertTrue(sql.contains("primary key (execution_key)"));assertTrue(sql.contains("unique key uk_todo_auto_action_audit_attempt"));
        assertTrue(sql.contains("check (status in ('success','retry','dead'))"));
        int audit=sql.indexOf("create table todo_auto_action_audit");assertTrue(audit>=0);String auditDdl=sql.substring(audit);
        assertTrue(!auditDdl.contains("on update"));assertTrue(!auditDdl.contains("update todo_auto_action_audit"));assertTrue(!auditDdl.contains("delete from todo_auto_action_audit"));
    }
}
