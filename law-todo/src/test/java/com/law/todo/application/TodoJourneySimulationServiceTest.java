package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
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

import com.alibaba.fastjson2.JSON;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoConfigurationCommands.ConfigurationSimulationCommand;
import com.law.todo.application.command.TodoConfigurationCommands.JourneySimulationCommand;
import com.law.todo.application.view.TodoConfigurationJourneyView;
import com.law.todo.application.view.TodoConfigurationJourneyView.CurrentResources;
import com.law.todo.application.view.TodoConfigurationJourneyView.EmployeeTodoPreview;
import com.law.todo.application.view.TodoConfigurationJourneyView.JourneyPermissions;
import com.law.todo.application.view.TodoConfigurationJourneyView.TemplateSummary;
import com.law.todo.application.view.TodoConfigurationViews.ConfigurationSimulationResult;
import com.law.todo.application.view.TodoJourneySimulationResult;
import com.law.todo.application.view.TodoSimulationReadinessView;
import com.law.todo.application.view.TodoSimulationView;
import com.law.todo.application.view.TodoSimulationView.FormTrace;
import com.law.todo.application.view.TodoSimulationView.OwnerTrace;
import com.law.todo.application.view.TodoSimulationView.SlaTrace;
import com.law.todo.application.view.TodoSimulationView.TriggerTrace;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoConfigurationMapper;
import com.law.todo.spi.TodoBusinessPayloadAccess;
import com.law.todo.spi.TodoBusinessPayloadAccess.PayloadFieldSource;
import com.law.todo.spi.TodoBusinessPayloadAccess.PayloadHydration;

@ExtendWith(MockitoExtension.class)
class TodoJourneySimulationServiceTest
{
    private static final String SECRET_PHONE="13800138000";
    private static final String SECRET_TOKEN="top-secret-token";
    @Mock private TodoConfigurationSimulationService simulations;
    @Mock private TodoConfigurationJourneyService journeys;
    @Mock private TodoConfigurationResourceCatalogService resources;
    @Mock private TodoConfigurationMapper sampleMapper;
    @Mock private TodoSimulationEvidenceService evidence;
    @Mock private TodoSimulationReadinessService readiness;

    @Test void returnsHydratedPayloadCoverageAndOrderedRedactedTrace()
    {
        Map<String,Object> governedSecrets=Map.of(
                "opaqueAlpha","neutral-secret",
                "opaqueBeta",94736251L,
                "opaqueGamma",true,
                "opaqueDelta",List.of("list-secret",73),
                "opaqueEpsilon",Map.of("inner","deep-secret","amount",91));
        TodoBusinessPayloadAccess access=org.mockito.Mockito.mock(TodoBusinessPayloadAccess.class);
        when(access.supports("LEAD")).thenReturn(true);
        Map<String,Object> raw=new java.util.LinkedHashMap<>(governedSecrets);
        raw.put("ownerId",11L);raw.put("customerPhone",SECRET_PHONE);
        List<PayloadFieldSource> governedFields=governedSecrets.keySet().stream()
                .map(path->new PayloadFieldSource(path,governedSecrets.get(path),"BUSINESS_OBJECT",true,false,null,true))
                .toList();
        List<PayloadFieldSource> hydrationFields=new java.util.ArrayList<>(governedFields);
        hydrationFields.add(new PayloadFieldSource("ownerId",11L,"BUSINESS_OBJECT",true,false,null,false));
        hydrationFields.add(new PayloadFieldSource("customerPhone",SECRET_PHONE,"BUSINESS_OBJECT",true,false,null,true));
        hydrationFields.add(new PayloadFieldSource("missingNeutral",null,"MISSING",false,true,"Unavailable",false));
        when(access.hydrate("LEAD_CREATED",1,"LEAD",3L,actor())).thenReturn(new PayloadHydration(
                raw,hydrationFields,false));
        TodoBusinessPayloadHydrationService hydration=new TodoBusinessPayloadHydrationService(List.of(access),resources,null);
        when(resources.fields("LEAD","LEAD_CREATED")).thenReturn(List.of());
        when(journeys.load(42L,actor())).thenReturn(journey());
        ArgumentCaptor<ConfigurationSimulationCommand> engineCommand=ArgumentCaptor.forClass(ConfigurationSimulationCommand.class);
        ArgumentCaptor<TodoSensitiveDataPolicy> policy=ArgumentCaptor.forClass(TodoSensitiveDataPolicy.class);
        when(simulations.simulate(engineCommand.capture(),any(),policy.capture()))
                .thenReturn(new ConfigurationSimulationResult(engine(governedSecrets),7L));
        when(readiness.readiness(42L,9L,"definition-hash","LEAD_FIRST_CONTACT","LEAD"))
                .thenReturn(readiness(true));
        TodoJourneySimulationService service=
                new TodoJourneySimulationService(hydration,simulations,journeys,evidence,readiness);

        TodoJourneySimulationResult result=service.simulate(command(3L),actor());

        assertEquals(100,result.payload().coveragePercent());
        assertEquals(List.of("EVENT","OWNER","DOD","SLA","ROUTING","TODO_PREVIEW"),
                result.trace().stream().map(TodoJourneySimulationResult.TraceSection::code).toList());
        assertEquals(SECRET_PHONE,((Map<?,?>)engineCommand.getValue().payload()).get("customerPhone"));
        assertEquals(SECRET_TOKEN,engineCommand.getValue().payload().get("apiToken"));
        assertEquals("neutral-secret",engineCommand.getValue().payload().get("opaqueAlpha"));
        assertEquals(94736251L,engineCommand.getValue().payload().get("opaqueBeta"));
        assertEquals(true,engineCommand.getValue().payload().get("opaqueGamma"));
        assertFalse(JSON.toJSONString(result).contains(SECRET_PHONE));
        assertFalse(JSON.toJSONString(result).contains(SECRET_TOKEN));
        for(String value:List.of("neutral-secret","94736251","list-secret","deep-secret"))
            assertFalse(JSON.toJSONString(result).contains(value));
        assertEquals("[REDACTED]",result.engine().form().ui().get("traceFlag"));
        assertEquals(null,result.engine().owner().ownerId());
        assertTrue(result.engine().owner().candidates().isEmpty());
        assertEquals("[REDACTED]",result.employeePreview().title());
        assertEquals("[REDACTED]",result.payload().values().get("customerPhone"));
        assertTrue(result.publishEligible());
        assertTrue(result.readiness().publicationReady());
        verify(evidence).recordFull(eq(42L),eq(command(3L)),eq(true),anyList(),eq(actor()));
    }

