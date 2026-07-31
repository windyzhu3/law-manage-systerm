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
            assertEquals("0.20.71",flyway(url,user,password).load().info().current()
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
}
