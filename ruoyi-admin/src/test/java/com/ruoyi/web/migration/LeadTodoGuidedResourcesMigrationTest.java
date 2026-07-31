package com.ruoyi.web.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

class LeadTodoGuidedResourcesMigrationTest
{
    private static final String MIGRATION="/db/migration/V0_20_72__lead_todo_guided_resources.sql";

    @Test void governsTheThreeLeadEventSchemasWithChineseSemanticMetadata() throws Exception
    {
        String sql=normalized(sql());

        assertEventFields(sql,"lead_suspect_invalid_marked",
                List.of("leadid","reviewid","reasoncode","ownerid","reviewerid","operatorid"));
        assertEventFields(sql,"lead_retry_window_due",
                List.of("leadid","planid","windowcode","occurrenceno","ownerid"));
        assertEventFields(sql,"lead_first_contact_valid",
                List.of("leadid","followupid","ownerid","operatorid","contactresult"));
        assertThat(sql).contains("x-semantic-type").contains("x-option-source").contains("x-dict-type")
                .contains("x-owner-eligible").contains("title").contains("description");
        assertThat(sql).contains("system_user").contains("law_lead_invalid_reason")
                .contains("retry_window_catalog").contains("law_first_contact_result");
    }

    @Test void seedsAllLeadFieldsMaterialsAndGuidedRecipesIdempotently() throws Exception
    {
        String sql=normalized(sql());

        List.of("reviewresult","reviewopinion","contactresult","name","city","demand","visited",
                "progresstype","progressat","remark").forEach(code->
                assertThat(sql).as("field %s",code).contains("'field','"+code+"'"));
        List.of("contact_proof","followup_proof").forEach(code->
                assertThat(sql).as("material %s",code).contains("'material','"+code+"'"));
        List.of("lead_invalid_review_ready","lead_retry_ready","lead_progress_ready").forEach(code->
                assertThat(sql).as("recipe %s",code).contains("'dod_recipe','"+code+"'"));
        assertThat(sql).contains("'requiredfields',json_array('progresstype','progressat')")
                .contains("'requiredattachments',json_array('followup_proof')")
                .contains("'validatorrefs',json_array()")
                .contains("'conditionalrules',json_array()")
                .contains("on duplicate key update");
    }

    @Test void preservesPublishedHistoryTriggersAndSimulationEvidence() throws Exception
    {
        String sql=normalized(sql());

        assertThat(sql).doesNotMatch("(?s).*\\b(?:insert\\s+into|update|delete\\s+from)\\s+todo_template_version\\b.*");
        assertThat(sql).doesNotMatch("(?s).*\\b(?:insert\\s+into|update|delete\\s+from)\\s+todo_trigger_rule\\b.*");
        assertThat(sql).doesNotMatch("(?s).*\\b(?:insert\\s+into|update|delete\\s+from)\\s+todo_simulation_evidence\\b.*");
    }

    private static void assertEventFields(String sql,String eventType,List<String> fields)
    {
        assertThat(sql).contains("where event_type='"+eventType+"' and payload_version=1");
        fields.forEach(field->assertThat(sql).as(eventType+":"+field)
                .contains("'$.properties."+field+".title'")
                .contains("'$.properties."+field+".description'"));
    }

    private String sql() throws Exception
    {
        try(InputStream input=getClass().getResourceAsStream(MIGRATION))
        {
            assertNotNull(input,"The forward LEAD guided-resource migration must be packaged");
            return new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }
    }

    private static String normalized(String sql)
    {return sql.replaceAll("--[^\\r\\n]*","").replaceAll("\\s+"," ").toLowerCase();}
}
