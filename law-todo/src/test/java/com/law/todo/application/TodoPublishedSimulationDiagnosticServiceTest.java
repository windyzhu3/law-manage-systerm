package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.view.TodoSimulationView;
import com.law.todo.application.view.TodoSimulationView.FormTrace;
import com.law.todo.application.view.TodoSimulationView.OwnerTrace;
import com.law.todo.application.view.TodoSimulationView.SlaTrace;
import com.law.todo.application.view.TodoSimulationView.TriggerTrace;
import com.law.todo.mapper.TodoConfigurationMapper;

@ExtendWith(MockitoExtension.class)
class TodoPublishedSimulationDiagnosticServiceTest
{
    @Mock private TodoConfigurationMapper mapper;
    @Mock private TodoDefinitionSimulationService simulations;

    @Test void batchKeepsGoingAndClassifiesOwnerAndExecutionFailures()
    {
        when(mapper.selectPublishedSimulationCandidates()).thenReturn(List.of(
            candidate(31L,"LEAD_FIRST_CONTACT","LEAD_ASSIGNED","LEAD","{\"ownerId\":11}"),
            candidate(32L,"CONTRACT_SIGN","CONTRACT_APPROVED","CONTRACT","{\"ownerId\":12}"),
            candidate(33L,"CASE_ASSIGN","CASE_CREATED","CASE","{\"ownerId\":21}")));
        when(simulations.simulate(eq(31L),any(),eq("LEAD_ASSIGNED"),eq(1),eq("LEAD")))
            .thenReturn(view(31L,"RESOLVED",List.of()));
        when(simulations.simulate(eq(32L),any(),eq("CONTRACT_APPROVED"),eq(1),eq("CONTRACT")))
            .thenReturn(view(32L,"UNRESOLVED",List.of(new TodoSimulationView.SimulationIssue(
                "TODO_SIMULATION_OWNER_UNRESOLVED","owner","WARNING","owner missing"))));
        when(simulations.simulate(eq(33L),any(),eq("CASE_CREATED"),eq(1),eq("CASE")))
            .thenThrow(new IllegalStateException("broken snapshot"));

        var result=new TodoPublishedSimulationDiagnosticService(mapper,simulations)
            .diagnose(new Actor(7L,"operator",2L));

        assertEquals(3,result.total());
        assertEquals(1,result.passed());
        assertEquals(1,result.warning());
        assertEquals(1,result.failed());
        assertEquals(List.of("PASSED","WARNING","FAILED"),result.items().stream().map(item->item.status()).toList());
    }

    private Map<String,Object> candidate(long versionId,String templateCode,String eventType,String businessType,String sample)
    {return Map.of("version_id",versionId,"template_code",templateCode,"template_name",templateCode,
        "event_type",eventType,"payload_version",1,"business_type",businessType,"sample_payload_json",sample);}

    private TodoSimulationView view(long versionId,String ownerStatus,List<TodoSimulationView.SimulationIssue> issues)
    {return new TodoSimulationView(versionId,"hash",new TriggerTrace("MATCHED","EVENT",1,List.of()),
        new OwnerTrace(ownerStatus,"RESOLVED".equals(ownerStatus)?11L:null,List.of(),List.of(),false,List.of()),
        new SlaTrace("PLANNED","DEFAULT",LocalDateTime.now(),LocalDateTime.now().plusHours(1),null,null,null,List.of()),
        new FormTrace(Map.of(),Map.of()),List.of(),List.of(),List.of(),issues);}
}
