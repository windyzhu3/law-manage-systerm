package com.law.file.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.law.file.domain.FileException;
import com.law.file.domain.FileObject.LifecycleAudit;
import com.law.file.infrastructure.internal.FilePersistenceModel.CleanupTask;
import com.law.file.repository.FileObjectRepository;
import com.law.file.spi.FileStoragePort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Job hook for retrying persisted storage cleanup failures. */
@Service
public class FileCleanupRetryService
{
    private final FileObjectRepository repository;
    private final FileStoragePort storage;
    private final Clock clock;

    @Autowired
    public FileCleanupRetryService(FileObjectRepository repository,FileStoragePort storage)
    {this(repository,storage,Clock.systemUTC());}

    FileCleanupRetryService(FileObjectRepository repository,FileStoragePort storage,Clock clock)
    {this.repository=repository;this.storage=storage;this.clock=clock;}

    @Transactional public int retryFailed(int batchSize)
    {
        int limit=Math.max(1,Math.min(batchSize,100));Instant now=clock.instant();int completed=0;
        List<CleanupTask> tasks=repository.findRetryableCleanupTasks(now,limit);
        if(tasks==null)return 0;
        for(CleanupTask task:tasks)
        {
            try
            {
                cleanup(task);
                if(repository.completeCleanupTask(task.cleanupTaskId(),now)!=1)
                    throw new FileException("FILE_CLEANUP_STATE_CONFLICT","Cleanup task changed concurrently");
                audit(task,"CLEANUP_RETRY_SUCCEEDED",null,now);completed++;
            }
            catch(RuntimeException error)
            {
                String code=code(error);String message=message(error);
                if(repository.failCleanupTask(task.cleanupTaskId(),code,message,now.plusSeconds(300))!=1)
                    throw new FileException("FILE_CLEANUP_STATE_CONFLICT","Unable to keep cleanup task retryable",error);
                audit(task,"CLEANUP_RETRY_FAILED",code+": "+message,now);
            }
        }
        return completed;
    }

    private void cleanup(CleanupTask task)
    {
        if("STAGED".equals(task.targetType()))
            storage.abort(new FileStoragePort.StagedObject(task.targetKey(),0L,null));
        else if("OBJECT".equals(task.targetType()))storage.delete(task.targetKey());
        else throw new FileException("FILE_CLEANUP_TARGET_INVALID","Unknown cleanup target");
    }

    private void audit(CleanupTask task,String event,String details,Instant now)
    {
        if(repository.insertLifecycleAudit(new LifecycleAudit(null,task.fileObjectId(),null,null,task.actionId(),event,
            details,task.actorId(),task.actorDeptId(),now))!=1)
            throw new FileException("FILE_CLEANUP_AUDIT_FAILED","Unable to write cleanup retry audit");
    }

    private static String code(RuntimeException error)
    {return error instanceof FileException file?file.getBusinessCode():error.getClass().getSimpleName();}
    private static String message(RuntimeException error)
    {String value=error.getMessage();if(value==null||value.isBlank())return "Storage cleanup failed";return value.length()>500?value.substring(0,500):value;}
}
