package com.law.todo.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class TodoMapperXmlContractTest
{
    @Test void legacyTemplateUpdateChangesNameOnly() throws Exception
    {
        try(InputStream input=getClass().getResourceAsStream("/mapper/todo/TodoMapper.xml"))
        {
            String xml=new String(input.readAllBytes(),StandardCharsets.UTF_8);
            int start=xml.indexOf("<update id=\"updateTemplate\"");int end=xml.indexOf("</update>",start);
            String update=xml.substring(start,end);
            assertTrue(update.contains("template_name=#{templateName}"));
            assertFalse(update.contains("template_code="));
            assertFalse(update.contains("business_type="));
            assertFalse(update.contains("status="));
        }
    }

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
            for (String guard : new String[] { "sourceDefinitionJson", "sourceOwnerRuleJson", "sourceDodRuleJson",
                    "sourceSlaRuleJson", "sourceNextRuleJson", "sourceUiSchemaJson" })
                assertTrue(update.contains("cast(#{" + guard + "} as json)"), guard);
        }
    }

    @Test
    void draftSavePersistsAuthoritativeEditorAndEditTime() throws Exception
    {
        try (InputStream input = getClass().getResourceAsStream("/mapper/todo/TodoMapper.xml"))
        {
            String xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            int start = xml.indexOf("<update id=\"updateTemplateVersionDraft\">");
            int end = xml.indexOf("</update>", start);
            assertTrue(start >= 0 && end > start);
            String update = xml.substring(start, end);
            assertTrue(update.contains("update_by=#{updateBy}"));
            assertTrue(update.contains("update_time=sysdate()"));
            assertTrue(update.contains("where version_id=#{versionId} and status='DRAFT'"));
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

    @Test
    void decisionImpactReadModelRetainsUnreferencedAndDraftReferences() throws Exception
    {
        try (InputStream input = getClass().getResourceAsStream("/mapper/todo/TodoMapper.xml"))
        {
            String xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            int start=xml.indexOf("<select id=\"selectDecisions\"");int end=xml.indexOf("</select>",start);
            String select=xml.substring(start,end);
            assertTrue(select.contains("left join todo_template_version"));
            assertTrue(select.contains("v.status in ('DRAFT','BLOCKED','PUBLISHED')"));
            assertTrue(select.contains("JSON_SEARCH") && select.contains("group_concat(distinct t.template_code"));
            assertTrue(!select.contains("where v.status"));
        }
    }

    @Test
    void triggerRulesPersistAndReadPayloadVersion() throws Exception
    {
        try (InputStream input = getClass().getResourceAsStream("/mapper/todo/TodoMapper.xml"))
        {
            String xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(xml.contains("payload_version,template_id"));
            assertTrue(xml.contains("payload_version=coalesce(#{payloadVersion},1)"));
            assertTrue(xml.contains("select r.*,t.template_code"));
            assertTrue(xml.contains("version=version+1 where trigger_rule_id=#{triggerRuleId} and version=#{expectedVersion}"));
            assertTrue(xml.contains("version=version+1,update_time=sysdate() where calendar_id=#{calendarId} and version=#{expectedVersion}"));
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

    @Test void definitionRollbackUsesLockedFingerprintClaimAndConditionalResult() throws Exception
    {
        try(InputStream input=getClass().getResourceAsStream("/mapper/todo/TodoMapper.xml"))
        {
            String xml=new String(input.readAllBytes(),StandardCharsets.UTF_8).replaceAll("\\s+"," ");
            assertTrue(xml.contains("insertDefinitionActionClaim"));
            assertTrue(xml.contains("action_status,request_fingerprint"));
            assertTrue(xml.contains("selectDefinitionActionForUpdate"));
            assertTrue(xml.contains("from todo_definition_action where action_id=#{actionId} for update"));
            assertTrue(xml.contains("completeDefinitionAction"));
            assertTrue(xml.contains("request_fingerprint=#{requestFingerprint} and action_status='CLAIMED' and entity_id is null"));
        }
    }

    @Test void triggerRulesPersistAndReadIdentityMetadata() throws Exception
    {
        try (InputStream input = getClass().getResourceAsStream("/mapper/todo/TodoMapper.xml"))
        {
            String xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(xml.contains("rule_code,rule_name,event_type"));
            assertTrue(xml.contains("rule_code=#{ruleCode},rule_name=#{ruleName}"));
            assertTrue(xml.contains("r.rule_code,r.rule_name"));
            assertTrue(xml.contains("countTriggerRulesByCode"));
        }
    }

    @Test void scheduleClaimsUseLeasesAndFenceTodoLinkingAgainstCancellation() throws Exception
    {
        try(InputStream input=getClass().getResourceAsStream("/mapper/todo/TodoMapper.xml"))
        {
            String xml=new String(input.readAllBytes(),StandardCharsets.UTF_8).replaceAll("\\s+"," ");
            String due=statement(xml,"select","selectDueScheduleWindows");
            assertTrue(due.contains("w.status='PROCESSING' and w.claimed_at&lt;=#{staleBefore}"));
            String claimWindow=statement(xml,"update","claimScheduleWindow");
            assertTrue(claimWindow.contains("status='PROCESSING' and claimed_at&lt;=#{staleBefore}"));
            String claimOccurrence=statement(xml,"update","claimScheduleOccurrence");
            assertTrue(claimOccurrence.contains("status='CLAIMED' and claimed_at&lt;=#{staleBefore}"));
            String fence=statement(xml,"select","selectScheduleOccurrenceFenceForUpdate");
            assertTrue(fence.contains("p.status planStatus"));
            assertTrue(fence.contains("w.status windowStatus"));
            assertTrue(fence.contains("for update"));
            String link=statement(xml,"update","linkScheduleOccurrenceByKey");
            assertTrue(link.contains("w.status='PROCESSING'"));
            assertTrue(link.contains("p.status='ACTIVE'"));
            assertTrue(link.contains("o.status='CLAIMED'"));
            String result=statement(xml,"update","recordScheduleOccurrenceResult");
            assertTrue(result.contains("status='MATERIALIZED'"));
            assertFalse(result.contains("'COMPLETED','MATERIALIZED'"));
            String sla=statement(xml,"insert","insertScheduledSlaRecord");
            assertTrue(sla.contains("remind80_at,overdue100_at"));
        }
    }

    @Test void triggerWritesAuditTheServerActorAndKeepOptimisticVersionGuards() throws Exception
    {
        try(InputStream input=getClass().getResourceAsStream("/mapper/todo/TodoMapper.xml"))
        {
            String xml=new String(input.readAllBytes(),StandardCharsets.UTF_8).replaceAll("\\s+"," ");
            assertTrue(xml.contains("condition_json,create_by,update_by,update_time"));
            assertTrue(xml.contains("#{createBy},#{updateBy},sysdate()"));
            assertTrue(xml.contains("condition_json=#{conditionJson},update_by=#{updateBy},update_time=sysdate(),version=version+1 where trigger_rule_id=#{triggerRuleId} and version=#{expectedVersion}"));
            assertTrue(xml.contains("sort_order=#{sortOrder},update_by=#{updateBy},update_time=sysdate(),version=version+1 where trigger_rule_id=#{triggerRuleId} and version=#{expectedVersion}"));
            assertTrue(xml.contains("enabled=#{enabled},update_by=#{updateBy},update_time=sysdate(),version=version+1 where trigger_rule_id=#{triggerRuleId} and version=#{expectedVersion}"));
        }
    }

    @Test void triggerCatalogAndEnableReadsUseMinimalAuthoritativeProjections() throws Exception
    {
        try(InputStream input=getClass().getResourceAsStream("/mapper/todo/TodoMapper.xml"))
        {
            String xml=new String(input.readAllBytes(),StandardCharsets.UTF_8).replaceAll("\\s+"," ");
            String catalog=statement(xml,"select","selectPublishedTemplateVersionCatalog");
            assertTrue(catalog.contains("select v.version_id,v.version_no,v.status from todo_template_version v"));
            assertTrue(catalog.contains("v.template_id=#{templateId} and v.status='PUBLISHED'"));
            for(String forbidden:new String[]{"v.*","definition_json","compiled_json","owner_rule_json","dod_rule_json","sla_rule_json","next_rule_json","ui_schema_json","validation_report_json","change_summary","impact_scope"})
                assertFalse(catalog.contains(forbidden),forbidden);

            String binding=statement(xml,"select","selectTriggerBindingForUpdate");
            for(String required:new String[]{"r.trigger_rule_id","r.event_type","r.payload_version","r.template_id","r.template_version_id","r.business_type","r.condition_json","r.version trigger_version","t.status template_status","v.template_id version_template_id","v.status version_status","for update"})
                assertTrue(binding.contains(required),required);
            for(String forbidden:new String[]{"r.*","v.*","definition_json","compiled_json","owner_rule_json","dod_rule_json","sla_rule_json","next_rule_json","ui_schema_json"})
                assertFalse(binding.contains(forbidden),forbidden);

            String saveBinding=statement(xml,"select","selectTriggerTemplateBinding");
            for(String required:new String[]{"v.template_id version_template_id","v.status version_status","t.status template_status","where v.version_id=#{versionId}"})
                assertTrue(saveBinding.contains(required),required);
            for(String forbidden:new String[]{"v.*","t.*","definition_json","compiled_json","owner_rule_json","dod_rule_json","sla_rule_json","next_rule_json","ui_schema_json"})
                assertFalse(saveBinding.contains(forbidden),forbidden);
        }
    }

    @Test void publishingCanLockTheAuthoritativeDefinitionVersion() throws Exception
    {
        try(InputStream input=getClass().getResourceAsStream("/mapper/todo/TodoMapper.xml"))
        {
            String xml=new String(input.readAllBytes(),StandardCharsets.UTF_8);
            int start=xml.indexOf("<select id=\"selectTemplateVersionForUpdate\"");
            int end=xml.indexOf("</select>",start);
            assertTrue(start>=0 && end>start);
            assertTrue(xml.substring(start,end).contains("where v.version_id=#{versionId} for update"));
        }
    }

    private String statement(String xml,String tag,String id)
    {
        int start=xml.indexOf("<"+tag+" id=\""+id+"\"");
        int end=xml.indexOf("</"+tag+">",start);
        assertTrue(start>=0&&end>start,id);return xml.substring(start,end);
    }
}
