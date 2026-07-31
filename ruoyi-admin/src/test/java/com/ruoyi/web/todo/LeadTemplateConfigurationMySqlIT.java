package com.ruoyi.web.todo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.law.todo.definition.codec.TodoDefinitionCodec;

class LeadTemplateConfigurationMySqlIT
{
    private static final String REPAIR_MARKER="V0.20.65 TD-001 配置易用性修复草稿";

    @Test
    void migrationCreatesOneRepairableDraftWithoutMutatingPublishedHistoryOrEvidence()
            throws Exception
    {
        String url=required("TODO_MIGRATION_DB_URL");
        String user=required("TODO_MIGRATION_DB_USER");
        String password=required("TODO_MIGRATION_DB_PASSWORD");
        Flyway throughPrevious=flyway(url,user,password).target("0.20.64").load();
        throughPrevious.migrate();

        try(Connection connection=DriverManager.getConnection(url,user,password))
        {
            String unrelatedPolicyVersion=scalar(connection,"""
                    select cast(v.version_id as char)
                    from todo_template t
                    join todo_template_version v
                      on v.template_id=t.template_id and v.version_no=t.current_version
                    where t.template_code='TD-004' and v.status='PUBLISHED'
                    """);
            try(Statement statement=connection.createStatement())
            {
                statement.executeUpdate("""
                        insert into biz_lead_assignment_policy(
                          policy_code,policy_name,sales_dept_id,source_code,business_type,
                          retry_rule_json,status,row_version,create_by)
                        values(
                          'P0P1_CANARY_POLICY','不相关策略保护探针',-9001,'P0P1_CANARY','LEAD',
                          json_object('templateVersionId',%s),'ACTIVE',0,'p0-p1-test')
                        """.formatted(unrelatedPolicyVersion));
            }
            String unrelatedPolicyBefore=fingerprint(connection,"""
                    select retry_rule_json,row_version,update_by,update_time
                    from biz_lead_assignment_policy
                    where policy_code='P0P1_CANARY_POLICY'
                    """);
            String publishedBefore=fingerprint(connection,"""
                    select v.version_id,v.version_no,v.status,v.source_version_id,
                           v.definition_hash,cast(v.definition_json as char)
                    from todo_template t
                    join todo_template_version v on v.template_id=t.template_id
                    where t.template_code='TD-001' and v.status='PUBLISHED'
                    order by v.version_id
                    """);
            String td002PublishedBefore=fingerprint(connection,"""
                    select t.current_version,v.version_id,v.version_no,v.status,v.source_version_id,
                           v.definition_hash,cast(v.definition_json as char)
                    from todo_template t
                    join todo_template_version v on v.template_id=t.template_id
                    where t.template_code='TD-002' and v.status='PUBLISHED'
                    order by v.version_id
                    """);
            String td003PublishedBefore;
            String evidenceBefore=fingerprint(connection,"""
                    select evidence_id,template_id,version_id,definition_hash,scenario_code,
                           scenario_version,result_status,input_hash,cast(trace_summary_json as char),
                           executed_by,executed_time,expire_time
                    from todo_simulation_evidence order by evidence_id
                    """);
            String entryPointBefore=scalar(connection,"""
                    select concat(t.current_version,':',r.trigger_rule_id,':',r.template_version_id)
                    from todo_template t
                    join todo_trigger_rule r on r.template_id=t.template_id
                    where t.template_code='TD-001' and r.event_type='LEAD_ASSIGNED' and r.enabled='Y'
                    """);

            var entrySlotResult=flyway(url,user,password).target("0.20.70").load().migrate();
            assertTrue(entrySlotResult.success);
            td003PublishedBefore=fingerprint(connection,"""
                    select t.current_version,v.version_id,v.version_no,v.status,v.source_version_id,
                           v.definition_hash,cast(v.definition_json as char)
                    from todo_template t
                    join todo_template_version v on v.template_id=t.template_id
                    where t.template_code='TD-003' and v.status='PUBLISHED'
                    order by v.version_id
                    """);
            try(Statement statement=connection.createStatement())
            {
                assertEquals(1,statement.executeUpdate("""
                        update todo_trigger_rule
                        set enabled='N'
                        where entry_slot_code='LEAD_FIRST_CONTACT_ENTRY' and enabled='Y'
                        """));
            }

            var result=flyway(url,user,password).load().migrate();
            assertTrue(result.success);
            assertEquals("0.20.76",flyway(url,user,password).load().info().current()
                    .getVersion().getVersion());

            assertEquals(publishedBefore,fingerprint(connection,"""
                    select v.version_id,v.version_no,v.status,v.source_version_id,
                           v.definition_hash,cast(v.definition_json as char)
                    from todo_template t
                    join todo_template_version v on v.template_id=t.template_id
                    where t.template_code='TD-001' and v.status='PUBLISHED'
                    order by v.version_id
                    """));
            assertEquals(evidenceBefore,fingerprint(connection,"""
                    select evidence_id,template_id,version_id,definition_hash,scenario_code,
                           scenario_version,result_status,input_hash,cast(trace_summary_json as char),
                           executed_by,executed_time,expire_time
                    from todo_simulation_evidence order by evidence_id
                    """));
            assertEquals(td002PublishedBefore,fingerprint(connection,"""
                    select t.current_version,v.version_id,v.version_no,v.status,v.source_version_id,
                           v.definition_hash,cast(v.definition_json as char)
                    from todo_template t
                    join todo_template_version v on v.template_id=t.template_id
                    where t.template_code='TD-002' and v.status='PUBLISHED'
                    order by v.version_id
                    """));
            assertEquals(td003PublishedBefore,fingerprint(connection,"""
                    select t.current_version,v.version_id,v.version_no,v.status,v.source_version_id,
                           v.definition_hash,cast(v.definition_json as char)
                    from todo_template t
                    join todo_template_version v on v.template_id=t.template_id
                    where t.template_code='TD-003' and v.status='PUBLISHED'
                    order by v.version_id
                    """));
            assertEquals(entryPointBefore,scalar(connection,"""
                    select concat(t.current_version,':',r.trigger_rule_id,':',r.template_version_id)
                    from todo_template t
                    join todo_trigger_rule r on r.template_id=t.template_id
                    where t.template_code='TD-001' and r.event_type='LEAD_ASSIGNED' and r.enabled='Y'
                    """));
            assertEquals(unrelatedPolicyBefore,fingerprint(connection,"""
                    select retry_rule_json,row_version,update_by,update_time
                    from biz_lead_assignment_policy
                    where policy_code='P0P1_CANARY_POLICY'
                    """),"TD-003 publication must not rewrite an unrelated assignment policy");

            assertEventSemantics(connection);
            assertDraft(connection);
            assertTd002GuidedDraft(connection);
            assertTd003GuidedDraft(connection);
            try(Statement statement=connection.createStatement())
            {
                assertEquals(1,count(statement,
                        "select count(*) from todo_trigger_rule where entry_slot_code='LEAD_FIRST_CONTACT_ENTRY' and enabled='Y'"));
                assertEquals("TD-001",scalar(statement,
                        "select t.template_code from todo_trigger_rule r join todo_template t on t.template_id=r.template_id " +
                        "where r.entry_slot_code='LEAD_FIRST_CONTACT_ENTRY' and r.enabled='Y'"));
                assertEquals("1",scalar(statement,
                        "select status from todo_template where template_code='LEAD_FIRST_CONTACT'"));
                assertEquals(0,count(statement,"""
                        select count(*)
                        from todo_trigger_rule r
                        join todo_template t on t.template_id=r.template_id
                        where r.event_type='LEAD_ASSIGNED' and r.business_type='LEAD' and r.enabled='Y'
                          and (r.entry_slot_code!='LEAD_FIRST_CONTACT_ENTRY' or t.template_code!='TD-001')
                        """));
            }
        }
    }

