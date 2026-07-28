package com.law.todo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoConfigurationCommands.JourneySimulationCommand;
import com.law.todo.application.command.TodoConfigurationCommands.ScenarioSimulationCommand;
import com.law.todo.application.view.TodoConfigurationJourneyView;
import com.law.todo.application.view.TodoConfigurationJourneyView.CurrentResources;
import com.law.todo.application.view.TodoConfigurationJourneyView.EmployeeTodoPreview;
import com.law.todo.application.view.TodoConfigurationJourneyView.JourneyPermissions;
import com.law.todo.application.view.TodoConfigurationJourneyView.TemplateSummary;
import com.law.todo.application.view.TodoJourneySimulationResult;
import com.law.todo.application.view.TodoSimulationScenarioViews.SimulationScenario;
import com.law.todo.application.view.TodoSimulationView;
import com.law.todo.application.view.TodoSimulationView.FormTrace;
import com.law.todo.application.view.TodoSimulationView.OwnerTrace;
import com.law.todo.application.view.TodoSimulationView.RouteTrace;
import com.law.todo.application.view.TodoSimulationView.SlaTrace;
import com.law.todo.application.view.TodoSimulationView.TriggerTrace;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoConfigurationMapper;
import com.law.todo.spi.TodoCompletionHandler;

@ExtendWith(MockitoExtension.class)
class TodoSimulationScenarioServiceTest
{
    @Mock TodoSimulationScenarioCatalog catalog;
    @Mock TodoConfigurationJourneyService journeys;
    @Mock TodoJourneySimulationService simulations;
    @Mock TodoSimulationEvidenceService evidence;
    @Mock TodoConfigurationMapper mapper;

    @Test
    void materializesTheScenarioCompletionAndMatchesTheActualRoute()
    {
        SimulationScenario scenario=scenario("TD001_VALID","VALID","TD-004");
        when(catalog.scenarios("TD-001","LEAD")).thenReturn(List.of(scenario));
        when(journeys.load(42L,actor())).thenReturn(journey());
        when(journeys.canonicalDefinition(42L,actor())).thenReturn(definition());
        when(simulations.simulate(any(JourneySimulationCommand.class),any())).thenReturn(result(104L));
        when(mapper.selectTemplateCodeByVersionId(104L)).thenReturn("TD-004");

        var result=service().simulate(42L,"TD001_VALID",command(),actor());

        ArgumentCaptor<JourneySimulationCommand> submitted=
                ArgumentCaptor.forClass(JourneySimulationCommand.class);
        verify(simulations).simulate(submitted.capture(),any());
        assertThat(submitted.getValue().taskCompletions().get(0).nodeKey()).isEqualTo("td001");
        assertThat(submitted.getValue().taskCompletions().get(0).occurrence()).isZero();
        assertThat(result.expectedNextTemplateCode()).isEqualTo("TD-004");
        assertThat(result.actualNextTemplateCode()).isEqualTo("TD-004");
        assertThat(result.passed()).isTrue();
    }

    @Test
    void batchPreservesGovernedOrderAndReportsEveryOutcome()
    {
        var valid=scenario("TD001_VALID","VALID","TD-004");
        var suspect=scenario("TD001_SUSPECT_INVALID","SUSPECT_INVALID","TD-002");
        var unreachable=scenario("TD001_UNREACHABLE","UNREACHABLE","TD-003");
        when(catalog.scenarios("TD-001","LEAD")).thenReturn(List.of(valid,suspect,unreachable));
        when(journeys.load(42L,actor())).thenReturn(journey());
        when(journeys.canonicalDefinition(42L,actor())).thenReturn(definition());
        when(mapper.selectTemplateCodeByVersionId(104L)).thenReturn("TD-004");
        when(mapper.selectTemplateCodeByVersionId(102L)).thenReturn("TD-002");
        when(mapper.selectTemplateCodeByVersionId(103L)).thenReturn("TD-003");
        when(simulations.simulate(any(),any())).thenAnswer(call->{
            JourneySimulationCommand command=call.getArgument(0);
            String value=String.valueOf(command.taskCompletions().get(0).payload().get("contactResult"));
            return result("VALID".equals(value)?104L:"SUSPECT_INVALID".equals(value)?102L:103L);
        });
        when(evidence.gate(any(Long.class),any(Long.class),any(String.class),any())).thenReturn(
                new TodoSimulationEvidenceService.PublicationGate(true,List.of()));

        var batch=service().simulateRequired(42L,command(),actor());

        assertThat(batch.results()).extracting(item->item.actualNextTemplateCode())
                .containsExactly("TD-004","TD-002","TD-003");
        assertThat(batch.results()).allMatch(item->item.passed());
        assertThat(batch.publicationReady()).isTrue();
    }

