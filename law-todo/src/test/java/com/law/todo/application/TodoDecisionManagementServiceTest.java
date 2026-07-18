package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.time.LocalDateTime;

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
    private final LocalDateTime dueAt=LocalDateTime.of(2026,7,31,18,0);

    @Test void resolvedDecisionRequiresConclusionAndResolution()
    {
        TodoException error=assertThrows(TodoException.class,()->service().create(new CreateDecisionCommand(
                "decision-create","D-001","Choose policy",null,true,"RESOLVED",null,null,
                8L,"product_owner",dueAt,"PHASE_ONE"),actor));
        assertEquals("TODO_DECISION_CONCLUSION_REQUIRED",error.getBusinessCode());
        verify(mapper,never()).insertDecision(anyMap());
    }

    @Test void blockingDecisionRequiresConcreteOwnerRoleDeadlineAndPhase()
    {
        TodoException error=assertThrows(TodoException.class,()->service().create(new CreateDecisionCommand(
                "decision-create","D-001","Choose policy",null,true,"OPEN",null,null,
                null,null,null,"PHASE_ONE"),actor));
        assertEquals("TODO_DECISION_OWNER_REQUIRED",error.getBusinessCode());
        verify(mapper,never()).insertDecision(anyMap());
    }

    @Test void createUsesIdempotentClaimAndWritesActorAudit()
    {
        CreateDecisionCommand command=new CreateDecisionCommand("decision-create","D-001","Choose policy","desc",true,"RESOLVED","Use A","Approved by board",
                8L,"product_owner",dueAt,"PHASE_ONE");
        when(mapper.selectDecisionGovernanceUser(8L)).thenReturn(Map.of("user_id",8L,"user_name","owner"));
        when(mapper.selectRoleIdByKey("product_owner")).thenReturn(3L);
        String fingerprint=TodoDecisionManagementService.fingerprint("CREATE_DECISION",null,null,command,actor);
        when(mapper.insertDefinitionActionClaim(anyMap())).thenReturn(1);
        when(mapper.selectDefinitionActionForUpdate("decision-create")).thenReturn(claim("decision-create","CREATE_DECISION",fingerprint,null));
        when(mapper.selectDecisionByCode("D-001")).thenReturn(null);
        when(mapper.insertDecision(anyMap())).thenAnswer(invocation->{Map<String,Object> row=invocation.getArgument(0);row.put("decisionId",21L);return 1;});
        when(mapper.completeDefinitionAction("decision-create",fingerprint,21L)).thenReturn(1);
        when(mapper.selectDecisionById(21L)).thenReturn(decision(21L,"D-001","RESOLVED",0,8L,"product_owner",dueAt,"PHASE_ONE"));

        var result=service().create(command,actor);

        assertEquals(21L,result.decisionId());assertEquals("RESOLVED",result.status());
        assertEquals(8L,result.ownerUserId());assertEquals("product_owner",result.ownerRoleKey());assertEquals(dueAt,result.dueAt());assertEquals("PHASE_ONE",result.deliveryPhase());
        verify(mapper).insertDecision(org.mockito.ArgumentMatchers.argThat(row->"alice".equals(row.get("decidedBy"))
                &&Long.valueOf(8L).equals(row.get("ownerUserId"))&&"product_owner".equals(row.get("ownerRoleKey"))
                &&dueAt.equals(row.get("dueAt"))&&"PHASE_ONE".equals(row.get("deliveryPhase"))));
    }

    @Test void identicalCreateReplayReturnsRecordedDecisionWithoutAnotherInsert()
    {
        CreateDecisionCommand command=new CreateDecisionCommand("decision-create","D-001","Choose policy",null,false,"OPEN",null,null,
                null,null,null,"CROSS_PHASE");
        String fingerprint=TodoDecisionManagementService.fingerprint("CREATE_DECISION",null,null,command,actor);
        when(mapper.insertDefinitionActionClaim(anyMap())).thenReturn(0);
        when(mapper.selectDefinitionActionForUpdate("decision-create")).thenReturn(claim("decision-create","CREATE_DECISION",fingerprint,21L));
        when(mapper.selectDecisionById(21L)).thenReturn(decision(21L,"D-001","OPEN",0));

        assertEquals(21L,service().create(command,actor).decisionId());
        verify(mapper,never()).insertDecision(anyMap());
    }

    @Test void updateUsesOptimisticVersionAndRejectsStaleWrites()
    {
        UpdateDecisionCommand command=new UpdateDecisionCommand("decision-update",21L,0,"D-001","Choose policy",null,true,"CLOSED","Use A","Final",
                8L,"product_owner",dueAt,"PHASE_ONE");
        when(mapper.selectDecisionById(21L)).thenReturn(decision(21L,"D-001","OPEN",0));
        when(mapper.selectDecisionGovernanceUser(8L)).thenReturn(Map.of("user_id",8L,"user_name","owner"));
        when(mapper.selectRoleIdByKey("product_owner")).thenReturn(3L);
        String fingerprint=TodoDecisionManagementService.fingerprint("UPDATE_DECISION",21L,0,command,actor);
        when(mapper.insertDefinitionActionClaim(anyMap())).thenReturn(1);
        when(mapper.selectDefinitionActionForUpdate("decision-update")).thenReturn(claim("decision-update","UPDATE_DECISION",fingerprint,null));
        when(mapper.updateDecisionConditionally(anyMap())).thenReturn(0);

        TodoException error=assertThrows(TodoException.class,()->service().update(command,actor));

        assertEquals("TODO_DECISION_VERSION_CONFLICT",error.getBusinessCode());
        verify(mapper,never()).completeDefinitionAction(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyLong());
    }

    @Test void listExposesServerComputedImpactsIncludingNoReference()
    {
        Map<String,Object> none=new HashMap<>(decision(1L,"D-001","OPEN",0));
        Map<String,Object> draftAndPublished=new HashMap<>(decision(2L,"D-002","OPEN",0));draftAndPublished.put("impacted_template_codes","CASE_ASSIGN,LEAD_CONTACT");
        when(mapper.selectDecisions()).thenReturn(List.of(draftAndPublished,none));
        var values=service().list();
        assertEquals(List.of(),values.get(0).impactedTemplateCodes());
        assertEquals(List.of("CASE_ASSIGN","LEAD_CONTACT"),values.get(1).impactedTemplateCodes());
    }

    @Test void governanceOptionsExposeOnlyMapperApprovedUsersRolesAndFixedPhases()
    {
        when(mapper.selectDecisionGovernanceUsers()).thenReturn(List.of(Map.of("user_id",8L,"user_name","owner","nick_name","Owner")));
        when(mapper.selectDecisionGovernanceRoles()).thenReturn(List.of(Map.of("role_key","product_owner","role_name","Product owner")));

        Map<String,Object> options=service().governanceOptions();

        assertEquals(1,((List<?>)options.get("users")).size());
        assertEquals(1,((List<?>)options.get("roles")).size());
        assertEquals(List.of("PHASE_ONE","PHASE_TWO","CROSS_PHASE"),options.get("deliveryPhases"));
    }

    private TodoDecisionManagementService service(){return new TodoDecisionManagementService(mapper);}
    private Map<String,Object> claim(String action,String type,String fingerprint,Long entity)
    {
        Map<String,Object> row=new HashMap<>();row.put("action_id",action);row.put("action_type",type);row.put("request_fingerprint",fingerprint);
        row.put("action_status",entity==null?"CLAIMED":"APPLIED");row.put("operator_id",7L);row.put("operator_name","alice");row.put("entity_id",entity);return row;
    }
    private Map<String,Object> decision(long id,String code,String status,int version)
    {return Map.of("decision_id",id,"decision_code",code,"title","Choose policy","blocking","Y","status",status,"version",version);}
    private Map<String,Object> decision(long id,String code,String status,int version,Long ownerUserId,String ownerRoleKey,LocalDateTime due,String phase)
    {Map<String,Object> row=new HashMap<>(decision(id,code,status,version));row.put("owner_user_id",ownerUserId);row.put("owner_role_key",ownerRoleKey);row.put("due_at",due);row.put("delivery_phase",phase);return row;}
}
