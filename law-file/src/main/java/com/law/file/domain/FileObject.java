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
    public record FileVersion(Long fileVersionId,Long fileObjectId,int versionNo,String objectKey,
        String originalFileName,String contentType,long sizeBytes,String sha256,Long createdBy,Instant createdAt) { }
    public record FileBusinessRelation(Long relationId,String actionId,Long fileObjectId,String businessType,Long businessId,
        String materialType,String visibility,Long createdBy,Long createdDeptId,boolean active) { }
    public record FileUploadIntent(String uploadIntentId,String idempotencyKey,Long fileObjectId,int targetVersionNo,String objectKey,
        String originalFileName,String contentType,long expectedSize,String expectedSha256,String requestFingerprint,
        Long actorId,String status,Long completedVersionId) { }
    public record AccessToken(Long accessTokenId,Long fileObjectId,Long fileVersionId,String accessType,String tokenHash,
        Long actorId,Long actorDeptId,Instant expiresAt,Instant consumedAt) { }
    public record AccessLog(Long accessLogId,Long fileObjectId,Long fileVersionId,String businessType,Long businessId,
        String accessType,Long actorId,Long actorDeptId,String clientIp,Instant accessedAt) { }
    public record FileMaterial(Long fileObjectId,String materialType) { }
    public record AccessContent(InputStream input,String fileName,String contentType,long sizeBytes) { }
}