    private void assertTd002GuidedDraft(Connection connection) throws Exception
    {
        try(Statement statement=connection.createStatement();ResultSet rows=statement.executeQuery("""
                select v.version_id,v.version_no,v.status,v.source_version_id,
                       cast(v.definition_json as char),cast(v.compiled_json as char),v.definition_hash
                from todo_template t
                join todo_template_version v on v.template_id=t.template_id
                where t.template_code='TD-002'
                  and v.change_summary='V0.20.73 TD-002 guided configuration draft'
                """))
        {
            assertTrue(rows.next(),"The governed TD-002 draft must exist");
            long versionId=rows.getLong(1);
            assertEquals("DRAFT",rows.getString(3));
            assertTrue(rows.getLong(4)>0);
            JSONObject definition=JSON.parseObject(rows.getString(5));
            assertEquals(definition,JSON.parseObject(rows.getString(6)));
            assertEquals(rows.getString(7),sha256(new TodoDefinitionCodec().canonicalJson(
                    new TodoDefinitionCodec().read(rows.getString(6)))));
            assertEquals("LEAD_SUSPECT_INVALID_MARKED",
                    definition.getJSONObject("event").getString("eventType"));
            JSONObject owner=definition.getJSONObject("owner").getJSONObject("config");
            assertEquals("PAYLOAD",owner.getString("type"));
            assertEquals("reviewerId",owner.getString("field"));
            JSONObject dod=definition.getJSONObject("dod").getJSONObject("config");
            JSONObject recipe=JSON.parseObject(scalar(connection,"""
                    select cast(value_json as char)
                    from todo_configuration_resource_item
                    where resource_type='DOD_RECIPE' and business_type='LEAD'
                      and resource_code='LEAD_INVALID_REVIEW_READY' and status='ACTIVE'
                    """));
            assertEquals(recipe.getJSONArray("requiredFields"),dod.getJSONArray("requiredFields"));
            assertEquals(recipe.getJSONArray("requiredAttachments"),dod.getJSONArray("materials"));
            assertEquals(recipe.getJSONArray("validatorRefs"),dod.getJSONArray("validatorRefs"));
            assertEquals(recipe.getJSONArray("conditionalRules"),
                    dod.getJSONArray("conditionalRequired"));
            JSONObject sla=definition.getJSONObject("sla").getJSONObject("config");
            assertEquals(1440,sla.getIntValue("minutes"));
            assertEquals("COMPLETE_DEFAULT",sla.getString("onDue"));

            JSONObject routing=definition.getJSONObject("routing").getJSONObject("config");
            JSONArray outcomes=routing.getJSONArray("businessOutcomes");
            assertEquals(2,outcomes.size());
            JSONObject terminal=outcomes.stream().map(JSONObject.class::cast)
                    .filter(item->"TRUE_INVALID".equals(item.getString("value")))
                    .findFirst().orElseThrow();
            assertEquals("END",terminal.getString("effectKind"));
            JSONObject reopened=outcomes.stream().map(JSONObject.class::cast)
                    .filter(item->"MISJUDGED_VALID".equals(item.getString("value")))
                    .findFirst().orElseThrow();
            assertEquals("NEXT_TEMPLATE",reopened.getString("effectKind"));
            assertEquals("TD-001",reopened.getString("targetTemplateCode"));
            long targetVersionId=reopened.getLongValue("targetVersionId");
            assertEquals(String.valueOf(targetVersionId),scalar(connection,"""
                    select cast(v.version_id as char)
                    from todo_template t
                    join todo_template_version v
                      on v.template_id=t.template_id and v.version_no=t.current_version
                    where t.template_code='TD-001' and v.status='PUBLISHED'
                    """));
            assertTrue(routing.getJSONArray("nodes").stream().map(JSONObject.class::cast)
                    .anyMatch(node->"td002".equals(node.getString("key"))
                            &&versionId==node.getLongValue("templateVersionId")));
            assertTrue(routing.getJSONArray("nodes").stream().map(JSONObject.class::cast)
                    .anyMatch(node->"reopenedTd001".equals(node.getString("key"))
                            &&targetVersionId==node.getLongValue("templateVersionId")));
            assertFalse(rows.next(),"Migration must create exactly one governed TD-002 draft");
        }

        assertEquals(0,Long.parseLong(scalar(connection,"""
                select count(*)
                from todo_trigger_rule r
                join todo_template t on t.template_id=r.template_id
                where t.template_code='TD-002' and r.enabled='Y'
                """)));
        try(Statement statement=connection.createStatement();ResultSet rows=statement.executeQuery("""
                select resource_code,status,cast(value_json as char)
                from todo_configuration_resource_item
                where resource_type='SIMULATION_SCENARIO' and business_type='LEAD'
                  and resource_code in ('TD002_TRUE_INVALID','TD002_MISJUDGED_VALID',
                                        'TD002_OVERDUE_DEFAULT')
                order by sort_order,resource_code
                """))
        {
            ObjectMapper mapper=new ObjectMapper();
            Map<String,ScenarioResource> scenarios=new LinkedHashMap<>();
            while(rows.next())
            {
                scenarios.put(rows.getString(1),new ScenarioResource(rows.getString(2),
                        mapper.readValue(rows.getString(3),new TypeReference<Map<String,Object>>(){})));
            }
            assertEquals(List.of("TD002_TRUE_INVALID","TD002_MISJUDGED_VALID",
                    "TD002_OVERDUE_DEFAULT"),List.copyOf(scenarios.keySet()));
            assertScenario(scenarios.get("TD002_TRUE_INVALID"),"TD002_TRUE_INVALID",
                    Map.of("reviewResult","TRUE_INVALID","reviewOpinion","确认无效"),
                    Map.of("kind","END"),false);
            assertScenario(scenarios.get("TD002_MISJUDGED_VALID"),"TD002_MISJUDGED_VALID",
                    Map.of("reviewResult","MISJUDGED_VALID","reviewOpinion","复核为误判"),
                    Map.of("kind","NEXT_TEMPLATE","targetTemplateCode","TD-001"),false);
            assertScenario(scenarios.get("TD002_OVERDUE_DEFAULT"),"TD002_OVERDUE_DEFAULT",
                    Map.of("reviewResult","TRUE_INVALID","reviewOpinion","系统超时默认确认"),
                    Map.of("kind","END"),true);
        }
    }

