package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoAdmissionEvidenceCommands.UpdateAdmissionEvidenceCommand;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoAdmissionEvidenceMapper;

@ExtendWith(MockitoExtension.class)
class TodoAdmissionEvidenceServiceTest
{
    @Mock TodoAdmissionEvidenceMapper mapper;
    @Mock TodoFoundationResourceService resources;
    @Mock TodoHistoricalMigrationReadinessService migrations;
    @Mock TodoFileSecurityReadinessService fileSecurity;
    private final Actor owner=new Actor(7L,"owner",3L);
    private final Actor reviewer=new Actor(9L,"reviewer",4L);
    private final LocalDateTime dueAt=LocalDateTime.of(2026,7,31,18,0);

    @Test void inReviewRequiresAccountabilityAndTraceableArtifact()
    {
        UpdateAdmissionEvidenceCommand command=new UpdateAdmissionEvidenceCommand(
                "submit-g02",1L,0,null,9L,dueAt,"IN_REVIEW",null,"ready for review");

        TodoException error=assertThrows(TodoException.class,()->service().update(command,owner));

        assertEquals("TODO_ADMISSION_OWNER_REQUIRED",error.getBusinessCode());
        verify(mapper,never()).updateEvidenceConditionally(anyMap());
    }

    @Test void ownerAndReviewerMustBeIndependentActiveUsers()
    {
        UpdateAdmissionEvidenceCommand command=new UpdateAdmissionEvidenceCommand(
                "submit-g02",1L,0,7L,7L,dueAt,"IN_REVIEW","repo://doc/g02.md","ready for review");

        TodoException error=assertThrows(TodoException.class,()->service().update(command,owner));

        assertEquals("TODO_ADMISSION_REVIEWER_INDEPENDENCE_REQUIRED",error.getBusinessCode());
    }

    @Test void onlySelectedReviewerCanApproveWithConclusion()
    {
        UpdateAdmissionEvidenceCommand command=new UpdateAdmissionEvidenceCommand(
                "approve-g02",1L,0,7L,9L,dueAt,"APPROVED","repo://doc/g02.md","Approved dictionary and role contract");
        when(mapper.selectEvidenceById(1L)).thenReturn(row("OPEN",0));
        when(mapper.selectActiveUser(7L)).thenReturn(Map.of("user_id",7L));
        when(mapper.selectActiveUser(9L)).thenReturn(Map.of("user_id",9L));

        TodoException error=assertThrows(TodoException.class,()->service().update(command,owner));

        assertEquals("TODO_ADMISSION_REVIEWER_REQUIRED",error.getBusinessCode());
        verify(mapper,never()).insertEvidenceActionClaim(anyMap());
    }

    @Test void reviewerApprovalUsesIdempotentClaimAndOptimisticVersion()
    {
        UpdateAdmissionEvidenceCommand command=new UpdateAdmissionEvidenceCommand(
                "approve-g02",1L,0,7L,9L,dueAt,"APPROVED","repo://doc/g02.md","Approved dictionary and role contract");
        when(mapper.selectEvidenceById(1L)).thenReturn(row("IN_REVIEW",0),row("APPROVED",1));
        when(mapper.selectActiveUser(7L)).thenReturn(Map.of("user_id",7L));
        when(mapper.selectActiveUser(9L)).thenReturn(Map.of("user_id",9L));
        when(resources.gateReady("G-02")).thenReturn(true);
        String fingerprint=TodoAdmissionEvidenceService.fingerprint(command,reviewer);
        when(mapper.insertEvidenceActionClaim(anyMap())).thenReturn(1);
        when(mapper.selectEvidenceActionForUpdate("approve-g02")).thenReturn(action(fingerprint,null,reviewer.userId()));
        when(mapper.updateEvidenceConditionally(anyMap())).thenReturn(1);
        when(mapper.completeEvidenceAction("approve-g02",fingerprint,1L)).thenReturn(1);

        var result=service().update(command,reviewer);

        assertEquals("APPROVED",result.status());
        assertEquals(9L,result.reviewerUserId());
        verify(mapper).updateEvidenceConditionally(org.mockito.ArgumentMatchers.argThat(value->
                "reviewer".equals(value.get("reviewedBy"))&&"APPROVED".equals(value.get("status"))));
    }

    @Test void g02CannotBeApprovedWhileRepositoryResourcesAreNotReady()
    {
        UpdateAdmissionEvidenceCommand command=new UpdateAdmissionEvidenceCommand(
                "approve-g02",1L,0,7L,9L,dueAt,"APPROVED","repo://doc/g02.md","Approved dictionary and role contract");
        when(mapper.selectEvidenceById(1L)).thenReturn(row("IN_REVIEW",0));
        when(mapper.selectActiveUser(7L)).thenReturn(Map.of("user_id",7L));
        when(mapper.selectActiveUser(9L)).thenReturn(Map.of("user_id",9L));
        when(resources.gateReady("G-02")).thenReturn(false);

        TodoException error=assertThrows(TodoException.class,()->service().update(command,reviewer));

        assertEquals("TODO_ADMISSION_RESOURCE_NOT_READY",error.getBusinessCode());
        verify(mapper,never()).insertEvidenceActionClaim(anyMap());
    }

