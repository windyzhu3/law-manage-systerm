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
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson2.JSON;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoConfigurationCommands.ConfigurationSimulationCommand;
import com.law.todo.application.view.TodoConfigurationViews.ConfigurationSimulationResult;
import com.law.todo.application.view.TodoSimulationView;
import com.law.todo.application.view.TodoSimulationView.FormTrace;
import com.law.todo.application.view.TodoSimulationView.OwnerTrace;
import com.law.todo.application.view.TodoSimulationView.SlaTrace;
import com.law.todo.application.view.TodoSimulationView.TriggerTrace;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoConfigurationMapper;

@ExtendWith(MockitoExtension.class)
class TodoConfigurationSimulationServiceTest
{
    @Mock private TodoDefinitionSimulationService definitions;
    @Mock private TodoConfigurationMapper configMapper;
    @InjectMocks private TodoConfigurationSimulationService service;

    @Test void simulationIsReadOnlyAndPersistsOnlySanitizedOrderedAudit()
    {
        whenSimulationReturns(sampleSimulation());

        ConfigurationSimulationResult result=service.simulate(command(),actor());

        assertEquals("MATCHED",result.simulation().trigger().status());
        ArgumentCaptor<Map<String,Object>> rows=ArgumentCaptor.forClass(Map.class);
        verify(configMapper).insertSimulationRecord(rows.capture());
        Map<String,Object> row=rows.getValue();
        assertEquals("request-6",row.get("requestId"));
        assertEquals(9L,row.get("templateVersionId"));
        assertEquals(7L,row.get("operatorId"));
        String persisted=JSON.toJSONString(row);
        for(String unsafe:List.of("customerPhone","13800138000","password","hunter2","accessToken",
                "top-secret","fileUrl","file:///private/evidence.pdf","customerEmail","alice@example.com",
                "idCardNo","11010519491231002X")) assertTrue(!persisted.contains(unsafe));
        assertTrue(persisted.contains("[REDACTED]"));
        Map<String,Object> ordered=JSON.parseObject(String.valueOf(row.get("resultJson")));
        assertEquals(List.of("state","template","owner","sla","dod","route","card","log"),
                List.copyOf(ordered.keySet()));
        verify(definitions).simulate(eq(9L),any());
        verify(configMapper,never()).insertSlaRule(any());
        verify(configMapper,never()).insertDodRule(any());
        verify(configMapper,never()).deleteDraftRuleRefs(any());
    }

    @Test void failedSimulationIsAuditedWithSanitizedFailureThenRethrown()
    {
        TodoException failure=new TodoException("TODO_SIMULATION_FAILED","token top-secret for alice@example.com");
        org.mockito.Mockito.when(definitions.simulate(eq(9L),any())).thenThrow(failure);

        TodoException thrown=assertThrows(TodoException.class,()->service.simulate(command(),actor()));

        assertSame(failure,thrown);
        ArgumentCaptor<Map<String,Object>> rows=ArgumentCaptor.forClass(Map.class);
        verify(configMapper).insertSimulationRecord(rows.capture());
        String persisted=JSON.toJSONString(rows.getValue());
        assertTrue(!persisted.contains("top-secret"));
        assertTrue(!persisted.contains("alice@example.com"));
        Map<String,Object> ordered=JSON.parseObject(String.valueOf(rows.getValue().get("resultJson")));
        assertEquals(List.of("state","template","owner","sla","dod","route","card","log"),
                List.copyOf(ordered.keySet()));
        assertEquals("FAILED",((Map<?,?>)ordered.get("state")).get("status"));
    }

    @Test void failedSimulationAuditIsNotRolledBackWithTheSimulationException() throws Exception
    {
        Transactional transaction=TodoConfigurationSimulationService.class
                .getMethod("simulate",ConfigurationSimulationCommand.class,Actor.class).getAnnotation(Transactional.class);

        assertTrue(List.of(transaction.noRollbackFor()).contains(RuntimeException.class));
    }

