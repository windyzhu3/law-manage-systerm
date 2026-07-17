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

    @Test void notificationAndThresholdWritesCarryRaceSafeIdentity() throws Exception
    {
        try(InputStream input=getClass().getResourceAsStream("/mapper/todo/TodoMapper.xml"))
        {
            String xml=new String(input.readAllBytes(),StandardCharsets.UTF_8).replaceAll("\\s+"," ");
            assertTrue(xml.contains("insertSlaNotification"));assertTrue(xml.contains("delivery_key"));
            assertTrue(xml.contains("#{sourceId},#{deliveryKey}"));
            assertTrue(xml.contains("s.version=#{expectedVersion}"));
            assertTrue(xml.contains("s.remind80_due_at=#{plannedDueAt}"));
            assertTrue(xml.contains("s.remind80_due_at&lt;=#{now}"));
            assertTrue(xml.contains("s.remind80_due_at is null and s.due_at=#{expectedDueAt}"));
            assertTrue(xml.contains("s.start_at,s.due_at,c.work_days,c.work_start,c.work_end,c.exception_json"));
            assertTrue(xml.contains("selectExtensionActionForUpdate"));
            assertTrue(xml.contains("where action_id=#{actionId} for update"));
            assertTrue(xml.contains("selectExtensionByIdForUpdate"));
            assertTrue(xml.contains("where e.extension_id=#{extensionId} for update"));
            assertTrue(xml.contains("insert into todo_sla_record(todo_id,calendar_id,start_at,due_at,original_due_at,remind80_due_at,overdue100_due_at,escalate150_due_at,status)"));
        }
    }

    @Test void routingPersistenceUsesDatabaseUniquenessAndLockedJoinClaims() throws Exception
    {
        try(InputStream input=getClass().getResourceAsStream("/mapper/todo/TodoMapper.xml"))
        {
            String xml=new String(input.readAllBytes(),StandardCharsets.UTF_8).replaceAll("\\s+"," ");
            assertTrue(xml.contains("definition_hash,route_definition_version_id,ui_schema_snapshot,sla_snapshot,route_node_key,route_token,occurrence_key,payload_schema_version"));
            assertTrue(xml.contains("#{definitionHash},#{routeDefinitionVersionId},#{uiSchemaSnapshot},#{slaSnapshot},#{routeNodeKey},#{routeToken},#{occurrenceKey},#{payloadSchemaVersion}"));
            assertTrue(xml.contains("insert ignore into todo_route_token"));
            assertTrue(xml.contains("insert ignore into todo_route_join"));
            assertTrue(xml.contains("selectRouteJoinForUpdate"));
            int arrivals=xml.indexOf("selectRouteTokenArrivalsForUpdate");
            assertTrue(arrivals>=0);
            assertTrue(xml.substring(arrivals,xml.indexOf("</select>",arrivals)).contains("for update"));
            assertTrue(xml.contains("for update"));
            assertTrue(xml.contains("status='WAITING'"));
        }
    }

    @Test void autoActionsUseClaimsCommandBoundaryAndAppendOnlyResultAudit() throws Exception
    {
        try(InputStream input=getClass().getResourceAsStream("/mapper/todo/TodoMapper.xml"))
        {
            String xml=new String(input.readAllBytes(),StandardCharsets.UTF_8).replaceAll("\\s+"," ");
            assertTrue(xml.contains("insert ignore into todo_auto_action_execution"));
            assertTrue(xml.contains("action_type,action_source,from_status"));
            assertTrue(xml.contains("status='RETRY' and attempt_count=#{expectedAttempt}"));
            assertTrue(xml.contains("status='CLAIMED' and attempt_count=#{attemptNo}"));
            assertTrue(xml.contains("finalizeStaleAutoActionDead"));
            assertTrue(xml.contains("selectAutoActionExecutionForUpdate"));assertTrue(xml.contains("from todo_auto_action_execution where execution_key=#{executionKey} for update"));
            assertTrue(xml.contains("status='CLAIMED' and attempt_count=#{expectedAttempt} and claimed_at&lt;=#{staleBefore}"));
            assertTrue(xml.contains("not exists(select 1 from todo_action_log committed where committed.action_id=#{executionKey})"));
            assertTrue(xml.contains("selectGovernedSupervisors"));assertTrue(xml.contains("coalesce((select owner.dept_id"));
            assertTrue(xml.contains("insert into todo_auto_action_audit"));
            assertTrue(xml.contains("not exists(select 1 from todo_sla_record paused"));
            assertTrue(xml.contains("returnToPoolConditionally"));
        }
    }
}
