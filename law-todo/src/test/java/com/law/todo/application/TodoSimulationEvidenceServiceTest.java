package com.law.todo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoConfigurationCommands.ScenarioSimulationCommand;
import com.law.todo.application.view.TodoSimulationScenarioViews.SimulationScenario;
import com.law.todo.mapper.TodoConfigurationMapper;

@ExtendWith(MockitoExtension.class)
class TodoSimulationEvidenceServiceTest
{
    @Mock TodoConfigurationMapper mapper;

    @Test
    void persistsOnlyHashesAndARedactedOutcomeSummary()
    {
        when(mapper.insertSimulationEvidence(anyMap())).thenReturn(1);
        SimulationScenario scenario=scenario();
        ScenarioSimulationCommand command=new ScenarioSimulationCommand(9L,"definition-hash","LEAD",3L,
                Map.of("name","客户甲","phone","13800138000"),LocalDateTime.of(2026,7,28,9,0),"run-1");
        TodoSimulationEvidenceService service=new TodoSimulationEvidenceService(mapper);

        service.record(42L,scenario,command,"TD-004",true,List.of("ROUTE_MATCHED"),new Actor(7L,"alice",2L));

        ArgumentCaptor<Map<String,Object>> row=ArgumentCaptor.forClass(Map.class);
        verify(mapper).insertSimulationEvidence(row.capture());
        String serialized=String.valueOf(row.getValue());
        assertThat(serialized).doesNotContain("客户甲","13800138000","completionPayload");
        assertThat(row.getValue().get("resultStatus")).isEqualTo("PASSED");
        assertThat(row.getValue().get("inputHash")).asString().hasSize(64);
        assertThat(row.getValue().get("traceSummaryJson")).asString().contains("TD-004").doesNotContain("phone");
    }

    @Test
    void definitionHashChangeMakesThePublicationGateIncomplete()
    {
        TodoSimulationEvidenceService service=new TodoSimulationEvidenceService(mapper);
        when(mapper.selectPassingSimulationEvidence(anyMap())).thenReturn(null);

        var gate=service.gate(42L,9L,"new-definition-hash",List.of(scenario()));

        assertThat(gate.publicationReady()).isFalse();
        assertThat(gate.blockingScenarioCodes()).containsExactly("TD001_VALID");
    }

    private SimulationScenario scenario()
    {
        return new SimulationScenario(11L,"TD001_VALID","TD-001","有效首联",1,
                Map.of("contactResult","VALID","contactedAt","${SIMULATION_NOW}"),
                List.of("contactResult","contactedAt"),List.of(),"TD-001",1,"TD-004",true,"ACTIVE",10);
    }
}
