package com.law.todo.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class LeadTodoAssignmentMigrationContractTest
{
    private static final Path MIGRATION=Path.of("..","ruoyi-admin","src","main","resources",
            "db","migration","V0_20_46__todo_assignment_runtime.sql");

    @Test
    void createsVersionedRoundRobinCursorWithAssignmentIndexes() throws Exception
    {
        String sql=normalizedSql();

        assertTrue(sql.contains("create table todo_round_robin_cursor"));
        assertTrue(sql.contains("strategy_key varchar(128) not null"));
        assertTrue(sql.contains("last_user_id bigint null"));
        assertTrue(sql.contains("version int not null default 0"));
        assertTrue(sql.contains("primary key (strategy_key)"));
        assertTrue(sql.contains("create table sys_user_availability"));
        assertTrue(sql.contains("unique key uk_sys_user_availability"));
        assertTrue(sql.contains("key idx_sys_user_availability_active (user_id,status,effective_from,effective_to)"));
        assertTrue(sql.contains("create table sys_user_delegation"));
        assertTrue(sql.contains("unique key uk_sys_user_delegation"));
        assertTrue(sql.contains("key idx_sys_user_delegation_active (from_user_id,status,effective_from,effective_to)"));
    }

    private String normalizedSql() throws Exception
    {
        return Files.readString(MIGRATION).toLowerCase().replaceAll("\\s+"," ");
    }
}
