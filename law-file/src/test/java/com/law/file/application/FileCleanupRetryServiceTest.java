package com.law.file.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import com.law.file.domain.FileException;
import com.law.file.infrastructure.internal.FilePersistenceModel.CleanupTask;
import com.law.file.repository.FileObjectRepository;
import com.law.file.spi.FileStoragePort;
import org.junit.jupiter.api.Test;

class FileCleanupRetryServiceTest
{
    private static final Instant NOW=Instant.parse("2026-07-17T01:00:00Z");

    @Test void retry_hook_completes_a_failed_staged_cleanup()
    {
        FileObjectRepository repository=mock(FileObjectRepository.class);
        FileStoragePort storage=mock(FileStoragePort.class);
        CleanupTask task=task(41L,"STAGED",".staged/41");
        when(repository.findRetryableCleanupTasks(NOW,10)).thenReturn(List.of(task));
        when(repository.completeCleanupTask(41L,NOW)).thenReturn(1);
        when(repository.insertLifecycleAudit(any())).thenReturn(1);

        int completed=new FileCleanupRetryService(repository,storage,Clock.fixed(NOW,ZoneOffset.UTC)).retryFailed(10);

        assertEquals(1,completed);
        verify(storage).abort(new FileStoragePort.StagedObject(".staged/41",0L,null));
        verify(repository).completeCleanupTask(41L,NOW);
        verify(repository).insertLifecycleAudit(argThat(value->value.eventType().equals("CLEANUP_RETRY_SUCCEEDED")));
    }

    @Test void retry_hook_keeps_failure_retryable_with_error_details()
    {
        FileObjectRepository repository=mock(FileObjectRepository.class);
        FileStoragePort storage=mock(FileStoragePort.class);
        CleanupTask task=task(42L,"OBJECT","objects/42");
        when(repository.findRetryableCleanupTasks(NOW,10)).thenReturn(List.of(task));
        when(repository.failCleanupTask(eq(42L),eq("FILE_STORAGE_DELETE_FAILED"),
            eq("Unable to delete stored file"),eq(NOW.plusSeconds(300)))).thenReturn(1);
        when(repository.insertLifecycleAudit(any())).thenReturn(1);
        doThrow(new FileException("FILE_STORAGE_DELETE_FAILED","Unable to delete stored file"))
            .when(storage).delete("objects/42");

        int completed=new FileCleanupRetryService(repository,storage,Clock.fixed(NOW,ZoneOffset.UTC)).retryFailed(10);

        assertEquals(0,completed);
        verify(repository).failCleanupTask(42L,"FILE_STORAGE_DELETE_FAILED","Unable to delete stored file",
            NOW.plusSeconds(300));
        verify(repository).insertLifecycleAudit(argThat(value->value.eventType().equals("CLEANUP_RETRY_FAILED")
            &&value.details().contains("FILE_STORAGE_DELETE_FAILED")));
    }

    private static CleanupTask task(Long id,String targetType,String targetKey)
    {
        return new CleanupTask(id,10L,"idem-1",targetType,targetKey,"FAILED",1,"old","old",
            NOW,7L,3L,NOW.minusSeconds(60));
    }
}
