package com.law.file.repository;

import java.time.Instant;
import java.util.List;

import com.law.file.domain.FileObject;
import com.law.file.domain.FileObject.AccessLog;
import com.law.file.domain.FileObject.AccessToken;
import com.law.file.domain.FileObject.FileBusinessRelation;
import com.law.file.domain.FileObject.FileMaterial;
import com.law.file.domain.FileObject.FileVersion;
import com.law.file.domain.FileObject.LifecycleAudit;
import com.law.file.infrastructure.internal.FilePersistenceModel.RelationAction;
import com.law.file.infrastructure.internal.FilePersistenceModel.CleanupTask;
import com.law.file.infrastructure.internal.FilePersistenceModel.StoredVersion;
import com.law.file.infrastructure.internal.FilePersistenceModel.UploadIntent;

public interface FileObjectRepository
{
    FileObject findById(Long fileObjectId);
    FileObject insertFileObject(FileObject value);
    int reserveNextVersion(Long fileObjectId,int expectedNextVersion,int expectedVersion);
    int activateVersion(Long fileObjectId,int versionNo,int minimumCurrentVersion);
    StoredVersion findCurrentVersion(Long fileObjectId);
    StoredVersion findVersionById(Long versionId);
    List<FileVersion> findVersions(Long fileObjectId);
    StoredVersion insertVersion(StoredVersion value);
    UploadIntent findUploadIntentByIdempotency(Long actorId,String idempotencyKey);
    UploadIntent findUploadIntentForUpdate(String uploadIntentId);
    int insertUploadIntent(UploadIntent value);
    int expireUploadIntent(String uploadIntentId,Instant expiredAt);
    int markUploadCompleted(String uploadIntentId,Long versionId);
    List<FileBusinessRelation> findActiveRelations(Long fileObjectId);
    FileBusinessRelation findRelation(Long fileObjectId,String businessType,Long businessId,String materialType,String visibility,Long scopeDeptId,Long scopeUserId);
    FileBusinessRelation findRelationById(Long relationId);
    FileBusinessRelation insertRelation(FileBusinessRelation relation);
    int revokeRelation(Long relationId);
    RelationAction findRelationAction(Long actorId,String actionId);
    int insertRelationAction(RelationAction action);
    List<FileMaterial> findMaterials(String businessType,Long businessId,List<Long> fileObjectIds);
    AccessToken insertAccessToken(AccessToken token);
    AccessToken findAccessTokenForUpdate(String tokenHash);
    int consumeAccessToken(Long accessTokenId,Instant consumedAt);
    int insertAccessLog(AccessLog log);
    List<AccessLog> findAccessLogs(Long fileObjectId);
    int insertLifecycleAudit(LifecycleAudit audit);
    List<LifecycleAudit> findLifecycleAudits(Long fileObjectId);
    CleanupTask insertCleanupTask(CleanupTask task);
    CleanupTask findCleanupTaskById(Long cleanupTaskId);
    List<CleanupTask> findRetryableCleanupTasks(Instant readyAt,int limit);
    int completeCleanupTask(Long cleanupTaskId,Instant completedAt);
    int failCleanupTask(Long cleanupTaskId,String errorCode,String errorMessage,Instant nextRetryAt);
}
