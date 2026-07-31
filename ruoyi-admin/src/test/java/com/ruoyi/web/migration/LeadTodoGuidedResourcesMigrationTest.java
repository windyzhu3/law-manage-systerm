package com.ruoyi.web.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    @Test void repeatedExecutionLeavesGovernedRowsVersionsAndAuditTimesUnchanged() throws Exception
    {
        String url=required("TODO_MIGRATION_DB_URL");
        try(Connection connection=DriverManager.getConnection(url,required("TODO_MIGRATION_DB_USER"),
                required("TODO_MIGRATION_DB_PASSWORD")))
        {
            execute(connection,sql());
            GovernedSnapshot first=snapshot(connection);
            execute(connection,sql());
            GovernedSnapshot second=snapshot(connection);

            assertThat(first.events()).hasSize(3);
            assertThat(first.resources()).hasSize(15);
            assertThat(second).isEqualTo(first);
        }
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

    private static String required(String name)
    {
        String value=System.getenv(name);
        org.junit.jupiter.api.Assumptions.assumeTrue(value!=null&&!value.isBlank(),
                name+" is required for the MySQL idempotency proof");
        return value;
    }

    private static void execute(Connection connection,String sql) throws Exception
    {try(Statement statement=connection.createStatement()){statement.execute(sql);}}

    private static GovernedSnapshot snapshot(Connection connection) throws Exception
    {
        List<Map<String,String>> events=rows(connection,"""
                select event_catalog_id,event_type,payload_version,version,update_by,update_time,
                       cast(payload_schema_json as char) payload_schema_json,
                       cast(owner_field_paths_json as char) owner_field_paths_json
                from todo_event_catalog
                where event_type in ('LEAD_SUSPECT_INVALID_MARKED','LEAD_RETRY_WINDOW_DUE','LEAD_FIRST_CONTACT_VALID')
                  and payload_version=1
                order by event_type,event_catalog_id
                """);
        List<Map<String,String>> resources=rows(connection,"""
                select resource_item_id,resource_type,resource_code,resource_name,description,business_type,
                       cast(value_json as char) value_json,status,sort_order,version,update_by,update_time
                from todo_configuration_resource_item
                where business_type='LEAD' and (
                  (resource_type='FIELD' and resource_code in
                    ('reviewResult','reviewOpinion','contactResult','name','city','demand','visited','progressType','progressAt','remark'))
                  or (resource_type='MATERIAL' and resource_code in ('CONTACT_PROOF','FOLLOWUP_PROOF'))
                  or (resource_type='DOD_RECIPE' and resource_code in
                    ('LEAD_INVALID_REVIEW_READY','LEAD_RETRY_READY','LEAD_PROGRESS_READY')))
                order by resource_type,resource_code,resource_item_id
                """);
        return new GovernedSnapshot(events,resources);
    }

    private static List<Map<String,String>> rows(Connection connection,String sql) throws Exception
    {
        List<Map<String,String>> result=new ArrayList<>();
        try(Statement statement=connection.createStatement();ResultSet rows=statement.executeQuery(sql))
        {
            ResultSetMetaData metadata=rows.getMetaData();
            while(rows.next())
            {
                Map<String,String> row=new LinkedHashMap<>();
                for(int index=1;index<=metadata.getColumnCount();index++)
                    row.put(metadata.getColumnLabel(index),rows.getString(index));
                result.add(row);
            }
        }
        return List.copyOf(result);
    }

    private record GovernedSnapshot(List<Map<String,String>> events,List<Map<String,String>> resources) { }
}