    @Test void identicalReplayReturnsRecordedEvidenceWithoutAnotherUpdate()
    {
        UpdateAdmissionEvidenceCommand command=new UpdateAdmissionEvidenceCommand(
                "submit-g02",1L,0,7L,9L,dueAt,"IN_REVIEW","repo://doc/g02.md","ready for review");
        when(mapper.selectEvidenceById(1L)).thenReturn(row("OPEN",0),row("IN_REVIEW",1));
        when(mapper.selectActiveUser(7L)).thenReturn(Map.of("user_id",7L));
        when(mapper.selectActiveUser(9L)).thenReturn(Map.of("user_id",9L));
        String fingerprint=TodoAdmissionEvidenceService.fingerprint(command,owner);
        when(mapper.insertEvidenceActionClaim(anyMap())).thenReturn(0);
        when(mapper.selectEvidenceActionForUpdate("submit-g02")).thenReturn(action(fingerprint,1L,owner.userId()));

        assertEquals("IN_REVIEW",service().update(command,owner).status());
        verify(mapper,never()).updateEvidenceConditionally(anyMap());
    }

    @Test void g05CannotBeApprovedBeforeSecurityReviewIsReady()
    {
        UpdateAdmissionEvidenceCommand command=new UpdateAdmissionEvidenceCommand(
                "approve-g05",1L,0,7L,9L,dueAt,"APPROVED","repo://doc/g05.md","Security review passed");
        when(mapper.selectEvidenceById(1L)).thenReturn(row("G-05","IN_REVIEW",0));
        when(mapper.selectActiveUser(7L)).thenReturn(Map.of("user_id",7L));
        when(mapper.selectActiveUser(9L)).thenReturn(Map.of("user_id",9L));
        when(fileSecurity.gateReady("G-05")).thenReturn(false);

        TodoException error=assertThrows(TodoException.class,()->service().update(command,reviewer));

        assertEquals("TODO_ADMISSION_FILE_SECURITY_NOT_READY",error.getBusinessCode());
        verify(mapper,never()).insertEvidenceActionClaim(anyMap());
    }

    @Test void g04CannotBeApprovedWhileHistoricalMigrationContractIsNotReady()
    {
        UpdateAdmissionEvidenceCommand command=new UpdateAdmissionEvidenceCommand(
                "approve-g04",1L,0,7L,9L,dueAt,"APPROVED","repo://doc/g04.md","Approved migration plan");
        when(mapper.selectEvidenceById(1L)).thenReturn(row("G-04","IN_REVIEW",0));
        when(mapper.selectActiveUser(7L)).thenReturn(Map.of("user_id",7L));
        when(mapper.selectActiveUser(9L)).thenReturn(Map.of("user_id",9L));
        when(migrations.gateReady("G-04")).thenReturn(false);

        TodoException error=assertThrows(TodoException.class,()->service().update(command,reviewer));

        assertEquals("TODO_ADMISSION_MIGRATION_NOT_READY",error.getBusinessCode());
        verify(mapper,never()).insertEvidenceActionClaim(anyMap());
    }

    @Test void listAndOptionsExposeOnlyMapperGovernedRows()
    {
        when(mapper.selectEvidence()).thenReturn(List.of(row("OPEN",0)));
        when(mapper.selectActiveUsers()).thenReturn(List.of(Map.of("user_id",7L,"user_name","owner")));

        assertEquals("G-02",service().list().get(0).gateCode());
        assertEquals(1,((List<?>)service().governanceOptions().get("users")).size());
    }

    @Test void approvedEvidenceIsImmutable()
    {
        UpdateAdmissionEvidenceCommand command=new UpdateAdmissionEvidenceCommand(
                "change-approved",1L,1,7L,9L,dueAt,"APPROVED","repo://doc/changed.md","changed conclusion");
        when(mapper.selectEvidenceById(1L)).thenReturn(row("APPROVED",1));
        when(mapper.selectActiveUser(7L)).thenReturn(Map.of("user_id",7L));
        when(mapper.selectActiveUser(9L)).thenReturn(Map.of("user_id",9L));

        TodoException error=assertThrows(TodoException.class,()->service().update(command,reviewer));

        assertEquals("TODO_ADMISSION_STATE_INVALID",error.getBusinessCode());
        verify(mapper,never()).insertEvidenceActionClaim(anyMap());
    }

    private TodoAdmissionEvidenceService service(){return new TodoAdmissionEvidenceService(mapper,resources,migrations,fileSecurity);}
    private Map<String,Object> row(String status,int version)
    {
        return row("G-02",status,version);
    }
    private Map<String,Object> row(String gateCode,String status,int version)
    {
        Map<String,Object> row=new HashMap<>();row.put("evidence_id",1L);row.put("evidence_code","G02-DICTIONARY-ROLE");
        row.put("gate_code",gateCode);row.put("category","DICTIONARY_ROLE");row.put("title","Dictionary and role contract");
        row.put("delivery_phase","PHASE_ONE");row.put("status",status);row.put("owner_user_id",7L);row.put("reviewer_user_id",9L);
        row.put("due_at",dueAt);row.put("artifact_ref","repo://doc/g02.md");row.put("conclusion","ready");row.put("version",version);return row;
    }
    private Map<String,Object> action(String fingerprint,Long evidenceId,Long operatorId)
    {
        Map<String,Object> row=new HashMap<>();row.put("action_id","submit-g02");row.put("request_fingerprint",fingerprint);
        row.put("action_status",evidenceId==null?"CLAIMED":"APPLIED");row.put("operator_id",operatorId);row.put("evidence_id",evidenceId);return row;
    }
}
