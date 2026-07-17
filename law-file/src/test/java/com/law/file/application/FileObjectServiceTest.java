package com.law.file.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import com.law.file.domain.FileObject;
import com.law.file.domain.FileObject.FileActor;
import com.law.file.domain.FileObject.FileBusinessRelation;
import com.law.file.domain.FileObject.FileVersion;
import com.law.file.infrastructure.internal.FilePersistenceModel.StoredVersion;
import com.law.file.infrastructure.internal.FilePersistenceModel.UploadIntent;
import com.law.file.repository.FileObjectRepository;
import com.law.file.security.FileAccessDeniedException;
import com.law.file.security.FileAccessPolicy;
import com.law.file.spi.FileCleanupAuditPort;
import com.law.file.spi.FileStoragePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileObjectServiceTest
{
    @Mock FileObjectRepository repository;
    @Mock FileStoragePort storage;
    @Mock FileAccessPolicy access;
    @Mock FileCleanupAuditPort cleanup;
    private FileObjectService service;
    private final FileActor actor=new FileActor(7L,"alice",3L);
    private final Instant now=Instant.parse("2026-07-17T01:00:00Z");
    private final Clock clock=Clock.fixed(now,ZoneOffset.UTC);

    @BeforeEach void setUp(){service=new FileObjectService(repository,storage,access,cleanup,clock,bytes->"token-fixed");}

    @Test void register_upload_authorizes_business_before_writing_metadata()
    {
        var command=upload();
        doThrow(new FileAccessDeniedException("denied")).when(access).requireCanWrite("CASE",9L,actor);
        assertThrows(FileAccessDeniedException.class,()->service.registerUpload(command,actor));
        verifyNoInteractions(repository,storage);
    }

    @Test void repeated_registration_returns_same_unexpired_intent_when_fingerprint_matches()
    {
        var command=upload();
        UploadIntent existing=intent("REGISTERED",null);
        when(repository.findUploadIntentByIdempotency(7L,"idem-1")).thenReturn(existing);
        var result=service.registerUpload(command,actor);
        assertEquals("intent-1",result.uploadIntentId());
        assertEquals(10L,result.fileObjectId());
        verify(repository,never()).insertFileObject(any());
    }

    @Test void same_logical_file_reserves_version_with_change_description()
    {
        FileObject object=new FileObject(10L,"proof.pdf",1,2,"ACTIVE",7L,0);
        when(access.requireCanWrite(10L,actor)).thenReturn(List.of(relation()));
        when(repository.findById(10L)).thenReturn(object);
        when(repository.reserveNextVersion(10L,2,0)).thenReturn(1);
        when(repository.insertUploadIntent(any())).thenReturn(1);
        var command=new FileObjectService.RegisterVersionCommand("idem-2","proof-v2.pdf","application/pdf",3L,HASH,"Added signature");
        var registered=service.addVersion(10L,command,actor);
        assertEquals(2,registered.versionNo());
        verify(repository).insertUploadIntent(argThat(value->"Added signature".equals(value.changeDescription())));
    }

    @Test void completing_intent_publishes_only_after_database_version_is_ready()
    {
        UploadIntent intent=intent("REGISTERED",null);
        FileStoragePort.StagedObject staged=new FileStoragePort.StagedObject(".staged/1",3L,HASH);
        when(repository.findUploadIntentForUpdate("intent-1")).thenReturn(intent);
        when(storage.stage(any(),eq(3L),eq(HASH))).thenReturn(staged);
        when(repository.insertVersion(any())).thenAnswer(invocation->{
            StoredVersion value=invocation.getArgument(0);FileVersion v=value.metadata();
            return new StoredVersion(new FileVersion(21L,v.fileObjectId(),v.versionNo(),v.originalFileName(),v.contentType(),
                v.sizeBytes(),v.sha256(),v.changeDescription(),v.createdBy(),v.createdAt()),value.objectKey());
        });
        when(repository.activateVersion(10L,1,0)).thenReturn(1);
        when(repository.markUploadCompleted("intent-1",21L)).thenReturn(1);
        when(repository.insertLifecycleAudit(any())).thenReturn(1);
        when(storage.publish(staged,"objects/key")).thenReturn(new FileStoragePort.StoredObject("objects/key",3L,HASH));

        FileVersion result=service.completeUpload("intent-1",new ByteArrayInputStream("abc".getBytes()),actor);

        assertEquals("Initial version",result.changeDescription());
        var order=inOrder(repository,storage);
        order.verify(repository).markUploadCompleted("intent-1",21L);
        order.verify(storage).publish(staged,"objects/key");
    }

    @Test void denied_download_does_not_create_token()
    {
        doThrow(new FileAccessDeniedException("denied")).when(access).requireCanReadRelation(10L,4L,actor);
        assertThrows(FileAccessDeniedException.class,()->service.issueAccessToken(10L,4L,"DOWNLOAD",actor));
        verify(repository,never()).insertAccessToken(any());
    }

    private FileObjectService.RegisterUploadCommand upload()
    {return new FileObjectService.RegisterUploadCommand("idem-1","proof.pdf","application/pdf",3L,HASH,"CASE",9L,"CONTACT_PROOF","BUSINESS");}
    private UploadIntent intent(String status,Long completed)
    {return new UploadIntent("intent-1","idem-1",10L,1,"objects/key","proof.pdf","application/pdf",3L,HASH,
        "Initial version",FileObjectService.requestFingerprint(upload()),7L,status,completed,now.plusSeconds(60));}
    private FileBusinessRelation relation()
    {return new FileBusinessRelation(1L,10L,"CASE",9L,"CONTACT_PROOF","BUSINESS",0L,0L,7L,3L,true);}
    private static final String HASH="ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
}
