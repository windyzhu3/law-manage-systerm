package com.law.todo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.law.todo.application.TodoSimulationEvidenceService.PublicationGate;
import com.law.todo.application.TodoSimulationEvidenceService.SimulationGateBlocker;
import com.law.todo.application.view.TodoConfigurationJourneyView.JourneyIssue;
import com.law.todo.application.view.TodoSimulationReadinessView;
import com.law.todo.application.view.TodoSimulationScenarioViews.SimulationScenario;

@ExtendWith(MockitoExtension.class)
class TodoSimulationReadinessServiceTest
{
    @Mock TodoSimulationScenarioCatalog scenarios;
    @Mock TodoSimulationEvidenceService evidence;

    private TodoSimulationReadinessService service;

    @BeforeEach
    void setUp()
    {
        service=new TodoSimulationReadinessService(scenarios,evidence);
    }

    @Test
    void blocksWhenScenariosPassButFullSimulationIsMissing()
    {
        List<SimulationScenario> required=requiredScenarios();
        when(scenarios.scenarios("TD-001","LEAD")).thenReturn(required);
        when(evidence.gate(17L,88L,"hash-88",required))
                .thenReturn(new PublicationGate(true,List.of()));
        when(evidence.hasPassingEvidence(17L,88L,"hash-88","FULL_SIMULATION",1))
                .thenReturn(false);

        TodoSimulationReadinessView value=
                service.readiness(17L,88L,"hash-88","TD-001","LEAD");

        assertThat(value.publicationReady()).isFalse();
        assertThat(value.fullSimulationPassed()).isFalse();
        assertThat(value.issues()).extracting(JourneyIssue::code)
                .containsExactly("TODO_FULL_SIMULATION_REQUIRED");
        assertThat(value.issues()).singleElement().satisfies(issue->{
            assertThat(issue.message()).isEqualTo("完整试运行尚未通过");
            assertThat(issue.repairAction()).isEqualTo("运行完整试运行");
        });
    }

    @Test
    void blocksWhenFullSimulationPassesButOneScenarioIsMissing()
    {
        List<SimulationScenario> required=requiredScenarios();
        when(scenarios.scenarios("TD-001","LEAD")).thenReturn(required);
        when(evidence.gate(17L,88L,"hash-88",required))
                .thenReturn(new PublicationGate(false,List.of("TD001_UNREACHABLE")));
        when(evidence.hasPassingEvidence(17L,88L,"hash-88","FULL_SIMULATION",1))
                .thenReturn(true);

        TodoSimulationReadinessView value=
                service.readiness(17L,88L,"hash-88","TD-001","LEAD");

        assertThat(value.publicationReady()).isFalse();
        assertThat(value.passedScenarioCount()).isEqualTo(1);
        assertThat(value.blockingScenarios()).extracting(SimulationGateBlocker::scenarioCode)
                .containsExactly("TD001_UNREACHABLE");
        assertThat(value.issues()).singleElement().satisfies(issue->{
            assertThat(issue.code()).isEqualTo("TODO_REQUIRED_SIMULATION_SCENARIOS_INCOMPLETE");
            assertThat(issue.message()).contains("无法联系");
        });
    }

    @Test
    void isReadyOnlyWhenBothGatesPass()
    {
        List<SimulationScenario> required=requiredScenarios();
        when(scenarios.scenarios("TD-001","LEAD")).thenReturn(required);
        when(evidence.gate(17L,88L,"hash-88",required))
                .thenReturn(new PublicationGate(true,List.of()));
        when(evidence.hasPassingEvidence(17L,88L,"hash-88","FULL_SIMULATION",1))
                .thenReturn(true);

        TodoSimulationReadinessView value=
                service.readiness(17L,88L,"hash-88","TD-001","LEAD");

        assertThat(value.publicationReady()).isTrue();
        assertThat(value.requiredScenarioCount()).isEqualTo(2);
        assertThat(value.passedScenarioCount()).isEqualTo(2);
        assertThat(value.issues()).isEmpty();
    }

    @Test
    void doesNotReuseEvidenceFromAnotherDefinitionHash()
    {
        List<SimulationScenario> required=requiredScenarios();
        when(scenarios.scenarios("TD-001","LEAD")).thenReturn(required);
        when(evidence.gate(17L,88L,"hash-new",required))
                .thenReturn(new PublicationGate(false,List.of("TD001_VALID")));
        when(evidence.hasPassingEvidence(17L,88L,"hash-new","FULL_SIMULATION",1))
                .thenReturn(false);

        TodoSimulationReadinessView value=
                service.readiness(17L,88L,"hash-new","TD-001","LEAD");

        assertThat(value.publicationReady()).isFalse();
        assertThat(value.blockingScenarios()).extracting(SimulationGateBlocker::scenarioCode)
                .containsExactly("TD001_VALID");
        verify(evidence,never()).hasPassingEvidence(
                17L,88L,"hash-old","FULL_SIMULATION",1);
    }

    private List<SimulationScenario> requiredScenarios()
    {
        return List.of(
                scenario("TD001_VALID","有效首联"),
                scenario("TD001_UNREACHABLE","无法联系"));
    }

    private SimulationScenario scenario(String code,String name)
    {
        return new SimulationScenario(11L,code,"TD-001",name,1,
                Map.of("contactResult","VALID"),List.of("contactResult"),
                List.of(),"TD-001",1,"TD-004",true,"ACTIVE",10);
    }
}
