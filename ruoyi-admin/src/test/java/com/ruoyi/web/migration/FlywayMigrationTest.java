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
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class FlywayMigrationTest
{
    private static final String SQL_NULL = "<SQL-NULL>";

    @Test
    void migratesV015BaselineToTodoPhaseOneSchema()
    {
        String url = System.getenv("TODO_MIGRATION_DB_URL");
        assumeTrue(url != null && !url.isBlank(), "Migration database is provided by the CI quality gate");
        Flyway baselineFlyway = Flyway.configure()
            .dataSource(url, System.getenv("TODO_MIGRATION_DB_USER"), System.getenv("TODO_MIGRATION_DB_PASSWORD"))
            .baselineOnMigrate(true)
            .baselineVersion("0.15.0")
            .locations("classpath:db/migration")
            .target("0.20.27")
            .load();

        baselineFlyway.migrate();
        assertNoHistoricalMigrationExportRoleGrant(url);
        RoleSnapshot beforeFoundationGovernanceMigration = snapshotRoleState(url);
        Flyway foundationGovernanceFlyway = Flyway.configure()
            .dataSource(url, System.getenv("TODO_MIGRATION_DB_USER"), System.getenv("TODO_MIGRATION_DB_PASSWORD"))
            .baselineOnMigrate(true)
            .baselineVersion("0.15.0")
            .locations("classpath:db/migration")
            .target("0.20.28")
            .load();
        foundationGovernanceFlyway.migrate();
        verifyFoundationGovernanceRoles(url, beforeFoundationGovernanceMigration);
        Flyway prePublishFlyway = Flyway.configure()
            .dataSource(url, System.getenv("TODO_MIGRATION_DB_USER"), System.getenv("TODO_MIGRATION_DB_PASSWORD"))
            .baselineOnMigrate(true)
            .baselineVersion("0.15.0")
            .locations("classpath:db/migration")
            .target("0.20.50")
            .load();
        prePublishFlyway.migrate();
        verifyLeadPublicationRollbackAfterInjectedFailure(url);
        Flyway publishFlyway = Flyway.configure()
            .dataSource(url, System.getenv("TODO_MIGRATION_DB_USER"), System.getenv("TODO_MIGRATION_DB_PASSWORD"))
            .baselineOnMigrate(true)
            .baselineVersion("0.15.0")
            .locations("classpath:db/migration")
            .target("0.20.51")
            .load();
        publishFlyway.migrate();
        insertLeadSourceGovernanceFixtures(url);
        Flyway governanceSchemaFlyway = Flyway.configure()
            .dataSource(url, System.getenv("TODO_MIGRATION_DB_USER"), System.getenv("TODO_MIGRATION_DB_PASSWORD"))
            .baselineOnMigrate(true)
            .baselineVersion("0.15.0")
            .locations("classpath:db/migration")
            .target("0.20.52")
            .load();
        governanceSchemaFlyway.migrate();
        verifyLeadGovernanceAuditSchemaBoundary(url);
        verifyLeadNavigationFailureRepairAndRetry(url);
        Flyway flyway = Flyway.configure()
            .dataSource(url, System.getenv("TODO_MIGRATION_DB_USER"), System.getenv("TODO_MIGRATION_DB_PASSWORD"))
            .baselineOnMigrate(true)
            .baselineVersion("0.15.0")
            .locations("classpath:db/migration")
            .load();

        MigrateResult result = flyway.migrate();
        MigrationInfo current = flyway.info().current();

        assertTrue(result.success);
        assertEquals("0.20.80", current.getVersion().getVersion());
        verifyTodoSchedulePolicySnapshotSchema(url);
        verifyPublishedLeadTodoFlow(url);
        verifyDatabaseInvariants(url);
        verifyV02PrdCatalogue(url);
        verifyDecisionAccountabilitySchema(url);
        verifyAdmissionEvidenceSchema(url);
        verifyFoundationResourceReadinessSchema(url);
        verifyHistoricalMigrationReadinessSchema(url);
        verifyHistoricalMigrationExportPermission(url);
        verifyFileSecurityReadinessSchema(url);
        verifyFinanceReadinessSchema(url);
        verifyAcceptanceReadinessSchema(url);
        verifyFoundationAdmissionAggregateQuery(url);
        verifySameMarkerRoleCollisionReceivesNoGrants(url);
        verifyTodoConfigurationCenterSchema(url);
        verifyTodoConfigurationResourceSchema(url);
        verifyTodoDodRecipeEventAlignment(url);
        verifyTodoPhaseOneAssetClosure(url);
        verifyReadableNavigationMenuNames(url);
        verifyTodoTemplateVersionEditMetadata(url);
        verifyLeadSourceGovernance(url);
    }

    private void insertLeadSourceGovernanceFixtures(String url)
    {
        try(Connection connection=DriverManager.getConnection(url,
                System.getenv("TODO_MIGRATION_DB_USER"),
                System.getenv("TODO_MIGRATION_DB_PASSWORD"));
            Statement statement=connection.createStatement())
        {
            statement.executeUpdate("insert into biz_lead_setting(setting_type,setting_code,"
                    +"setting_name,color,order_num,status,create_by,create_time) values"
                    +"('source','retired_campaign','Retired campaign','#64748B',998,'1',"
                    +"'migration-test',sysdate())");
            statement.executeUpdate("insert into biz_business_tag(tag_code,tag_name,"
                    +"applicable_business_type,tag_level,color,status,create_by,create_time) values"
                    +"('LEAD_SOURCE_STALE','Stale source','LEAD','SOURCE','#64748B','0',"
                    +"'migration-test',sysdate())");
            statement.executeUpdate(leadFixtureSql(9_920_521L,
                    "MIGRATION-SOURCE-KNOWN","online","0"));
            statement.executeUpdate(leadFixtureSql(9_920_522L,
                    "MIGRATION-SOURCE-UNKNOWN","historical_partner","0"));
            statement.executeUpdate(leadFixtureSql(9_920_523L,
                    "MIGRATION-SOURCE-BLANK","","0"));
            statement.executeUpdate(leadFixtureSql(9_920_524L,
                    "MIGRATION-SOURCE-DISABLED","retired_campaign","0"));
            statement.executeUpdate(leadFixtureSql(9_920_525L,
                    "MIGRATION-SOURCE-DELETED","deleted_historical","2"));
            statement.executeUpdate("insert into biz_business_tag_rel("
                    +"business_type,business_id,tag_id,tag_source,confirm_status,"
                    +"create_by,create_time) select 'LEAD',9920521,tag_id,'SYSTEM','PENDING',"
                    +"'migration-test',sysdate() from biz_business_tag "
                    +"where tag_code='LEAD_SOURCE_STALE'");
        }
        catch(SQLException exception)
        {
            throw new AssertionError("Could not insert lead source governance fixtures",exception);
        }
    }

    private String leadFixtureSql(long leadId,String leadNo,String sourceCode,String delFlag)
    {
        return "insert into biz_lead(lead_id,lead_no,lead_name,source_code,status,"
                +"pool_status,priority,disposition,del_flag,create_by,create_time) values("
                +leadId+",'"+leadNo+"','Migration source fixture','"+sourceCode
                +"','0','0','2','ACTIVE','"+delFlag+"','migration-test',sysdate())";
    }

    private void verifyLeadGovernanceAuditSchemaBoundary(String url)
    {
        try(Connection connection=DriverManager.getConnection(url,
                System.getenv("TODO_MIGRATION_DB_USER"),
                System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            LeadNavigationState state=leadNavigationState(connection);
            assertTrue(state.auditTableExists(),
                    "V0.20.52 must establish the audit schema");
            assertEquals(0L,state.auditRows(),
                    "V0.20.52 is schema-only and must not govern source data");
            assertEquals(0L,state.navigationRows(),
                    "V0.20.52 must not create operational navigation");
        }
        catch(SQLException exception)
        {
            throw new AssertionError("Lead governance audit schema boundary failed",exception);
        }
    }

    private void verifyLeadNavigationFailureRepairAndRetry(String url)
    {
        Path directory=null;
        try(Connection connection=DriverManager.getConnection(url,
                System.getenv("TODO_MIGRATION_DB_USER"),
                System.getenv("TODO_MIGRATION_DB_PASSWORD"));
            InputStream dataInput=getClass().getResourceAsStream(
                    "/db/migration/V0_20_53__lead_source_governance_and_navigation.sql"))
        {
            assertTrue(dataInput!=null,"0.20.53 migration resource is required");
            LeadNavigationState before=leadNavigationState(connection);
            assertTrue(before.auditTableExists(),
                    "Failure probe must start from the legal V0.20.52 schema");
            assertEquals(0L,before.auditRows(),
                    "Failure probe must start before source governance DML");
            String sql=new String(dataInput.readAllBytes(),StandardCharsets.UTF_8);
            String marker="-- FAILURE_INJECTION_POINT_BEFORE_COMMIT";
            assertTrue(sql.contains(marker),"Navigation failure-injection marker is required");
            String injected=sql.replace(marker,
                    "signal sqlstate '45000' set message_text='INJECTED_BEFORE_COMMIT';");
            directory=Files.createTempDirectory("lead-navigation-atomicity-");
            Files.writeString(directory.resolve(
                    "V0_20_53__lead_source_governance_and_navigation.sql"),
                    injected,StandardCharsets.UTF_8);

            String filesystem="filesystem:"+directory.toAbsolutePath().toString()
                    .replace('\\','/');
            Flyway probe=Flyway.configure()
                    .dataSource(url,System.getenv("TODO_MIGRATION_DB_USER"),
                            System.getenv("TODO_MIGRATION_DB_PASSWORD"))
                    .locations(filesystem)
                    .table("flyway_lead_navigation_probe_history")
                    .baselineOnMigrate(true)
                    .baselineVersion("0.20.52")
                    .target("0.20.53")
                    .load();
            RuntimeException injectedFailure=assertThrows(RuntimeException.class,probe::migrate);
            assertTrue(messageChain(injectedFailure).contains("INJECTED_BEFORE_COMMIT"),
                    "Probe must reach the explicit pre-commit failure injection");

            assertEquals(before,leadNavigationState(connection),
                    "V0.20.53 permanent DML must roll back to the legal V0.20.52 state");
            assertEquals(1L,count(connection,
                    "select count(*) from flyway_lead_navigation_probe_history "
                    +"where version='0.20.53' and success=0"),
                    "Failed V0.20.53 must be explicit in Flyway history");

            probe.repair();
            assertEquals(0L,count(connection,
                    "select count(*) from flyway_lead_navigation_probe_history "
                    +"where version='0.20.53' and success=0"),
                    "Flyway repair must remove the failed V0.20.53 entry");
            assertEquals("0.20.52",probe.info().current().getVersion().getVersion(),
                    "After repair the database must remain legally applied through V0.20.52");
            assertEquals(before,leadNavigationState(connection),
                    "Repair must not mutate lead source/navigation business state");
            try(Statement cleanup=connection.createStatement())
            {
                cleanup.executeUpdate("drop table flyway_lead_navigation_probe_history");
            }

            Flyway retry=Flyway.configure()
                    .dataSource(url,System.getenv("TODO_MIGRATION_DB_USER"),
                            System.getenv("TODO_MIGRATION_DB_PASSWORD"))
                    .baselineOnMigrate(true)
                    .baselineVersion("0.15.0")
                    .locations("classpath:db/migration")
                    .target("0.20.53")
                    .load();
            MigrateResult retried=retry.migrate();
            assertTrue(retried.success,"Repaired V0.20.53 must rerun successfully");
            assertEquals("0.20.53",retry.info().current().getVersion().getVersion());
            LeadNavigationState after=leadNavigationState(connection);
            assertTrue(after.auditTableExists());
            assertTrue(after.auditRows()>0,
                    "Successful V0.20.53 must persist governance audit rows");
            assertTrue(after.navigationRows()>0,
                    "Successful V0.20.53 must create lead workbench navigation");
        }
        catch(Exception exception)
        {
            throw new AssertionError("Lead navigation failure/repair/retry contract failed",exception);
        }
        finally
        {
            if(directory!=null)
            {
                try
                {
                    Files.deleteIfExists(directory.resolve(
                            "V0_20_53__lead_source_governance_and_navigation.sql"));
                    Files.deleteIfExists(directory);
                }
                catch(IOException ignored){ }
            }
        }
    }

    private LeadNavigationState leadNavigationState(Connection connection) throws SQLException
    {
        return new LeadNavigationState(
                text(connection,"select group_concat(concat(lead_id,':',source_code) "
                        +"order by lead_id separator '|') from biz_lead "
                        +"where lead_id between 9920521 and 9920525"),
                count(connection,"select count(*) from biz_business_tag_rel relation "
                        +"join biz_business_tag tag on tag.tag_id=relation.tag_id "
                        +"where relation.business_type='LEAD' "
                        +"and relation.business_id between 9920521 and 9920525 "
                        +"and tag.tag_level='SOURCE'"),
                count(connection,"select count(*) from information_schema.tables "
                        +"where table_schema=database() "
                        +"and table_name='biz_lead_source_governance_audit'") == 1,
                tableExists(connection,"biz_lead_source_governance_audit")
                        ? count(connection,"select count(*) "
                                +"from biz_lead_source_governance_audit") : 0L,
                count(connection,"select count(*) from sys_menu where component in "
                        +"('lead/review/index','lead/retry/index','lead/dead-pool/index',"
                        +"'lead/policy/index')"));
    }

    private record LeadNavigationState(String leadSources,long sourceRelations,
            boolean auditTableExists,long auditRows,long navigationRows) { }

    private void verifyLeadSourceGovernance(String url)
    {
        try(Connection connection=DriverManager.getConnection(url,
                System.getenv("TODO_MIGRATION_DB_USER"),
                System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            assertEquals("online",text(connection,
                    "select source_code from biz_lead where lead_id=9920521"));
            assertEquals("deleted_historical",text(connection,
                    "select source_code from biz_lead where lead_id=9920525"));
            assertEquals(4L,count(connection,
                    "select count(*) from biz_lead_source_governance_audit "
                    +"where lead_id between 9920521 and 9920525"));
            assertEquals(1L,count(connection,
                    "select count(*) from biz_lead_source_governance_audit "
                    +"where lead_id=9920521 and original_source_code='online' "
                    +"and governed_source_code='online' "
                    +"and governance_result='RETAINED'"));
            assertEquals(3L,count(connection,
                    "select count(*) from biz_lead_source_governance_audit audit "
                    +"join biz_lead_setting source "
                    +"on source.setting_type='source' "
                    +"and source.setting_code=audit.governed_source_code "
                    +"and source.status='0' "
                    +"where audit.lead_id between 9920522 and 9920524 "
                    +"and audit.governance_result='REMAPPED' "
                    +"and audit.governed_source_code like 'LEGACY_%'"));
            assertEquals(0L,count(connection,
                    "select count(*) from biz_lead l "
                    +"where l.lead_id between 9920521 and 9920524 "
                    +"and (select count(*) from biz_business_tag_rel relation "
                    +"join biz_business_tag tag on tag.tag_id=relation.tag_id "
                    +"where relation.business_type='LEAD' "
                    +"and relation.business_id=l.lead_id "
                    +"and tag.tag_level='SOURCE')<>1"));
            assertEquals(0L,count(connection,
                    "select count(*) from biz_lead l "
                    +"where l.lead_id between 9920521 and 9920524 "
                    +"and not exists(select 1 from biz_business_tag_rel relation "
                    +"join biz_business_tag tag on tag.tag_id=relation.tag_id "
                    +"where relation.business_type='LEAD' "
                    +"and relation.business_id=l.lead_id "
                    +"and tag.tag_level='SOURCE' "
                    +"and tag.tag_code=concat('LEAD_SOURCE_',upper(l.source_code)))"));
        }
        catch(SQLException exception)
        {
            throw new AssertionError("Lead source governance invariants failed",exception);
        }
    }

    private String messageChain(Throwable error)
    {
        StringBuilder messages=new StringBuilder();
        for(Throwable current=error;current!=null;current=current.getCause())
            messages.append(' ').append(current.getMessage());
        return messages.toString();
    }

    private void verifyTodoSchedulePolicySnapshotSchema(String url)
    {
        try (Connection connection = DriverManager.getConnection(url,
                System.getenv("TODO_MIGRATION_DB_USER"),
                System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            assertEquals(3L,count(connection,
                    "select count(*) from information_schema.columns where table_schema=database() "
                    +"and table_name='todo_schedule_plan' and column_name in "
                    +"('assignment_policy_id','assignment_policy_version',"
                    +"'assignment_policy_snapshot_source')"));
            assertEquals(1L,count(connection,
                    "select count(*) from information_schema.table_constraints "
                    +"where constraint_schema=database() and table_name='todo_schedule_plan' "
                    +"and constraint_name='chk_todo_schedule_plan_policy_snapshot' "
                    +"and constraint_type='CHECK'"));
        }
        catch(SQLException exception)
        {
            throw new AssertionError("Todo schedule policy snapshot schema invariants failed",exception);
        }
    }

    private void verifyLeadPublicationRollbackAfterInjectedFailure(String url)
    {
        Path directory=null;
        try(Connection connection=DriverManager.getConnection(url,
                System.getenv("TODO_MIGRATION_DB_USER"),
                System.getenv("TODO_MIGRATION_DB_PASSWORD"));
            InputStream input=getClass().getResourceAsStream(
                    "/db/migration/V0_20_51__publish_lead_todo_templates.sql"))
        {
            assertTrue(input!=null,"0.20.51 migration resource is required");
            PublicationState before=publicationState(connection);
            String sql=new String(input.readAllBytes(),StandardCharsets.UTF_8);
            String marker="-- FAILURE_INJECTION_POINT_AFTER_VERSION_INSERT";
            assertTrue(sql.contains(marker),"Publication failure-injection marker is required");
            String injected=sql.replace(marker,
                    "signal sqlstate '45000' set message_text='INJECTED_AFTER_VERSION_INSERT';");
            directory=Files.createTempDirectory("todo-publish-atomicity-");
            Files.writeString(directory.resolve(
                    "V0_20_50_1__lead_publish_atomicity_probe.sql"),injected,StandardCharsets.UTF_8);

            String filesystem="filesystem:"+directory.toAbsolutePath().toString().replace('\\','/');
            Flyway probe=Flyway.configure()
                    .dataSource(url,System.getenv("TODO_MIGRATION_DB_USER"),
                            System.getenv("TODO_MIGRATION_DB_PASSWORD"))
                    .locations(filesystem)
                    .table("flyway_atomicity_probe_history")
                    .baselineOnMigrate(true)
                    .baselineVersion("0.20.50")
                    .validateOnMigrate(false)
                    .load();
            assertThrows(RuntimeException.class,probe::migrate);

            assertEquals(before,publicationState(connection),
                    "Every permanent publication mutation must roll back together");
            assertEquals(0L,count(connection,
                    "select count(*) from flyway_schema_history where version='0.20.50.1' "
                    +"and success=1"));
            assertEquals("0.20.50",text(connection,
                    "select version from flyway_schema_history where success=1 "
                    +"order by installed_rank desc limit 1"));
            try(Statement cleanup=connection.createStatement())
            {
                cleanup.execute("drop table if exists flyway_atomicity_probe_history");
            }
        }
        catch(Exception exception)
        {
            throw new AssertionError("Lead publication atomicity failure injection failed",exception);
        }
        finally
        {
            if(directory!=null)
            {
                try
                {
                    Files.deleteIfExists(directory.resolve(
                            "V0_20_50_1__lead_publish_atomicity_probe.sql"));
                    Files.deleteIfExists(directory);
                }
                catch(IOException ignored){ }
            }
        }
    }

    private PublicationState publicationState(Connection connection) throws SQLException
    {
        return new PublicationState(
                text(connection,"select coalesce(group_concat(concat(template_code,':',current_version) "
                        +"order by template_code separator '|'),'') from todo_template "
                        +"where template_code between 'TD-001' and 'TD-004'"),
                text(connection,"select coalesce(group_concat(concat(r.rule_code,':',r.enabled,':',"
                        +"r.template_version_id) order by r.rule_code separator '|'),'') "
                        +"from todo_trigger_rule r where r.event_type='LEAD_ASSIGNED' "
                        +"or r.rule_code='TRIGGER_LEAD_ASSIGNED_TD001_V02051'"),
                text(connection,"select coalesce(group_concat(concat(template_code,':',"
                        +"definition_package_state,':',foundation_state,':',production_state,':',"
                        +"sha2(cast(definition_json as char),256)) order by template_code separator '|'),'') "
                        +"from todo_prd_definition_catalog where template_code between 'TD-001' and 'TD-004'"),
                count(connection,"select count(*) from todo_template_version v join todo_template t "
                        +"on t.template_id=v.template_id where t.template_code between 'TD-001' and 'TD-004'"),
                count(connection,"select count(*) from sys_dict_type "
                        +"where dict_type='law_lead_progress_type'"),
                count(connection,"select count(*) from sys_dict_data "
                        +"where dict_type='law_lead_progress_type'"));
    }

    private String text(Connection connection,String sql) throws SQLException
    {
        try(Statement statement=connection.createStatement();ResultSet rows=statement.executeQuery(sql))
        {
            assertTrue(rows.next());
            return rows.getString(1);
        }
    }

    private record PublicationState(String currentVersions,String triggers,String catalogue,
            long versionRows,long dictionaryTypes,long dictionaryRows) { }

    private void verifyTodoTemplateVersionEditMetadata(String url)
    {
        try (Connection connection = DriverManager.getConnection(url, System.getenv("TODO_MIGRATION_DB_USER"),
            System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            assertEquals(2L, count(connection,
                "select count(*) from information_schema.columns where table_schema=database() "
                    + "and table_name='todo_template_version' and column_name in ('update_by','update_time')"));
            assertEquals(3L, count(connection,
                "select count(*) from information_schema.statistics where table_schema=database() "
                    + "and table_name='todo_template_version' and index_name='idx_todo_template_version_recent_edit'"));
        }
        catch (SQLException exception)
        {
            throw new AssertionError("Todo template version edit metadata invariants failed", exception);
        }
    }

    private void verifyTodoPhaseOneAssetClosure(String url)
    {
        try (Connection connection = DriverManager.getConnection(url, System.getenv("TODO_MIGRATION_DB_USER"),
            System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            assertEquals(4L, count(connection,"select count(*) from todo_sla_rule where status='0'"));
            assertEquals(15L, count(connection,"select count(*) from todo_dod_rule where status='0'"));
            assertEquals(15L, count(connection,
                "select count(*) from todo_template t join todo_template_version v on v.template_id=t.template_id "
                    + "where v.status='PUBLISHED' and t.template_code not like 'TD-%'"));
            assertEquals(30L, count(connection,
                "select count(*) from todo_template_draft_rule_ref ref join todo_template_version v "
                    + "on v.version_id=ref.version_id where v.status='PUBLISHED'"));
            assertEquals(0L, count(connection,
                "select count(*) from (select t.template_id from todo_template t join todo_template_version v "
                    + "on v.template_id=t.template_id where v.status='PUBLISHED' and t.template_code not like 'TD-%' "
                    + "group by t.template_id having count(*)<>1) invalid"));
            assertEquals(0L, count(connection,
                "select count(*) from todo_template t join todo_template_version v on v.template_id=t.template_id "
                    + "where t.template_code='LEAD_FIRST_CONTACT' and v.status='PUBLISHED' "
                    + "and json_unquote(json_extract(v.definition_json,'$.owner.config.operand'))<>'ownerId'"));
            assertEquals(0L, count(connection,
                "select count(*) from todo_template t join todo_template_version v on v.template_id=t.template_id "
                    + "where t.template_code='CASE_ACCEPT' and v.status='PUBLISHED' "
                    + "and json_unquote(json_extract(v.definition_json,'$.owner.config.operand'))<>'lawyerId'"));
            assertEquals(16L, count(connection,
                "select count(*) from todo_trigger_rule r join todo_template_version v "
                    + "on v.version_id=r.template_version_id where r.enabled='Y' and v.status='PUBLISHED'"));
        }
        catch (SQLException exception)
        {
            throw new AssertionError("Todo phase-one asset closure invariants failed", exception);
        }
    }

    private void verifyReadableNavigationMenuNames(String url)
    {
        try (Connection connection = DriverManager.getConnection(url, System.getenv("TODO_MIGRATION_DB_USER"),
            System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            assertEquals(0L, count(connection,
                "select count(*) from sys_menu where menu_type in ('M','C') and menu_name <> '' "
                    + "and menu_name not regexp '[^?]'"));
        }
        catch (SQLException exception)
        {
            throw new AssertionError("Navigation menu names must not contain bootstrap replacement characters", exception);
        }
    }

    private void verifyTodoConfigurationResourceSchema(String url)
    {
        try (Connection connection = DriverManager.getConnection(url, System.getenv("TODO_MIGRATION_DB_USER"),
            System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            assertEquals(43L, count(connection,
                "select count(*) from todo_event_catalog where status='ACTIVE' and schema_status='READY' "
                    + "and json_length(json_extract(payload_schema_json,'$.properties'))>0 "
                    + "and sample_payload_json is not null"));
            assertEquals(5L, count(connection,
                "select count(*) from todo_validator_metadata where status='ACTIVE'"));
            assertEquals(41L, count(connection,
                "select count(*) from todo_configuration_resource_item where resource_type='FIELD' and status='ACTIVE'"));
            assertEquals(2L, count(connection,
                "select count(*) from todo_configuration_resource_item where resource_type='FIELD' "
                    + "and status='ACTIVE' and resource_code in ('invalidReasonCode','salesExplanation')"));
            assertEquals(16L, count(connection,
                "select count(*) from todo_configuration_resource_item where resource_type='MATERIAL' and status='ACTIVE'"));
            assertEquals(8L, count(connection,
                "select count(*) from todo_configuration_resource_item where resource_type='DOD_RECIPE' and status='ACTIVE'"));
            assertEquals(1L, count(connection,
                "select count(*) from sys_menu where component='todo/config/resource/index' "
                    + "and perms='todo:resource:list'"));
            assertEquals(5L, count(connection,
                "select count(distinct perms) from sys_menu where perms in ('todo:resource:list',"
                    + "'todo:resource:query','todo:resource:add','todo:resource:edit','todo:resource:status')"));
            assertEquals(1L, count(connection,
                "select count(*) from information_schema.columns where table_schema=database() "
                    + "and table_name='todo_dod_rule' and column_name='business_type'"));
        }
        catch (SQLException exception)
        {
            throw new AssertionError("Todo configuration resource database invariants failed", exception);
        }
    }

    private void verifyTodoDodRecipeEventAlignment(String url)
    {
        try (Connection connection = DriverManager.getConnection(url, System.getenv("TODO_MIGRATION_DB_USER"),
            System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            assertEquals(5L, count(connection,
                "select count(*) from todo_configuration_resource_item where resource_type='DOD_RECIPE' "
                    + "and status='ACTIVE' and json_length(json_extract(value_json,'$.businessActions'))=1 "
                    + "and json_length(json_extract(value_json,'$.templateStages'))=0"));
            assertEquals(5L, count(connection,
                "select count(*) from todo_configuration_resource_item where "
                    + "(resource_code='LEAD_FIRST_CONTACT_READY' "
                    + "and json_contains(json_extract(value_json,'$.businessActions'),json_quote('LEAD_ASSIGNED'))) "
                    + "or (resource_code='CUSTOMER_PROGRESS_READY' "
                    + "and json_contains(json_extract(value_json,'$.businessActions'),json_quote('LEAD_FIRST_CONTACT_VALID'))) "
                    + "or (resource_code='CONTRACT_SIGN_READY' "
                    + "and json_contains(json_extract(value_json,'$.businessActions'),json_quote('CONTRACT_APPROVED'))) "
                    + "or (resource_code='CASE_ACCEPT_READY' "
                    + "and json_contains(json_extract(value_json,'$.businessActions'),json_quote('CASE_ASSIGNED'))) "
                    + "or (resource_code='MATTER_ARCHIVE_READY' "
                    + "and json_contains(json_extract(value_json,'$.businessActions'),json_quote('CASE_CLOSED')))"));
        }
        catch (SQLException exception)
        {
            throw new AssertionError("Todo DoD recipe event alignment invariants failed", exception);
        }
    }

    private void verifyTodoConfigurationCenterSchema(String url)
    {
        try (Connection connection = DriverManager.getConnection(url, System.getenv("TODO_MIGRATION_DB_USER"),
            System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            assertEquals(4L, count(connection,
                "select count(*) from information_schema.tables where table_schema=database() "
                    + "and table_name in ('todo_sla_rule','todo_dod_rule','todo_template_draft_rule_ref','todo_simulation_record')"));
            assertEquals(6L, count(connection,
                "select count(*) from sys_menu where component in "
                    + "('todo/config/template/index','todo/config/trigger/index','todo/config/sla/index',"
                    + "'todo/config/dod/index','todo/config/simulation/index','todo/config/release/index')"));
            assertEquals(14L, count(connection,
                "select count(*) from sys_dict_type where dict_type like 'law_todo_%' and dict_type in "
                    + "('law_todo_business_stage','law_todo_business_type','law_todo_template_type',"
                    + "'law_todo_publish_status','law_todo_trigger_mode','law_todo_condition_operator',"
                    + "'law_todo_owner_rule_type','law_todo_sla_type','law_todo_sla_unit',"
                    + "'law_todo_sla_start_strategy','law_todo_timeout_strategy',"
                    + "'law_todo_dod_rule_type','law_todo_rule_status','law_todo_version_status')"));
            assertEquals(3L, count(connection,
                "select count(*) from information_schema.columns where table_schema=database() "
                    + "and table_name='todo_template_version' and column_name in "
                    + "('change_summary','impact_scope','rollback_source_version_id')"));
            assertEquals(1L, count(connection,
                "select count(*) from sys_menu where component='todo/config/index' and visible='1' and status='1'"));
            assertEquals(6L, count(connection,
                "select count(*) from sys_menu page join sys_menu parent on parent.menu_id=page.parent_id "
                    + "where parent.menu_name='Todo Engine' and parent.path='todo-engine' and parent.menu_type='M' "
                    + "and page.menu_type='C' and page.component in ('todo/config/template/index',"
                    + "'todo/config/trigger/index','todo/config/sla/index','todo/config/dod/index',"
                    + "'todo/config/simulation/index','todo/config/release/index')"));
            assertEquals(24L, count(connection,
                "select count(distinct perms) from sys_menu where perms in ('todo:template:list','todo:template:create',"
                    + "'todo:template:edit','todo:template:copy','todo:trigger:list','todo:trigger:create',"
                    + "'todo:trigger:edit','todo:trigger:toggle','todo:sla-rule:list','todo:sla-rule:create',"
                    + "'todo:sla-rule:edit','todo:sla-rule:copy','todo:sla-rule:toggle','todo:dod-rule:list',"
                    + "'todo:dod-rule:create','todo:dod-rule:edit','todo:dod-rule:copy','todo:dod-rule:toggle',"
                    + "'todo:simulation:list','todo:simulation:simulate','todo:release:list','todo:release:publish',"
                    + "'todo:release:diff','todo:release:rollback')"));
            assertEquals(6L, count(connection,
                "select count(*) from sys_menu where (component='todo/config/template/index' and menu_name='待办模板') "
                    + "or (component='todo/config/trigger/index' and menu_name='触发规则') "
                    + "or (component='todo/config/sla/index' and menu_name='SLA规则') "
                    + "or (component='todo/config/dod/index' and menu_name='完成条件') "
                    + "or (component='todo/config/simulation/index' and menu_name='模拟测试') "
                    + "or (component='todo/config/release/index' and menu_name='发布记录')"));
            assertEquals(14L, count(connection,
                "select count(*) from sys_dict_type where dict_type in "
                    + "('law_todo_business_stage','law_todo_business_type','law_todo_template_type',"
                    + "'law_todo_publish_status','law_todo_trigger_mode','law_todo_condition_operator',"
                    + "'law_todo_owner_rule_type','law_todo_sla_type','law_todo_sla_unit',"
                    + "'law_todo_sla_start_strategy','law_todo_timeout_strategy','law_todo_dod_rule_type',"
                    + "'law_todo_rule_status','law_todo_version_status') and dict_name in "
                    + "('业务阶段','业务类型','模板类型','发布状态','触发方式','条件操作符','负责人规则类型',"
                    + "'SLA类型','SLA时间单位','SLA计时起点','超时策略','完成条件类型','规则状态','版本状态')"));
            assertEquals(48L, count(connection,
                "select count(*) from sys_dict_data where dict_type in "
                    + "('law_todo_business_stage','law_todo_business_type','law_todo_template_type',"
                    + "'law_todo_publish_status','law_todo_trigger_mode','law_todo_condition_operator',"
                    + "'law_todo_owner_rule_type','law_todo_sla_type','law_todo_sla_unit',"
                    + "'law_todo_sla_start_strategy','law_todo_timeout_strategy','law_todo_dod_rule_type',"
                    + "'law_todo_rule_status','law_todo_version_status') and char_length(dict_label)<length(dict_label)"));
            assertEquals(2L, count(connection,
                "select count(*) from sys_dict_data where dict_type='law_todo_rule_status' and dict_value in ('0','1')"));
            assertEquals(1L, count(connection,
                "select count(*) from sys_dict_data where dict_type='law_todo_sla_type' and dict_value='RESPONSE'"));
            assertEquals(1L, count(connection,
                "select count(*) from sys_dict_data where dict_type='law_todo_sla_start_strategy' and dict_value='TODO_CREATED'"));
            assertEquals(1L, count(connection,
                "select count(*) from sys_dict_data where dict_type='law_todo_dod_rule_type' and dict_value='TASK'"));
            assertEquals(9L, count(connection,
                "select count(*) from sys_dict_data where dict_type='law_todo_owner_rule_type' and dict_value in "
                    + "('USER','ROLE','DEPT','POST','PAYLOAD','BUSINESS_OWNER','SUPERVISOR','ROUND_ROBIN','ASSIGNMENT_LEVEL')"));
        }
        catch (SQLException exception)
        {
            throw new AssertionError("Todo configuration center database invariants failed", exception);
        }
    }

    private void verifyFoundationGovernanceRoles(String url, RoleSnapshot beforeFoundationGovernanceMigration)
    {
        Map<String, Set<String>> expectedPermissions = Map.of(
            "foundation_product_owner", Set.of("todo:decision:view", "todo:decision:edit", "todo:admission:view",
                "todo:admission:edit"),
            "foundation_security_reviewer", Set.of("todo:admission:view", "todo:admission:edit"),
            "foundation_arch_dba_reviewer", Set.of("todo:admission:view", "todo:admission:edit", "todo:admission:export"),
            "foundation_qa_acceptor", Set.of("todo:admission:view", "todo:admission:edit"),
            "foundation_independent_reviewer", Set.of("todo:admission:view", "todo:admission:edit")
        );
        Map<String, GovernanceRoleDefinition> expectedDefinitions = Map.of(
            "foundation_product_owner", new GovernanceRoleDefinition("Foundation产品负责人", "40"),
            "foundation_security_reviewer", new GovernanceRoleDefinition("Foundation安全评审人", "41"),
            "foundation_arch_dba_reviewer", new GovernanceRoleDefinition("Foundation架构DBA评审人", "42"),
            "foundation_qa_acceptor", new GovernanceRoleDefinition("Foundation QA验收人", "43"),
            "foundation_independent_reviewer", new GovernanceRoleDefinition("Foundation独立准入评审人", "44")
        );
        try (Connection connection = DriverManager.getConnection(url, System.getenv("TODO_MIGRATION_DB_USER"),
            System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            assertEquals(5L, count(connection,
                "select count(*) from sys_role where role_key in ("
                    + "'foundation_product_owner','foundation_security_reviewer',"
                    + "'foundation_arch_dba_reviewer','foundation_qa_acceptor',"
                    + "'foundation_independent_reviewer') and status='0' and del_flag='0'"));
            assertEquals(5L, count(connection,
                "select count(*) from sys_role where create_by='flyway-v0.20.28'"));
            assertEquals(expectedPermissions.keySet(), roleKeysCreatedByFoundationMigration(connection));
            assertEquals(expectedDefinitions, governanceRoleDefinitions(connection));
            assertEquals(0L, count(connection,
                "select count(*) from sys_user where user_name like 'ft\\_%' escape '\\\\'"));
            assertEquals(0L, count(connection,
                "select count(*) from sys_dept where dept_code in ('FOUNDATION_TEST_FIRM','FOUNDATION_TEST_SALES',"
                    + "'FOUNDATION_TEST_CASE_MANAGEMENT','FOUNDATION_TEST_GENERAL_LAW','FOUNDATION_TEST_FINANCE',"
                    + "'FOUNDATION_TEST_GOVERNANCE')"));
            assertEquals(0L, count(connection,
                "select count(*) from sys_dept where create_by='foundation-test-seeder'"));
            assertEquals(0L, count(connection,
                "select count(*) from sys_user_role ur join sys_user u on u.user_id=ur.user_id "
                    + "where u.user_name like 'ft\\_%' escape '\\\\'"));

            Set<Long> allowedMenuIds = definitionMenuAndAncestorIds(connection);
            RoleSnapshot afterFoundationGovernanceMigration = snapshotRoleState(connection);
            assertPreExistingRolesUnchanged(beforeFoundationGovernanceMigration, afterFoundationGovernanceMigration);
            assertEquals(expectedPermissions.keySet(), newRoleKeys(beforeFoundationGovernanceMigration,
                afterFoundationGovernanceMigration), "Unexpected governance role delta");
            assertEquals(expectedRoleMenuGrantMultiset(beforeFoundationGovernanceMigration.roleMenuGrants(),
                expectedRoleMenuGrants(connection, expectedPermissions, allowedMenuIds)),
                afterFoundationGovernanceMigration.roleMenuGrants(), "Unexpected role-menu grant delta");
            for (Map.Entry<String, Set<String>> expected : expectedPermissions.entrySet())
            {
                assertEquals(expected.getValue(), buttonPermissions(connection, expected.getKey()),
                    () -> "Unexpected button permissions for " + expected.getKey());
                assertEquals((long) expected.getValue().size(), roleMenuGrantCount(connection, expected.getKey(), "F"),
                    () -> "Unexpected button permission grant count for " + expected.getKey());
                assertEquals(allowedMenuIds, nonButtonMenuIds(connection, expected.getKey()),
                    () -> "Unexpected non-button menus for " + expected.getKey());
                assertEquals((long) allowedMenuIds.size(), nonButtonMenuGrantCount(connection, expected.getKey()),
                    () -> "Unexpected non-button menu grant count for " + expected.getKey());
            }
        }
        catch (SQLException exception)
        {
            throw new AssertionError("Foundation governance role invariants failed", exception);
        }
    }

    private void verifySameMarkerRoleCollisionReceivesNoGrants(String url)
    {
        try (Connection connection = DriverManager.getConnection(url, System.getenv("TODO_MIGRATION_DB_USER"),
            System.getenv("TODO_MIGRATION_DB_PASSWORD")); Statement statement = connection.createStatement())
        {
            statement.executeUpdate("delete rm from sys_role_menu rm join sys_role r on r.role_id=rm.role_id "
                + "where r.role_key in ('foundation_product_owner','foundation_security_reviewer',"
                + "'foundation_arch_dba_reviewer','foundation_qa_acceptor','foundation_independent_reviewer')");
            statement.executeUpdate("delete from sys_role where role_key in ('foundation_product_owner',"
                + "'foundation_security_reviewer','foundation_arch_dba_reviewer','foundation_qa_acceptor',"
                + "'foundation_independent_reviewer')");
            statement.executeUpdate("delete from flyway_schema_history where version='0.20.28'");
            statement.executeUpdate("insert into sys_role(role_name,role_key,role_sort,data_scope,menu_check_strictly,"
                + "dept_check_strictly,status,del_flag,create_by,create_time) values ('collision',"
                + "'foundation_product_owner',999,'5',1,1,'0','0','flyway-v0.20.28',sysdate())");
        }
        catch (SQLException exception)
        {
            throw new AssertionError("Collision scenario setup failed", exception);
        }

        Flyway collisionFlyway = Flyway.configure()
            .dataSource(url, System.getenv("TODO_MIGRATION_DB_USER"), System.getenv("TODO_MIGRATION_DB_PASSWORD"))
            .baselineOnMigrate(true)
            .baselineVersion("0.15.0")
            .locations("classpath:db/migration")
            .outOfOrder(true)
            .load();
        assertTrue(collisionFlyway.migrate().success);

        try (Connection connection = DriverManager.getConnection(url, System.getenv("TODO_MIGRATION_DB_USER"),
            System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            assertEquals(0L, count(connection, "select count(*) from sys_role_menu rm join sys_role r "
                + "on r.role_id=rm.role_id where r.role_key='foundation_product_owner' and r.role_name='collision'"));
            assertEquals(4L, count(connection, "select count(*) from sys_role where role_key in "
                + "('foundation_security_reviewer','foundation_arch_dba_reviewer','foundation_qa_acceptor',"
                + "'foundation_independent_reviewer') and create_by='flyway-v0.20.28'"));
        }
        catch (SQLException exception)
        {
            throw new AssertionError("Collision scenario verification failed", exception);
        }
    }

    private RoleSnapshot snapshotRoleState(String url)
    {
        try (Connection connection = DriverManager.getConnection(url, System.getenv("TODO_MIGRATION_DB_USER"),
            System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            return snapshotRoleState(connection);
        }
        catch (SQLException exception)
        {
            throw new AssertionError("Foundation governance role snapshot failed", exception);
        }
    }

    private RoleSnapshot snapshotRoleState(Connection connection) throws SQLException
    {
        Map<Long, RoleState> roleStates = new HashMap<>();
        Map<RoleMenuGrant, Integer> roleMenuGrants = new HashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("select role_id,role_key,role_name,role_sort,data_scope,"
                 + "menu_check_strictly,dept_check_strictly,status,del_flag,create_by,update_by,create_time,update_time,remark "
                 + "from sys_role"))
        {
            while (rows.next())
            {
                RoleState role = new RoleState(rows.getLong("role_id"), nullable(rows, "role_key"),
                    nullable(rows, "role_name"), nullable(rows, "role_sort"), nullable(rows, "data_scope"),
                    nullable(rows, "menu_check_strictly"), nullable(rows, "dept_check_strictly"), nullable(rows, "status"),
                    nullable(rows, "del_flag"), nullable(rows, "create_by"), nullable(rows, "update_by"),
                    nullableObject(rows, "create_time"), nullableObject(rows, "update_time"), nullable(rows, "remark"));
                roleStates.put(role.roleId(), role);
            }
        }
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("select r.role_key,rm.menu_id from sys_role_menu rm "
                 + "join sys_role r on r.role_id=rm.role_id"))
        {
            while (rows.next())
            {
                roleMenuGrants.merge(new RoleMenuGrant(rows.getString(1), rows.getLong(2)), 1, Integer::sum);
            }
        }
        return new RoleSnapshot(roleStates, roleMenuGrants);
    }

    private Set<RoleMenuGrant> expectedRoleMenuGrants(Connection connection, Map<String, Set<String>> expectedPermissions,
        Set<Long> allowedMenuIds) throws SQLException
    {
        Set<RoleMenuGrant> expectedGrants = new HashSet<>();
        for (Map.Entry<String, Set<String>> expected : expectedPermissions.entrySet())
        {
            for (Long menuId : allowedMenuIds)
            {
                expectedGrants.add(new RoleMenuGrant(expected.getKey(), menuId));
            }
            for (String permission : expected.getValue())
            {
                expectedGrants.add(new RoleMenuGrant(expected.getKey(), buttonMenuId(connection, permission)));
            }
        }
        return expectedGrants;
    }

    private long buttonMenuId(Connection connection, String permission) throws SQLException
    {
        try (PreparedStatement statement = connection.prepareStatement(
            "select menu_id from sys_menu where perms=? and menu_type='F'"))
        {
            statement.setString(1, permission);
            try (ResultSet rows = statement.executeQuery())
            {
                assertTrue(rows.next(), () -> "Required button permission menu must exist: " + permission);
                long menuId = rows.getLong(1);
                assertTrue(!rows.next(), () -> "Required button permission menu must be unique: " + permission);
                return menuId;
            }
        }
    }

    private String nullable(ResultSet rows, String column) throws SQLException
    {
        String value = rows.getString(column);
        return value == null ? SQL_NULL : value;
    }

    private Object nullableObject(ResultSet rows, String column) throws SQLException
    {
        Object value = rows.getObject(column);
        return value == null ? SQL_NULL : value;
    }

    private void assertPreExistingRolesUnchanged(RoleSnapshot before, RoleSnapshot after)
    {
        for (Map.Entry<Long, RoleState> entry : before.roleStates().entrySet())
        {
            assertEquals(entry.getValue(), after.roleStates().get(entry.getKey()),
                () -> "Pre-existing role was deleted or mutated: role_id=" + entry.getKey());
        }
        assertEquals(before.roleStates().size() + 5, after.roleStates().size(),
            "Migration must add exactly five roles without replacing existing role IDs");
    }

    private Set<String> newRoleKeys(RoleSnapshot before, RoleSnapshot after)
    {
        Set<Long> newRoleIds = new HashSet<>(after.roleStates().keySet());
        newRoleIds.removeAll(before.roleStates().keySet());
        Set<String> newRoleKeys = new HashSet<>();
        for (Long roleId : newRoleIds)
        {
            newRoleKeys.add(after.roleStates().get(roleId).roleKey());
        }
        return newRoleKeys;
    }

    private Map<RoleMenuGrant, Integer> expectedRoleMenuGrantMultiset(Map<RoleMenuGrant, Integer> before,
        Set<RoleMenuGrant> expectedNewGrants)
    {
        Map<RoleMenuGrant, Integer> expected = new HashMap<>(before);
        for (RoleMenuGrant grant : expectedNewGrants)
        {
            expected.merge(grant, 1, Integer::sum);
        }
        return expected;
    }

    private record RoleSnapshot(Map<Long, RoleState> roleStates, Map<RoleMenuGrant, Integer> roleMenuGrants)
    {
    }

    private record RoleState(long roleId, String roleKey, String roleName, String roleSort, String dataScope,
        String menuCheckStrictly, String deptCheckStrictly, String status, String delFlag, String createBy,
        String updateBy, Object createTime, Object updateTime, String remark)
    {
    }

    private record RoleMenuGrant(String roleKey, long menuId)
    {
    }

    private record GovernanceRoleDefinition(String roleName, String roleSort)
    {
    }

    private Map<String, GovernanceRoleDefinition> governanceRoleDefinitions(Connection connection) throws SQLException
    {
        Map<String, GovernanceRoleDefinition> definitions = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
            "select role_key,role_name,role_sort from sys_role where role_key in ("
                + "'foundation_product_owner','foundation_security_reviewer','foundation_arch_dba_reviewer',"
                + "'foundation_qa_acceptor','foundation_independent_reviewer') and data_scope='5' "
                + "and menu_check_strictly=1 and dept_check_strictly=1 and status='0' and del_flag='0' "
                + "and create_by='flyway-v0.20.28'"))
        {
            try (ResultSet rows = statement.executeQuery())
            {
                while (rows.next())
                {
                    definitions.put(rows.getString("role_key"), new GovernanceRoleDefinition(
                        rows.getString("role_name"), rows.getString("role_sort")));
                }
            }
        }
        return definitions;
    }

    private Set<String> roleKeysCreatedByFoundationMigration(Connection connection) throws SQLException
    {
        Set<String> roleKeys = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
            "select role_key from sys_role where create_by='flyway-v0.20.28'");
             ResultSet rows = statement.executeQuery())
        {
            while (rows.next())
            {
                roleKeys.add(rows.getString(1));
            }
        }
        return roleKeys;
    }

    private Set<String> buttonPermissions(Connection connection, String roleKey) throws SQLException
    {
        Set<String> permissions = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
            "select m.perms from sys_role_menu rm join sys_role r on r.role_id=rm.role_id "
                + "join sys_menu m on m.menu_id=rm.menu_id where r.role_key=? and m.menu_type='F' "
                + "and m.perms is not null and m.perms<>''"))
        {
            statement.setString(1, roleKey);
            try (ResultSet rows = statement.executeQuery())
            {
                while (rows.next())
                {
                    permissions.add(rows.getString(1));
                }
            }
        }
        return permissions;
    }

    private Set<String> roleKeysWithPermission(Connection connection, String permission) throws SQLException
    {
        Set<String> roleKeys = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
            "select r.role_key from sys_role_menu rm join sys_role r on r.role_id=rm.role_id "
                + "join sys_menu m on m.menu_id=rm.menu_id where m.perms=?"))
        {
            statement.setString(1, permission);
            try (ResultSet rows = statement.executeQuery())
            {
                while (rows.next())
                {
                    roleKeys.add(rows.getString(1));
                }
            }
        }
        return roleKeys;
    }

    private long roleMenuGrantCount(Connection connection, String roleKey, String menuType) throws SQLException
    {
        try (PreparedStatement statement = connection.prepareStatement(
            "select count(*) from sys_role_menu rm join sys_role r on r.role_id=rm.role_id "
                + "join sys_menu m on m.menu_id=rm.menu_id where r.role_key=? and m.menu_type=?"))
        {
            statement.setString(1, roleKey);
            statement.setString(2, menuType);
            try (ResultSet rows = statement.executeQuery())
            {
                assertTrue(rows.next());
                return rows.getLong(1);
            }
        }
    }

    private long nonButtonMenuGrantCount(Connection connection, String roleKey) throws SQLException
    {
        try (PreparedStatement statement = connection.prepareStatement(
            "select count(*) from sys_role_menu rm join sys_role r on r.role_id=rm.role_id "
                + "join sys_menu m on m.menu_id=rm.menu_id where r.role_key=? and m.menu_type<>'F'"))
        {
            statement.setString(1, roleKey);
            try (ResultSet rows = statement.executeQuery())
            {
                assertTrue(rows.next());
                return rows.getLong(1);
            }
        }
    }

    private Set<Long> nonButtonMenuIds(Connection connection, String roleKey) throws SQLException
    {
        Set<Long> menuIds = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
            "select m.menu_id from sys_role_menu rm join sys_role r on r.role_id=rm.role_id "
                + "join sys_menu m on m.menu_id=rm.menu_id where r.role_key=? and m.menu_type<>'F'"))
        {
            statement.setString(1, roleKey);
            try (ResultSet rows = statement.executeQuery())
            {
                while (rows.next())
                {
                    menuIds.add(rows.getLong(1));
                }
            }
        }
        return menuIds;
    }

    private Set<Long> definitionMenuAndAncestorIds(Connection connection) throws SQLException
    {
        Set<Long> menuIds = new HashSet<>();
        long menuId;
        try (PreparedStatement statement = connection.prepareStatement(
            "select menu_id from sys_menu where component='todo/config/index' order by menu_id limit 1");
             ResultSet rows = statement.executeQuery())
        {
            assertTrue(rows.next(), "Foundation configuration menu must exist");
            menuId = rows.getLong(1);
        }
        while (menuId != 0 && menuIds.add(menuId))
        {
            try (PreparedStatement statement = connection.prepareStatement("select parent_id from sys_menu where menu_id=?"))
            {
                statement.setLong(1, menuId);
                try (ResultSet rows = statement.executeQuery())
                {
                    assertTrue(rows.next(), "Foundation configuration menu ancestor must exist");
                    menuId = rows.getLong(1);
                }
            }
        }
        return menuIds;
    }

    private void verifyHistoricalMigrationExportPermission(String url)
    {
        try (Connection connection = DriverManager.getConnection(url, System.getenv("TODO_MIGRATION_DB_USER"),
            System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            assertEquals(1L,count(connection,"select count(*) from sys_menu where perms='todo:admission:export'"));
            assertEquals(1L,count(connection,"select count(*) from sys_role_menu rm join sys_menu m on m.menu_id=rm.menu_id "
                    + "join sys_role r on r.role_id=rm.role_id where m.perms='todo:admission:export' "
                    + "and r.role_key='foundation_arch_dba_reviewer' and r.status='0' and r.del_flag='0'"));
            assertEquals(Set.of("foundation_arch_dba_reviewer"), roleKeysWithPermission(connection,
                "todo:admission:export"));
        }
        catch (SQLException exception)
        {
            throw new AssertionError("Historical migration export permission invariants failed",exception);
        }
    }

    private void assertNoHistoricalMigrationExportRoleGrant(String url)
    {
        try (Connection connection = DriverManager.getConnection(url, System.getenv("TODO_MIGRATION_DB_USER"),
            System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            assertEquals(0L, count(connection, "select count(*) from sys_role_menu rm join sys_menu m on m.menu_id=rm.menu_id "
                + "where m.perms='todo:admission:export'"));
        }
        catch (SQLException exception)
        {
            throw new AssertionError("Historical migration export permission boundary invariants failed", exception);
        }
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
            assertEquals(1L, count(connection,
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
            assertEquals(1L, count(connection,
                "select count(*) from todo_foundation_finance_requirement "
                    + "where gate_code='G-06' and requirement_code='FINANCE_BUSINESS_SIGNOFF' "
                    + "and source_status='NEEDS_REVIEW' "
                    + "and source_ref='doc/reviews/v0.2-foundation-g06-finance-formula-review-package.md'"));
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
            assertEquals(4L,count(connection,"select count(*) from todo_foundation_migration_requirement where gate_code='G-04' "
                + "and source_status='NEEDS_EVIDENCE' and source_ref='doc/reviews/v0.2-foundation-g04-historical-migration-review-package.md'"));
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
            assertEquals(7L,count(connection,"select count(*) from todo_foundation_resource_requirement where source_status='CONFIRMED'"));
            assertEquals(3L,count(connection,"select count(*) from todo_foundation_resource_requirement where expected_values_json is not null"));
            assertEquals(3L,count(connection,"select json_length(expected_values_json) from todo_foundation_resource_requirement where resource_code='law_business_line'"));
            assertEquals(13L,count(connection,"select count(*) from todo_foundation_resource_requirement r "
                + "join json_table(coalesce(r.expected_values_json,json_array()), '$[*]' "
                + "columns(expected_value varchar(100) path '$.value',expected_label varchar(100) path '$.label')) expected"));
            assertEquals(1L,count(connection,"select count(*) from sys_dict_type where dict_type='law_business_line' and status='0'"));
            assertEquals(3L,count(connection,"select count(*) from sys_dict_data where dict_type='law_business_line' and status='0' "
                + "and dict_value in ('NON_LITIGATION','COMPREHENSIVE','EXECUTION')"));
            assertEquals(1L,count(connection,"select count(*) from sys_role where role_key='sales' and status='0' and del_flag='0'"));
            assertEquals(15L,count(connection,"select count(distinct m.perms) from sys_role_menu rm "
                + "join sys_role r on r.role_id=rm.role_id join sys_menu m on m.menu_id=rm.menu_id "
                + "where r.role_key='sales' and m.perms in ("
                + "'lead:dashboard:view','lead:mine:list','lead:mine:query',"
                + "'lead:first-contact:handle','lead:retry:list','lead:retry:handle',"
                + "'lead:call-record:add','lead:call-record:view','todo:list','todo:query',"
                + "'todo:complete','todo:chain:query','file:object:upload',"
                + "'file:object:relate','file:object:read')"));
            assertEquals(0L,count(connection,"select count(*) from sys_role_menu rm "
                + "join sys_role r on r.role_id=rm.role_id join sys_menu m on m.menu_id=rm.menu_id "
                + "where r.role_key='sales' and m.perms='lead:tag:confirm'"));
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
            assertEquals(21L, count(connection,
                "select count(*) from todo_prd_definition_catalog where production_state='BLOCKED'"));
            assertEquals(4L, count(connection,
                "select count(*) from todo_prd_definition_catalog where template_code between 'TD-001' and 'TD-004' "
                    + "and foundation_state='READY' and production_state='READY'"));
            assertEquals(21L, count(connection,
                "select count(*) from todo_prd_definition_catalog c join todo_template t on t.template_code=c.template_code "
                    + "join todo_template_version v on v.template_id=t.template_id "
                    + "where c.template_code>'TD-004' and v.status='DRAFT' "
                    + "and cast(v.definition_json as char)=cast(c.definition_json as char)"));
            assertEquals(1L, count(connection,
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

    private void verifyPublishedLeadTodoFlow(String url)
    {
        try (Connection connection = DriverManager.getConnection(url,
                System.getenv("TODO_MIGRATION_DB_USER"),
                System.getenv("TODO_MIGRATION_DB_PASSWORD")))
        {
            assertEquals(4L,count(connection,
                    "select count(*) from todo_template t join todo_template_version v "
                    +"on v.template_id=t.template_id and v.version_no=t.current_version "
                    +"where t.template_code between 'TD-001' and 'TD-004' "
                    +"and t.business_type='LEAD' and v.status='PUBLISHED' "
                    +"and v.version_id<>v.source_version_id"));
            assertEquals(2L,count(connection,
                    "select count(distinct routes.template_version_id) "
                    +"from todo_template root "
                    +"join todo_template_version root_version "
                    +"on root_version.template_id=root.template_id "
                    +"and root_version.version_no=root.current_version "
                    +"join json_table(root_version.definition_json,'$.routing.config.nodes[*]' "
                    +"columns(template_version_id bigint path '$.templateVersionId')) routes "
                    +"where root.template_code='TD-001' "
                    +"and routes.template_version_id in ("
                    +"select target_version.version_id from todo_template target "
                    +"join todo_template_version target_version "
                    +"on target_version.template_id=target.template_id "
                    +"and target_version.version_no=target.current_version "
                    +"where target.template_code in ('TD-002','TD-004') "
                    +"and target_version.status='PUBLISHED')"));
            assertEquals(1L,count(connection,
                    "select count(*) from todo_template t join todo_template_version v "
                    +"on v.template_id=t.template_id and v.version_no=t.current_version "
                    +"join todo_template retry on retry.template_code='TD-003' "
                    +"join todo_template_version retry_version on retry_version.template_id=retry.template_id "
                    +"and retry_version.version_no=retry.current_version "
                    +"where t.template_code='TD-003' "
                    +"and cast(json_unquote(json_extract(v.definition_json,"
                    +"'$.sla.config.schedule.targetTemplateVersionId')) as unsigned)=retry_version.version_id"));
            assertEquals(4L,count(connection,
                    "select count(*) from todo_template t join todo_template_version v "
                    +"on v.template_id=t.template_id and v.version_no=t.current_version "
                    +"where t.template_code between 'TD-001' and 'TD-004' "
                    +"and json_extract(v.definition_json,'$.owner.config.skipUnavailable')=false "
                    +"and json_extract(v.definition_json,'$.owner.config.useDelegation')=false "
                    +"and json_extract(v.definition_json,'$.owner.config.requireAvailable')=true "
                    +"and json_extract(v.definition_json,'$.owner.config.fallback') is null"));
            assertEquals(0L,count(connection,
                    "select count(*) from todo_trigger_rule r join todo_template t "
                    +"on t.template_id=r.template_id "
                    +"where t.template_code='LEAD_FIRST_CONTACT' and r.enabled='Y'"));
            assertEquals(1L,count(connection,
                    "select count(*) from todo_trigger_rule r join todo_template t "
                    +"on t.template_id=r.template_id "
                    +"where r.event_type='LEAD_ASSIGNED' and r.enabled='Y' "
                    +"and t.template_code='TD-001' and r.payload_version=1"));
            assertEquals(4L,count(connection,
                    "select count(*) from todo_prd_definition_catalog "
                    +"where template_code between 'TD-001' and 'TD-004' "
                    +"and json_unquote(json_extract(handler_capability_json,'$.repositoryStatus'))='PRESENT'"));
            assertEquals(1L,count(connection,
                    "select count(*) from todo_template t join todo_template_version v "
                    +"on v.template_id=t.template_id and v.version_no=t.current_version "
                    +"where t.template_code='TD-002' "
                    +"and json_unquote(json_extract(v.definition_json,"
                    +"'$.autoActions[0].config.fields.reviewResult'))='TRUE_INVALID'"));
            assertEquals(0L,count(connection,
                    "select count(*) from todo_template t join todo_template_version v "
                    +"on v.template_id=t.template_id and v.version_no=t.current_version "
                    +"where t.template_code between 'TD-001' and 'TD-004' "
                    +"and json_search(v.definition_json,'one','CONTRACT_SIGN') is not null"));
            assertEquals(1L,count(connection,
                    "select count(*) from sys_dict_type where dict_type='law_lead_progress_type' "
                    +"and status='0'"));
            assertEquals(6L,count(connection,
                    "select count(*) from sys_dict_data where dict_type='law_lead_progress_type' "
                    +"and status='0'"));
        }
        catch (SQLException exception)
        {
            throw new AssertionError("Published lead Todo flow invariants failed",exception);
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

    private boolean tableExists(Connection connection,String tableName) throws SQLException
    {
        try(PreparedStatement statement=connection.prepareStatement(
                "select count(*) from information_schema.tables "
                +"where table_schema=database() and table_name=?"))
        {
            statement.setString(1,tableName);
            try(ResultSet rows=statement.executeQuery())
            {
                assertTrue(rows.next());
                return rows.getLong(1)==1L;
            }
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
