package com.law.todo.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class LeadTodoFlowMigrationContractTest
{
    private static final Path ROOT=Path.of("..");
    private static final Path MIGRATION=ROOT.resolve(Path.of("ruoyi-admin","src","main","resources","db","migration",
            "V0_20_48__lead_todo_flow.sql"));

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
        int end=xml.indexOf(closing,start);
        assertTrue(start>=0&&end>start,id);
        return xml.substring(start,end);
    }
}
