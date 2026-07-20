package com.law.todo.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TodoConfigurationMapperXmlContractTest
{
    @Test void everyMapperMethodHasExactlyOneParameterizedXmlStatement() throws Exception
    {
        String xml=resource("mapper/todo/TodoConfigurationMapper.xml");

        Set<String> methodIds=Arrays.stream(TodoConfigurationMapper.class.getDeclaredMethods()).map(method -> method.getName())
                .collect(java.util.stream.Collectors.toSet());
        methodIds.forEach(id ->
                assertTrue(xml.contains("id=\""+id+"\""),id));
        Set<String> xmlIds=Pattern.compile("<(?:select|insert|update|delete) id=\"([^\"]+)\"").matcher(xml)
                .results().map(match -> match.group(1)).collect(java.util.stream.Collectors.toSet());
        assertEquals(methodIds,xmlIds);
        assertFalse(xml.contains("replaceDraftRuleRefs"));
        assertFalse(xml.contains("${"));
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

        assertTrue(slaInsert.contains("pause_policy_json") && slaInsert.contains("escalation_policy_json")
                && slaInsert.contains("auto_action_json") && slaInsert.contains("cast(#{pausePolicyJson} as json)"));
        assertTrue(slaUpdate.contains("where sla_rule_id=#{slaRuleId} and version=#{expectedVersion}"));
        assertTrue(dodInsert.contains("required_fields_json") && dodInsert.contains("validator_refs_json")
                && dodInsert.contains("error_messages_json") && dodInsert.contains("cast(#{requiredFieldsJson} as json)"));
        assertTrue(dodUpdate.contains("where dod_rule_id=#{dodRuleId} and version=#{expectedVersion}"));
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

        assertTrue(release.contains("candidate.entity_type='VERSION'"));
        assertTrue(release.contains("candidate.source_entity_id=v.version_id"));
        assertTrue(release.contains("coalesce(v.published_time,v.create_time) update_time"));
        assertTrue(release.contains("order by coalesce(v.published_time,v.create_time) desc,v.version_id desc"));
    }

    private String statement(String xml,String tag,String id)
    {
        int start=xml.indexOf("<"+tag+" id=\""+id+"\"");
        int end=xml.indexOf("</"+tag+">",start);
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
