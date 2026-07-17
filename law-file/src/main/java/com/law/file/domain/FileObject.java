package com.law.file.domain;

import java.io.InputStream;
import java.time.Instant;

/** Public file-center model. Storage object keys never leave this module. */
public record FileObject(Long fileObjectId,String logicalName,int currentVersionNo,int nextVersionNo,
        String status,Long createdBy,int version)
{
    public record FileActor(Long userId,String userName,Long deptId)
    {
        public FileActor { if(userId==null||userId<=0)throw new FileException("FILE_ACTOR_REQUIRED","Actor is required"); }
    }
    public record FileVersion(Long fileVersionId,Long fileObjectId,int versionNo,String originalFileName,
        String contentType,long sizeBytes,String sha256,String changeDescription,Long createdBy,Instant createdAt) { }
    public record FileBusinessRelation(Long relationId,Long fileObjectId,String businessType,Long businessId,
        String materialType,String visibility,Long scopeDeptId,Long scopeUserId,Long createdBy,Long createdDeptId,boolean active) { }
    public record AccessToken(Long accessTokenId,Long fileObjectId,Long fileVersionId,Long relationId,String accessType,String tokenHash,
        Long actorId,Long actorDeptId,Instant expiresAt,Instant consumedAt) { }
    public record AccessLog(Long accessLogId,String accessSessionId,Long fileObjectId,Long fileVersionId,Long relationId,
        String businessType,Long businessId,String accessType,String eventType,String outcome,String failureCode,
        Long actorId,Long actorDeptId,String clientIp,Instant accessedAt) { }
    public record LifecycleAudit(Long lifecycleAuditId,Long fileObjectId,Long fileVersionId,Long relationId,String actionId,
        String eventType,String details,Long actorId,Long actorDeptId,Instant occurredAt) { }
    public record FileMaterial(Long fileObjectId,String materialType) { }
    public record AccessReceipt(String sessionId,Long fileObjectId,Long fileVersionId,Long relationId,String accessType,
        Long actorId,Long actorDeptId,String clientIp) { }
    public record AccessContent(InputStream input,String fileName,String contentType,long sizeBytes,String accessType,
        AccessReceipt receipt) { }
}
