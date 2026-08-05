package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.law.todo.definition.codec.TodoDefinitionCodec;

/** Release-bundle invariants against the credentialed disposable MySQL gate. */
class LeadTodoGuidedConfigurationExternalMysqlIT
{
    @Test
    void guidedLeadReleaseBundleHasOneIngressOwnedDraftHashesAndLeadOnlyRoutes()
            throws Exception
    {
        MigrationTestDatabase.migrate();
        try(Connection connection=connection())
        {
            assertEquals(1,enabledEntrySlotCount(connection,"LEAD_FIRST_CONTACT_ENTRY"));
            assertEquals("TD-001",activeEntryTemplateCode(connection));
            assertEquals(0,supersededGuidedDraftCount(connection),
                    "Published guided lead templates must not fall back to superseded seed drafts");
            assertTrue(allLatestGuidedVersionsUseOwnHash(connection,"TD-002","TD-003","TD-004"));
            assertEquals(Set.of("LEAD_INVALID_REVIEW_READY","LEAD_RETRY_READY",
                    "LEAD_PROGRESS_READY"),activeRecipeCodes(connection));
            assertEquals(1,enabledScheduleMaterializerJobCount(connection),
                    "The recurring lead routes require one enabled schedule materializer job");
            assertEquals(3,td001GovernedScenarioCount(connection));
            assertEquals(0,crossBusinessLeadRouteCount(connection));
            assertEquals("0.20.80",scalar(connection,"""
                    select version from flyway_schema_history
                    where version='0.20.80' and success=1
                    """));
        }
    }

    private int td001GovernedScenarioCount(Connection connection) throws Exception
    {
        try(Statement query=connection.createStatement();ResultSet rows=query.executeQuery("""
                select count(*)
                from todo_configuration_resource_item
                where resource_type='SIMULATION_SCENARIO'
                  and resource_code in ('TD001_VALID','TD001_SUSPECT_INVALID','TD001_UNREACHABLE')
                  and json_contains(value_json->'$.requiredMaterials',json_quote('CONTACT_PROOF'))
                  and case resource_code
                    when 'TD001_VALID' then json_contains_path(value_json,'all',
                      '$.completionPayload.name','$.completionPayload.city',
                      '$.completionPayload.demand','$.completionPayload.visited')
                    when 'TD001_SUSPECT_INVALID' then json_contains_path(value_json,'all',
                      '$.completionPayload.invalidReasonCode','$.completionPayload.salesExplanation')
                    else true
                  end
                """))
        {rows.next();return rows.getInt(1);}
    }

    private int enabledEntrySlotCount(Connection connection,String slot) throws Exception
    {
        try(PreparedStatement query=connection.prepareStatement("""
                select count(*) from todo_trigger_rule
                where entry_slot_code=? and enabled='Y'
                """))
        {
            query.setString(1,slot);
            try(ResultSet rows=query.executeQuery()){rows.next();return rows.getInt(1);}
        }
    }

    private String activeEntryTemplateCode(Connection connection) throws Exception
    {
        return scalar(connection,"""
                select t.template_code
                from todo_trigger_rule r
                join todo_template t on t.template_id=r.template_id
                where r.entry_slot_code='LEAD_FIRST_CONTACT_ENTRY' and r.enabled='Y'
                """);
    }

    private boolean allLatestGuidedVersionsUseOwnHash(Connection connection,String... codes)
            throws Exception
    {
        Set<String> found=new LinkedHashSet<>();
        try(PreparedStatement query=connection.prepareStatement("""
                select t.template_code,v.definition_json,v.definition_hash,
                       source.definition_hash source_hash
                from todo_template t
                join todo_template_version v on v.template_id=t.template_id
                left join todo_template_version source on source.version_id=v.source_version_id
                where t.template_code in ('TD-002','TD-003','TD-004')
                  and not exists(select 1 from todo_template_version newer
                    where newer.template_id=v.template_id
                      and (newer.version_no>v.version_no or
                           (newer.version_no=v.version_no and newer.version_id>v.version_id)))
                order by t.template_code
                """))
        {
            try(ResultSet rows=query.executeQuery())
            {
                TodoDefinitionCodec codec=new TodoDefinitionCodec();
                while(rows.next())
                {
                    String code=rows.getString("template_code");
                    String json=rows.getString("definition_json");
                    String persisted=rows.getString("definition_hash");
                    String canonical=codec.canonicalJson(codec.read(json));
                    assertEquals(sha256(canonical),persisted,code+" latest hash must own its canonical definition");
                    assertFalse(persisted.equals(rows.getString("source_hash")),
                            code+" latest version must not borrow its source hash");
                    found.add(code);
                }
            }
        }
        return found.equals(Set.of(codes));
    }

