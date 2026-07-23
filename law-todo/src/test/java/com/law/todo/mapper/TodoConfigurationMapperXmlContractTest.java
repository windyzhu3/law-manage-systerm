package com.law.todo.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TodoConfigurationMapperXmlContractTest
{
    @Test void everyMapperMethodHasExactlyOneParameterizedXmlStatement() throws Exception
    {
        String xml=resource("mapper/todo/TodoConfigurationMapper.xml");

        assertMapperStatements(xml);
        assertFalse(xml.contains("replaceDraftRuleRefs"));
        assertFalse(xml.contains("${"));
    }

    @Test void duplicateStatementMutationFailsTheContract() throws Exception
    {
        String duplicated=resource("mapper/todo/TodoConfigurationMapper.xml")
                +"<select id=\"selectSlaRules\"></select>";

        assertThrows(AssertionError.class,()->assertMapperStatements(duplicated));
    }

    private void assertMapperStatements(String xml)
    {

        Set<String> methodIds=Arrays.stream(TodoConfigurationMapper.class.getDeclaredMethods()).map(method -> method.getName())
                .collect(java.util.stream.Collectors.toSet());
        methodIds.forEach(id ->
                assertTrue(xml.contains("id=\""+id+"\""),id));
        Map<String,Long> xmlIdCounts=Pattern.compile("<(?:select|insert|update|delete) id=\"([^\"]+)\"").matcher(xml)
                .results().collect(java.util.stream.Collectors.groupingBy(match -> match.group(1),java.util.stream.Collectors.counting()));
        assertEquals(methodIds,xmlIdCounts.keySet());
        methodIds.forEach(id -> assertEquals(1L,xmlIdCounts.get(id),id));
    }

    @Test void ruleListsUseExactFiltersAndStableOrder() throws Exception
    {
        String xml=resource("mapper/todo/TodoConfigurationMapper.xml");
        String sla=statement(xml,"select","selectSlaRules");
        String dod=statement(xml,"select","selectDodRules");

        assertTrue(sla.contains("r.status=#{status}") && sla.contains("r.sla_type=#{slaType}"));
        assertTrue(sla.contains("#{keyword}") && sla.contains("r.update_time &gt;= #{beginTime}")
                && sla.contains("r.update_time &lt;= #{endTime}"));
        assertTrue(sla.contains("order by r.update_time desc,r.sla_rule_id desc"));
        assertTrue(dod.contains("r.status=#{status}") && dod.contains("r.rule_type=#{ruleType}"));
        assertTrue(dod.contains("#{keyword}") && dod.contains("r.update_time &gt;= #{beginTime}")
                && dod.contains("r.update_time &lt;= #{endTime}"));
        assertTrue(dod.contains("order by r.update_time desc,r.dod_rule_id desc"));
    }

    @Test void ruleWritesUseSchemaColumnsAndOptimisticLocks() throws Exception
    {
        String xml=resource("mapper/todo/TodoConfigurationMapper.xml");
        String slaInsert=statement(xml,"insert","insertSlaRule");
        String slaUpdate=statement(xml,"update","updateSlaRuleConditionally");
        String dodInsert=statement(xml,"insert","insertDodRule");
        String dodUpdate=statement(xml,"update","updateDodRuleConditionally");
        String dodStatusUpdate=statement(xml,"update","updateDodRuleStatusConditionally");

        assertTrue(slaInsert.contains("pause_policy_json") && slaInsert.contains("escalation_policy_json")
                && slaInsert.contains("auto_action_json") && slaInsert.contains("cast(#{pausePolicyJson} as json)"));
        assertTrue(slaUpdate.contains("where sla_rule_id=#{slaRuleId} and version=#{expectedVersion}"));
        assertTrue(dodInsert.contains("business_type") && dodInsert.contains("coalesce(#{businessType},'ALL')")
                && dodInsert.contains("required_fields_json") && dodInsert.contains("validator_refs_json")
                && dodInsert.contains("error_messages_json") && dodInsert.contains("cast(#{requiredFieldsJson} as json)"));
        assertTrue(dodUpdate.contains("business_type=coalesce(#{businessType},business_type)")
                && dodUpdate.contains("where dod_rule_id=#{dodRuleId} and version=#{expectedVersion}"));
        assertTrue(dodStatusUpdate.contains("status=#{status}") && dodStatusUpdate.contains("update_by=#{updateBy}")
                && dodStatusUpdate.contains("update_time=sysdate()") && dodStatusUpdate.contains("version=version+1"));
        assertTrue(dodStatusUpdate.contains("where dod_rule_id=#{dodRuleId} and version=#{expectedVersion}"));
        assertFalse(dodStatusUpdate.contains("rule_code") || dodStatusUpdate.contains("required_fields_json")
                || dodStatusUpdate.contains("validator_refs_json"),"status-only update must not write full rule columns");
    }

    @Test void draftRulePersistenceMatchesTypedMapperContract() throws Exception
    {
        String xml=resource("mapper/todo/TodoConfigurationMapper.xml");

        assertTrue(statement(xml,"delete","deleteDraftRuleRefs").contains("where version_id=#{versionId}"));
        assertTrue(statement(xml,"insert","insertDraftRuleRef").contains("ref_id_value")
                && statement(xml,"insert","insertDraftRuleRef").contains("cast(#{configJson} as json)"));
    }

    @Test void releaseProjectionUsesVersionLedgerAndCompatibleUpdateTime() throws Exception
    {
        String release=statement(resource("mapper/todo/TodoConfigurationMapper.xml"),"select","selectReleaseRecords");
        String detail=statement(resource("mapper/todo/TodoConfigurationMapper.xml"),"select","selectReleaseRecord");

        assertTrue(release.contains("candidate.entity_type='VERSION'"));
        assertTrue(release.contains("candidate.source_entity_id=v.version_id"));
        assertTrue(release.contains("coalesce(v.published_time,v.create_time) update_time"));
        assertTrue(release.contains("order by coalesce(v.published_time,v.create_time) desc,v.version_id desc"));
        assertTrue(release.contains("v.status in ('PUBLISHED','RETIRED')"));
        assertTrue(release.contains("v.status=#{status}"));
        assertTrue(release.contains("v.published_by=#{publisher}"));
        assertTrue(detail.contains("v.status in ('PUBLISHED','RETIRED')"));
    }

    @Test void counterAndProjectionQueriesRemainSargableAndStable() throws Exception
    {
        String xml=resource("mapper/todo/TodoConfigurationMapper.xml");
        String today=statement(xml,"select","countTodayTriggeredTodos");
        String refs=statement(xml,"select","selectDraftRuleRefs");
        String release=statement(xml,"select","selectReleaseRecords");

        assertTrue(today.contains("created_at &gt;= current_date()") && today.contains("created_at &lt; date_add(current_date(),interval 1 day)"));
        assertFalse(today.toLowerCase().contains("date(created_at)"));
        assertTrue(refs.contains("order by ref.sort_order,ref.ref_id_value,ref.ref_type"));
        assertTrue(release.contains("candidate.action_type='PUBLISH_VERSION'") && release.contains("#{limit}") && release.contains("#{offset}"));
    }

    @Test void workbenchBatchCarriesTruthfulInputsAndAppliesAllNonIssueFilters() throws Exception
    {
        String xml=resource("mapper/todo/TodoConfigurationMapper.xml");
        String batch=statement(xml,"select","selectTemplateJourneySummaries");
        String filters=fragment(xml,"templateJourneySummaryWhere");

        assertTrue(batch.contains("t.template_code") && batch.contains("definition_json")
                && batch.contains("validation_report_json") && batch.contains("definition_hash"));
        assertTrue(filters.contains("t.business_type=#{businessType}"));
        assertTrue(filters.contains("#{businessStage}") && filters.contains("#{publishStatus}"));
        assertTrue(filters.contains("t.template_code like concat('%',#{keyword},'%')"));
        assertTrue(batch.contains("limit #{limit} offset #{offset}"));
        assertTrue(batch.contains("coalesce(draft.update_by,t.update_by,published.published_by,t.create_by) last_editor"));
        assertTrue(batch.contains("coalesce(draft.update_time,draft.create_time,published.published_time,t.update_time,t.create_time) update_time"));
        assertTrue(batch.contains("order by coalesce(draft.update_time,draft.create_time,published.published_time,t.update_time,t.create_time) desc"));
    }

    @Test void draftEditMetadataMigrationIsAppendOnlyAndSupportsRecentEditOrdering() throws Exception
    {
        java.nio.file.Path migration=java.nio.file.Path.of("..","ruoyi-admin","src","main","resources","db",
                "migration","V0_20_44__todo_template_version_edit_metadata.sql");

        assertTrue(java.nio.file.Files.exists(migration));
        String sql=java.nio.file.Files.readString(migration).toLowerCase().replaceAll("\\s+"," ");
        assertTrue(sql.contains("alter table todo_template_version"));
        assertTrue(sql.contains("add column update_by varchar(64)"));
        assertTrue(sql.contains("add column update_time datetime"));
        assertTrue(sql.contains("idx_todo_template_version_recent_edit"));
    }

    @Test void dictionaryLookupIsParameterizedAndFiltersDisabledTypesAndValues() throws Exception
    {
        String lookup=statement(resource("mapper/todo/TodoConfigurationMapper.xml"),"select","countEnabledDictionaryValue");

        assertTrue(lookup.contains("type.dict_type=#{dictType}") && lookup.contains("data.dict_value=#{dictValue}"));
        assertTrue(lookup.contains("type.status='0'") && lookup.contains("data.status='0'"));
    }

    @Test void releaseVersionDirectoryIsImmutableAndGenericMapperDoesNotExposeBusinessRows() throws Exception
    {
        String xml=resource("mapper/todo/TodoConfigurationMapper.xml");
        String versions=statement(xml,"select","selectImmutableTemplateVersions");

        assertTrue(versions.contains("status in ('PUBLISHED','RETIRED')"));
        assertFalse(versions.contains("DRAFT")||versions.contains("BLOCKED"));
        assertFalse(xml.contains("selectBusinessObjects")||xml.contains("countBusinessObjects")||xml.contains("selectBusinessObject"),
                "Business objects must only be exposed by the actor-scoped system directory adapter");
    }

    private String statement(String xml,String tag,String id)
    {
        int start=xml.indexOf("<"+tag+" id=\""+id+"\"");
        int end=xml.indexOf("</"+tag+">",start);
        assertTrue(start>=0 && end>start,id);
        return xml.substring(start,end);
    }

    private String fragment(String xml,String id)
    {
        int start=xml.indexOf("<sql id=\""+id+"\"");
        int end=xml.indexOf("</sql>",start);
        assertTrue(start>=0 && end>start,id);
        return xml.substring(start,end);
    }

    private String resource(String path) throws Exception
    {
        try (InputStream input=getClass().getClassLoader().getResourceAsStream(path))
        {
            assertTrue(input!=null,"missing resource: "+path);
            return new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }
    }
}