    private void assertTd003GuidedDraft(Connection connection) throws Exception
    {
        try(Statement statement=connection.createStatement();ResultSet rows=statement.executeQuery("""
                select v.version_id,v.version_no,v.status,v.source_version_id,
                       cast(v.definition_json as char),cast(v.compiled_json as char),v.definition_hash
                from todo_template t
                join todo_template_version v on v.template_id=t.template_id
                where t.template_code='TD-003'
                  and v.change_summary='V0.20.74 TD-003 guided configuration draft'
                """))
        {
            assertTrue(rows.next(),"The governed TD-003 draft must exist");
            long versionId=rows.getLong(1);
            assertEquals("DRAFT",rows.getString(3));
            assertTrue(rows.getLong(4)>0);
            JSONObject definition=JSON.parseObject(rows.getString(5));
            assertEquals(definition,JSON.parseObject(rows.getString(6)));
            assertEquals(rows.getString(7),sha256(new TodoDefinitionCodec().canonicalJson(
                    new TodoDefinitionCodec().read(rows.getString(6)))));
            assertEquals("LEAD_RETRY_WINDOW_DUE",
                    definition.getJSONObject("event").getString("eventType"));
            JSONObject owner=definition.getJSONObject("owner").getJSONObject("config");
            assertEquals("BUSINESS_OWNER",owner.getString("type"));
            assertEquals("LEAD",owner.getString("businessType"));

            JSONObject recipe=JSON.parseObject(scalar(connection,"""
                    select cast(value_json as char)
                    from todo_configuration_resource_item
                    where resource_type='DOD_RECIPE' and business_type='LEAD'
                      and resource_code='LEAD_RETRY_READY' and status='ACTIVE'
                    """));
            JSONObject dod=definition.getJSONObject("dod").getJSONObject("config");
            assertEquals(recipe.getJSONArray("requiredFields"),dod.getJSONArray("requiredFields"));
            assertEquals(recipe.getJSONArray("validatorRefs"),dod.getJSONArray("validatorRefs"));
            assertEquals(recipe.getJSONArray("requiredAttachments").toJavaList(String.class),
                    dod.getJSONArray("materials").stream().map(JSONObject.class::cast)
                            .map(item->item.getString("type")).toList());
            assertEquals(List.of("name","city","demand","visited"),
                    dod.getJSONArray("conditionalRequired").stream().map(JSONObject.class::cast)
                            .map(item->item.getString("field")).toList());
            assertTrue(dod.getJSONArray("conditionalRequired").stream().map(JSONObject.class::cast)
                    .allMatch(item->"contactResult".equals(item.getJSONObject("when").getString("field"))
                            &&"CONNECTED".equals(item.getJSONObject("when").getString("equals"))));

            JSONObject routing=definition.getJSONObject("routing").getJSONObject("config");
            JSONArray outcomes=routing.getJSONArray("businessOutcomes");
            assertEquals(4,outcomes.size());
            Map<String,String> effects=new LinkedHashMap<>();
            outcomes.stream().map(JSONObject.class::cast).forEach(outcome->
                    effects.put(outcome.getString("value"),outcome.getString("effectKind")));
            assertEquals(Map.of("CONNECTED","NEXT_TEMPLATE",
                    "CONTINUE_CURRENT_WINDOW","RETAIN_CURRENT",
                    "NEXT_WINDOW","SCHEDULE_NEXT","EXHAUSTED","END"),effects);
            JSONObject connected=outcomes.stream().map(JSONObject.class::cast)
                    .filter(item->"CONNECTED".equals(item.getString("value")))
                    .findFirst().orElseThrow();
            assertEquals("TD-004",connected.getString("targetTemplateCode"));
            long td004VersionId=connected.getLongValue("targetVersionId");
            assertEquals(String.valueOf(td004VersionId),scalar(connection,"""
                    select cast(v.version_id as char)
                    from todo_template t
                    join todo_template_version v
                      on v.template_id=t.template_id and v.version_no=t.current_version
                    where t.template_code='TD-004' and v.status='PUBLISHED'
                    """));
            assertTrue(routing.getJSONArray("nodes").stream().map(JSONObject.class::cast)
                    .anyMatch(node->"td003".equals(node.getString("key"))
                            &&versionId==node.getLongValue("templateVersionId")));
            assertTrue(routing.getJSONArray("nodes").stream().map(JSONObject.class::cast)
                    .anyMatch(node->"td004".equals(node.getString("key"))
                            &&td004VersionId==node.getLongValue("templateVersionId")));

            JSONObject schedule=definition.getJSONObject("sla").getJSONObject("config")
                    .getJSONObject("schedule");
            assertEquals("TD-003",schedule.getString("targetTemplateCode"));
            assertEquals(versionId,schedule.getLongValue("targetTemplateVersionId"));
            assertEquals(List.of("T0","T1_AM","T1_NOON","T1_PM","T2_AM","T2_NOON","T2_PM"),
                    schedule.getJSONArray("windows").stream().map(JSONObject.class::cast)
                            .map(item->item.getString("windowCode")).toList());
            assertFalse(rows.next(),"Migration must create exactly one governed TD-003 draft");
        }

        try(Statement statement=connection.createStatement())
        {
            assertEquals(0,count(statement,"""
                    select count(*)
                    from todo_trigger_rule r
                    join todo_template t on t.template_id=r.template_id
                    where t.template_code='TD-003' and r.enabled='Y'
                    """));
        }
        try(Statement statement=connection.createStatement();ResultSet rows=statement.executeQuery("""
                select resource_code,status,cast(value_json as char)
                from todo_configuration_resource_item
                where resource_type='SIMULATION_SCENARIO' and business_type='LEAD'
                  and resource_code in ('TD003_CONNECTED','TD003_CONTINUE_WINDOW',
                                        'TD003_NEXT_WINDOW','TD003_EXHAUSTED')
                order by sort_order,resource_code
                """))
        {
            ObjectMapper mapper=new ObjectMapper();
            Map<String,ScenarioResource> scenarios=new LinkedHashMap<>();
            while(rows.next())
                scenarios.put(rows.getString(1),new ScenarioResource(rows.getString(2),
                        mapper.readValue(rows.getString(3),new TypeReference<Map<String,Object>>(){})));
            assertEquals(List.of("TD003_CONNECTED","TD003_CONTINUE_WINDOW",
                    "TD003_NEXT_WINDOW","TD003_EXHAUSTED"),List.copyOf(scenarios.keySet()));
            assertTd003Scenario(scenarios.get("TD003_CONNECTED"),"TD003_CONNECTED",
                    Map.of("kind","NEXT_TEMPLATE","targetTemplateCode","TD-004"),
                    Map.of("contactResult","CONNECTED","attemptStage","T0","attemptCount",1,
                            "name","张女士","city","上海","demand","劳动争议咨询","visited","NO"));
            assertTd003Scenario(scenarios.get("TD003_CONTINUE_WINDOW"),"TD003_CONTINUE_WINDOW",
                    Map.of("kind","RETAIN_CURRENT"),
                    Map.of("contactResult","CONTINUE_CURRENT_WINDOW","attemptStage","T0",
                            "attemptCount",2));
            assertTd003Scenario(scenarios.get("TD003_NEXT_WINDOW"),"TD003_NEXT_WINDOW",
                    Map.of("kind","SCHEDULE_NEXT"),
                    Map.of("contactResult","NEXT_WINDOW","attemptStage","T1_AM","attemptCount",1));
            assertTd003Scenario(scenarios.get("TD003_EXHAUSTED"),"TD003_EXHAUSTED",
                    Map.of("kind","END"),
                    Map.of("contactResult","EXHAUSTED","attemptStage","T2_PM","attemptCount",1));
        }
    }