    @Test void negativeBusinessIdIsAllowedForReadOnlySamplesButZeroIsRejected()
    {
        TodoBusinessPayloadHydrationService hydration=org.mockito.Mockito.mock(TodoBusinessPayloadHydrationService.class);
        TodoJourneySimulationService service=
                new TodoJourneySimulationService(hydration,simulations,journeys,evidence,readiness);

        TodoException error=assertThrows(TodoException.class,()->service.simulate(command(0L),actor()));

        assertEquals("TODO_SIMULATION_BUSINESS_ID_INVALID",error.getBusinessCode());
    }

    @Test void negativeSampleRunsThroughHydrationAndTheExistingEngineWithoutBusinessWrites()
    {
        when(sampleMapper.selectEventResourceByTypeVersion("LEAD_CREATED",1)).thenReturn(Map.of(
                "business_object_type","LEAD","sample_payload_json","{\"ownerId\":11}"));
        TodoBusinessPayloadHydrationService hydration=new TodoBusinessPayloadHydrationService(List.of(),resources,
                new TodoSimulationSampleCatalog(sampleMapper));
        when(resources.fields("LEAD","LEAD_CREATED")).thenReturn(List.of());
        when(journeys.load(42L,actor())).thenReturn(journey());
        ArgumentCaptor<ConfigurationSimulationCommand> engineCommand=ArgumentCaptor.forClass(ConfigurationSimulationCommand.class);
        when(simulations.simulate(engineCommand.capture(),any(),any()))
                .thenReturn(new ConfigurationSimulationResult(engine(Map.of(
                        "opaqueAlpha","neutral-secret","opaqueBeta",94736251L,"opaqueGamma",true,
                        "opaqueDelta",List.of("list-secret",73),
                        "opaqueEpsilon",Map.of("inner","deep-secret","amount",91))),3L));
        when(readiness.readiness(42L,9L,"definition-hash","LEAD_FIRST_CONTACT","LEAD"))
                .thenReturn(readiness(true));
        TodoJourneySimulationService service=
                new TodoJourneySimulationService(hydration,simulations,journeys,evidence,readiness);

        TodoJourneySimulationResult result=service.simulate(command(-1001L),actor());

        assertEquals(-1001L,engineCommand.getValue().businessId());
        assertEquals(-1001L,engineCommand.getValue().payload().get("leadId"));
        assertTrue(result.payload().fields().stream().anyMatch(field->"leadId".equals(field.path())));
    }

