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
import com.law.todo.definition.compiler.DefinitionValidationReport;
import com.law.todo.mapper.TodoConfigurationMapper;

@ExtendWith(MockitoExtension.class)
class TodoSimulationReadinessServiceTest
{
    @Mock TodoSimulationScenarioCatalog scenarios;
    @Mock TodoSimulationEvidenceService evidence;
    @Mock TodoConfigurationMapper mapper;

    private TodoSimulationReadinessService service;

    @BeforeEach
    void setUp()
    {
        service=new TodoSimulationReadinessService(scenarios,evidence,mapper);
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

    @Test
    void preflightUsesTheSameDoubleGateAndExplainsMissingFullSimulation()
    {
        List<SimulationScenario> required=requiredScenarios();
        when(mapper.selectTemplateIdentityByVersionId(88L)).thenReturn(Map.of(
                "template_id",17L,"template_code","TD-001","business_type","LEAD"));
        when(scenarios.scenarios("TD-001","LEAD")).thenReturn(required);
        when(evidence.gate(17L,88L,"hash-88",required))
                .thenReturn(new PublicationGate(true,List.of()));
        when(evidence.hasPassingEvidence(17L,88L,"hash-88","FULL_SIMULATION",1))
                .thenReturn(false);

        DefinitionValidationReport result=service.applyPreflightGate(88L,
                new DefinitionValidationReport(List.of(),List.of(),"{}","hash-88"));

        assertThat(result.errors()).singleElement().satisfies(issue->{
            assertThat(issue.code()).isEqualTo("TODO_FULL_SIMULATION_REQUIRED");
            assertThat(issue.path()).isEqualTo("simulation.full");
            assertThat(issue.message()).isEqualTo("完整试运行尚未通过");
        });
    }

    @Test
    void preflightHasNoSimulationErrorsWhenBothGatesPass()
    {
        List<SimulationScenario> required=requiredScenarios();
        when(mapper.selectTemplateIdentityByVersionId(88L)).thenReturn(Map.of(
                "template_id",17L,"template_code","TD-001","business_type","LEAD"));
        when(scenarios.scenarios("TD-001","LEAD")).thenReturn(required);
        when(evidence.gate(17L,88L,"hash-88",required))
                .thenReturn(new PublicationGate(true,List.of()));
        when(evidence.hasPassingEvidence(17L,88L,"hash-88","FULL_SIMULATION",1))
                .thenReturn(true);

        DefinitionValidationReport result=service.applyPreflightGate(88L,
                new DefinitionValidationReport(List.of(),List.of(),"{}","hash-88"));

        assertThat(result.errors()).isEmpty();
    }

    @Test
    void loadsWorkbenchReadinessInOneBatchAndKeepsBothGates()
    {
        List<TodoSimulationReadinessService.BatchRequest> requests=List.of(
                new TodoSimulationReadinessService.BatchRequest(
                        17L,88L,"hash-88","DRAFT"),
                new TodoSimulationReadinessService.BatchRequest(
                        18L,89L,"hash-89","DRAFT"));
        when(mapper.selectSimulationReadinessBatch(List.of(88L,89L))).thenReturn(List.of(
                Map.of("version_id",88L,"definition_hash","hash-88",
                        "required_scenario_count",3L,"passed_scenario_count",3L,
                        "blocking_scenario_codes","","blocking_scenario_names","",
                        "full_simulation_passed",1),
                Map.of("version_id",89L,"definition_hash","hash-89",
                        "required_scenario_count",3L,"passed_scenario_count",2L,
                        "blocking_scenario_codes","TD001_UNREACHABLE",
                        "blocking_scenario_names","未接通",
                        "full_simulation_passed",0)));

        Map<Long,TodoSimulationReadinessView> result=service.readinessBatch(requests);

        assertThat(result.get(88L).publicationReady()).isTrue();
        assertThat(result.get(89L).publicationReady()).isFalse();
        assertThat(result.get(89L).issues()).extracting(JourneyIssue::code)
                .containsExactly(
                        "TODO_REQUIRED_SIMULATION_SCENARIOS_INCOMPLETE",
                        "TODO_FULL_SIMULATION_REQUIRED");
        verify(mapper).selectSimulationReadinessBatch(List.of(88L,89L));
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
