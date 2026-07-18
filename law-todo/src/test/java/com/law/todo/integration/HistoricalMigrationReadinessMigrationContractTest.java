package com.law.todo.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class HistoricalMigrationReadinessMigrationContractTest
{
    private static final Path MIGRATION=Path.of("..","ruoyi-admin","src","main","resources","db","migration",
            "V0_20_18__foundation_historical_migration_readiness.sql");

    @Test void migration_is_a_readiness_catalog_and_never_rewrites_historical_rows() throws Exception
    {
        String sql=Files.readString(MIGRATION).toLowerCase().replaceAll("\\s+"," ");
        assertTrue(sql.contains("create table todo_foundation_migration_requirement"));
        assertEquals(8,count(sql,"('g-04'"));
        assertTrue(sql.contains("'historical_case_default'"));
        assertTrue(sql.contains("'todo_version_reference'"));
        assertFalse(sql.contains("update biz_case"));
        assertFalse(sql.contains("update todo_instance"));
        assertFalse(sql.contains("alter table biz_case"));
    }

    private int count(String text,String token){return text.split(java.util.regex.Pattern.quote(token),-1).length-1;}
}
