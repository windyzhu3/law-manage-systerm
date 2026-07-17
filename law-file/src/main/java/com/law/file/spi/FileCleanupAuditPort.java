package com.law.file.spi;

import com.law.file.domain.FileObject.FileActor;

public interface FileCleanupAuditPort
{
    Long beginCleanup(Long fileObjectId,String actionId,String targetType,String targetKey,FileActor actor);
    void recordCleanupSuccess(Long cleanupTaskId,String reason,FileActor actor);
    void recordCleanupFailure(Long cleanupTaskId,String errorCode,String errorMessage,FileActor actor);
}