    @Test
    void usesDryRunBusinessOutcomeWhenTheRouteEndsBeforeADeferredTodoIsMaterialized()
    {
        var unreachable=scenario("TD001_UNREACHABLE","UNREACHABLE","TD-003");
        when(catalog.scenarios("TD-001","LEAD")).thenReturn(List.of(unreachable));
        when(journeys.load(42L,actor())).thenReturn(journey());
        when(journeys.canonicalDefinition(42L,actor())).thenReturn(definition());
        when(simulations.simulate(any(),any())).thenReturn(result(null));

        var result=service(List.of(new DeferredRetrySimulator()))
                .simulate(42L,"TD001_UNREACHABLE",command(),actor());

        assertThat(result.actualNextTemplateCode()).isEqualTo("TD-003");
        assertThat(result.passed()).isTrue();
    }

    private TodoSimulationScenarioService service()
    {return service(List.of());}

    private TodoSimulationScenarioService service(List<TodoCompletionHandler> handlers)
    {return new TodoSimulationScenarioService(catalog,journeys,simulations,evidence,mapper,handlers);}

    private ScenarioSimulationCommand command()
    {
        return new ScenarioSimulationCommand(9L,"definition-hash","LEAD",3L,Map.of(),
                LocalDateTime.of(2026,7,28,9,0),"run-1");
    }

    private SimulationScenario scenario(String code,String contactResult,String expected)
    {
        return new SimulationScenario(11L,code,"TD-001",code,1,
                Map.of("contactResult",contactResult,"contactedAt","${SIMULATION_NOW}"),
                List.of("contactResult","contactedAt"),List.of(),"TD-001",1,expected,true,"ACTIVE",10);
    }

    private TodoConfigurationJourneyView journey()
    {
        return new TodoConfigurationJourneyView(new TemplateSummary(42L,9L,1,0,"TD-001","首联","LEAD",
                "LEAD","DRAFT","definition-hash"),List.of(),
                new CurrentResources(List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of()),
                new EmployeeTodoPreview("首联","负责人",List.of(),List.of(),List.of(),"1小时"),
                List.of(),new JourneyPermissions(true,true,true,true,true,true));
    }

    private String definition()
    {
        return "{\"schemaVersion\":1,\"templateCode\":\"TD-001\","
                +"\"event\":{\"eventType\":\"LEAD_ASSIGNED\",\"payloadVersion\":1,\"condition\":{}},"
                +"\"owner\":{\"config\":{}},\"dod\":{\"config\":{}},\"sla\":{\"config\":{}},"
                +"\"ui\":{\"config\":{}},\"routing\":{\"config\":{\"start\":\"td001\",\"nodes\":["
                +"{\"key\":\"td001\",\"type\":\"TASK\",\"templateCode\":\"TD-001\",\"templateVersionId\":9},"
                +"{\"key\":\"end\",\"type\":\"END\"}],\"edges\":["
                +"{\"key\":\"done\",\"from\":\"td001\",\"to\":\"end\",\"priority\":0}]}},\"autoActions\":[],"
                +"\"decisionRefs\":[],\"acceptanceRefs\":[]}";
    }

    private TodoJourneySimulationResult result(Long routeVersion)
    {
        TodoSimulationView engine=new TodoSimulationView(9L,"definition-hash",
                new TriggerTrace("MATCHED","LEAD_ASSIGNED",1,List.of()),
                new OwnerTrace("RESOLVED",7L,List.of(),List.of(),false,List.of()),
                new SlaTrace("PLANNED","DEFAULT",null,null,null,null,null,List.of()),
                new FormTrace(Map.of(),Map.of()),
                routeVersion==null?List.of():
                        List.of(new RouteTrace(1,"next","TASK","PENDING_COMPLETION","result",0,
                                routeVersion,null,List.of())),
                List.of(),List.of(),List.of());
        return new TodoJourneySimulationResult(
                new TodoJourneySimulationResult.HydratedPayload(Map.of(),List.of(),100),engine,List.of(),
                new EmployeeTodoPreview("首联","负责人",List.of(),List.of(),List.of(),"1小时"),
                List.of(),true);
    }

    private static final class DeferredRetrySimulator implements TodoCompletionHandler
    {
        @Override public boolean supports(TodoInstance todo)
        {return todo!=null&&"TD-001".equals(todo.getTemplateCode());}
        @Override public void complete(TodoInstance todo,Map<String,Object> payload,Long operatorId,String operatorName)
        {throw new AssertionError("Read-only simulation must not execute the business handler");}
        @Override public boolean supportsSimulation(){return true;}
        @Override public SimulationResult simulate(TodoInstance todo,Map<String,Object> payload)
        {return SimulationResult.produces(payload,List.of("TD-003"));}
    }

    private Actor actor(){return new Actor(7L,"alice",2L);}
}
