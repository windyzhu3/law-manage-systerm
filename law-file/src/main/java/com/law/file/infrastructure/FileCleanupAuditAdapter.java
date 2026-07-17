package com.law.file.infrastructure;

import java.time.Clock;

import com.law.file.domain.FileObject.FileActor;
import com.law.file.domain.FileObject.LifecycleAudit;
import com.law.file.domain.FileException;
import com.law.file.infrastructure.internal.FilePersistenceModel.CleanupTask;
import com.law.file.repository.FileObjectRepository;
import com.law.file.spi.FileCleanupAuditPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class FileCleanupAuditAdapter implements FileCleanupAuditPort
{
    private final FileObjectRepository repository;
    private final Clock clock=Clock.systemUTC();
    public FileCleanupAuditAdapter(FileObjectRepository repository){this.repository=repository;}
    @Override @Transactional(propagation=Propagation.REQUIRES_NEW)
    public Long beginCleanup(Long fileObjectId,String actionId,String targetType,String targetKey,FileActor actor)
    {
        CleanupTask task=repository.insertCleanupTask(new CleanupTask(null,fileObjectId,actionId,targetType,targetKey,
            "PENDING",0,null,null,clock.instant(),actor.userId(),actor.deptId(),clock.instant()));
        if(task==null||task.cleanupTaskId()==null)throw new FileException("FILE_CLEANUP_AUDIT_FAILED","Unable to persist cleanup task");
        return task.cleanupTaskId();
    }
    @Override @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void recordCleanupSuccess(Long cleanupTaskId,String reason,FileActor actor)
    {
        CleanupTask task=repository.findCleanupTaskById(cleanupTaskId);
        if(task==null)throw new FileException("FILE_CLEANUP_AUDIT_FAILED","Cleanup task is unavailable");
        if(repository.completeCleanupTask(cleanupTaskId,clock.instant())!=1)
            throw new FileException("FILE_CLEANUP_AUDIT_FAILED","Unable to complete cleanup task");
        repository.insertLifecycleAudit(new LifecycleAudit(null,task.fileObjectId(),null,null,task.actionId(),
            "STORAGE_CLEANUP",reason,actor.userId(),actor.deptId(),clock.instant()));
    }
    @Override @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void recordCleanupFailure(Long cleanupTaskId,String errorCode,String errorMessage,FileActor actor)
    {
        CleanupTask task=repository.findCleanupTaskById(cleanupTaskId);
        if(task==null)throw new FileException("FILE_CLEANUP_AUDIT_FAILED","Cleanup task is unavailable");
        if(repository.failCleanupTask(cleanupTaskId,errorCode,errorMessage,clock.instant().plusSeconds(300))!=1)
            throw new FileException("FILE_CLEANUP_AUDIT_FAILED","Unable to fail cleanup task");
        repository.insertLifecycleAudit(new LifecycleAudit(null,task.fileObjectId(),null,null,task.actionId(),
            "CLEANUP_FAILED",errorCode+": "+errorMessage,actor.userId(),actor.deptId(),clock.instant()));
    }
}
