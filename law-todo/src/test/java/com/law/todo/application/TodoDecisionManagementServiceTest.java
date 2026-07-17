package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoDecisionCommands.CreateDecisionCommand;
import com.law.todo.application.command.TodoDecisionCommands.UpdateDecisionCommand;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoMapper;

@ExtendWith(MockitoExtension.class)
class TodoDecisionManagementServiceTest
{
    @Mock TodoMapper mapper;
    private final Actor actor=new Actor(7L,"alice",3L);

    @Test void resolvedDecisionRequiresConclusionAndResolution()
    {
        TodoException error=assertThrows(TodoException.class,()->service().create(new CreateDecisionCommand(
                "decision-create","D-001","Choose policy",null,true,"RESOLVED",null,null),actor));
        assertEquals("TODO_DECISION_CONCLUSION_REQUIRED",error.getBusinessCode());
        verify(mapper,never()).insertDecision(anyMap());
    }

    @Test void createUsesIdempotentClaimAndWritesActorAudit()
    {
        CreateDecisionCommand command=new CreateDecisionCommand("decision-create","D-001","Choose policy","desc",true,"RESOLVED","Use A","Approved by board");
        String fingerprint=TodoDecisionManagementService.fingerprint("CREATE_DECISION",null,null,command,actor);
        when(mapper.insertDefinitionActionClaim(anyMap())).thenReturn(1);
        when(mapper.selectDefinitionActionForUpdate("decision-create")).thenReturn(claim("decision-create","CREATE_DECISION",fingerprint,null));
        when(mapper.selectDecisionByCode("D-001")).thenReturn(null);
        when(mapper.insertDecision(anyMap())).thenAnswer(invocation->{Map<String,Object> row=invocation.getArgument(0);row.put("decisionId",21L);return 1;});
        when(mapper.completeDefinitionAction("decision-create",fingerprint,21L)).thenReturn(1);
        when(mapper.selectDecisionById(21L)).thenReturn(decision(21L,"D-001","RESOLVED",0));

        var result=service().create(command,actor);

        assertEquals(21L,result.decisionId());assertEquals("RESOLVED",result.status());
        verify(mapper).insertDecision(org.mockito.ArgumentMatchers.argThat(row->"alice".equals(row.get("decidedBy"))));
    }

    @Test void identicalCreateReplayReturnsRecordedDecisionWithoutAnotherInsert()
    {
        CreateDecisionCommand command=new CreateDecisionCommand("decision-create","D-001","Choose policy",null,false,"OPEN",null,null);
        String fingerprint=TodoDecisionManagementService.fingerprint("CREATE_DECISION",null,null,command,actor);
        when(mapper.insertDefinitionActionClaim(anyMap())).thenReturn(0);
        when(mapper.selectDefinitionActionForUpdate("decision-create")).thenReturn(claim("decision-create","CREATE_DECISION",fingerprint,21L));
        when(mapper.selectDecisionById(21L)).thenReturn(decision(21L,"D-001","OPEN",0));

        assertEquals(21L,service().create(command,actor).decisionId());
        verify(mapper,never()).insertDecision(anyMap());
    }

    @Test void updateUsesOptimisticVersionAndRejectsStaleWrites()
    {
        UpdateDecisionCommand command=new UpdateDecisionCommand("decision-update",21L,0,"D-001","Choose policy",null,true,"CLOSED","Use A","Final");
        when(mapper.selectDecisionById(21L)).thenReturn(decision(21L,"D-001","OPEN",0));
        String fingerprint=TodoDecisionManagementService.fingerprint("UPDATE_DECISION",21L,0,command,actor);
        when(mapper.insertDefinitionActionClaim(anyMap())).thenReturn(1);
        when(mapper.selectDefinitionActionForUpdate("decision-update")).thenReturn(claim("decision-update","UPDATE_DECISION",fingerprint,null));
        when(mapper.updateDecisionConditionally(anyMap())).thenReturn(0);

        TodoException error=assertThrows(TodoException.class,()->service().update(command,actor));

        assertEquals("TODO_DECISION_VERSION_CONFLICT",error.getBusinessCode());
        verify(mapper,never()).completeDefinitionAction(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyLong());
    }

    private TodoDecisionManagementService service(){return new TodoDecisionManagementService(mapper);}
    private Map<String,Object> claim(String action,String type,String fingerprint,Long entity)
    {
        Map<String,Object> row=new HashMap<>();row.put("action_id",action);row.put("action_type",type);row.put("request_fingerprint",fingerprint);
        row.put("action_status",entity==null?"CLAIMED":"APPLIED");row.put("operator_id",7L);row.put("operator_name","alice");row.put("entity_id",entity);return row;
    }
    private Map<String,Object> decision(long id,String code,String status,int version)
    {return Map.of("decision_id",id,"decision_code",code,"title","Choose policy","blocking","Y","status",status,"version",version);}
}
