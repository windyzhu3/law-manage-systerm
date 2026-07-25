package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class LeadFlowMysqlGateContractTest
{
    @Test
    void migrationMysqlGateRunsAndRejectsSkippedLeadFlowIntegrationEvidence() throws Exception
    {
        String workflow=Files.readString(Path.of("..",".github","workflows","ci.yml"));
        int stepStart=workflow.indexOf("- name: Execute and verify all Flyway migrations");
        int stepEnd=workflow.indexOf("- name: Assert external-database Todo tests were not skipped",
                stepStart);
        assertTrue(stepStart>=0&&stepEnd>stepStart,
                "migration MySQL gate step must remain identifiable");
        String step=workflow.substring(stepStart,stepEnd);
        assertTrue(step.contains("LeadFlowMapperExternalMysqlIT"),
                "migration MySQL gate must explicitly execute the lead-flow real-MySQL test");

        String assertion=Files.readString(Path.of("..","ruoyi-ui","scripts",
                "assert-external-db-reports.js"));
        assertTrue(assertion.contains("'LeadFlowMapperExternalMysqlIT'"),
                "external report gate must reject missing or skipped lead-flow evidence");
    }
}
