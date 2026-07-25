package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class TodoAssignmentDelegationMysqlGateContractTest
{
    private static final String WORKFLOW_CONTENT_PROPERTY="todo.assignment.mysql.gate.contract.workflow";

    @Test
    void migrationMysqlGateRunsDelegationFallbackMapperIntegrationTest() throws Exception
    {
        String workflow=workflowUnderTest();
        int stepStart=workflow.indexOf("- name: Execute and verify all Flyway migrations");
        int stepEnd=workflow.indexOf("- name: Assert external-database Todo tests were not skipped",stepStart);
        assertTrue(stepStart>=0&&stepEnd>stepStart,"migration MySQL gate step must remain identifiable");
        String step=workflow.substring(stepStart,stepEnd);

        assertTrue(workflow.contains("MYSQL_ROOT_PASSWORD: root"),
                "migration MySQL service must expose the root account used for disposable schemas");
        assertTrue(step.contains("TODO_MIGRATION_DB_URL:")
                        &&step.contains("TODO_MIGRATION_DB_USER: root")
                        &&step.contains("TODO_MIGRATION_DB_PASSWORD: root"),
                "delegation IT must run in the credentialed migration MySQL gate");
        assertTrue(step.matches("(?s).*mvn\\s+.*-Dtest=.*TodoAssignmentDelegationMapperExternalMysqlIT.*test.*"),
                "migration MySQL gate must explicitly execute the delegation fallback mapper IT");
    }

    private static String workflowUnderTest() throws Exception
    {
        String configured=System.getProperty(WORKFLOW_CONTENT_PROPERTY);
        if(configured!=null)
            return configured.replace("\\n","\n");
        return Files.readString(Path.of("..",".github","workflows","ci.yml"));
    }
}
