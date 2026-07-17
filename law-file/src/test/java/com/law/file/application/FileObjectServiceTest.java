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
import com.law.file.domain.FileObject.FileUploadIntent;
import com.law.file.domain.FileObject.FileVersion;
import com.law.file.repository.FileObjectRepository;
import com.law.file.security.FileAccessDeniedException;
import com.law.file.security.FileAccessPolicy;
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
    private FileObjectService service;
    private final FileActor actor=new FileActor(7L,"alice",3L);
    private final Clock clock=Clock.fixed(Instant.parse("2026-07-17T01:00:00Z"),ZoneOffset.UTC);

    @BeforeEach void setUp(){service=new FileObjectService(repository,storage,access,clock,bytes->"token-fixed");}

    @Test void register_upload_authorizes_business_before_writing_metadata()
    {
        var command=new FileObjectService.RegisterUploadCommand("idem-1","proof.pdf","application/pdf",3L,
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad","CASE",9L,"CONTACT_PROOF","BUSINESS");
        doThrow(new FileAccessDeniedException("denied")).when(access).requireCanWrite("CASE",9L,actor);
        assertThrows(FileAccessDeniedException.class,()->service.registerUpload(command,actor));
        verifyNoInteractions(repository,storage);
    }

    @Test void repeated_registration_returns_same_intent_when_fingerprint_matches()
    {
        var command=new FileObjectService.RegisterUploadCommand("idem-1","proof.pdf","application/pdf",3L,
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad","CASE",9L,"CONTACT_PROOF","BUSINESS");
        FileUploadIntent existing=intent("intent-1",10L,1,FileObjectService.requestFingerprint(command));
        when(repository.findUploadIntentByIdempotency(7L,"idem-1")).thenReturn(existing);
        var result=service.registerUpload(command,actor);
        assertEquals("intent-1",result.uploadIntentId());
        assertEquals(10L,result.fileObjectId());
        verify(repository,never()).insertFileObject(any());
    }

    @Test void completing_intent_stores_verified_content_and_creates_immutable_version()
    {
        FileUploadIntent intent=intent("intent-1",10L,1,"fingerprint");
        when(repository.findUploadIntentForUpdate("intent-1")).thenReturn(intent);
        when(storage.store(any(),eq(intent.objectKey()),eq(3L),eq(intent.expectedSha256())))
            .thenReturn(new FileStoragePort.StoredObject(intent.objectKey(),3L,intent.expectedSha256()));
        when(repository.insertVersion(any())).thenAnswer(invocation->{FileVersion v=invocation.getArgument(0);return new FileVersion(21L,v.fileObjectId(),v.versionNo(),v.objectKey(),v.originalFileName(),v.contentType(),v.sizeBytes(),v.sha256(),v.createdBy(),v.createdAt());});
        when(repository.activateVersion(10L,1,0)).thenReturn(1);
        when(repository.markUploadCompleted("intent-1",21L)).thenReturn(1);
        FileVersion result=service.completeUpload("intent-1",new ByteArrayInputStream("abc".getBytes()),actor);
        assertEquals(1,result.versionNo());
        assertEquals(3L,result.sizeBytes());
        verify(repository).activateVersion(10L,1,0);
    }

    @Test void same_logical_file_can_reserve_and_receive_a_new_version()
    {
        FileObject object=new FileObject(10L,"proof.pdf",1,2,"ACTIVE",7L,0);
        when(access.requireCanWrite(10L,actor)).thenReturn(List.of(relation()));
        when(repository.findById(10L)).thenReturn(object);
        when(repository.reserveNextVersion(10L,2,0)).thenReturn(1);
        when(repository.insertUploadIntent(any())).thenReturn(1);
        var command=new FileObjectService.RegisterVersionCommand("idem-2","proof-v2.pdf","application/pdf",3L,
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        var registered=service.addVersion(10L,command,actor);
        assertEquals(2,registered.versionNo());
    }

    @Test void denied_download_does_not_read_storage_or_write_audit()
    {
        doThrow(new FileAccessDeniedException("denied")).when(access).requireCanRead(10L,actor);
        assertThrows(FileAccessDeniedException.class,()->service.issueAccessToken(10L,"DOWNLOAD",actor));
        verify(storage,never()).read(anyString());
        verify(repository,never()).insertAccessLog(any());
    }

    @Test void successful_token_redemption_reauthorizes_reads_and_then_logs_access() throws Exception
    {
        when(access.requireCanRead(10L,actor)).thenReturn(List.of(relation()));
        FileVersion version=new FileVersion(21L,10L,1,"objects/key","proof.pdf","application/pdf",3L,"hash",7L,null);
        when(repository.findCurrentVersion(10L)).thenReturn(version);
        when(repository.findVersionById(21L)).thenReturn(version);
        when(repository.insertAccessToken(any())).thenAnswer(invocation->{FileObject.AccessToken t=invocation.getArgument(0);return new FileObject.AccessToken(31L,t.fileObjectId(),t.fileVersionId(),t.accessType(),t.tokenHash(),t.actorId(),t.actorDeptId(),t.expiresAt(),null);});
        when(repository.findAccessTokenForUpdate(anyString())).thenAnswer(invocation ->
            new FileObject.AccessToken(31L,10L,21L,"DOWNLOAD","hash",7L,3L,clock.instant().plusSeconds(60),null));
        when(storage.read("objects/key")).thenReturn(new ByteArrayInputStream("abc".getBytes()));
        when(repository.consumeAccessToken(31L,clock.instant())).thenReturn(1);
        when(repository.insertAccessLog(any())).thenReturn(1);
        var token=service.issueAccessToken(10L,"DOWNLOAD",actor);
        var content=service.open(token.token(),actor);
        assertEquals("proof.pdf",content.fileName());
        assertArrayEquals("abc".getBytes(),content.input().readAllBytes());
        verify(access,times(2)).requireCanRead(10L,actor);
        verify(repository).insertAccessLog(argThat(row->row.fileObjectId().equals(10L)&&row.actorId().equals(7L)));
    }

    private FileUploadIntent intent(String id,Long fileId,int version,String fingerprint)
    {
        return new FileUploadIntent(id,"idem-1",fileId,version,"objects/key","proof.pdf","application/pdf",3L,
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",fingerprint,7L,"REGISTERED",null);
    }
    private FileBusinessRelation relation(){return new FileBusinessRelation(1L,"rel-1",10L,"CASE",9L,"CONTACT_PROOF","BUSINESS",7L,3L,true);}
}