    @Test void recordsFailedFullSimulationWithoutMakingTheDraftReady()
    {
        when(sampleMapper.selectEventResourceByTypeVersion("LEAD_CREATED",1)).thenReturn(Map.of(
                "business_object_type","LEAD","sample_payload_json","{\"ownerId\":11}"));
        TodoBusinessPayloadHydrationService hydration=new TodoBusinessPayloadHydrationService(List.of(),resources,
                new TodoSimulationSampleCatalog(sampleMapper));
        when(resources.fields("LEAD","LEAD_CREATED")).thenReturn(List.of());
        when(journeys.load(42L,actor())).thenReturn(journey());
        when(simulations.simulate(any(),any(),any()))
                .thenReturn(new ConfigurationSimulationResult(failedEngine(),3L));
        when(readiness.readiness(42L,9L,"definition-hash","LEAD_FIRST_CONTACT","LEAD"))
                .thenReturn(readiness(false));
        TodoJourneySimulationService service=
                new TodoJourneySimulationService(hydration,simulations,journeys,evidence,readiness);

        TodoJourneySimulationResult result=service.simulate(command(-1001L),actor());

        verify(evidence).recordFull(eq(42L),eq(command(-1001L)),eq(false),anyList(),eq(actor()));
        assertFalse(result.readiness().publicationReady());
        assertFalse(result.publishEligible());
    }

    private JourneySimulationCommand command(long businessId)
    {
        return new JourneySimulationCommand(42L,9L,"LEAD_CREATED",1,"LEAD",businessId,
                Map.of("apiToken",SECRET_TOKEN),
                LocalDateTime.of(2026,7,23,9,0),List.of(),"definition-hash");
    }

    private TodoConfigurationJourneyView journey()
    {
        return new TodoConfigurationJourneyView(
                new TemplateSummary(42L,9L,1,0,"LEAD_FIRST_CONTACT","Lead first contact","LEAD","LEAD",
                        "DRAFT","definition-hash"),
                List.of(),new CurrentResources(List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of()),
                new EmployeeTodoPreview("neutral-secret","Lead owner",List.of(),List.of(),
                        List.of("deep-secret"),"Due today"),
                List.of(),new JourneyPermissions(true,true,true,true,true,true));
    }

    private TodoSimulationView engine(Map<String,Object> governedSecrets)
    {
        return new TodoSimulationView(9L,"definition-hash",
                new TriggerTrace("MATCHED","LEAD_CREATED",1,List.of("phone:"+SECRET_PHONE,
                        "value:"+governedSecrets.get("opaqueAlpha"))),
                new OwnerTrace("RESOLVED",94736251L,List.of(94736251L),List.of(),false,
                        List.of("owner:"+governedSecrets.get("opaqueBeta"))),
                new SlaTrace("PLANNED","DEFAULT",LocalDateTime.of(2026,7,23,9,0),
                        LocalDateTime.of(2026,7,23,10,0),null,null,null,List.of("sla:60")),
                new FormTrace(Map.of("customerPhone",SECRET_PHONE,
                        "traceText",governedSecrets.get("opaqueAlpha"),
                        "traceNumber",governedSecrets.get("opaqueBeta"),
                        "traceFlag",governedSecrets.get("opaqueGamma"),
                        "traceList",governedSecrets.get("opaqueDelta"),
                        "traceObject",governedSecrets.get("opaqueEpsilon")),
                        Map.of("requiredFields",List.of("customerPhone"))),
                List.of(),List.of(),List.of(),List.of());
    }

    private TodoSimulationView failedEngine()
    {
        return new TodoSimulationView(9L,"definition-hash",
                new TriggerTrace("NOT_MATCHED","LEAD_CREATED",1,List.of()),
                new OwnerTrace("NOT_EVALUATED",null,List.of(),List.of(),false,List.of()),
                new SlaTrace("NOT_EVALUATED",null,null,null,null,null,null,List.of()),
                new FormTrace(Map.of(),Map.of()),List.of(),List.of(),List.of(),List.of());
    }

    private TodoSimulationReadinessView readiness(boolean ready)
    {
        return new TodoSimulationReadinessView(42L,9L,"definition-hash",
                3,ready?3:2,List.of(),ready,ready,List.of());
    }

    private Actor actor(){return new Actor(7L,"operator",2L);}
}
