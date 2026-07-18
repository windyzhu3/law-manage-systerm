package com.law.todo.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class FileSecurityReadinessMigrationContractTest
{
    @Test void migration_catalogs_controls_without_granting_permissions_or_approving_review() throws Exception
    {
        String sql=Files.readString(Path.of("..","ruoyi-admin","src","main","resources","db","migration",
                "V0_20_19__foundation_file_security_readiness.sql")).toLowerCase().replaceAll("\\s+"," ");
        assertTrue(sql.contains("create table todo_foundation_file_security_requirement"));
        assertEquals(7,sql.split("\\('g-05'",-1).length-1);
        assertTrue(sql.contains("'security_review_signoff'"));
        assertFalse(sql.contains("update file_"));
        assertFalse(sql.contains("insert into sys_role"));
        assertFalse(sql.contains("insert into sys_role_menu"));
        assertFalse(sql.contains("'approved'"));
    }
}
