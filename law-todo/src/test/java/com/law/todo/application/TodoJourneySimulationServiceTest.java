package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import com.law.todo.application.view.TodoSimulationView;
import com.law.todo.application.view.TodoSimulationView.FormTrace;
import com.law.todo.application.view.TodoSimulationView.OwnerTrace;
import com.law.todo.application.view.TodoSimulationView.SlaTrace;
import com.law.todo.application.view.TodoSimulationView.TriggerTrace;
import com.law.todo.domain.TodoException;
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

    @Test void returnsHydratedPayloadCoverageAndOrderedRedactedTrace()
    {
        TodoBusinessPayloadAccess access=org.mockito.Mockito.mock(TodoBusinessPayloadAccess.class);
        when(access.supports("LEAD")).thenReturn(true);
        when(access.hydrate("LEAD_CREATED",1,"LEAD",3L,actor())).thenReturn(new PayloadHydration(
                Map.of("ownerId",11L,"customerPhone",SECRET_PHONE),
                List.of(new PayloadFieldSource("ownerId",11L,"BUSINESS_OBJECT",true,false,null,false),
                        new PayloadFieldSource("customerPhone",SECRET_PHONE,"BUSINESS_OBJECT",true,false,null,true)),false));
        TodoBusinessPayloadHydrationService hydration=new TodoBusinessPayloadHydrationService(List.of(access),resources,null);
        when(resources.fields("LEAD","LEAD_CREATED")).thenReturn(List.of());
        when(journeys.load(42L,actor())).thenReturn(journey());
        ArgumentCaptor<ConfigurationSimulationCommand> engineCommand=ArgumentCaptor.forClass(ConfigurationSimulationCommand.class);
        when(simulations.simulate(engineCommand.capture(),any())).thenReturn(new ConfigurationSimulationResult(engine(),7L));
        TodoJourneySimulationService service=new TodoJourneySimulationService(hydration,simulations,journeys);

        TodoJourneySimulationResult result=service.simulate(command(3L),actor());

        assertEquals(100,result.payload().coveragePercent());
        assertEquals(List.of("EVENT","OWNER","DOD","SLA","ROUTING","TODO_PREVIEW"),
                result.trace().stream().map(TodoJourneySimulationResult.TraceSection::code).toList());
        assertEquals(SECRET_PHONE,((Map<?,?>)engineCommand.getValue().payload()).get("customerPhone"));
        assertEquals(SECRET_TOKEN,engineCommand.getValue().payload().get("apiToken"));
        assertFalse(JSON.toJSONString(result).contains(SECRET_PHONE));
        assertFalse(JSON.toJSONString(result).contains(SECRET_TOKEN));
        assertEquals("[REDACTED]",result.payload().values().get("customerPhone"));
        assertTrue(result.publishEligible());
    }

    @Test void negativeBusinessIdIsAllowedForReadOnlySamplesButZeroIsRejected()
    {
        TodoBusinessPayloadHydrationService hydration=org.mockito.Mockito.mock(TodoBusinessPayloadHydrationService.class);
        TodoJourneySimulationService service=new TodoJourneySimulationService(hydration,simulations,journeys);

        TodoException error=assertThrows(TodoException.class,()->service.simulate(command(0L),actor()));

        assertEquals("TODO_SIMULATION_BUSINESS_ID_INVALID",error.getBusinessCode());
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
                new EmployeeTodoPreview("Contact lead","Lead owner",List.of(),List.of(),List.of(),"Due today"),
                List.of(),new JourneyPermissions(true,true,true,true,true,true));
    }

    private TodoSimulationView engine()
    {
        return new TodoSimulationView(9L,"definition-hash",
                new TriggerTrace("MATCHED","LEAD_CREATED",1,List.of("phone:"+SECRET_PHONE)),
                new OwnerTrace("RESOLVED",11L,List.of(11L),List.of(),false,List.of("owner:11")),
                new SlaTrace("PLANNED","DEFAULT",LocalDateTime.of(2026,7,23,9,0),
                        LocalDateTime.of(2026,7,23,10,0),null,null,null,List.of("sla:60")),
                new FormTrace(Map.of("customerPhone",SECRET_PHONE),Map.of("requiredFields",List.of("customerPhone"))),
                List.of(),List.of(),List.of(),List.of());
    }

    private Actor actor(){return new Actor(7L,"operator",2L);}
}