    private void assertTd003Scenario(ScenarioResource resource,String scenarioCode,
            Map<String,Object> expectedEffect,Map<String,Object> completionPayload)
    {
        assertEquals("ACTIVE",resource.status());
        Map<String,Object> value=resource.value();
        assertEquals("TD-003",value.get("templateCode"));
        assertEquals(scenarioCode,value.get("scenarioCode"));
        assertEquals(1,value.get("scenarioVersion"));
        assertEquals(Boolean.TRUE,value.get("requiredForPublish"));
        assertEquals(List.of("contactResult"),value.get("editableFields"));
        assertEquals(List.of("CONTACT_PROOF"),value.get("requiredMaterials"));
        assertEquals(completionPayload,value.get("completionPayload"));
        assertEquals(expectedEffect,value.get("expectedEffect"));
    }

    private void assertScenario(ScenarioResource resource,String scenarioCode,
            Map<String,Object> completionPayload,Map<String,Object> expectedEffect,
            boolean automatic)
    {
        assertEquals("ACTIVE",resource.status());
        Map<String,Object> value=resource.value();
        assertEquals("TD-002",value.get("templateCode"));
        assertEquals(scenarioCode,value.get("scenarioCode"));
        assertEquals(1,value.get("scenarioVersion"));
        assertEquals(Boolean.TRUE,value.get("requiredForPublish"));
        assertEquals(completionPayload,value.get("completionPayload"));
        assertEquals(expectedEffect,value.get("expectedEffect"));
        if(automatic)
        {
            assertEquals(Boolean.TRUE,value.get("automatic"));
        }
        else
        {
            assertTrue(!value.containsKey("automatic")||Boolean.FALSE.equals(value.get("automatic")),
                    scenarioCode+" must remain a manual scenario");
        }
    }

