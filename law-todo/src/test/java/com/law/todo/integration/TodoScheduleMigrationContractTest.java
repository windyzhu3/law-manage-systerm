package com.law.todo.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class TodoScheduleMigrationContractTest
{
    private static final Path MIGRATION=Path.of("..","ruoyi-admin","src","main","resources",
            "db","migration","V0_20_47__todo_schedule_windows.sql");
    private static final Path POLICY_SNAPSHOT_MIGRATION=Path.of("..","ruoyi-admin","src","main",
            "resources","db","migration","V0_20_49__todo_schedule_policy_snapshot.sql");

    @Test
    void createsVersionedPlansWindowsAndUniqueOccurrences() throws Exception
    {
        String sql=Files.readString(MIGRATION).toLowerCase().replaceAll("\\s+"," ");

        assertTrue(sql.contains("create table todo_schedule_plan"));
        assertTrue(sql.contains("create table todo_schedule_window"));
        assertTrue(sql.contains("create table todo_schedule_occurrence"));
        assertTrue(sql.contains("unique key uk_todo_schedule_occurrence (plan_id,window_code,occurrence_no)"));
        assertTrue(sql.contains("key idx_todo_schedule_window_due (status,due_at)"));
        assertTrue(sql.contains("key idx_todo_schedule_occurrence_due (status,due_at)"));
        assertTrue(sql.split("version int not null default 0",-1).length-1>=3);
        assertTrue(sql.contains("timezone varchar(64) not null default 'asia/shanghai'"));
        assertTrue(sql.contains("materialize_at datetime not null"));
        assertTrue(sql.contains("due_at datetime not null"));
    }

    @Test
    void addsAuditableResolvedAndLegacyPolicySnapshotsAndInternalContinuationOutcome() throws Exception
    {
        String sql=Files.readString(POLICY_SNAPSHOT_MIGRATION).toLowerCase().replaceAll("\\s+"," ");

        assertTrue(sql.contains("add column assignment_policy_id bigint"));
        assertTrue(sql.contains("add column assignment_policy_version int"));
        assertTrue(sql.contains("assignment_policy_snapshot_source"));
        assertTrue(sql.contains("legacy_pre_0_20_49"));
        assertTrue(sql.contains("resolved_policy"));
        assertTrue(sql.contains("assignment_policy_id is null"));
        assertTrue(sql.contains("assignment_policy_id is not null"));
        assertTrue(sql.contains("continue_current_window"));
    }
}
