package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoConfigurationCommands.ConfigurationSimulationCommand;
import com.law.todo.application.view.TodoConfigurationViews.ConfigurationSimulationResult;
import com.law.todo.application.view.TodoSimulationView;
import com.law.todo.application.view.TodoSimulationView.FormTrace;
import com.law.todo.application.view.TodoSimulationView.OwnerTrace;
import com.law.todo.application.view.TodoSimulationView.SlaTrace;
import com.law.todo.application.view.TodoSimulationView.TriggerTrace;
import com.law.todo.domain.TodoException;

@ExtendWith(MockitoExtension.class)
class TodoConfigurationSimulationServiceTest
{
    @Mock private TodoDefinitionSimulationService definitions;
    @Mock private TodoConfigurationSimulationAuditService audits;
    @InjectMocks private TodoConfigurationSimulationService service;

    @Test void simulationIsReadOnlyAndPersistsOnlySanitizedOrderedAudit()
    {
        whenSimulationReturns(sampleSimulation());

        ConfigurationSimulationResult result=service.simulate(command(),actor());

        assertEquals("MATCHED",result.simulation().trigger().status());
        ArgumentCaptor<Map<String,Object>> results=ArgumentCaptor.forClass(Map.class);
        verify(audits).record(any(),any(),org.mockito.ArgumentMatchers.anyLong(),results.capture());
        Map<String,Object> ordered=results.getValue();
        assertEquals(List.of("state","template","owner","sla","dod","route","card","log"),
                List.copyOf(ordered.keySet()));
        verify(definitions).simulate(eq(9L),any(),eq("LEAD_CREATED"));
    }

    @Test void failedSimulationIsAuditedWithSanitizedFailureThenRethrown()
    {
        TodoException failure=new TodoException("TODO_SIMULATION_FAILED","token top-secret for alice@example.com");
        org.mockito.Mockito.when(definitions.simulate(eq(9L),any(),eq("LEAD_CREATED"))).thenThrow(failure);

        TodoException thrown=assertThrows(TodoException.class,()->service.simulate(command(),actor()));

        assertSame(failure,thrown);
        ArgumentCaptor<Map<String,Object>> results=ArgumentCaptor.forClass(Map.class);
        verify(audits).record(any(),any(),org.mockito.ArgumentMatchers.anyLong(),results.capture());
        Map<String,Object> ordered=results.getValue();
        assertEquals(List.of("state","template","owner","sla","dod","route","card","log"),
                List.copyOf(ordered.keySet()));
        assertEquals("FAILED",((Map<?,?>)ordered.get("state")).get("status"));
    }

    @Test void auditTruncatesOversizedSafeTextWithoutDroppingTheAuditSummary()
    {
        whenSimulationReturns(sampleSimulation());
        Map<String,Object> payload=new LinkedHashMap<>(command().payload());
        payload.put("summary","x".repeat(5000));
        ConfigurationSimulationCommand oversized=new ConfigurationSimulationCommand("request-6",9L,"LEAD_CREATED","LEAD",3L,
                payload,LocalDateTime.of(2026,7,21,9,0),List.of());

        service.simulate(oversized,actor());

        verify(audits).record(any(),any(),org.mockito.ArgumentMatchers.anyLong(),any());
    }

    @Test void ineligibleSimulationWithMissingDefinitionFieldsIsStillAudited()
    {
        TodoSimulationView halted=new TodoSimulationView(9L,null,new TriggerTrace("SKIPPED","LEAD_CREATED",0,List.of()),
                new OwnerTrace("SKIPPED",null,List.of(),List.of(),false,List.of()),
                new SlaTrace("SKIPPED",null,LocalDateTime.of(2026,7,21,9,0),null,null,null,null,List.of()),
                new FormTrace(Map.of(),Map.of()),List.of(),List.of(),List.of(),List.of());
        whenSimulationReturns(halted);

        service.simulate(command(),actor());

        verify(audits).record(any(),any(),org.mockito.ArgumentMatchers.anyLong(),any());
    }

    @Test void auditDetectsSensitiveKeysBeforeTheyAreBounded()
    {
        whenSimulationReturns(sampleSimulation());
        Map<String,Object> payload=new LinkedHashMap<>(command().payload());
        payload.put("x".repeat(600)+"password","hunter2");
        ConfigurationSimulationCommand oversizedKey=new ConfigurationSimulationCommand("request-6",9L,"LEAD_CREATED","LEAD",3L,
                payload,LocalDateTime.of(2026,7,21,9,0),List.of());

        service.simulate(oversizedKey,actor());

        verify(audits).record(any(),any(),org.mockito.ArgumentMatchers.anyLong(),any());
    }