    private void assertEventSemantics(Connection connection) throws Exception
    {
        try(Statement statement=connection.createStatement();ResultSet rows=statement.executeQuery("""
                select payload_schema_json,owner_field_paths_json
                from todo_event_catalog
                where event_type='LEAD_ASSIGNED' and payload_version=1
                """))
        {
            assertTrue(rows.next());
            JSONObject schema=JSON.parseObject(rows.getString(1));
            JSONObject properties=schema.getJSONObject("properties");
            assertEquals(List.of("ownerId"),
                    JSON.parseArray(rows.getString(2)).toJavaList(String.class));
            assertEquals(5,properties.size());
            properties.forEach((key,value)->{
                JSONObject field=(JSONObject)value;
                assertTrue(hasHan(field.getString("title")),key+" must have a Chinese title");
                assertFalse(field.getString("description")==null
                        ||field.getString("description").isBlank(),key+" must explain its meaning");
            });
            assertEquals("USER_ID",properties.getJSONObject("ownerId")
                    .getString("x-semantic-type"));
            assertEquals("SYSTEM_USER",properties.getJSONObject("ownerId")
                    .getString("x-option-source"));
        }
    }

    private void assertDraft(Connection connection) throws Exception
    {
        try(Statement statement=connection.createStatement();ResultSet rows=statement.executeQuery("""
                select v.version_id,v.version_no,v.status,v.source_version_id,
                       cast(v.definition_json as char),cast(v.compiled_json as char),v.definition_hash
                from todo_template t
                join todo_template_version v on v.template_id=t.template_id
                where t.template_code='TD-001' and v.change_summary='%s'
                """.formatted(REPAIR_MARKER)))
        {
            assertTrue(rows.next(),"The repairable TD-001 draft must exist");
            long versionId=rows.getLong(1);
            assertEquals("DRAFT",rows.getString(3));
            assertTrue(rows.getLong(4)>0);
            JSONObject definition=JSON.parseObject(rows.getString(5));
            assertEquals(definition,JSON.parseObject(rows.getString(6)));
            assertEquals(rows.getString(7),sha256(new TodoDefinitionCodec().canonicalJson(
                    new TodoDefinitionCodec().read(rows.getString(6)))));

            assertTrue(definition.getJSONObject("event").getJSONObject("condition").isEmpty());
            JSONObject owner=definition.getJSONObject("owner").getJSONObject("config");
            assertEquals("PAYLOAD",owner.getString("type"));
            assertEquals("ownerId",owner.getString("field"));

            JSONObject routing=definition.getJSONObject("routing").getJSONObject("config");
            JSONArray outcomes=routing.getJSONArray("businessOutcomes");
            assertEquals(3,outcomes.size());
            Map<String,String> expected=Map.of(
                    "VALID","TD-004",
                    "SUSPECT_INVALID","TD-002",
                    "UNREACHABLE","TD-003");
            Map<String,String> actual=new LinkedHashMap<>();
            for(Object raw:outcomes)
            {
                JSONObject outcome=(JSONObject)raw;
                String code=outcome.getString("targetTemplateCode");
                long targetVersionId=outcome.getLongValue("targetVersionId");
                assertEquals(code,scalar(connection,"""
                        select t.template_code
                        from todo_template t
                        join todo_template_version v on v.template_id=t.template_id
                        where v.version_id=%d and v.status='PUBLISHED'
                        """.formatted(targetVersionId)));
                assertEquals(String.valueOf(targetVersionId),scalar(connection,"""
                        select cast(v.version_id as char)
                        from todo_template t
                        join todo_template_version v
                          on v.template_id=t.template_id and v.version_no=t.current_version
                        where t.template_code='%s' and v.status='PUBLISHED'
                        """.formatted(code)),
                        "TD-001 business outcomes must target each template's current published version");
                actual.put(outcome.getString("resultValue"),code);
            }
            assertEquals(expected,actual);
            assertTrue(routing.getJSONArray("nodes").stream()
                    .map(JSONObject.class::cast)
                    .anyMatch(node->"current_task".equals(node.getString("key"))
                            &&versionId==node.getLongValue("templateVersionId")));
            assertFalse(rows.next(),"Migration must create exactly one marked repair draft");
        }
    }

