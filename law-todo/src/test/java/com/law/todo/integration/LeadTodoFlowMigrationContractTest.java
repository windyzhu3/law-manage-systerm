package com.law.todo.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class LeadTodoFlowMigrationContractTest
{
    private static final Path ROOT=Path.of("..");
    private static final Path MIGRATION=ROOT.resolve(Path.of("ruoyi-admin","src","main","resources","db","migration",
            "V0_20_48__lead_todo_flow.sql"));
    private static final Path CONFIGURATION_HARDENING=ROOT.resolve(Path.of("ruoyi-admin","src","main","resources",
            "db","migration","V0_20_65__lead_template_configuration_hardening.sql"));

    @Test
    void forwardConfigurationRepairKeepsPublishedHistoryImmutable() throws Exception
    {
        assertTrue(Files.exists(CONFIGURATION_HARDENING));
        String sql=normalized(CONFIGURATION_HARDENING);

        assertTrue(sql.contains("insert into todo_template_version"));
        assertTrue(sql.contains("'draft'"));
        assertTrue(sql.contains("'$.event.condition',json_object()"));
        assertTrue(sql.contains("'field','ownerid'"));
        assertTrue(sql.contains("'resultvalue','valid'"));
        assertTrue(sql.contains("'targettemplatecode','td-004'"));
        assertTrue(sql.contains("'resultvalue','suspect_invalid'"));
        assertTrue(sql.contains("'targettemplatecode','td-002'"));
        assertTrue(sql.contains("'resultvalue','unreachable'"));
        assertTrue(sql.contains("'targettemplatecode','td-003'"));
        assertFalse(sql.matches("(?s).*update\\s+todo_template_version\\b.*"));
        assertFalse(sql.matches("(?s).*\\b(?:insert\\s+into|update|delete\\s+from)\\s+"
                +"todo_simulation_evidence\\b.*"));
    }

    @Test
    void migrationDefinesLeadFactsDictionariesPermissionsAndEvents() throws Exception
    {
        String sql=normalized(MIGRATION);

        for(String column:List.of("tag_confirm_status","tag_confirm_time","tag_confirm_by",
                "first_contact_status","first_contact_time","first_contact_result","city","visited",
                "invalid_reason_code","invalid_source_node","invalid_review_status","retry_stage",
                "retry_attempt_count","next_retry_time","disposition","dead_pool_time","dead_pool_reason",
                "row_version"))
            assertTrue(sql.contains("add column "+column),column);
        assertTrue(sql.contains("update biz_lead set disposition=case"));
        assertTrue(sql.contains("modify column disposition varchar(20) not null"));
        assertTrue(sql.contains("key idx_biz_lead_disposition"));

        for(String table:List.of("biz_lead_call_record","biz_lead_invalid_review","biz_lead_retry_record",
                "biz_lead_quality_record","biz_lead_dead_pool_log","biz_business_tag","biz_business_tag_rel",
                "biz_lead_assignment_policy","biz_lead_assignment_policy_candidate"))
            assertTrue(sql.contains("create table "+table),table);
        assertTrue(sql.contains("unique key uk_biz_lead_call_record_idempotency (idempotency_key)"));
        assertTrue(sql.contains("unique key uk_biz_lead_retry_attempt (plan_id,window_code,attempt_no)"));
        assertTrue(sql.contains("unique key uk_biz_business_tag_rel (business_type,business_id,tag_id)"));
        assertTrue(sql.contains("unique key uk_biz_lead_assignment_policy_scope (sales_dept_id,source_code,business_type)"));

        for(String type:List.of("law_first_contact_result","law_lead_tag_confirm_status",
                "law_lead_invalid_reason","law_lead_invalid_review_result","law_retry_stage",
                "law_retry_result","law_call_channel","law_lead_disposition"))
            assertTrue(sql.contains("'"+type+"'"),type);
        for(String value:List.of("VALID","SUSPECT_INVALID","UNREACHABLE","PENDING","CONFIRMED","CORRECTED",
                "NO_DEMAND","DENY_SUBMISSION","COMPETITOR_INTERFERENCE","OTHER","TRUE_INVALID",
                "MISJUDGED_VALID","T0","T1_AM","T1_NOON","T1_PM","T2_AM","T2_NOON","T2_PM",
                "EXHAUSTED","CONNECTED","NEXT_WINDOW","MANUAL","APP","OUTBOUND_SYSTEM","ACTIVE",
                "PUBLIC_POOL","DEAD_POOL","CONVERTED"))
            assertTrue(sql.contains("'"+value.toLowerCase()+"'"),value);

        for(String permission:List.of("lead:tag:confirm","lead:first-contact:handle",
                "lead:invalid-review:list","lead:invalid-review:handle","lead:retry:list",
                "lead:retry:handle","lead:dead-pool:list","lead:dead-pool:restore",
                "lead:assignment-policy:list","lead:assignment-policy:edit","lead:call-record:add",
                "lead:call-record:view"))
            assertTrue(sql.contains("'"+permission+"'"),permission);

        for(String event:List.of("lead_tag_confirmed","lead_first_contact_valid",
                "lead_suspect_invalid_marked","lead_first_contact_unreachable",
                "lead_invalid_review_confirmed","lead_invalid_review_misjudged","lead_retry_window_due",
                "lead_retry_connected","lead_retry_exhausted","lead_moved_to_dead_pool"))
            assertTrue(sql.contains("'"+event+"'"),event);
        assertTrue(sql.contains("where event_type='lead_assigned' and payload_version=1"));
        for(String property:List.of("'schemaversion'","'assignmentid'","'ownerid'","'ownerdeptid'","'operatorid'"))
            assertTrue(sql.contains(property),"LEAD_ASSIGNED property "+property);
        assertTrue(sql.contains("schema_status='ready'"));
    }

    @Test
    void javaAndMapperContractsExposeVersionGuardedFocusedWrites() throws Exception
    {
        String event=normalized(ROOT.resolve(Path.of("law-business","src","main","java","com","law","business",
                "event","BusinessEventType.java")));
        String permissions=normalized(ROOT.resolve(Path.of("law-business","src","main","java","com","law","business",
                "security","LeadPermissions.java")));
        String domain=normalized(ROOT.resolve(Path.of("ruoyi-system","src","main","java","com","ruoyi","system",
                "domain","BizLead.java")));
        String mapper=normalized(ROOT.resolve(Path.of("ruoyi-system","src","main","java","com","ruoyi","system",
                "mapper","BizLeadMapper.java")));
        String xml=normalized(ROOT.resolve(Path.of("ruoyi-system","src","main","resources","mapper","system",
                "BizLeadMapper.xml")));

        assertTrue(event.contains("lead_tag_confirmed"));
        assertTrue(event.contains("lead_moved_to_dead_pool"));
        assertTrue(permissions.contains("invalid_review_handle"));
        assertTrue(permissions.contains("\"lead:invalid-review:handle\""));
        for(String field:List.of("tagconfirmstatus","firstcontactstatus","firstcontactresult","invalidreasoncode",
                "invalidreviewstatus","retrystage","retryattemptcount","nextretrytime","disposition",
                "deadpooltime","deadpoolreason","rowversion"))
            assertTrue(domain.contains(field),field);

        for(String method:List.of("confirmleadtags","completefirstcontact","markinvalidreviewed",
                "advanceretrystage","movetodeadpool","restorefromdeadpool"))
            assertTrue(mapper.contains(method),method);
        assertFalse(mapper.contains("updateleadflow(map<"));
        for(String update:List.of("confirmLeadTags","completeFirstContact","markInvalidReviewed",
                "advanceRetryStage","moveToDeadPool","restoreFromDeadPool"))
        {
            String statement=statement(xml,update);
            assertTrue(statement.contains("row_version=row_version+1"),update);
            assertTrue(statement.contains("row_version=#{rowversion}"),update);
        }
        assertTrue(statement(xml,"insertLead").contains("disposition"));
        assertTrue(statement(xml,"assignLead").contains("disposition = 'active'"));
        assertTrue(statement(xml,"moveToPool").contains("disposition = 'public_pool'"));
        assertTrue(statement(xml,"claimLead").contains("disposition = 'active'"));
        assertTrue(statement(xml,"bindCustomerConditionally").contains("disposition = 'converted'"));
    }

    @Test
    void deadPoolTransitionRequiresConfirmedInvalidReviewAndPreservesRetryState() throws Exception
    {
        String xml=normalized(ROOT.resolve(Path.of("ruoyi-system","src","main","resources","mapper","system",
                "BizLeadMapper.xml")));
        String update=statement(xml,"moveToDeadPool");

        assertTrue(update.contains("disposition='active'"));
        assertTrue(update.contains("invalid_review_status='confirmed'"));
        assertFalse(update.contains("retry_stage="));
    }

    @Test
    void dictionariesPairEveryControlledValueWithItsExactType() throws Exception
    {
        Map<String,Set<String>> expected=new LinkedHashMap<>();
        expected.put("law_first_contact_result",Set.of("VALID","SUSPECT_INVALID","UNREACHABLE"));
        expected.put("law_lead_tag_confirm_status",Set.of("PENDING","CONFIRMED","CORRECTED"));
        expected.put("law_lead_invalid_reason",
                Set.of("NO_DEMAND","DENY_SUBMISSION","COMPETITOR_INTERFERENCE","OTHER"));
        expected.put("law_lead_invalid_review_result",Set.of("TRUE_INVALID","MISJUDGED_VALID"));
        expected.put("law_retry_stage",
                Set.of("T0","T1_AM","T1_NOON","T1_PM","T2_AM","T2_NOON","T2_PM","EXHAUSTED"));
        expected.put("law_retry_result",Set.of("CONNECTED","NEXT_WINDOW","EXHAUSTED"));
        expected.put("law_call_channel",Set.of("MANUAL","APP","OUTBOUND_SYSTEM"));
        expected.put("law_lead_disposition",Set.of("ACTIVE","PUBLIC_POOL","DEAD_POOL","CONVERTED"));

        assertEquals(expected,dictPairs(Files.readString(MIGRATION)));
    }

    @Test
    void eventSchemasHaveExactPropertiesRequiredFieldsAndSampleShapes() throws Exception
    {
        String sql=normalized(MIGRATION);
        Map<String,EventShape> expected=Map.ofEntries(
                Map.entry("LEAD_ASSIGNED",shape(
                        "schemaVersion,assignmentId,ownerId,ownerDeptId,operatorId",
                        "schemaVersion,assignmentId,ownerId,ownerDeptId,operatorId")),
                Map.entry("LEAD_TAG_CONFIRMED",shape(
                        "schemaVersion,tagRelationId,confirmStatus,operatorId,leadId",
                        "schemaVersion,tagRelationId,confirmStatus,operatorId")),
                Map.entry("LEAD_FIRST_CONTACT_VALID",shape(
                        "schemaVersion,followupId,ownerId,contactResult,operatorId,leadId",
                        "schemaVersion,followupId,ownerId,contactResult,operatorId")),
                Map.entry("LEAD_SUSPECT_INVALID_MARKED",shape(
                        "schemaVersion,reviewId,ownerId,reviewerId,reasonCode,operatorId,leadId",
                        "schemaVersion,reviewId,ownerId,reviewerId,reasonCode,operatorId")),
                Map.entry("LEAD_FIRST_CONTACT_UNREACHABLE",shape(
                        "schemaVersion,planId,ownerId,attempts,nextContactAt,operatorId,leadId",
                        "schemaVersion,planId,ownerId,attempts,nextContactAt,operatorId")),
                Map.entry("LEAD_INVALID_REVIEW_CONFIRMED",shape(
                        "schemaVersion,reviewId,reviewerId,reviewResult,operatorId,leadId",
                        "schemaVersion,reviewId,reviewerId,reviewResult,operatorId")),
                Map.entry("LEAD_INVALID_REVIEW_MISJUDGED",shape(
                        "schemaVersion,reviewId,reviewerId,reviewResult,ownerId,operatorId,leadId",
                        "schemaVersion,reviewId,reviewerId,reviewResult,ownerId,operatorId")),
                Map.entry("LEAD_RETRY_WINDOW_DUE",shape(
                        "schemaVersion,planId,windowCode,occurrenceNo,ownerId,leadId",
                        "schemaVersion,planId,windowCode,occurrenceNo,ownerId")),
                Map.entry("LEAD_RETRY_CONNECTED",shape(
                        "schemaVersion,planId,retryRecordId,ownerId,operatorId,leadId",
                        "schemaVersion,planId,retryRecordId,ownerId,operatorId")),
                Map.entry("LEAD_RETRY_EXHAUSTED",shape(
                        "schemaVersion,planId,operatorId,leadId",
                        "schemaVersion,planId,operatorId")),
                Map.entry("LEAD_MOVED_TO_DEAD_POOL",shape(
                        "schemaVersion,deadPoolLogId,reasonCode,operatorId,leadId",
                        "schemaVersion,deadPoolLogId,reasonCode,operatorId")));

        for(Map.Entry<String,EventShape> entry:expected.entrySet())
        {
            String update=eventUpdate(sql,entry.getKey());
            EventShape shape=entry.getValue();
            assertEquals(shape.properties(),propertyNames(update),entry.getKey()+" properties");
            assertEquals(shape.required(),requiredNames(update),entry.getKey()+" required");
            assertEquals(shape.sample(),sampleNames(update),entry.getKey()+" sample");
            assertTrue(update.contains("schema_status='ready'"),entry.getKey()+" READY");
            assertTrue(update.contains("status='active'"),entry.getKey()+" ACTIVE");
        }
    }

    @Test
    void purgeAndLegacyStateWritesHaveExactSafetyGuards() throws Exception
    {
        String xml=normalized(ROOT.resolve(Path.of("ruoyi-system","src","main","resources","mapper","system",
                "BizLeadMapper.xml")));
        Map<String,String> purgeTables=Map.of(
                "purgeLeadCallRecords","biz_lead_call_record",
                "purgeLeadInvalidReviews","biz_lead_invalid_review",
                "purgeLeadRetryRecords","biz_lead_retry_record",
                "purgeLeadQualityRecords","biz_lead_quality_record",
                "purgeLeadDeadPoolLogs","biz_lead_dead_pool_log",
                "purgeLeadTagRelations","biz_business_tag_rel");
        for(Map.Entry<String,String> purge:purgeTables.entrySet())
        {
            String delete=statement(xml,purge.getKey());
            assertTrue(delete.contains("delete from "+purge.getValue()),purge.getKey());
            assertTrue(delete.contains("l.del_flag = '2'"),purge.getKey()+" recycle guard");
        }
        assertTrue(statement(xml,"purgeLeadTagRelations").contains("business_type = 'lead'"));

        for(String update:List.of("assignLead","moveToPool","claimLead","bindCustomerConditionally",
                "touchLeadFollowTime","touchLeadFollowTimeConditionally"))
            assertTrue(statement(xml,update).replace(" ","")
                    .contains("row_version=#{expectedrowversion}"),update);
    }

    private String normalized(Path path) throws Exception
    {
        return Files.readString(path).toLowerCase().replaceAll("--[^\\r\\n]*","").replaceAll("\\s+"," ");
    }

    private String statement(String xml,String id)
    {
        String normalizedId=id.toLowerCase();
        int start=xml.indexOf("<update id=\""+normalizedId+"\"");
        String closing="</update>";
        if(start<0)
        {
            start=xml.indexOf("<insert id=\""+normalizedId+"\"");
            closing="</insert>";
        }
        if(start<0)
        {
            start=xml.indexOf("<delete id=\""+normalizedId+"\"");
            closing="</delete>";
        }
        int end=xml.indexOf(closing,start);
        assertTrue(start>=0&&end>start,id);
        return xml.substring(start,end);
    }

    private Map<String,Set<String>> dictPairs(String sql)
    {
        int start=sql.indexOf("insert into sys_dict_data(");
        int end=sql.indexOf("-- Permissions",start);
        assertTrue(start>=0&&end>start);
        Matcher matcher=Pattern.compile("'([A-Z][A-Z0-9_]*)'(?:\\s+dict_value)?\\s*,\\s*"
                        +"'(law_[a-z0-9_]+)'")
                .matcher(sql.substring(start,end));
        Map<String,Set<String>> result=new LinkedHashMap<>();
        while(matcher.find())
            result.computeIfAbsent(matcher.group(2),ignored->new LinkedHashSet<>()).add(matcher.group(1));
        return result;
    }

    private String eventUpdate(String sql,String eventType)
    {
        String where="where event_type='"+eventType.toLowerCase()+"' and payload_version=1";
        int end=sql.indexOf(where);
        int start=sql.lastIndexOf("update todo_event_catalog",end);
        assertTrue(start>=0&&end>start,eventType);
        return sql.substring(start,end+where.length());
    }

    private List<String> propertyNames(String update)
    {
        String marker="'properties',json_object(";
        int start=update.indexOf(marker);
        int end=update.indexOf("'required',json_array(",start);
        assertTrue(start>=0&&end>start);
        Matcher matcher=Pattern.compile("'([a-z0-9]+)'\\s*,\\s*json_object\\(")
                .matcher(update.substring(start+marker.length(),end));
        List<String> result=new ArrayList<>();
        while(matcher.find()) result.add(matcher.group(1));
        return result;
    }

    private List<String> requiredNames(String update)
    {
        String marker="'required',json_array(";
        int start=update.indexOf(marker);
        int end=update.indexOf(")",start+marker.length());
        assertTrue(start>=0&&end>start);
        return quotedNames(update.substring(start+marker.length(),end));
    }

    private List<String> sampleNames(String update)
    {
        String marker="sample_payload_json=json_object(";
        int start=update.indexOf(marker);
        int end=update.indexOf("), schema_status=",start+marker.length());
        assertTrue(start>=0&&end>start);
        String[] arguments=update.substring(start+marker.length(),end).split(",");
        List<String> result=new ArrayList<>();
        for(int index=0;index<arguments.length;index+=2)
        {
            String key=arguments[index].trim();
            assertTrue(key.startsWith("'")&&key.endsWith("'"),key);
            result.add(key.substring(1,key.length()-1));
        }
        return result;
    }

    private List<String> quotedNames(String value)
    {
        Matcher matcher=Pattern.compile("'([^']+)'").matcher(value);
        List<String> result=new ArrayList<>();
        while(matcher.find()) result.add(matcher.group(1));
        return result;
    }

    private EventShape shape(String properties,String required)
    {
        List<String> propertyList=lowerNames(properties);
        List<String> requiredList=lowerNames(required);
        return new EventShape(propertyList,requiredList,requiredList);
    }

    private List<String> lowerNames(String value)
    {
        return java.util.Arrays.stream(value.toLowerCase().split(",")).toList();
    }

    private record EventShape(List<String> properties,List<String> required,List<String> sample) { }
}
