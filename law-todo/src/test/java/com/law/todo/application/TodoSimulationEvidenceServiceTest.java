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
import com.law.todo.application.command.TodoConfigurationCommands.JourneySimulationCommand;
import com.law.todo.application.command.TodoDefinitionCommands.VirtualTaskCompletionSample;
import com.law.todo.application.view.TodoSimulationScenarioViews.SimulationScenario;
import com.law.todo.definition.compiler.DefinitionValidationReport;
import com.law.todo.mapper.TodoConfigurationMapper;

@ExtendWith(MockitoExtension.class)
class TodoSimulationEvidenceServiceTest
{
    @Mock TodoConfigurationMapper mapper;
    @Mock TodoSimulationScenarioCatalog scenarios;

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
    void recordsFullSimulationEvidenceForTheExactDefinitionWithoutLeakingInputs()
    {
        when(mapper.insertSimulationEvidence(anyMap())).thenReturn(1);
        JourneySimulationCommand command=new JourneySimulationCommand(
                42L,9L,"LEAD_CREATED",1,"LEAD",3L,
                Map.of("phone","13800138000"),
                LocalDateTime.of(2026,7,28,9,0),
                List.of(new VirtualTaskCompletionSample("TD-001",0,
                        Map.of("contactResult","VALID"),
                        LocalDateTime.of(2026,7,28,9,30))),
                "definition-hash");
        TodoSimulationEvidenceService service=new TodoSimulationEvidenceService(mapper);

        service.recordFull(42L,command,true,List.of("EVENT","ROUTING"),
                new Actor(7L,"alice",2L));

        ArgumentCaptor<Map<String,Object>> row=ArgumentCaptor.forClass(Map.class);
        verify(mapper).insertSimulationEvidence(row.capture());
        assertThat(row.getValue()).containsEntry("templateId",42L)
                .containsEntry("versionId",9L)
                .containsEntry("definitionHash","definition-hash")
                .containsEntry("scenarioCode","FULL_SIMULATION")
                .containsEntry("scenarioVersion",1)
                .containsEntry("resultStatus","PASSED");
        assertThat(row.getValue().get("inputHash")).asString().hasSize(64);
        assertThat(String.valueOf(row.getValue()))
                .doesNotContain("13800138000","contactResult");
    }

    @Test
    void definitionHashChangeMakesThePublicationGateIncomplete()
    {
        TodoSimulationEvidenceService service=new TodoSimulationEvidenceService(mapper);
        when(mapper.selectPassingSimulationEvidence(anyMap())).thenReturn(null);
        when(mapper.selectLatestSimulationEvidence(anyMap())).thenReturn(Map.of(
                "definition_hash","old-definition-hash","result_status","PASSED"));

        var gate=service.gate(42L,9L,"new-definition-hash",List.of(scenario()));

        assertThat(gate.publicationReady()).isFalse();
        assertThat(gate.blockingScenarioCodes()).containsExactly("TD001_VALID");
        assertThat(gate.blockers()).containsExactly(
                new TodoSimulationEvidenceService.SimulationGateBlocker("TD001_VALID","DEFINITION_CHANGED"));
    }

    @Test
    void preflightExplainsMissingEvidenceWithChineseScenarioNamesAndReasons()
    {
        when(mapper.selectTemplateIdentityByVersionId(9L)).thenReturn(Map.of(
                "template_id",42L,"template_code","TD-001","business_type","LEAD"));
        when(scenarios.scenarios("TD-001","LEAD")).thenReturn(List.of(scenario()));
        when(mapper.selectPassingSimulationEvidence(anyMap())).thenReturn(null);
        when(mapper.selectLatestSimulationEvidence(anyMap())).thenReturn(Map.of(
                "definition_hash","old-definition-hash","result_status","PASSED"));
        TodoSimulationEvidenceService service=new TodoSimulationEvidenceService(mapper,scenarios);

        var result=service.applyPreflightGate(9L,
                new DefinitionValidationReport(List.of(),List.of(),"{}","new-definition-hash"));

        assertThat(result.errors()).singleElement().satisfies(issue->{
            assertThat(issue.code()).isEqualTo("TODO_REQUIRED_SIMULATION_SCENARIOS_INCOMPLETE");
            assertThat(issue.message()).contains("有效首联","配置已变更","请重新验证")
                    .doesNotContain("TD001_VALID","Required simulation scenarios are incomplete");
        });
    }

    private SimulationScenario scenario()
    {
        return new SimulationScenario(11L,"TD001_VALID","TD-001","有效首联",1,
                Map.of("contactResult","VALID","contactedAt","${SIMULATION_NOW}"),
                List.of("contactResult","contactedAt"),List.of(),"TD-001",1,"TD-004",true,"ACTIVE",10);
    }
}
