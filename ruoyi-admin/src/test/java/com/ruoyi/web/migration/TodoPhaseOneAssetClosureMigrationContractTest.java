package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

class TodoPhaseOneAssetClosureMigrationContractTest
{
    private final String sql=resource("db/migration/V0_20_40__todo_phase_one_asset_closure.sql")
        .toLowerCase().replaceAll("--[^\\r\\n]*","").replaceAll("\\s+"," ");
    private final String ownerRepairSql=resource("db/migration/V0_20_41__repair_case_accept_owner_sample_binding.sql")
        .toLowerCase().replaceAll("--[^\\r\\n]*","").replaceAll("\\s+"," ");

    @Test
    void seedsReusableRulesAndBindsAReleaseSnapshotForEveryTechnicalTemplate()
    {
        for(String code:List.of("sla_response_30m","sla_response_4h","sla_response_8h","sla_response_16h"))
            assertTrue(sql.contains("'"+code+"'"),()->"Missing SLA seed "+code);
        for(String code:List.of("lead_first_contact_dod","contract_review_dod","contract_sign_dod",
                "payment_confirm_dod","invoice_handle_dod","case_create_check_dod","case_assign_dod",
                "case_accept_dod","case_reassign_dod","case_transfer_review_dod","matter_node_handle_dod",
                "matter_expense_review_dod","matter_document_supply_dod","case_close_confirm_dod",
                "case_archive_confirm_dod"))
            assertTrue(sql.contains("'"+code+"'"),()->"Missing DoD seed "+code);
        assertTrue(sql.contains("insert into todo_template_draft_rule_ref"));
        assertTrue(sql.contains("'sla'"));
        assertTrue(sql.contains("'dod'"));
        assertTrue(sql.contains("set v.status='retired'"));
        assertTrue(sql.contains("set r.enabled='n'"));
    }

    @Test
    void repairsLeadOwnerAgainstTheGovernedEventPayload()
    {
        assertTrue(sql.contains("'lead_first_contact','lead_assigned','lead','payload:ownerid'"));
        assertTrue(sql.contains("json_object('operand','ownerid','type','payload')"));
        assertTrue(sql.contains("sample_payload_json"));
    }

    @Test
    void publishesAnImmutableCaseAcceptOwnerRepairAgainstTheGovernedSample()
    {
        assertTrue(ownerRepairSql.contains("'payload:lawyerid'"));
        assertTrue(ownerRepairSql.contains("\"operand\":\"lawyerid\""));
        assertTrue(ownerRepairSql.contains("insert into todo_template_version"));
        assertTrue(ownerRepairSql.contains("set status='retired'"));
    }

    private String resource(String path)
    {
        try(InputStream input=getClass().getClassLoader().getResourceAsStream(path))
        {
            assertNotNull(input,()->"Migration resource must exist: "+path);
            return new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }
        catch(IOException exception)
        {
            throw new AssertionError("Unable to read migration resource: "+path,exception);
        }
    }
}