    private Set<String> activeRecipeCodes(Connection connection) throws Exception
    {
        Set<String> result=new LinkedHashSet<>();
        try(Statement query=connection.createStatement();ResultSet rows=query.executeQuery("""
                select resource_code from todo_configuration_resource_item
                where resource_type='DOD_RECIPE' and business_type='LEAD' and status='ACTIVE'
                  and resource_code in ('LEAD_INVALID_REVIEW_READY','LEAD_RETRY_READY',
                                        'LEAD_PROGRESS_READY')
                order by resource_code
                """))
        {
            while(rows.next())result.add(rows.getString(1));
        }
        return result;
    }

    private int crossBusinessLeadRouteCount(Connection connection) throws Exception
    {
        try(Statement query=connection.createStatement();ResultSet rows=query.executeQuery("""
                select count(*)
                from (
                  select target.version_id
                  from todo_template source_template
                  join todo_template_version source on source.template_id=source_template.template_id
                  join json_table(source.compiled_json,'$.routing.config.nodes[*]'
                    columns(node_type varchar(16) path '$.type',
                            target_version_id bigint path '$.templateVersionId' null on empty)) node
                  join todo_template_version target on target.version_id=node.target_version_id
                  join todo_template target_template on target_template.template_id=target.template_id
                  where source_template.template_code in ('TD-001','TD-002','TD-003','TD-004')
                    and source.status in ('DRAFT','PUBLISHED') and node.node_type='TASK'
                    and target_template.business_type<>'LEAD'
                  union all
                  select target.version_id
                  from todo_template source_template
                  join todo_template_version source on source.template_id=source_template.template_id
                  join json_table(source.compiled_json,'$.routing.config.businessOutcomes[*]'
                    columns(target_version_id bigint path '$.targetVersionId' null on empty)) outcome
                  join todo_template_version target on target.version_id=outcome.target_version_id
                  join todo_template target_template on target_template.template_id=target.template_id
                  where source_template.template_code in ('TD-001','TD-002','TD-003','TD-004')
                    and source.status in ('DRAFT','PUBLISHED')
                    and target_template.business_type<>'LEAD'
                ) cross_business
                """))
        {
            rows.next();return rows.getInt(1);
        }
    }

    private int enabledScheduleMaterializerJobCount(Connection connection) throws Exception
    {
        return Integer.parseInt(scalar(connection,"""
                select count(*) from sys_job
                where invoke_target='todoScheduleTask.scan' and status='0'
                """));
    }

    private int supersededGuidedDraftCount(Connection connection) throws Exception
    {
        return Integer.parseInt(scalar(connection,"""
                select count(*)
                from todo_template template
                join todo_template_version draft on draft.template_id=template.template_id
                where template.template_code in ('TD-001','TD-002','TD-003','TD-004')
                  and draft.status in ('DRAFT','BLOCKED')
                  and exists (
                    select 1 from todo_template_version published
                    where published.template_id=template.template_id
                      and published.status='PUBLISHED'
                      and published.version_no>draft.version_no)
                """));
    }

    private Connection connection() throws Exception
    {
        return DriverManager.getConnection(required("TODO_MIGRATION_DB_URL"),
                required("TODO_MIGRATION_DB_USER"),required("TODO_MIGRATION_DB_PASSWORD"));
    }

    private String scalar(Connection connection,String sql) throws Exception
    {
        try(Statement query=connection.createStatement();ResultSet rows=query.executeQuery(sql))
        {assertTrue(rows.next());String value=rows.getString(1);assertFalse(rows.next());return value;}
    }

    private String sha256(String value) throws Exception
    {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private String required(String name)
    {
        String value=System.getenv(name);
        if(value==null||value.isBlank())throw new IllegalStateException(name+" is required");
        return value;
    }
}
