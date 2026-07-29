package com.ruoyi.web.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class LeadTemplateConfigurationHardeningMigrationTest
{
    private static final String MIGRATION =
            "/db/migration/V0_20_65__lead_template_configuration_hardening.sql";

    @Test
    void createsARepairableDraftWithTypedOwnerAndCompleteLeadOutcomes() throws Exception
    {
        String sql=sql();
        String normalized=normalized(sql);

        assertThat(normalized).contains("insert into todo_template_version");
        assertThat(normalized).contains("'draft'");
        assertThat(normalized).contains("'$.event.condition',json_object()");
        assertThat(normalized).contains("'$.owner.config',json_object(");
        assertThat(normalized).contains("'type','payload'");
        assertThat(normalized).contains("'field','ownerid'");
        assertThat(normalized).contains("set @td001_repair_routing=json_object(");
        assertThat(normalized).contains("'$.routing.config',@td001_repair_routing");
        assertThat(normalized).contains("'businessoutcomes',json_array(");
        assertOutcome(normalized,"valid","td-004");
        assertOutcome(normalized,"suspect_invalid","td-002");
        assertOutcome(normalized,"unreachable","td-003");
    }

    @Test
    void governsChineseEventFieldMeaningsAndTheSingleOwnerSource() throws Exception
    {
        String sql=normalized(sql());

        assertThat(sql).contains("where event_type='lead_assigned' and payload_version=1");
        assertThat(sql).contains("owner_field_paths_json=json_array('ownerid')");
        assertThat(sql).contains("'分配记录id'");
        assertThat(sql).contains("'系统生成的线索分配记录标识，仅用于追踪'");
        assertThat(sql).contains("'线索负责人'");
        assertThat(sql).contains("'待办将分配给该用户'");
        assertThat(sql).contains("'负责人所属部门'");
        assertThat(sql).contains("'分配操作人'");
    }

    @Test
    void preservesPublishedVersionsTriggersAndHistoricalSimulationEvidence() throws Exception
    {
        String normalized=normalized(sql());

        assertThat(normalized).doesNotMatch("(?s).*update\\s+todo_template_version\\b.*");
        assertThat(normalized).doesNotMatch("(?s).*delete\\s+from\\s+todo_template_version\\b.*");
        assertThat(normalized).doesNotMatch("(?s).*\\b(?:insert\\s+into|update|delete\\s+from)\\s+"
                +"todo_simulation_evidence\\b.*");
        assertThat(normalized).doesNotMatch("(?s).*\\b(?:insert\\s+into|update|delete\\s+from)\\s+"
                +"todo_trigger_rule\\b.*");
        assertThat(normalized).doesNotContain("set t.current_version");
    }

    private static void assertOutcome(String sql,String result,String target)
    {
        assertThat(sql).contains("'resultfield','contactresult'");
        assertThat(sql).contains("'resultvalue','"+result+"'");
        assertThat(sql).contains("'targettemplatecode','"+target+"'");
    }

    private String sql() throws Exception
    {
        try(InputStream input=getClass().getResourceAsStream(MIGRATION))
        {
            assertNotNull(input,"The forward TD-001 configuration hardening migration must be packaged");
            return new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }
    }

    private static String normalized(String sql)
    {
        return sql.replaceAll("--[^\\r\\n]*","").replaceAll("\\s+"," ").toLowerCase();
    }
}
