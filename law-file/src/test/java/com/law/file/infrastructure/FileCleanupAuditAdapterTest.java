package com.law.file.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.law.file.domain.FileException;
import com.law.file.domain.FileObject.FileActor;
import com.law.file.infrastructure.internal.FilePersistenceModel.CleanupTask;
import com.law.file.repository.FileObjectRepository;
import org.junit.jupiter.api.Test;

class FileCleanupAuditAdapterTest
{
    private static final Instant NOW=Instant.parse("2026-07-17T01:00:00Z");
    private static final FileActor ACTOR=new FileActor(7L,"alice",3L);

    @Test void pending_cleanup_gets_a_future_retry_lease()
    {
        FileObjectRepository repository=mock(FileObjectRepository.class);
        when(repository.insertCleanupTask(any())).thenAnswer(invocation->{
            CleanupTask value=invocation.getArgument(0);
            return new CleanupTask(31L,value.fileObjectId(),value.actionId(),value.targetType(),value.targetKey(),
                value.status(),value.retryCount(),value.lastErrorCode(),value.lastErrorMessage(),value.nextRetryAt(),
                value.actorId(),value.actorDeptId(),value.createdAt());
        });

        Long id=new FileCleanupAuditAdapter(repository,Clock.fixed(NOW,ZoneOffset.UTC))
            .beginCleanup(10L,"idem-1","STAGED",".staged/1",ACTOR);

        assertEquals(31L,id);
        verify(repository).insertCleanupTask(argThat(value->value.status().equals("PENDING")
            &&value.nextRetryAt().equals(NOW.plusSeconds(60))));
    }

    @Test void successful_cleanup_rejects_a_silent_lifecycle_audit_failure()
    {
        FileObjectRepository repository=mock(FileObjectRepository.class);
        when(repository.findCleanupTaskById(31L)).thenReturn(task());
        when(repository.completeCleanupTask(31L,NOW)).thenReturn(1);
        when(repository.insertLifecycleAudit(any())).thenReturn(0);
        var adapter=new FileCleanupAuditAdapter(repository,Clock.fixed(NOW,ZoneOffset.UTC));

        FileException error=assertThrows(FileException.class,
            ()->adapter.recordCleanupSuccess(31L,"UPLOAD_ABORTED",ACTOR));

        assertEquals("FILE_CLEANUP_AUDIT_FAILED",error.getBusinessCode());
    }

    @Test void failed_cleanup_rejects_a_silent_lifecycle_audit_failure()
    {
        FileObjectRepository repository=mock(FileObjectRepository.class);
        when(repository.findCleanupTaskById(31L)).thenReturn(task());
        when(repository.failCleanupTask(31L,"DELETE_FAILED","failed",NOW.plusSeconds(300))).thenReturn(1);
        when(repository.insertLifecycleAudit(any())).thenReturn(0);
        var adapter=new FileCleanupAuditAdapter(repository,Clock.fixed(NOW,ZoneOffset.UTC));

        FileException error=assertThrows(FileException.class,
            ()->adapter.recordCleanupFailure(31L,"DELETE_FAILED","failed",ACTOR));

        assertEquals("FILE_CLEANUP_AUDIT_FAILED",error.getBusinessCode());
    }

    private static CleanupTask task()
    {return new CleanupTask(31L,10L,"idem-1","STAGED",".staged/1","PENDING",0,null,null,NOW.plusSeconds(60),7L,3L,NOW);}
}
