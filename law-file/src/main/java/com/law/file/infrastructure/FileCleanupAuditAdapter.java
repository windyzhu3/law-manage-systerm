package com.law.file.infrastructure;

import java.time.Clock;

import com.law.file.domain.FileObject.FileActor;
import com.law.file.domain.FileObject.LifecycleAudit;
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
    public void recordCleanup(Long fileObjectId,String actionId,String reason,FileActor actor)
    {
        repository.insertLifecycleAudit(new LifecycleAudit(null,fileObjectId,null,null,actionId,
            "STORAGE_CLEANUP",reason,actor.userId(),actor.deptId(),clock.instant()));
    }
}
