package com.law.file.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;

import com.law.file.domain.FileException;
import com.law.file.domain.FileObject.AccessToken;
import com.law.file.domain.FileObject.FileActor;
import com.law.file.domain.FileObject.FileBusinessRelation;
import com.law.file.domain.FileObject.FileVersion;
import com.law.file.infrastructure.internal.FilePersistenceModel.RelationAction;
import com.law.file.infrastructure.internal.FilePersistenceModel.StoredVersion;
import com.law.file.infrastructure.internal.FilePersistenceModel.UploadIntent;
import com.law.file.repository.FileObjectRepository;
import com.law.file.security.FileAccessPolicy;
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
    {service=new FileObjectService(repository,storage,access,cleanupAudit,Clock.fixed(now,ZoneOffset.UTC),bytes->"token-fixed");}

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
            ()->service.completeUpload("intent-1",new ByteArrayInputStream("abc".getBytes()),actor));

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
            ()->service.completeUpload("missing",new ByteArrayInputStream(new byte[0]),actor));
        FileException foreign=assertThrows(FileException.class,
            ()->service.completeUpload("foreign",new ByteArrayInputStream(new byte[0]),actor));

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

        assertEquals(21L,service.completeUpload("intent-1",new ByteArrayInputStream(new byte[0]),actor).fileVersionId());
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

    @Test void revoke_is_action_idempotent_and_writes_lifecycle_audit()
    {
        FileBusinessRelation relation=relation(4L,"DEPARTMENT",3L,0L,true);
        when(access.requireCanWriteRelation(10L,4L,actor)).thenReturn(relation);
        when(repository.revokeRelation(4L)).thenReturn(1);
        when(repository.insertRelationAction(any())).thenReturn(1);
        when(repository.insertLifecycleAudit(any())).thenReturn(1);

        FileBusinessRelation revoked=service.revokeRelation(10L,4L,"revoke-1",actor);

        assertFalse(revoked.active());
        verify(repository).insertRelationAction(argThat(value->value.actionType().equals("REVOKE")
            &&value.relationId().equals(4L)));
        verify(repository).insertLifecycleAudit(argThat(value->value.eventType().equals("RELATION_REVOKED")));

        when(repository.findRelationAction(7L,"revoke-1")).thenReturn(new RelationAction(
            7L,"revoke-1","REVOKE",4L,FileObjectService.relationActionFingerprint("REVOKE",4L),now));
        when(repository.findRelationById(4L)).thenReturn(revoked);
        assertFalse(service.revokeRelation(10L,4L,"revoke-1",actor).active());
        verify(repository,times(1)).revokeRelation(4L);
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
        when(repository.insertVersion(any())).thenReturn(null);

        assertThrows(FileException.class,
            ()->service.completeUpload("intent-1",new ByteArrayInputStream("abc".getBytes()),actor));

        verify(storage).abort(staged);
        verify(cleanupAudit).recordCleanup(10L,"idem-1","UPLOAD_ABORTED",actor);
        verify(storage,never()).publish(any(),anyString());
    }

    @Test void transactional_database_failure_aborts_staged_content_after_rollback()
    {
        UploadIntent intent=intent("REGISTERED",now.plusSeconds(60),null);
        FileStoragePort.StagedObject staged=new FileStoragePort.StagedObject("staged/1",3L,HASH);
        when(repository.findUploadIntentForUpdate("intent-1")).thenReturn(intent);
        when(storage.stage(any(),eq(3L),eq(HASH))).thenReturn(staged);
        when(repository.insertVersion(any())).thenReturn(null);
        TransactionSynchronizationManager.initSynchronization();
        try
        {
            assertThrows(FileException.class,
                ()->service.completeUpload("intent-1",new ByteArrayInputStream("abc".getBytes()),actor));

            for(TransactionSynchronization synchronization:TransactionSynchronizationManager.getSynchronizations())
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

            verify(storage).abort(staged);
            verify(storage,never()).delete(anyString());
            verify(cleanupAudit).recordCleanup(10L,"idem-1","UPLOAD_ABORTED",actor);
        }
        finally
        {
            TransactionSynchronizationManager.clearSynchronization();
        }
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
