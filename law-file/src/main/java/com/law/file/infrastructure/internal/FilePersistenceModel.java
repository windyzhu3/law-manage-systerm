package com.law.file.infrastructure.internal;

import java.time.Instant;

import com.law.file.domain.FileObject.FileVersion;

/** Persistence-only records. Storage keys are deliberately absent from the public domain model. */
public final class FilePersistenceModel
{
    private FilePersistenceModel(){ }
    public record StoredVersion(FileVersion metadata,String objectKey) { }
    public record UploadIntent(String uploadIntentId,String idempotencyKey,Long fileObjectId,int targetVersionNo,
        String objectKey,String originalFileName,String contentType,long expectedSize,String expectedSha256,
        String changeDescription,String requestFingerprint,Long actorId,String status,Long completedVersionId,
        Instant expiresAt) { }
    public record RelationAction(Long actorId,String actionId,String actionType,Long relationId,
        String requestFingerprint,Instant createdAt) { }
}