    @Test void auditTruncatesOversizedSafeTextWithoutDroppingTheAuditSummary()
    {
        whenSimulationReturns(sampleSimulation());
        Map<String,Object> payload=new LinkedHashMap<>(command().payload());
        payload.put("summary","x".repeat(5000));
        ConfigurationSimulationCommand oversized=new ConfigurationSimulationCommand("request-6",9L,"LEAD_CREATED","LEAD",3L,
                payload,LocalDateTime.of(2026,7,21,9,0),List.of());

        service.simulate(oversized,actor());

        ArgumentCaptor<Map<String,Object>> rows=ArgumentCaptor.forClass(Map.class);
        verify(configMapper).insertSimulationRecord(rows.capture());
        String input=String.valueOf(rows.getValue().get("inputSummaryJson"));
        assertTrue(input.length()<2000);
        assertTrue(input.contains("[TRUNCATED]"));
        assertTrue(input.contains("stage"));
    }

    @Test void ineligibleSimulationWithMissingDefinitionFieldsIsStillAudited()
    {
        TodoSimulationView halted=new TodoSimulationView(9L,null,new TriggerTrace("SKIPPED",null,0,List.of()),
                new OwnerTrace("SKIPPED",null,List.of(),List.of(),false,List.of()),
                new SlaTrace("SKIPPED",null,LocalDateTime.of(2026,7,21,9,0),null,null,null,null,List.of()),
                new FormTrace(Map.of(),Map.of()),List.of(),List.of(),List.of(),List.of());
        whenSimulationReturns(halted);

        service.simulate(command(),actor());

        verify(configMapper).insertSimulationRecord(any());
    }

    @Test void auditDetectsSensitiveKeysBeforeTheyAreBounded()
    {
        whenSimulationReturns(sampleSimulation());
        Map<String,Object> payload=new LinkedHashMap<>(command().payload());
        payload.put("x".repeat(600)+"password","hunter2");
        ConfigurationSimulationCommand oversizedKey=new ConfigurationSimulationCommand("request-6",9L,"LEAD_CREATED","LEAD",3L,
                payload,LocalDateTime.of(2026,7,21,9,0),List.of());

        service.simulate(oversizedKey,actor());

        ArgumentCaptor<Map<String,Object>> rows=ArgumentCaptor.forClass(Map.class);
        verify(configMapper).insertSimulationRecord(rows.capture());
        assertTrue(!String.valueOf(rows.getValue().get("inputSummaryJson")).contains("hunter2"));
    }

    @Test void auditWriteFailureIsNotMisreportedAsASecondFailedSimulationAudit()
    {
        whenSimulationReturns(sampleSimulation());
        org.mockito.Mockito.when(configMapper.insertSimulationRecord(any())).thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(IllegalStateException.class,()->service.simulate(command(),actor()));

        verify(configMapper,times(1)).insertSimulationRecord(any());
    }

    @Test void failedSimulationKeepsItsOriginalExceptionWhenAuditPersistenceFails()
    {
        TodoException simulationFailure=new TodoException("TODO_SIMULATION_FAILED","simulation failed");
        org.mockito.Mockito.when(definitions.simulate(eq(9L),any())).thenThrow(simulationFailure);
        org.mockito.Mockito.when(configMapper.insertSimulationRecord(any())).thenThrow(new IllegalStateException("database unavailable"));

        TodoException thrown=assertThrows(TodoException.class,()->service.simulate(command(),actor()));

        assertSame(simulationFailure,thrown);
        assertEquals(1,thrown.getSuppressed().length);
        verify(configMapper,times(1)).insertSimulationRecord(any());
    }

    private void whenSimulationReturns(TodoSimulationView value)
    {org.mockito.Mockito.when(definitions.simulate(eq(9L),any())).thenReturn(value);}

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