    private FluentConfiguration flyway(String url,String user,String password)
    {
        return Flyway.configure()
                .dataSource(url,user,password)
                .baselineOnMigrate(true)
                .baselineVersion("0.15.0")
                .locations("classpath:db/migration");
    }

    private String fingerprint(Connection connection,String sql) throws Exception
    {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(Statement statement=connection.createStatement();ResultSet rows=statement.executeQuery(sql))
        {
            int columns=rows.getMetaData().getColumnCount();
            while(rows.next())
            {
                for(int column=1;column<=columns;column++)
                {
                    String value=rows.getString(column);
                    digest.update((value==null?"<NULL>":value).getBytes(StandardCharsets.UTF_8));
                    digest.update((byte)0);
                }
                digest.update((byte)'\n');
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private String scalar(Connection connection,String sql) throws Exception
    {
        try(Statement statement=connection.createStatement();ResultSet rows=statement.executeQuery(sql))
        {
            assertTrue(rows.next(),sql);
            String value=rows.getString(1);
            assertFalse(rows.next(),sql+" must return one row");
            return value;
        }
    }

    private String scalar(Statement statement,String sql) throws Exception
    {
        try(ResultSet rows=statement.executeQuery(sql))
        {
            assertTrue(rows.next(),sql);
            String value=rows.getString(1);
            assertFalse(rows.next(),sql+" must return one row");
            return value;
        }
    }

    private long count(Statement statement,String sql) throws Exception
    {
        try(ResultSet rows=statement.executeQuery(sql))
        {
            assertTrue(rows.next(),sql);
            return rows.getLong(1);
        }
    }

    private boolean hasHan(String value)
    {
        return value!=null&&value.codePoints().anyMatch(character->
                Character.UnicodeScript.of(character)==Character.UnicodeScript.HAN);
    }

    private String sha256(String value) throws Exception
    {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private String required(String name)
    {
        String value=System.getenv(name);
        assumeTrue(value!=null&&!value.isBlank(),name+" is required");
        return value;
    }

    private record ScenarioResource(String status,Map<String,Object> value) { }
}
