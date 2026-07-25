package com.law.file.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import com.law.file.domain.FileException;
import com.law.file.domain.FileObject.AccessToken;
import com.law.file.domain.FileObject.FileActor;
import com.law.file.domain.FileObject.FileBusinessRelation;
import com.law.file.domain.FileObject.FileVersion;
import com.law.file.infrastructure.internal.FilePersistenceModel.RelationAction;
import com.law.file.infrastructure.internal.FilePersistenceModel.CleanupTask;
import com.law.file.infrastructure.internal.FilePersistenceModel.StoredVersion;
import com.law.file.infrastructure.internal.FilePersistenceModel.UploadIntent;
import com.law.file.repository.FileObjectRepository;
import com.law.file.security.DetectedContentType;
import com.law.file.security.FileAccessPolicy;
import com.law.file.security.FileContentPolicy;
import com.law.file.spi.FileCleanupAuditPort;
import com.law.file.spi.FileStoragePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class FileObjectGovernanceReviewTest
{
    @Mock FileObjectRepository repository;
    @Mock FileStoragePort storage;
    @Mock FileAccessPolicy access;
    @Mock FileCleanupAuditPort cleanupAudit;
    private final FileActor actor=new FileActor(7L,"alice",3L);
    private final Instant now=Instant.parse("2026-07-17T01:00:00Z");
    private FileObjectService service;

    @BeforeEach void setUp()
    {
        lenient().when(repository.lockById(anyLong())).thenAnswer(invocation->
            new com.law.file.domain.FileObject(invocation.getArgument(0),"proof",2,3,"ACTIVE",7L,1));
        service=new FileObjectService(repository,storage,access,cleanupAudit,new FileContentPolicy(),
            Clock.fixed(now,ZoneOffset.UTC),bytes->"token-fixed");
    }

    @Test void public_version_metadata_never_contains_an_object_key()
    {
        assertFalse(Arrays.stream(FileVersion.class.getRecordComponents())
            .anyMatch(component->component.getName().toLowerCase().contains("objectkey")));
    }

    @Test void intent_expiring_exactly_now_is_atomically_expired_without_touching_storage()
    {
        UploadIntent intent=intent("REGISTERED",now,null);
        when(repository.findUploadIntentForUpdate("intent-1")).thenReturn(intent);
        when(repository.expireUploadIntent("intent-1",now)).thenReturn(1);

        FileException error=assertThrows(FileException.class,
            ()->service.completeUpload("intent-1",new ByteArrayInputStream("abc".getBytes()),
                "proof.pdf","application/pdf",actor));

        assertEquals("FILE_UPLOAD_UNAVAILABLE",error.getBusinessCode());
        verify(repository).expireUploadIntent("intent-1",now);
        verify(storage,never()).stage(any(),anyLong(),anyString());
    }

    @Test void missing_and_foreign_intents_have_the_same_non_enumerable_error()
    {
        when(repository.findUploadIntentForUpdate("missing")).thenReturn(null);
        when(repository.findUploadIntentForUpdate("foreign")).thenReturn(new UploadIntent(
            "foreign","a",10L,1,"objects/key","proof.pdf","application/pdf",3L,HASH,"first",
            "fingerprint",99L,"REGISTERED",null,now.plusSeconds(60)));

        FileException missing=assertThrows(FileException.class,
            ()->service.completeUpload("missing",new ByteArrayInputStream(new byte[0]),
                "proof.pdf","application/pdf",actor));
        FileException foreign=assertThrows(FileException.class,
            ()->service.completeUpload("foreign",new ByteArrayInputStream(new byte[0]),
                "proof.pdf","application/pdf",actor));

        assertEquals("FILE_UPLOAD_UNAVAILABLE",missing.getBusinessCode());
        assertEquals(missing.getBusinessCode(),foreign.getBusinessCode());
        assertEquals(missing.getMessage(),foreign.getMessage());
    }

    @Test void concurrent_completion_replay_returns_the_same_immutable_version_without_republishing()
    {
        UploadIntent completed=intent("COMPLETED",now.plusSeconds(60),21L);
        StoredVersion stored=new StoredVersion(version(21L,"first"),"objects/internal");
        when(repository.findUploadIntentForUpdate("intent-1")).thenReturn(completed);
        when(repository.findVersionById(21L)).thenReturn(stored);

        assertEquals(21L,service.completeUpload("intent-1",new ByteArrayInputStream(new byte[0]),
            "proof.pdf","application/pdf",actor).fileVersionId());
        verifyNoInteractions(storage);
    }

    @Test void relation_action_replay_is_fingerprinted_and_cross_relation_reuse_conflicts()
    {
        when(repository.findRelationAction(7L,"rel-action")).thenReturn(new RelationAction(
            7L,"rel-action","RELATE",1L,"different-fingerprint",now));

        FileException error=assertThrows(FileException.class,()->service.relate(
            10L,"rel-action","CASE",9L,"PROOF","BUSINESS",actor));

        assertEquals("FILE_ACTION_ID_CONFLICT",error.getBusinessCode());
        verify(repository,never()).insertRelation(any());
        verify(repository,never()).revokeRelation(anyLong());
    }

    @Test void department_relation_has_its_own_scope_and_every_successful_action_gets_a_ledger_row()
    {
        FileBusinessRelation relation=relation(4L,"DEPARTMENT",3L,0L,true);
        when(repository.insertRelation(any())).thenReturn(relation);
        when(repository.insertRelationAction(any())).thenReturn(1);
        when(repository.insertLifecycleAudit(any())).thenReturn(1);

        FileBusinessRelation created=service.relate(10L,"rel-action","CASE",9L,"PROOF","DEPARTMENT",actor);

        assertEquals(3L,created.scopeDeptId());assertEquals(0L,created.scopeUserId());
        verify(repository).insertRelation(argThat(value->value.scopeDeptId().equals(3L)&&value.scopeUserId().equals(0L)));
        verify(repository).insertRelationAction(argThat(value->value.actionId().equals("rel-action")
            &&value.relationId().equals(4L)&&value.requestFingerprint()!=null));
    }

    @Test void disabled_object_is_rejected_under_lock_before_attach_authorization_or_mutation()
    {
        when(repository.lockById(10L)).thenReturn(new com.law.file.domain.FileObject(
            10L,"proof",2,3,"DISABLED",7L,2));

        FileException error=assertThrows(FileException.class,()->service.relate(
            10L,"late-attach","CASE",9L,"PROOF","BUSINESS",actor));

        assertEquals("FILE_OBJECT_DISABLED",error.getBusinessCode());
        verifyNoInteractions(access);
        verify(repository,never()).insertRelation(any());
        verify(repository,never()).insertRelationAction(any());
    }

    @Test void action_committed_while_waiting_for_the_object_lock_is_replayed_before_disabled_rejection()
    {
        AtomicBoolean objectLockAcquired=new AtomicBoolean();
        String fingerprint=FileObjectService.retireActionFingerprint(10L,4L,true);
        RelationAction winner=new RelationAction(
            7L,"retire-after-wait","RETIRE_OBJECT",4L,fingerprint,now);
        when(repository.findRelationAction(7L,"retire-after-wait"))
            .thenAnswer(invocation->objectLockAcquired.get()?winner:null);
        when(repository.lockById(10L)).thenAnswer(invocation->{
            objectLockAcquired.set(true);
            return new com.law.file.domain.FileObject(10L,"proof",2,3,"DISABLED",7L,2);
        });
        when(repository.findRelationById(4L)).thenReturn(relation(4L,"DEPARTMENT",3L,0L,false));
        when(repository.findById(10L)).thenReturn(new com.law.file.domain.FileObject(
            10L,"proof",2,3,"DISABLED",7L,2));
        when(repository.findCleanupTasks(10L,7L,"retire-after-wait")).thenReturn(java.util.List.of(
            new CleanupTask(31L,10L,"retire-after-wait","OBJECT","objects/first","COMPLETED",0,
                null,null,now,7L,3L,now)));

        var result=service.retire(10L,
            new FileObjectService.RetireFileObjectCommand("retire-after-wait",4L,true),actor);

        assertTrue(result.objectRetired());
        verifyNoInteractions(access);
        verify(repository,never()).revokeRelation(anyLong());
        verify(repository,never()).disableObject(anyLong());
    }

    @Test void final_relation_cannot_be_revoked_without_the_governed_object_retirement_path()
    {
        FileBusinessRelation relation=relation(4L,"DEPARTMENT",3L,0L,true);
        when(access.requireCanWriteRelation(10L,4L,actor)).thenReturn(relation);
        when(repository.lockActiveRelations(10L)).thenReturn(java.util.List.of(relation));

        FileException error=assertThrows(FileException.class,
            ()->service.revokeRelation(10L,4L,"revoke-final",actor));

        assertEquals("FILE_LAST_RELATION_REQUIRES_RETIREMENT",error.getBusinessCode());
        verify(repository,never()).revokeRelation(anyLong());
        verify(repository,never()).insertRelationAction(any());
    }

    @Test void final_retire_false_is_rejected_without_stranding_an_active_object()
    {
        FileBusinessRelation relation=relation(4L,"DEPARTMENT",3L,0L,true);
        when(access.requireCanWriteRelation(10L,4L,actor)).thenReturn(relation);
        when(repository.lockActiveRelations(10L)).thenReturn(java.util.List.of(relation));

        FileException error=assertThrows(FileException.class,()->service.retire(10L,
            new FileObjectService.RetireFileObjectCommand("retire-final-false",4L,false),actor));

        assertEquals("FILE_LAST_RELATION_REQUIRES_RETIREMENT",error.getBusinessCode());
        verify(repository,never()).revokeRelation(anyLong());
        verify(repository,never()).disableObject(anyLong());
    }

    @Test void revoke_is_action_idempotent_and_writes_lifecycle_audit()
    {
        FileBusinessRelation relation=relation(4L,"DEPARTMENT",3L,0L,true);
        FileBusinessRelation retained=relation(5L,"DEPARTMENT",3L,0L,true);
        when(access.requireCanWriteRelation(10L,4L,actor)).thenReturn(relation);
        when(repository.lockActiveRelations(10L)).thenReturn(java.util.List.of(relation,retained));
        when(repository.revokeRelation(4L)).thenReturn(1);
        when(repository.insertRelationAction(any())).thenReturn(1);
        when(repository.insertLifecycleAudit(any())).thenReturn(1);

        FileBusinessRelation revoked=service.revokeRelation(10L,4L,"revoke-1",actor);

        assertFalse(revoked.active());
        verify(repository).insertRelationAction(argThat(value->value.actionType().equals("REVOKE")
            &&value.relationId().equals(4L)));
        verify(repository).insertLifecycleAudit(argThat(value->value.eventType().equals("RELATION_REVOKED")));

        when(repository.findRelationAction(7L,"revoke-1")).thenReturn(new RelationAction(
            7L,"revoke-1","REVOKE",4L,FileObjectService.relationActionFingerprint("REVOKE",10L,4L),now));
        when(repository.findRelationById(4L)).thenReturn(revoked);
        assertFalse(service.revokeRelation(10L,4L,"revoke-1",actor).active());
        verify(repository,times(1)).revokeRelation(4L);
    }

    @Test void revoke_action_replay_on_a_different_file_path_is_a_non_enumerating_conflict()
    {
        FileBusinessRelation original=relation(4L,"DEPARTMENT",3L,0L,false);
        when(repository.findRelationAction(7L,"revoke-1")).thenReturn(new RelationAction(
            7L,"revoke-1","REVOKE",4L,FileObjectService.relationActionFingerprint("REVOKE",99L,4L),now));
        when(repository.findRelationById(4L)).thenReturn(original);

        FileException error=assertThrows(FileException.class,
            ()->service.revokeRelation(99L,4L,"revoke-1",actor));

        assertEquals("FILE_ACTION_ID_CONFLICT",error.getBusinessCode());
        assertFalse(error.getMessage().contains("10"));
        verify(repository,never()).revokeRelation(anyLong());
    }

    @Test void retire_is_relation_scoped_and_preserves_a_shared_object_and_other_authority()
    {
        FileBusinessRelation own=relation(4L,"DEPARTMENT",3L,0L,true);
        FileBusinessRelation shared=new FileBusinessRelation(
            5L,10L,"CASE",12L,"PROOF","USER",0L,99L,99L,8L,true);
        when(access.requireCanWriteRelation(10L,4L,actor)).thenReturn(own);
        when(repository.lockActiveRelations(10L)).thenReturn(java.util.List.of(own,shared));
        when(repository.revokeRelation(4L)).thenReturn(1);
        when(repository.insertRelationAction(any())).thenReturn(1);
        when(repository.insertLifecycleAudit(any())).thenReturn(1);

        var result=service.retire(10L,
            new FileObjectService.RetireFileObjectCommand("retire-shared",4L,true),actor);

        assertFalse(result.relationActive());
        assertFalse(result.objectRetired());
        assertEquals("ACTIVE",result.status());
        verify(repository).revokeRelation(4L);
        verify(repository,never()).revokeRelation(5L);
        verify(repository,never()).disableObject(anyLong());
        verify(repository,never()).insertCleanupTask(any());
        verifyNoInteractions(storage);
    }

    @Test void owner_can_retire_the_last_relation_and_all_versions_only_after_commit()
    {
        FileBusinessRelation own=relation(4L,"DEPARTMENT",3L,0L,true);
        when(access.requireCanWriteRelation(10L,4L,actor)).thenReturn(own);
        when(repository.lockActiveRelations(10L)).thenReturn(java.util.List.of(own));
        when(repository.lockById(10L)).thenReturn(new com.law.file.domain.FileObject(
            10L,"proof",2,3,"ACTIVE",7L,1));
        when(repository.findStoredVersions(10L)).thenReturn(java.util.List.of(
            new StoredVersion(version(21L,"first"),"objects/first"),
            new StoredVersion(version(22L,"second"),"objects/second")));
        when(repository.revokeRelation(4L)).thenReturn(1);
        when(repository.insertRelationAction(any())).thenReturn(1);
        when(repository.disableObject(10L)).thenReturn(1);
        when(repository.insertLifecycleAudit(any())).thenReturn(1);
        when(repository.insertCleanupTask(any())).thenAnswer(invocation->{
            CleanupTask task=invocation.getArgument(0);
            long id=task.targetKey().endsWith("first")?31L:32L;
            return new CleanupTask(id,task.fileObjectId(),task.actionId(),task.targetType(),task.targetKey(),
                task.status(),task.retryCount(),null,null,task.nextRetryAt(),task.actorId(),task.actorDeptId(),
                task.createdAt());
        });

        TransactionSynchronizationManager.initSynchronization();
        try
        {
            var result=service.retire(10L,
                new FileObjectService.RetireFileObjectCommand("retire-last",4L,true),actor);

            assertTrue(result.objectRetired());
            assertEquals(java.util.List.of(31L,32L),result.cleanupTaskIds());
            verifyNoInteractions(storage);
            for(TransactionSynchronization synchronization:TransactionSynchronizationManager.getSynchronizations())
                synchronization.afterCommit();
            verify(storage).delete("objects/first");
            verify(storage).delete("objects/second");
            verify(cleanupAudit).recordCleanupSuccess(31L,"OBJECT_RETIRED",actor);
            verify(cleanupAudit).recordCleanupSuccess(32L,"OBJECT_RETIRED",actor);
        }
        finally
        {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test void non_owner_cannot_retire_the_last_object_and_no_relation_is_mutated()
    {
        FileBusinessRelation own=relation(4L,"DEPARTMENT",3L,0L,true);
        when(access.requireCanWriteRelation(10L,4L,actor)).thenReturn(own);
        when(repository.lockActiveRelations(10L)).thenReturn(java.util.List.of(own));
        when(repository.lockById(10L)).thenReturn(new com.law.file.domain.FileObject(
            10L,"proof",1,2,"ACTIVE",99L,1));

        assertThrows(com.law.file.security.FileAccessDeniedException.class,()->service.retire(10L,
            new FileObjectService.RetireFileObjectCommand("retire-foreign",4L,true),actor));

        verify(repository,never()).revokeRelation(anyLong());
        verify(repository,never()).disableObject(anyLong());
        verifyNoInteractions(storage);
    }

    @Test void cleanup_metadata_failure_never_touches_physical_storage()
    {
        FileBusinessRelation own=relation(4L,"DEPARTMENT",3L,0L,true);
        when(access.requireCanWriteRelation(10L,4L,actor)).thenReturn(own);
        when(repository.lockActiveRelations(10L)).thenReturn(java.util.List.of(own));
        when(repository.lockById(10L)).thenReturn(new com.law.file.domain.FileObject(
            10L,"proof",1,2,"ACTIVE",7L,1));
        when(repository.findStoredVersions(10L)).thenReturn(java.util.List.of(
            new StoredVersion(version(21L,"first"),"objects/first")));
        when(repository.insertRelationAction(any())).thenReturn(1);
        when(repository.revokeRelation(4L)).thenReturn(1);
        when(repository.disableObject(10L)).thenReturn(1);
        when(repository.insertLifecycleAudit(any())).thenReturn(1);
        when(repository.insertCleanupTask(any())).thenReturn(null);

        assertThrows(FileException.class,()->service.retire(10L,
            new FileObjectService.RetireFileObjectCommand("retire-fails",4L,true),actor));

        verifyNoInteractions(storage);
        verify(cleanupAudit,never()).recordCleanupSuccess(anyLong(),anyString(),any());
    }

    @Test void retire_replay_returns_the_durable_result_without_duplicate_metadata_or_cleanup()
    {
        String fingerprint=FileObjectService.retireActionFingerprint(10L,4L,true);
        when(repository.findRelationAction(7L,"retire-replay")).thenReturn(new RelationAction(
            7L,"retire-replay","RETIRE_OBJECT",4L,fingerprint,now));
        when(repository.findRelationById(4L)).thenReturn(relation(4L,"DEPARTMENT",3L,0L,false));
        when(repository.findById(10L)).thenReturn(new com.law.file.domain.FileObject(
            10L,"proof",2,3,"DISABLED",7L,2));
        when(repository.findCleanupTasks(10L,7L,"retire-replay")).thenReturn(java.util.List.of(
            new CleanupTask(31L,10L,"retire-replay","OBJECT","objects/first","COMPLETED",0,null,null,
                now,7L,3L,now),
            new CleanupTask(32L,10L,"retire-replay","OBJECT","objects/second","COMPLETED",0,null,null,
                now,7L,3L,now)));

        var result=service.retire(10L,
            new FileObjectService.RetireFileObjectCommand("retire-replay",4L,true),actor);

        assertTrue(result.objectRetired());
        assertEquals(java.util.List.of(31L,32L),result.cleanupTaskIds());
        verify(repository,never()).revokeRelation(anyLong());
        verify(repository,never()).disableObject(anyLong());
        verify(repository,never()).insertCleanupTask(any());
        verifyNoInteractions(storage);
    }

    @Test void concurrent_action_claim_replays_the_winner_without_revoking_twice()
    {
        FileBusinessRelation active=relation(4L,"DEPARTMENT",3L,0L,true);
        FileBusinessRelation retained=relation(5L,"DEPARTMENT",3L,0L,true);
        FileBusinessRelation retired=relation(4L,"DEPARTMENT",3L,0L,false);
        String fingerprint=FileObjectService.retireActionFingerprint(10L,4L,false);
        RelationAction winner=new RelationAction(
            7L,"retire-race","RETIRE_RELATION",4L,fingerprint,now);
        when(repository.findRelationAction(7L,"retire-race")).thenReturn(null);
        when(repository.lockRelationAction(7L,"retire-race")).thenReturn(winner);
        when(access.requireCanWriteRelation(10L,4L,actor)).thenReturn(active);
        when(repository.lockActiveRelations(10L)).thenReturn(java.util.List.of(active,retained));
        when(repository.insertRelationAction(any())).thenReturn(0);
        when(repository.findRelationById(4L)).thenReturn(retired);
        when(repository.findById(10L)).thenReturn(new com.law.file.domain.FileObject(
            10L,"proof",1,2,"ACTIVE",7L,1));

        var result=service.retire(10L,
            new FileObjectService.RetireFileObjectCommand("retire-race",4L,false),actor);

        assertFalse(result.relationActive());
        assertFalse(result.objectRetired());
        verify(repository,never()).revokeRelation(anyLong());
        verify(repository,never()).insertCleanupTask(any());
        verifyNoInteractions(storage);
    }

    @Test void token_is_bound_to_one_relation_and_redemption_rechecks_that_relation()
    {
        FileBusinessRelation relation=relation(4L,"DEPARTMENT",3L,0L,true);
        FileVersion metadata=version(21L,"first");
        StoredVersion stored=new StoredVersion(metadata,"objects/internal");
        when(access.requireCanReadRelation(10L,4L,actor)).thenReturn(relation);
        when(repository.findCurrentVersion(10L)).thenReturn(stored);
        when(repository.insertAccessToken(any())).thenAnswer(invocation->{
            AccessToken value=invocation.getArgument(0);
            return new AccessToken(31L,value.fileObjectId(),value.fileVersionId(),value.relationId(),
                value.accessType(),value.tokenHash(),value.actorId(),value.actorDeptId(),value.expiresAt(),null);
        });
        when(repository.findAccessTokenForUpdate(anyString())).thenReturn(new AccessToken(
            31L,10L,21L,4L,"DOWNLOAD","hash",7L,3L,now.plusSeconds(60),null));
        when(repository.findVersionById(21L)).thenReturn(stored);
        when(storage.read("objects/internal")).thenReturn(new ByteArrayInputStream("abc".getBytes()));
        when(repository.consumeAccessToken(31L,now)).thenReturn(1);
        when(repository.insertAccessLog(any())).thenReturn(1);

        var issued=service.issueAccessToken(10L,4L,"DOWNLOAD",actor);
        var content=service.open(issued.token(),actor,"127.0.0.1");

        assertEquals(4L,content.receipt().relationId());
        verify(access,times(2)).requireCanReadRelation(10L,4L,actor);
        verify(repository).insertAccessLog(argThat(value->value.eventType().equals("ACCESS_OPEN")
            &&value.relationId().equals(4L)));
    }

    @Test void database_failure_aborts_staged_content_and_writes_cleanup_audit()
    {
        UploadIntent intent=intent("REGISTERED",now.plusSeconds(60),null);
        FileStoragePort.StagedObject staged=new FileStoragePort.StagedObject("staged/1",3L,HASH);
        when(repository.findUploadIntentForUpdate("intent-1")).thenReturn(intent);
        when(storage.stage(any(),eq(3L),eq(HASH))).thenReturn(staged);
        when(storage.inspect(staged)).thenReturn(DetectedContentType.PDF);
        when(repository.insertVersion(any())).thenReturn(null);
        when(cleanupAudit.beginCleanup(10L,"idem-1","STAGED","staged/1",actor)).thenReturn(31L);

        assertThrows(FileException.class,
            ()->service.completeUpload("intent-1",new ByteArrayInputStream("abc".getBytes()),
                "proof.pdf","application/pdf",actor));

        verify(storage).abort(staged);
        verify(cleanupAudit).recordCleanupSuccess(31L,"UPLOAD_ABORTED",actor);
        verify(storage,never()).publish(any(),anyString());
    }

    @Test void transactional_database_failure_aborts_staged_content_after_rollback()
    {
        UploadIntent intent=intent("REGISTERED",now.plusSeconds(60),null);
        FileStoragePort.StagedObject staged=new FileStoragePort.StagedObject("staged/1",3L,HASH);
        when(repository.findUploadIntentForUpdate("intent-1")).thenReturn(intent);
        when(storage.stage(any(),eq(3L),eq(HASH))).thenReturn(staged);
        when(storage.inspect(staged)).thenReturn(DetectedContentType.PDF);
        when(repository.insertVersion(any())).thenReturn(null);
        when(cleanupAudit.beginCleanup(10L,"idem-1","STAGED","staged/1",actor)).thenReturn(32L);
        TransactionSynchronizationManager.initSynchronization();
        try
        {
            assertThrows(FileException.class,
                ()->service.completeUpload("intent-1",new ByteArrayInputStream("abc".getBytes()),
                    "proof.pdf","application/pdf",actor));

            for(TransactionSynchronization synchronization:TransactionSynchronizationManager.getSynchronizations())
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

            verify(storage).abort(staged);
            verify(storage,never()).delete(anyString());
            verify(cleanupAudit).recordCleanupSuccess(32L,"UPLOAD_ABORTED",actor);
        }
        finally
        {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test void cleanup_storage_failure_is_persisted_as_failed_instead_of_upload_aborted()
    {
        UploadIntent intent=intent("REGISTERED",now.plusSeconds(60),null);
        FileStoragePort.StagedObject staged=new FileStoragePort.StagedObject("staged/1",3L,HASH);
        when(repository.findUploadIntentForUpdate("intent-1")).thenReturn(intent);
        when(storage.stage(any(),eq(3L),eq(HASH))).thenReturn(staged);
        when(storage.inspect(staged)).thenReturn(DetectedContentType.PDF);
        when(repository.insertVersion(any())).thenReturn(null);
        when(cleanupAudit.beginCleanup(10L,"idem-1","STAGED","staged/1",actor)).thenReturn(33L);
        doThrow(new FileException("FILE_STORAGE_ABORT_FAILED","Unable to discard staged file"))
            .when(storage).abort(staged);

        assertThrows(FileException.class,
            ()->service.completeUpload("intent-1",new ByteArrayInputStream("abc".getBytes()),
                "proof.pdf","application/pdf",actor));

        verify(cleanupAudit).recordCleanupFailure(33L,"FILE_STORAGE_ABORT_FAILED",
            "Unable to discard staged file",actor);
        verify(cleanupAudit,never()).recordCleanupSuccess(anyLong(),eq("UPLOAD_ABORTED"),any());
    }

    private UploadIntent intent(String status,Instant expiresAt,Long completedVersionId)
    {
        return new UploadIntent("intent-1","idem-1",10L,1,"objects/key","proof.pdf","application/pdf",3L,HASH,
            "first","fingerprint",7L,status,completedVersionId,expiresAt);
    }
    private FileVersion version(Long id,String description)
    {return new FileVersion(id,10L,1,"proof.pdf","application/pdf",3L,HASH,description,7L,now);}
    private FileBusinessRelation relation(Long id,String visibility,Long scopeDeptId,Long scopeUserId,boolean active)
    {return new FileBusinessRelation(id,10L,"CASE",9L,"PROOF",visibility,scopeDeptId,scopeUserId,7L,3L,active);}
    private static final String HASH="ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
}
