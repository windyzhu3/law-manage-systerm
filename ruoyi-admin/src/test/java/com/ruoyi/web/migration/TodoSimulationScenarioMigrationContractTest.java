package com.ruoyi.web.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class TodoSimulationScenarioMigrationContractTest
{
    @Test
    void createsGovernedScenariosAndAnExactEvidenceIdentity() throws IOException
    {
        String sql=new String(getClass().getResourceAsStream(
                "/db/migration/V0_20_62__todo_simulation_scenarios_and_evidence.sql").readAllBytes(),
                StandardCharsets.UTF_8);
        String normalized=sql.replaceAll("\\s+"," ").toLowerCase();

        assertThat(normalized).contains("todo_simulation_evidence");
        assertThat(normalized).contains("version_id,definition_hash,scenario_code,scenario_version,input_hash");
        assertThat(sql).contains("TD001_VALID","TD001_SUSPECT_INVALID","TD001_UNREACHABLE");
        assertThat(sql).contains("TD-004","TD-002","TD-003","concat('$','{SIMULATION_NOW}')");
        assertThat(sql).doesNotContain("${SIMULATION_NOW}");
        assertThat(sql).contains("SIMULATION_SCENARIO");
    }
}
