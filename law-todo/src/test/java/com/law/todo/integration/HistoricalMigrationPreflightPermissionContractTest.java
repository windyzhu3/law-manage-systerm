package com.law.todo.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class HistoricalMigrationPreflightPermissionContractTest
{
    private static final Path MIGRATION=Path.of("..","ruoyi-admin","src","main","resources","db","migration",
            "V0_20_27__foundation_g04_preflight_export_permission.sql");

    @Test void migrationRegistersOnlyOneExportPermissionWithoutGrantingOrMutatingState() throws Exception
    {
        String sql=Files.readString(MIGRATION).toLowerCase().replaceAll("\\s+"," ");

        assertEquals(1,count(sql,"todo:admission:export"));
        for(String forbidden:List.of("sys_role_menu","alter table biz_case","update biz_case","update todo_instance",
                "todo_foundation_migration_requirement","todo_admission_evidence","approved","confirmed"))
            assertFalse(sql.contains(forbidden),"Migration must not contain "+forbidden);
    }

    private int count(String text,String token){return text.split(java.util.regex.Pattern.quote(token),-1).length-1;}
}
