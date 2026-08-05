package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class LeadTodoReleaseVersionLockMysqlGateContractTest
{
    @Test
    void migrationMysqlGateRunsAndVerifiesReleaseVersionLockProof() throws Exception
    {
        String workflow=Files.readString(Path.of("..",".github","workflows","ci.yml"));
        int stepStart=workflow.indexOf("- name: Execute and verify all Flyway migrations");
        int stepEnd=workflow.indexOf("- name: Assert external-database Todo tests were not skipped",stepStart);
        assertTrue(stepStart>=0&&stepEnd>stepStart,"migration MySQL gate step must remain identifiable");
        String step=workflow.substring(stepStart,stepEnd);

        assertTrue(step.contains("TODO_MIGRATION_DB_URL:")
                &&step.contains("TODO_MIGRATION_DB_USER: root")
                &&step.contains("TODO_MIGRATION_DB_PASSWORD: root"),
            "release version lock proof must run with the credentialed migration MySQL environment");
        assertTrue(step.matches("(?s).*mvn\\s+.*-Dtest=[^\\r\\n]*LeadTodoReleaseVersionLockExternalMysqlIT[^\\r\\n]*\\s+test.*"),
            "migration MySQL gate must explicitly execute the release version lock proof");
        assertTrue(step.matches("(?s).*mvn\\s+.*-Dtest=[^\\r\\n]*LeadTodoGuidedConfigurationExternalMysqlIT[^\\r\\n]*\\s+test.*"),
            "migration MySQL gate must explicitly execute the guided lead release-bundle proof");

        String reportGate=Files.readString(Path.of("..","ruoyi-ui","scripts","assert-external-db-reports.js"));
        assertTrue(reportGate.contains("'LeadTodoReleaseVersionLockExternalMysqlIT'"),
            "the report gate must fail when the release version lock proof is not discovered or is skipped");
        assertTrue(reportGate.contains("'LeadTodoGuidedConfigurationExternalMysqlIT'"),
            "the report gate must fail when the guided lead release-bundle proof is not discovered or is skipped");
    }
}