    @Test void auditWriteFailureIsNotMisreportedAsASecondFailedSimulationAudit()
    {
        whenSimulationReturns(sampleSimulation());
        org.mockito.Mockito.doThrow(new IllegalStateException("database unavailable")).when(audits)
                .record(any(),any(),org.mockito.ArgumentMatchers.anyLong(),any());

        assertThrows(IllegalStateException.class,()->service.simulate(command(),actor()));

        verify(audits,times(1)).record(any(),any(),org.mockito.ArgumentMatchers.anyLong(),any());
    }

    @Test void failedSimulationKeepsItsOriginalExceptionWhenAuditPersistenceFails()
    {
        TodoException simulationFailure=new TodoException("TODO_SIMULATION_FAILED","simulation failed");
        org.mockito.Mockito.when(definitions.simulate(eq(9L),any(),eq("LEAD_CREATED"))).thenThrow(simulationFailure);
        org.mockito.Mockito.doThrow(new IllegalStateException("database unavailable")).when(audits)
                .record(any(),any(),org.mockito.ArgumentMatchers.anyLong(),any());

        TodoException thrown=assertThrows(TodoException.class,()->service.simulate(command(),actor()));

        assertSame(simulationFailure,thrown);
        assertEquals(1,thrown.getSuppressed().length);
        verify(audits,times(1)).record(any(),any(),org.mockito.ArgumentMatchers.anyLong(),any());
    }

    @Test void eventTypeMustMatchTheTargetDefinitionSnapshotAndMismatchIsAudited()
    {
        org.mockito.Mockito.when(definitions.simulate(eq(9L),any(),eq("LEAD_CREATED")))
                .thenThrow(new TodoException("TODO_SIMULATION_EVENT_TYPE_MISMATCH","event mismatch"));

        TodoException thrown=assertThrows(TodoException.class,()->service.simulate(command(),actor()));

        assertEquals("TODO_SIMULATION_EVENT_TYPE_MISMATCH",thrown.getBusinessCode());
        verify(definitions).simulate(eq(9L),any(),eq("LEAD_CREATED"));
        verify(audits).record(any(),any(),org.mockito.ArgumentMatchers.anyLong(),any());
    }

    @Test void simulationRejectsAHashThatNoLongerMatchesThePreflightGate()
    {
        whenSimulationReturns(sampleSimulation());
        ConfigurationSimulationCommand stale=new ConfigurationSimulationCommand("request-stale",9L,"LEAD_CREATED","LEAD",3L,
                command().payload(),LocalDateTime.of(2026,7,21,9,0),List.of(),"old-hash");

        TodoException error=assertThrows(TodoException.class,()->service.simulate(stale,actor()));

        assertEquals("TODO_TEMPLATE_PREFLIGHT_STALE",error.getBusinessCode());
        verify(audits).record(any(),any(),org.mockito.ArgumentMatchers.anyLong(),any());
    }

    private void whenSimulationReturns(TodoSimulationView value)
    {org.mockito.Mockito.when(definitions.simulate(eq(9L),any(),eq("LEAD_CREATED"))).thenReturn(value);}

    private Actor actor(){return new Actor(7L,"operator",2L);}

    private ConfigurationSimulationCommand command()
    {
        Map<String,Object> nested=new LinkedHashMap<>();
        nested.put("customerPhone","13800138000");nested.put("password","hunter2");nested.put("accessToken","top-secret");
        nested.put("fileUrl","file:///private/evidence.pdf");nested.put("customerEmail","alice@example.com");
        nested.put("idCardNo","11010519491231002X");
        return new ConfigurationSimulationCommand("request-6",9L,"LEAD_CREATED","LEAD",3L,
                Map.of("stage","READY","customer",nested),LocalDateTime.of(2026,7,21,9,0),List.of());
    }

    private TodoSimulationView sampleSimulation()
    {
        return new TodoSimulationView(9L,"definition-hash",new TriggerTrace("MATCHED","LEAD_CREATED",1,List.of("state:matched")),
                new OwnerTrace("RESOLVED",11L,List.of(11L),List.of(),false,List.of("owner:11")),
                new SlaTrace("PLANNED","DEFAULT",LocalDateTime.of(2026,7,21,9,0),LocalDateTime.of(2026,7,21,10,0),null,null,null,List.of("sla:60")),
                new FormTrace(Map.of("customerEmail","alice@example.com","cardTitle","Safe card"),
                        Map.of("idCardNo","11010519491231002X","requiredFields",List.of("summary"))),
                List.of(),List.of(),List.of(),List.of());
    }

}
