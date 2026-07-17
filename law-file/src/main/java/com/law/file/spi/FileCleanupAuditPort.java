package com.law.file.spi;

import com.law.file.domain.FileObject.FileActor;

public interface FileCleanupAuditPort
{
    void recordCleanup(Long fileObjectId,String actionId,String reason,FileActor actor);
}
