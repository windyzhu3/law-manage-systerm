package com.law.todo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.TodoBusinessOutcomeCatalogService.BusinessOutcomeOption;
import com.law.todo.application.TodoBusinessOutcomeCatalogService.BusinessOutcomeSet;
import com.law.todo.application.TodoSimulationEffectResolver.EffectKind;
import com.law.todo.application.TodoSimulationEffectResolver.SimulationEffect;
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
import com.law.business.lead.support.LeadProgressPayloadParser;
import com.law.business.lead.support.LeadProgressPayloadParser.PayloadValidationException;

@ExtendWith(MockitoExtension.class)
class TodoSimulationScenarioServiceTest
{
    @Mock TodoSimulationScenarioCatalog catalog;
    @Mock TodoConfigurationJourneyService journeys;
    @Mock TodoJourneySimulationService simulations;
    @Mock TodoSimulationEvidenceService evidence;
    @Mock TodoConfigurationMapper mapper;

    @Test
    void scenarioSimulationSuspendsAnyCallerTransaction()
    {
        assertThat(java.util.Arrays.stream(TodoSimulationScenarioService.class.getDeclaredMethods())
                .filter(method->Set.of("simulate","simulateRequired").contains(method.getName()))
                .map(method->method.getAnnotation(Transactional.class))
                .map(Transactional::propagation))
                .containsOnly(Propagation.NOT_SUPPORTED);
    }

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
        assertThat(result.message()).isEqualTo("场景验证通过");
    }

    @Test
    void acceptsTheExpectedScenarioRouteBeforeTheOverallPublicationGateIsReady()
    {
        SimulationScenario scenario=scenario("TD001_VALID","VALID","TD-004");
        when(catalog.scenarios("TD-001","LEAD")).thenReturn(List.of(scenario));
        when(journeys.load(42L,actor())).thenReturn(journey());
        when(journeys.canonicalDefinition(42L,actor())).thenReturn(definition());
        when(simulations.simulate(any(JourneySimulationCommand.class),any()))
                .thenReturn(result(104L,false));
        when(mapper.selectTemplateCodeByVersionId(104L)).thenReturn("TD-004");

        var result=service().simulate(42L,"TD001_VALID",command(),actor());

        assertThat(result.actualNextTemplateCode()).isEqualTo("TD-004");
        assertThat(result.passed()).isTrue();
    }

    @Test
    void resolvesTheGovernedTemplateReferenceFromTheStartNodeVersionIdentity()
    {
        SimulationScenario scenario=scenario("TD001_VALID","VALID","TD-004");
        when(catalog.scenarios("TD-001","LEAD")).thenReturn(List.of(scenario));
        when(journeys.load(42L,actor())).thenReturn(journey());
        when(journeys.canonicalDefinition(42L,actor())).thenReturn(
                definition().replace("\"start\":\"td001\"","\"start\":\"current_task\"")
                        .replace("\"key\":\"td001\"","\"key\":\"current_task\"")
                        .replace("\"from\":\"td001\"","\"from\":\"current_task\"")
                        .replace(",\"templateCode\":\"TD-001\"",""));
        when(simulations.simulate(any(JourneySimulationCommand.class),any())).thenReturn(result(104L));
        when(mapper.selectTemplateCodeByVersionId(9L)).thenReturn("TD-001");
        when(mapper.selectTemplateCodeByVersionId(104L)).thenReturn("TD-004");

        service().simulate(42L,"TD001_VALID",command(),actor());

        ArgumentCaptor<JourneySimulationCommand> submitted=
                ArgumentCaptor.forClass(JourneySimulationCommand.class);
        verify(simulations).simulate(submitted.capture(),any());
        assertThat(submitted.getValue().taskCompletions().get(0).nodeKey())
                .isEqualTo("current_task");
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

    @ParameterizedTest
    @ValueSource(strings={"ENDED"," ended ","ended"})
    void passesATerminalScenarioWhenTheGraphEndsNormally(String terminalStatus)
    {
        SimulationScenario scenario=effectScenario("TD002_TRUE_INVALID","reviewResult","TRUE_INVALID",
                EffectKind.END,null,null);
        when(catalog.scenarios("TD-001","LEAD")).thenReturn(List.of(scenario));
        when(journeys.load(42L,actor())).thenReturn(journey());
        when(journeys.canonicalDefinition(42L,actor())).thenReturn(definition());
        when(simulations.simulate(any(),any())).thenReturn(endedResult(terminalStatus));

        var result=service().simulate(42L,"TD002_TRUE_INVALID",command(),actor());

        assertThat(result.expectedEffect().kind()).isEqualTo(EffectKind.END);
        assertThat(result.actualEffect().kind()).isEqualTo(EffectKind.END);
        assertThat(result.actualNextTemplateCode()).isNull();
        assertThat(result.passed()).isTrue();
    }

    @Test
    void doesNotTreatAnEmptyRouteTraceAsATerminalEffect()
    {
        SimulationScenario scenario=effectScenario("TD002_TRUE_INVALID","reviewResult","TRUE_INVALID",
                EffectKind.END,null,null);
        when(catalog.scenarios("TD-001","LEAD")).thenReturn(List.of(scenario));
        when(journeys.load(42L,actor())).thenReturn(journey());
        when(journeys.canonicalDefinition(42L,actor())).thenReturn(definition());
        when(simulations.simulate(any(),any())).thenReturn(result(null));

        var result=service().simulate(42L,"TD002_TRUE_INVALID",command(),actor());

        assertThat(result.actualEffect()).isNull();
        assertThat(result.passed()).isFalse();
    }

    @Test
    void doesNotTreatAWaitingGraphAsATerminalEffect()
    {
        SimulationScenario scenario=effectScenario("TD002_TRUE_INVALID","reviewResult","TRUE_INVALID",
                EffectKind.END,null,null);
        when(catalog.scenarios("TD-001","LEAD")).thenReturn(List.of(scenario));
        when(journeys.load(42L,actor())).thenReturn(journey());
        when(journeys.canonicalDefinition(42L,actor())).thenReturn(definition());
        when(simulations.simulate(any(),any())).thenReturn(waitingResult());

        var result=service().simulate(42L,"TD002_TRUE_INVALID",command(),actor());

        assertThat(result.actualEffect()).isNull();
        assertThat(result.passed()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings={"WAITING","WAITING_JOIN"," waiting ","PENDING","PENDING_ROUTING",
            "PENDING_COMPLETION"," pending_completion ","UNKNOWN","UNKNOWN_STATE","UNKNOWN_BRANCH",
            " unknown_branch "})
    void doesNotTreatAMixedBlockedAndEndedGraphAsATerminalEffect(String blockedStatus)
    {
        SimulationScenario scenario=effectScenario("TD002_TRUE_INVALID","reviewResult","TRUE_INVALID",
                EffectKind.END,null,null);
        when(catalog.scenarios("TD-001","LEAD")).thenReturn(List.of(scenario));
        when(journeys.load(42L,actor())).thenReturn(journey());
        when(journeys.canonicalDefinition(42L,actor())).thenReturn(definition());
        when(simulations.simulate(any(),any())).thenReturn(mixedStatusEndedResult(blockedStatus));

        var result=service().simulate(42L,"TD002_TRUE_INVALID",command(),actor());

        assertThat(result.actualEffect()).isNull();
        assertThat(result.passed()).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings={" ","\t"})
    void doesNotTreatAMixedMissingStatusAndEndedGraphAsATerminalEffect(String missingStatus)
    {
        SimulationScenario scenario=effectScenario("TD002_TRUE_INVALID","reviewResult","TRUE_INVALID",
                EffectKind.END,null,null);
        when(catalog.scenarios("TD-001","LEAD")).thenReturn(List.of(scenario));
        when(journeys.load(42L,actor())).thenReturn(journey());
        when(journeys.canonicalDefinition(42L,actor())).thenReturn(definition());
        when(simulations.simulate(any(),any())).thenReturn(mixedStatusEndedResult(missingStatus));

        var result=service().simulate(42L,"TD002_TRUE_INVALID",command(),actor());

        assertThat(result.actualEffect()).isNull();
        assertThat(result.passed()).isFalse();
    }

    @Test
    void resolvesScheduledEffectsFromTheGovernedOutcomeCatalog()
    {
        SimulationScenario scenario=effectScenario("TD003_NEXT_WINDOW","contactResult","NEXT_WINDOW",
                EffectKind.SCHEDULE_NEXT,null,null);
        BusinessOutcomeSet outcomeSet=new BusinessOutcomeSet("contactResult","联系结果","重试动作",
                List.of(new BusinessOutcomeOption("NEXT_WINDOW","进入下一窗口","SCHEDULE_NEXT",
                        null,null,null)),"TD003_GOVERNED_OUTCOMES");
        when(catalog.scenarios("TD-001","LEAD")).thenReturn(List.of(scenario));
        when(journeys.load(42L,actor())).thenReturn(journey(outcomeSet));
        when(journeys.canonicalDefinition(42L,actor())).thenReturn(definition());
        when(simulations.simulate(any(),any())).thenReturn(result(null));

        var result=service().simulate(42L,"TD003_NEXT_WINDOW",command(),actor());

        assertThat(result.actualEffect().kind()).isEqualTo(EffectKind.SCHEDULE_NEXT);
        assertThat(result.actualEffect().actionCode()).isEqualTo("NEXT_WINDOW");
        assertThat(result.passed()).isTrue();
    }

    @Test
    void expectedValidationFailurePassesOnlyForTheConfiguredErrorCode()
    {
        SimulationScenario scenario=new SimulationScenario(12L,"TD004_PROOF_REQUIRED","TD-004",
                "缺少进展凭证",1,Map.of("progressType","PHONE",
                        "progressAt","2026-07-31T10:00:00"),
                List.of("progressType","progressAt","remark"),List.of(),"td001",1,
                new SimulationEffect(EffectKind.EXPECTED_VALIDATION_FAILURE,null,null),null,
                "TODO_DOD_ATTACHMENT_MISSING",true,"ACTIVE",10);
        when(catalog.scenarios("TD-001","LEAD")).thenReturn(List.of(scenario));
        when(journeys.load(42L,actor())).thenReturn(journey());
        when(journeys.canonicalDefinition(42L,actor())).thenReturn(definitionWithProgressProof());

        var result=service().simulate(42L,"TD004_PROOF_REQUIRED",command(),actor());

        assertThat(result.actualEffect().kind()).isEqualTo(EffectKind.EXPECTED_VALIDATION_FAILURE);
        assertThat(result.actualEffect().actionCode()).isEqualTo("TODO_DOD_ATTACHMENT_MISSING");
        assertThat(result.passed()).isTrue();
        verifyNoInteractions(simulations);
    }

    @Test
    void validatesProofThenResolvesThePureTd004ScheduleSelfSimulation()
    {
        SimulationScenario scenario=new SimulationScenario(13L,"TD004_PROGRESS_RECORDED","TD-004",
                "记录实质进展",1,Map.of("progressType","PHONE",
                        "progressAt","2026-07-31T10:00:00"),
                List.of("progressType","progressAt","remark"),List.of("FOLLOWUP_PROOF"),
                "td001",1,new SimulationEffect(EffectKind.SCHEDULE_SELF,"TD-004",null),
                null,null,true,"ACTIVE",10);
        BusinessOutcomeSet outcomeSet=new BusinessOutcomeSet("result","处理结果","五天循环",
                List.of(new BusinessOutcomeOption("PROGRESS_RECORDED","已记录实质进展",
                        "SCHEDULE_SELF","TD-004","5天实质进展",9L)),
                "TD004_GOVERNED_OUTCOMES");
        when(catalog.scenarios("TD-001","LEAD")).thenReturn(List.of(scenario));
        when(journeys.load(42L,actor())).thenReturn(journey(outcomeSet));
        when(journeys.canonicalDefinition(42L,actor())).thenReturn(definitionWithProgressProof());
        when(simulations.simulate(any(),any())).thenReturn(endedResult("ENDED"));

        var result=service(List.of(new ProgressSimulator()))
                .simulate(42L,"TD004_PROGRESS_RECORDED",command(),actor());

        assertThat(result.actualEffect().kind()).isEqualTo(EffectKind.SCHEDULE_SELF);
        assertThat(result.actualEffect().targetTemplateCode()).isEqualTo("TD-004");
        assertThat(result.passed()).isTrue();
    }

    @Test
    void malformedTd004ManualOverrideCannotPassThePositiveScheduleSelfScenario()
    {
        SimulationScenario scenario=new SimulationScenario(13L,"TD004_PROGRESS_RECORDED","TD-004",
                "记录实质进展",1,Map.of("progressType","PHONE",
                        "progressAt","2026-07-31T10:00:00"),
                List.of("progressType","progressAt","remark"),List.of("FOLLOWUP_PROOF"),
                "td001",1,new SimulationEffect(EffectKind.SCHEDULE_SELF,"TD-004",null),
                null,null,true,"ACTIVE",10);
        when(catalog.scenarios("TD-001","LEAD")).thenReturn(List.of(scenario));
        when(journeys.load(42L,actor())).thenReturn(journey());
        when(journeys.canonicalDefinition(42L,actor())).thenReturn(definitionWithProgressProof());

        var result=service(List.of(new ProgressSimulator())).simulate(42L,
                "TD004_PROGRESS_RECORDED",command(Map.of("progressAt","invalid")),actor());

        assertThat(result.passed()).isFalse();
        assertThat(result.actualEffect().kind()).isEqualTo(EffectKind.EXPECTED_VALIDATION_FAILURE);
        assertThat(result.actualEffect().actionCode()).isEqualTo("TODO_HANDLER_PAYLOAD_INVALID");
        verifyNoInteractions(simulations);
    }

    @Test
    void manualOverrideCannotForgeGovernedScenarioMaterials()
    {
        SimulationScenario scenario=new SimulationScenario(12L,"TD004_PROOF_REQUIRED","TD-004",
                "缺少进展凭证",1,Map.of("progressType","PHONE",
                        "progressAt","2026-07-31T10:00:00"),
                List.of("progressType","progressAt","remark"),List.of(),"td001",1,
                new SimulationEffect(EffectKind.EXPECTED_VALIDATION_FAILURE,null,null),null,
                "TODO_DOD_ATTACHMENT_MISSING",true,"ACTIVE",10);
        when(catalog.scenarios("TD-001","LEAD")).thenReturn(List.of(scenario));
        when(journeys.load(42L,actor())).thenReturn(journey());
        when(journeys.canonicalDefinition(42L,actor())).thenReturn(definitionWithProgressProof());

        var result=service(List.of(new ProgressSimulator())).simulate(42L,
                "TD004_PROOF_REQUIRED",command(Map.of(
                        "requiredMaterials",List.of("FOLLOWUP_PROOF"))),actor());

        assertThat(result.passed()).isTrue();
        assertThat(result.actualEffect().actionCode()).isEqualTo("TODO_DOD_ATTACHMENT_MISSING");
        verifyNoInteractions(simulations);
    }

    @Test
    void rejectsAnOtherwiseMatchingEffectFromAnotherDefinitionHash()
    {
        SimulationScenario scenario=scenario("TD001_VALID","VALID","TD-004");
        when(catalog.scenarios("TD-001","LEAD")).thenReturn(List.of(scenario));
        when(journeys.load(42L,actor())).thenReturn(journey());
        when(journeys.canonicalDefinition(42L,actor())).thenReturn(definition());
        when(simulations.simulate(any(),any())).thenReturn(result(104L,"published-hash"));
        when(mapper.selectTemplateCodeByVersionId(104L)).thenReturn("TD-004");

        var result=service().simulate(42L,"TD001_VALID",command(),actor());

        assertThat(result.actualEffect().targetTemplateCode()).isEqualTo("TD-004");
        assertThat(result.passed()).isFalse();
    }

    private TodoSimulationScenarioService service()
    {return service(List.of());}

    private TodoSimulationScenarioService service(List<TodoCompletionHandler> handlers)
    {return new TodoSimulationScenarioService(catalog,journeys,simulations,evidence,mapper,handlers);}

    private ScenarioSimulationCommand command()
    {
        return command(Map.of());
    }

    private ScenarioSimulationCommand command(Map<String,Object> overrides)
    {
        return new ScenarioSimulationCommand(9L,"definition-hash","LEAD",3L,overrides,
                LocalDateTime.of(2026,7,28,9,0),"run-1");
    }

    private SimulationScenario scenario(String code,String contactResult,String expected)
    {
        return new SimulationScenario(11L,code,"TD-001",code,1,
                Map.of("contactResult",contactResult,"contactedAt","${SIMULATION_NOW}"),
                List.of("contactResult","contactedAt"),List.of(),"TD-001",1,expected,true,"ACTIVE",10);
    }

    private SimulationScenario effectScenario(String code,String field,String value,EffectKind kind,
            String target,String expectedErrorCode)
    {
        SimulationEffect effect=new SimulationEffect(kind,target,null);
        return new SimulationScenario(12L,code,"TD-001",code,1,Map.of(field,value),List.of(field),
                List.of(),"TD-001",1,effect,
                kind==EffectKind.NEXT_TEMPLATE?target:null,expectedErrorCode,true,"ACTIVE",10);
    }

    private TodoConfigurationJourneyView journey()
    {return journey(BusinessOutcomeSet.empty());}

    private TodoConfigurationJourneyView journey(BusinessOutcomeSet outcomeSet)
    {
        return new TodoConfigurationJourneyView(new TemplateSummary(42L,9L,1,0,"TD-001","首联","LEAD",
                "LEAD","DRAFT","definition-hash"),List.of(),
                new CurrentResources(List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),
                        outcomeSet),
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

    private String definitionWithProgressProof()
    {
        return definition().replace("\"dod\":{\"config\":{}}",
                "\"dod\":{\"config\":{\"requiredFields\":[\"progressType\",\"progressAt\"],"
                        +"\"materials\":[{\"type\":\"FOLLOWUP_PROOF\",\"minCount\":1}]}}");
    }

    private TodoJourneySimulationResult result(Long routeVersion)
    {return result(routeVersion,true);}

    private TodoJourneySimulationResult result(Long routeVersion,boolean publishEligible)
    {return result(routeVersion,publishEligible,"definition-hash");}

    private TodoJourneySimulationResult result(Long routeVersion,String definitionHash)
    {return result(routeVersion,true,definitionHash);}

    private TodoJourneySimulationResult result(Long routeVersion,boolean publishEligible,String definitionHash)
    {
        TodoSimulationView engine=new TodoSimulationView(9L,definitionHash,
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
                List.of(),publishEligible);
    }

    private TodoJourneySimulationResult waitingResult()
    {
        TodoSimulationView engine=new TodoSimulationView(9L,"definition-hash",
                new TriggerTrace("MATCHED","LEAD_ASSIGNED",1,List.of()),
                new OwnerTrace("RESOLVED",7L,List.of(),List.of(),false,List.of()),
                new SlaTrace("PLANNED","DEFAULT",null,null,null,null,null,List.of()),
                new FormTrace(Map.of(),Map.of()),
                List.of(new RouteTrace(1,"join","JOIN","WAITING","branch",0,null,null,List.of())),
                List.of(),List.of(),List.of());
        return new TodoJourneySimulationResult(
                new TodoJourneySimulationResult.HydratedPayload(Map.of(),List.of(),100),engine,List.of(),
                new EmployeeTodoPreview("首联","负责人",List.of(),List.of(),List.of(),"1小时"),
                List.of(),false);
    }

    private TodoJourneySimulationResult endedResult(String status)
    {
        return routeResult(List.of(new RouteTrace(1,"end","END",status,null,0,null,null,List.of())));
    }

    private TodoJourneySimulationResult mixedStatusEndedResult(String status)
    {
        return routeResult(List.of(
                new RouteTrace(1,"end","END","ENDED","first",0,null,null,List.of()),
                new RouteTrace(2,"join","JOIN",status,"second",0,null,null,List.of())));
    }

    private TodoJourneySimulationResult routeResult(List<RouteTrace> routes)
    {
        TodoSimulationView engine=new TodoSimulationView(9L,"definition-hash",
                new TriggerTrace("MATCHED","LEAD_ASSIGNED",1,List.of()),
                new OwnerTrace("RESOLVED",7L,List.of(),List.of(),false,List.of()),
                new SlaTrace("PLANNED","DEFAULT",null,null,null,null,null,List.of()),
                new FormTrace(Map.of(),Map.of()),routes,List.of(),List.of(),List.of());
        return new TodoJourneySimulationResult(
                new TodoJourneySimulationResult.HydratedPayload(Map.of(),List.of(),100),engine,List.of(),
                new EmployeeTodoPreview("首联","负责人",List.of(),List.of(),List.of(),"1小时"),
                List.of(),false);
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

    private static final class ProgressSimulator implements TodoCompletionHandler
    {
        @Override public boolean supports(TodoInstance todo){return true;}
        @Override public void complete(TodoInstance todo,Map<String,Object> payload,Long operatorId,
                String operatorName)
        {throw new AssertionError("Read-only simulation must not execute the business handler");}
        @Override public boolean supportsSimulation(){return true;}
        @Override public SimulationResult simulate(TodoInstance todo,Map<String,Object> payload)
        {
            try
            {
                LeadProgressPayloadParser.parse(payload,todo.getBusinessId(),todo.getTodoId());
            }
            catch(PayloadValidationException invalid)
            {
                throw new com.law.todo.domain.TodoException(
                        invalid.getBusinessCode(),invalid.getMessage());
            }
            Map<String,Object> result=new java.util.LinkedHashMap<>(payload);
            result.put("result","PROGRESS_RECORDED");
            return SimulationResult.none(result);
        }
    }

    private Actor actor(){return new Actor(7L,"alice",2L);}
}
