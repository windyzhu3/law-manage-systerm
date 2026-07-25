package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class FileObjectLockOrderMysqlGateContractTest
{
    @Test
    void migrationMysqlGateRunsTheRealFileObjectLockOrderProof() throws Exception
    {
        String workflow=Files.readString(Path.of("..",".github","workflows","ci.yml"));
        int start=workflow.indexOf("- name: Execute and verify all Flyway migrations");
        int end=workflow.indexOf("- name: Assert external-database Todo tests were not skipped",start);
        assertTrue(start>=0&&end>start,"migration MySQL gate step must remain identifiable");
        String step=workflow.substring(start,end);
        assertTrue(step.contains("TODO_MIGRATION_DB_URL:")
            &&step.contains("TODO_MIGRATION_DB_USER: root")
            &&step.contains("TODO_MIGRATION_DB_PASSWORD: root"));
        assertTrue(step.matches("(?s).*mvn\\s+.*-Dtest=.*FileObjectLockOrderExternalMysqlIT.*test.*"),
            "the credentialed MySQL gate must execute the real file-object concurrency proof");
    }
}
