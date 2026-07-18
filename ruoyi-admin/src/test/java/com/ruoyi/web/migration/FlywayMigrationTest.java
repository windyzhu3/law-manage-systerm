package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class FlywayMigrationTest
{
    @Test
    void migratesV015BaselineToTodoPhaseOneSchema()
    {
        String url = System.getenv("TODO_MIGRATION_DB_URL");
        assumeTrue(url != null && !url.isBlank(), "Migration database is provided by the CI quality gate");
        Flyway flyway = Flyway.configure()
            .dataSource(url, System.getenv("TODO_MIGRATION_DB_USER"), System.getenv("TODO_MIGRATION_DB_PASSWORD"))
            .baselineOnMigrate(true)
            .baselineVersion("0.15.0")
            .locations("classpath:db/migration")
            .load();

        MigrateResult result = flyway.migrate();
        MigrationInfo current = flyway.info().current();

        assertTrue(result.success);
        assertEquals("0.20.23", current.getVersion().getVersion());
        verifyDatabaseInvariants(url);
        verifyV02PrdCatalogue(url);
        verifyDecisionAccountabilitySchema(url);
        verifyAdmissionEvidenceSchema(url);
        verifyFoundationResourceReadinessSchema(url);
        verifyHistoricalMigrationReadinessSchema(url);
        verifyFileSecurityReadinessSchema(url);
        verifyFinanceReadinessSchema(url);
        verifyAcceptanceReadinessSchema(url);
        verifyFoundationAdmissionAggregateQuery(url);
    }

    private void verifyFoundationAdmissionAggregateQuery(String url)
    {
        try (InputStream resource = getClass().getResourceAsStream(
                "/mapper/todo/TodoFoundationAdmissionReadinessMapper.xml");
             Connection connection = DriverManager.getConnection(url, System.getenv("TODO_MIGRATION_DB_USER"),
                System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            assertTrue(resource != null, "Foundation admission mapper must be available on the runtime classpath");
            String mapperXml = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
            Matcher select = Pattern.compile("<select[^>]*id=\"selectAdmissionFacts\"[^>]*>([\\s\\S]*?)</select>")
                .matcher(mapperXml);
            assertTrue(select.find(), "Foundation admission aggregate SQL must be present");
            try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(select.group(1)))
            {
                assertTrue(rows.next());
                assertEquals(12, rows.getInt("decision_total"));
                assertEquals(0, rows.getInt("decision_accountable"));
                assertEquals(8, rows.getInt("phase_one_total"));
                assertEquals(0, rows.getInt("phase_one_closed"));
                assertEquals(25, rows.getInt("prd_total"));
                assertEquals(25, rows.getInt("prd_ready"));
                assertEquals(25, rows.getInt("prd_valid_definition"));
                assertEquals(150, rows.getInt("prd_acceptance_ref_total"));
                assertEquals("OPEN", rows.getString("g02_evidence_status"));
                assertEquals("OPEN", rows.getString("g04_evidence_status"));
                assertEquals("OPEN", rows.getString("g05_evidence_status"));
                assertEquals("OPEN", rows.getString("g06_evidence_status"));
                assertEquals("OPEN", rows.getString("g07_evidence_status"));
                assertEquals(1, rows.getInt("foundation_migration_present"));
                assertEquals(0, rows.getInt("failed_migration_count"));
                assertEquals(4, rows.getInt("core_idempotency_index_count"));
            }
        }
        catch (SQLException | IOException exception)
        {
            throw new AssertionError("Foundation admission aggregate query failed", exception);
        }
    }

    private void verifyAcceptanceReadinessSchema(String url)
    {
        try (Connection connection = DriverManager.getConnection(url, System.getenv("TODO_MIGRATION_DB_USER"),
            System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            assertEquals(8L, count(connection,
                "select count(*) from todo_foundation_acceptance_requirement where gate_code='G-07'"));
            assertEquals(19L, count(connection,
                "select count(distinct template_code) from todo_acceptance_ref_mapping"));
            assertEquals(114L, count(connection,
                "select count(*) from todo_acceptance_ref_mapping"));
            assertEquals(114L, count(connection,
                "select count(*) from todo_acceptance_ref_mapping where status='UNMAPPED' "
                    + "and scenario_id is null and planned_test_ref is null and evidence_note is null "
                    + "and owner_user_id is null and reviewer_user_id is null"));
            assertEquals(0L, count(connection, "select count(*) from todo_acceptance_scenario"));
            assertEquals(0L, count(connection, "select count(*) from todo_acceptance_action"));
            assertEquals(0L, count(connection,
                "select count(*) from todo_trigger_rule r join todo_template t on t.template_id=r.template_id "
                    + "where t.template_code in ('TD-001','TD-002','TD-003','TD-004','TD-005','TD-006',"
                    + "'TD-007','TD-008','TD-009','TD-010','TD-011','TD-012','TD-013','TD-014','TD-015',"
                    + "'TD-016','TD-022','TD-023','TD-025') and r.enabled='Y'"));
        }
        catch (SQLException exception)
        {
            throw new AssertionError("Acceptance readiness database invariants failed", exception);
        }
    }

    private void verifyFinanceReadinessSchema(String url)
    {
        try (Connection connection = DriverManager.getConnection(url, System.getenv("TODO_MIGRATION_DB_USER"),
            System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            assertEquals(9L, count(connection,
                "select count(*) from todo_foundation_finance_requirement where gate_code='G-06'"));
            assertEquals(6L, count(connection,
                "select count(*) from todo_foundation_finance_requirement where source_status='CONFIRMED'"));
            assertEquals(2L, count(connection,
                "select count(*) from todo_foundation_finance_requirement where source_status='NEEDS_DECISION'"));
            assertEquals(1L, count(connection,
                "select count(*) from todo_foundation_finance_requirement where source_status='NEEDS_REVIEW'"));
            assertEquals(4L, count(connection,
                "select count(*) from information_schema.columns where table_schema=database() "
                    + "and table_name='biz_contract_fee_plan' "
                    + "and column_name in ('receivable_amount','received_amount','confirm_status','invoice_status')"));
            assertEquals(0L, count(connection,
                "select count(*) from information_schema.columns where table_schema=database() "
                    + "and table_name='biz_contract_fee_plan' "
                    + "and column_name in ('trigger_type','trigger_node_code','trigger_business_id',"
                    + "'collection_owner_id','due_rule_json','risk_fee_calc_id')"));
        }
        catch (SQLException exception)
        {
            throw new AssertionError("Finance readiness database invariants failed", exception);
        }
    }

    private void verifyFileSecurityReadinessSchema(String url)
    {
        try (Connection connection = DriverManager.getConnection(url, System.getenv("TODO_MIGRATION_DB_USER"),
            System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            assertEquals(7L,count(connection,"select count(*) from todo_foundation_file_security_requirement where gate_code='G-05'"));
            assertEquals(6L,count(connection,"select count(*) from todo_foundation_file_security_requirement where source_status='CONFIRMED'"));
            assertEquals(0L,count(connection,"select count(*) from todo_foundation_file_security_requirement where source_status='NEEDS_EVIDENCE'"));
            assertEquals(1L,count(connection,"select count(*) from todo_foundation_file_security_requirement where source_status='NEEDS_REVIEW'"));
            assertEquals(1L,count(connection,"select count(*) from todo_foundation_file_security_requirement "
                +"where requirement_code='PRD_MATERIAL_TYPE_E2E' and source_status='CONFIRMED' "
                +"and source_ref like '%FileMaterialEndToEndTest.java'"));
            assertEquals(1L,count(connection,"select count(*) from todo_foundation_file_security_requirement "
                +"where requirement_code='SECURITY_REVIEW_SIGNOFF' and source_status='NEEDS_REVIEW' "
                +"and source_ref='doc/reviews/v0.2-foundation-g05-file-security-review-package.md'"));
            assertEquals(5L,count(connection,"select count(*) from information_schema.tables where table_schema=database() and table_name in ('file_object','file_object_version','file_business_relation','file_access_log','file_storage_cleanup')"));
            assertEquals(4L,count(connection,"select count(*) from information_schema.columns where table_schema=database() and table_name='file_access_token' and column_name in ('token_hash','relation_id','actor_id','consumed_at')"));
            assertEquals(1L,count(connection,"select count(distinct index_name) from information_schema.statistics where table_schema=database() and table_name='file_access_token' and index_name='uk_file_access_token_hash' and non_unique=0"));
        }
        catch (SQLException exception)
        {
            throw new AssertionError("File security readiness database invariants failed",exception);
        }
    }

    private void verifyHistoricalMigrationReadinessSchema(String url)
    {
        try (Connection connection = DriverManager.getConnection(url, System.getenv("TODO_MIGRATION_DB_USER"),
            System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            assertEquals(8L,count(connection,"select count(*) from todo_foundation_migration_requirement where gate_code='G-04'"));
            assertEquals(3L,count(connection,"select count(*) from todo_foundation_migration_requirement where source_status='CONFIRMED'"));
            assertEquals(1L,count(connection,"select count(*) from todo_foundation_migration_requirement where source_status='NEEDS_DECISION'"));
            assertEquals(4L,count(connection,"select count(*) from todo_foundation_migration_requirement where source_status='NEEDS_EVIDENCE'"));
            assertEquals(0L,count(connection,"select count(*) from information_schema.columns where table_schema=database() and table_name='biz_case' and column_name='business_line'"));
            assertEquals(0L,count(connection,"select count(*) from todo_instance i left join todo_template_version v on v.version_id=i.template_version_id where v.version_id is null"));
        }
        catch (SQLException exception)
        {
            throw new AssertionError("Historical migration readiness database invariants failed",exception);
        }
    }

    private void verifyFoundationResourceReadinessSchema(String url)
    {
        try (Connection connection = DriverManager.getConnection(url, System.getenv("TODO_MIGRATION_DB_USER"),
            System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            assertEquals(51L,count(connection,"select count(*) from todo_foundation_resource_requirement where gate_code='G-02'"));
            assertEquals(40L,count(connection,"select count(*) from todo_foundation_resource_requirement where resource_type='DICTIONARY'"));
            assertEquals(11L,count(connection,"select count(*) from todo_foundation_resource_requirement where resource_type='ROLE'"));
            assertEquals(39L,count(connection,"select count(*) from todo_foundation_resource_requirement where source_status='NEEDS_DECISION'"));
            assertEquals(5L,count(connection,"select count(*) from todo_foundation_resource_requirement where source_status='CONFLICTING' and decision_ref='Q-003'"));
            assertEquals(3L,count(connection,"select count(*) from todo_foundation_resource_requirement where expected_values_json is not null"));
            assertEquals(3L,count(connection,"select json_length(expected_values_json) from todo_foundation_resource_requirement where resource_code='law_business_line'"));
            assertEquals(13L,count(connection,"select count(*) from todo_foundation_resource_requirement r "
                + "join json_table(coalesce(r.expected_values_json,json_array()), '$[*]' "
                + "columns(expected_value varchar(100) path '$.value',expected_label varchar(100) path '$.label')) expected"));
        }
        catch (SQLException exception)
        {
            throw new AssertionError("Foundation resource readiness database invariants failed",exception);
        }
    }

    private void verifyAdmissionEvidenceSchema(String url)
    {
        try (Connection connection = DriverManager.getConnection(url, System.getenv("TODO_MIGRATION_DB_USER"),
            System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            assertEquals(5L, count(connection,"select count(*) from todo_admission_evidence"));
            assertEquals(5L, count(connection,"select count(*) from todo_admission_evidence where status='OPEN' "
                + "and owner_user_id is null and reviewer_user_id is null and due_at is null and artifact_ref is null"));
            assertEquals(5L, count(connection,"select count(distinct gate_code) from todo_admission_evidence "
                + "where gate_code in ('G-02','G-04','G-05','G-06','G-07')"));
            assertEquals(2L, count(connection,"select count(*) from sys_menu where perms in ('todo:admission:view','todo:admission:edit')"));
        }
        catch (SQLException exception)
        {
            throw new AssertionError("Admission evidence database invariants failed", exception);
        }
    }

    private void verifyDecisionAccountabilitySchema(String url)
    {
        try (Connection connection = DriverManager.getConnection(url, System.getenv("TODO_MIGRATION_DB_USER"),
            System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            assertEquals(4L, count(connection,
                "select count(*) from information_schema.columns where table_schema=database() "
                    + "and table_name='todo_decision' and column_name in ('owner_user_id','owner_role_key','due_at','delivery_phase')"));
            assertEquals(12L, count(connection,
                "select count(*) from todo_decision where decision_code between 'Q-001' and 'Q-012' "
                    + "and status='OPEN' and owner_user_id is null and due_at is null"));
            assertEquals(8L, count(connection,
                "select count(*) from todo_decision where decision_code between 'Q-001' and 'Q-012' and delivery_phase='PHASE_ONE'"));
            assertEquals(4L, count(connection,
                "select count(*) from todo_decision where decision_code between 'Q-001' and 'Q-012' and delivery_phase='PHASE_TWO'"));
        }
        catch (SQLException exception)
        {
            throw new AssertionError("Decision accountability database invariants failed", exception);
        }
    }

    private void verifyV02PrdCatalogue(String url)
    {
        try (Connection connection = DriverManager.getConnection(url, System.getenv("TODO_MIGRATION_DB_USER"),
            System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            assertEquals(25L, count(connection, "select count(*) from todo_prd_definition_catalog"));
            assertEquals(25L, count(connection,
                "select count(*) from todo_prd_definition_catalog where definition_package_state='READY'"));
            assertEquals(25L, count(connection,
                "select count(*) from todo_prd_definition_catalog where production_state='BLOCKED'"));
            assertEquals(25L, count(connection,
                "select count(*) from todo_prd_definition_catalog c join todo_template t on t.template_code=c.template_code "
                    + "join todo_template_version v on v.template_id=t.template_id "
                    + "where v.status='DRAFT' and cast(v.definition_json as char)=cast(c.definition_json as char)"));
            assertEquals(0L, count(connection,
                "select count(*) from todo_trigger_rule r join todo_template t on t.template_id=r.template_id "
                    + "where t.template_code between 'TD-001' and 'TD-025' and r.enabled='Y'"));
            assertEquals(12L, count(connection,
                "select count(*) from todo_decision where decision_code between 'Q-001' and 'Q-012' "
                    + "and status='OPEN' and blocking='Y'"));
        }
        catch (SQLException exception)
        {
            throw new AssertionError("v0.2 PRD catalogue database invariants failed", exception);
        }
    }

    private long count(Connection connection, String sql) throws SQLException
    {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql))
        {
            assertTrue(rows.next());
            return rows.getLong(1);
        }
    }

    private void verifyDatabaseInvariants(String url)
    {
        try(Connection connection=DriverManager.getConnection(url,System.getenv("TODO_MIGRATION_DB_USER"),System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            long templateId;long versionId;
            try(Statement statement=connection.createStatement();ResultSet rows=statement.executeQuery("select t.template_id,v.version_id from todo_template t join todo_template_version v on v.template_id=t.template_id where t.template_code='LEAD_FIRST_CONTACT' and v.version_no=1")){assertTrue(rows.next());templateId=rows.getLong(1);versionId=rows.getLong(2);}
            try(Statement cleanup=connection.createStatement()){cleanup.executeUpdate("delete from todo_instance where trigger_idempotency_key='migration-invariant-key'");}
            long todoId=insertTodo(connection,templateId,versionId,"migration-invariant-key");
            assertThrows(SQLException.class,()->insertTodo(connection,templateId,versionId,"migration-invariant-key"));
            try(PreparedStatement update=connection.prepareStatement("update todo_instance set status='CLAIMED' where todo_id=? and status='CREATED'")){update.setLong(1,todoId);assertEquals(1,update.executeUpdate());assertEquals(0,update.executeUpdate());}
        }
        catch(SQLException exception){throw new AssertionError("Todo database invariants failed",exception);}
    }

    private long insertTodo(Connection connection,long templateId,long versionId,String key) throws SQLException
    {
        try(PreparedStatement insert=connection.prepareStatement("insert into todo_instance(todo_no,template_id,template_version_id,title,business_type,business_id,status,trigger_idempotency_key) values(?,?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS))
        {
            insert.setString(1,"IT"+System.nanoTime());insert.setLong(2,templateId);insert.setLong(3,versionId);insert.setString(4,"迁移验收待办");insert.setString(5,"LEAD");insert.setLong(6,999999L);insert.setString(7,"CREATED");insert.setString(8,key);insert.executeUpdate();try(ResultSet keys=insert.getGeneratedKeys()){assertTrue(keys.next());return keys.getLong(1);}
        }
    }
}
