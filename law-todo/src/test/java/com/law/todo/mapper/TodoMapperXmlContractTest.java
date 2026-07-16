package com.law.todo.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class TodoMapperXmlContractTest
{
    @Test
    void lifecycleUpdatePersistsMilestoneTimestamps() throws Exception
    {
        try (InputStream input = getClass().getResourceAsStream("/mapper/todo/TodoMapper.xml"))
        {
            String xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(xml.contains("claimed_at=case when #{toStatus}='CLAIMED'"));
            assertTrue(xml.contains("started_at=case when #{toStatus}='IN_PROGRESS'"));
            assertTrue(xml.contains("submitted_at=case when #{toStatus}='SUBMITTED'"));
            assertTrue(xml.contains("completed_at=case when #{toStatus}='COMPLETED'"));
            assertTrue(xml.contains("cancelled_at=case when #{toStatus}='CANCELLED'"));
            assertTrue(xml.contains("owner_dept_id=case when #{ownerId} is not null"));
        }
    }

    @Test
    void queueQueriesEnforceAllCandidateAndReadOnlyScopes() throws Exception
    {
        try (InputStream input = getClass().getResourceAsStream("/mapper/todo/TodoMapper.xml"))
        {
            String xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(xml.contains("c.candidate_type='ROLE'"));
            assertTrue(xml.contains("c.candidate_type='POST'"));
            assertTrue(xml.contains("from todo_cc cc"));
            assertTrue(xml.contains("led_dept.leader=viewer.user_name"));
            assertTrue(xml.contains("<include refid=\"visibleTodoPredicate\"/>"));
        }
    }

    @Test
    void definitionUpdateAtomicallyPersistsCanonicalAndLegacyProjections() throws Exception
    {
        try (InputStream input = getClass().getResourceAsStream("/mapper/todo/TodoMapper.xml"))
        {
            String xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            int start = xml.indexOf("<update id=\"updateDefinitionDocument\">");
            int end = xml.indexOf("</update>", start);
            assertTrue(start >= 0 && end > start);
            String update = xml.substring(start, end);
            assertTrue(update.contains("owner_rule_json=#{ownerRuleJson}"));
            assertTrue(update.contains("dod_rule_json=#{dodRuleJson}"));
            assertTrue(update.contains("sla_rule_json=#{slaRuleJson}"));
            assertTrue(update.contains("next_rule_json=#{nextRuleJson}"));
            assertTrue(update.contains("ui_schema_json=#{uiSchemaJson}"));
            assertTrue(update.contains("where version_id=#{versionId} and status in ('DRAFT','BLOCKED')"));
        }
    }

    @Test
    void legacyDefinitionReadsUseTheSmallestEnabledTriggerAndPayloadVersionOne() throws Exception
    {
        try (InputStream input = getClass().getResourceAsStream("/mapper/todo/TodoMapper.xml"))
        {
            String xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            for (String statementId : new String[] { "selectTemplateVersion", "selectTemplateVersionById" })
            {
                int start = xml.indexOf("<select id=\"" + statementId + "\"");
                int end = xml.indexOf("</select>", start);
                assertTrue(start >= 0 && end > start, statementId);
                String select = xml.substring(start, end);
                assertTrue(select.contains("legacy_trigger.event_type"), statementId);
                assertTrue(select.contains("legacy_trigger.condition_json"), statementId);
                assertTrue(select.contains("1 payload_version"), statementId);
                assertTrue(select.contains("min(candidate.trigger_rule_id)"), statementId);
                assertTrue(select.contains("candidate.enabled='Y'"), statementId);
            }
        }
    }

    @Test
    void eventCatalogLookupIsActiveOnly() throws Exception
    {
        try (InputStream input = getClass().getResourceAsStream("/mapper/todo/TodoMapper.xml"))
        {
            String xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            int start = xml.indexOf("<select id=\"selectEventCatalog\"");
            int end = xml.indexOf("</select>", start);
            assertTrue(xml.substring(start, end).contains("status='ACTIVE'"));
        }
    }
}
