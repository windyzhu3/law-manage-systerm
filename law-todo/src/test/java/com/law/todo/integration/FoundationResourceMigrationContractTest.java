package com.law.todo.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class FoundationResourceMigrationContractTest
{
    private static final Path MIGRATION=Path.of("..","ruoyi-admin","src","main","resources","db","migration",
            "V0_20_17__foundation_resource_readiness.sql");

    @Test void requirementCatalogDoesNotPromoteUnapprovedRuntimeResources() throws Exception
    {
        String sql=Files.readString(MIGRATION).toLowerCase().replaceAll("--[^\r\n]*","");

        assertFalse(sql.contains("insert into sys_dict_type"));
        assertFalse(sql.contains("insert into sys_dict_data"));
        assertFalse(sql.contains("insert into sys_role"));
        assertTrue(sql.contains("'law_business_line'"));
        assertTrue(sql.contains("'non_litigation'"));
        assertTrue(sql.contains("'execution_manager'"));
        assertTrue(sql.contains("'enforcement_primary_assistant'"));
        assertTrue(sql.contains("'conflicting','q-003'"));
    }
}
