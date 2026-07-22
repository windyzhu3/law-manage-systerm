package com.law.todo.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class TodoResourceCatalogMigrationContractTest
{
    private static final Path MIGRATION=Path.of("..","ruoyi-admin","src","main","resources","db","migration",
            "V0_20_37__todo_resource_catalog_items.sql");

    @Test void migrationCreatesBusinessFriendlyFieldsMaterialsAndRecipes() throws Exception
    {
        String sql=Files.readString(MIGRATION).toLowerCase().replaceAll("--[^\r\n]*","").replaceAll("\\s+"," ");

        assertTrue(sql.contains("create table todo_configuration_resource_item"));
        for(String type:new String[]{"'field'","'material'","'dod_recipe'"})assertTrue(sql.contains(type));
        for(String business:new String[]{"'lead'","'customer'","'contract'","'case'","'matter'"})assertTrue(sql.contains(business));
        assertTrue(sql.contains("'contact_proof'"));
        assertTrue(sql.contains("'archive_handoff_form'"));
        assertTrue(sql.contains("'lead_first_contact_ready'"));
        assertTrue(sql.contains("'matter_archive_ready'"));
    }
}
