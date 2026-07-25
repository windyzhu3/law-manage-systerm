package com.law.file.mapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

public interface FileObjectMapper
{
    Map<String,Object> selectObject(Long fileObjectId);
    Map<String,Object> selectObjectForUpdate(Long fileObjectId);
    int insertObject(Map<String,Object> row);
    int reserveNextVersion(@Param("fileObjectId") Long fileObjectId,@Param("expectedNextVersion") int expectedNextVersion,@Param("expectedVersion") int expectedVersion);
    int activateVersion(@Param("fileObjectId") Long fileObjectId,@Param("versionNo") int versionNo,@Param("minimumCurrentVersion") int minimumCurrentVersion);
    Map<String,Object> selectCurrentVersion(Long fileObjectId);
    Map<String,Object> selectVersionById(Long fileVersionId);
    List<Map<String,Object>> selectStoredVersions(Long fileObjectId);
    List<Map<String,Object>> selectVersions(Long fileObjectId);
    int insertVersion(Map<String,Object> row);
    Map<String,Object> selectIntentByIdempotency(@Param("actorId") Long actorId,@Param("idempotencyKey") String idempotencyKey);
    Map<String,Object> selectIntentForUpdate(String uploadIntentId);
    int insertUploadIntent(Map<String,Object> row);
    int expireUploadIntent(@Param("uploadIntentId") String uploadIntentId,@Param("expiredAt") Instant expiredAt);
    int markUploadCompleted(@Param("uploadIntentId") String uploadIntentId,@Param("versionId") Long versionId);
    List<Map<String,Object>> selectActiveRelations(Long fileObjectId);
    List<Map<String,Object>> selectActiveRelationsForUpdate(Long fileObjectId);
    List<Map<String,Object>> selectActiveBusinessRelations(@Param("businessType") String businessType,@Param("businessId") Long businessId);
    Map<String,Object> selectRelation(Map<String,Object> query);
    Map<String,Object> selectRelationById(Long relationId);
    int insertRelation(Map<String,Object> row);
    int revokeRelation(Long relationId);
    int revokeAllRelations(Long fileObjectId);
    int disableObject(Long fileObjectId);
    Map<String,Object> selectRelationAction(@Param("actorId") Long actorId,@Param("actionId") String actionId);
    Map<String,Object> selectRelationActionForUpdate(@Param("actorId") Long actorId,@Param("actionId") String actionId);
    int insertRelationAction(Map<String,Object> row);
    int insertAccessToken(Map<String,Object> row);
    Map<String,Object> selectAccessTokenForUpdate(String tokenHash);
    int consumeAccessToken(@Param("accessTokenId") Long accessTokenId,@Param("consumedAt") Instant consumedAt);
    int insertAccessLog(Map<String,Object> row);
    List<Map<String,Object>> selectAccessLogs(Long fileObjectId);
    int insertLifecycleAudit(Map<String,Object> row);
    List<Map<String,Object>> selectLifecycleAudits(Long fileObjectId);
    int insertCleanupTask(Map<String,Object> row);
    Map<String,Object> selectCleanupTaskById(Long cleanupTaskId);
    List<Map<String,Object>> selectCleanupTasks(@Param("fileObjectId") Long fileObjectId,
        @Param("actorId") Long actorId,@Param("actionId") String actionId);
    List<Map<String,Object>> selectRetryableCleanupTasks(@Param("readyAt") Instant readyAt,@Param("limit") int limit);
    int completeCleanupTask(@Param("cleanupTaskId") Long cleanupTaskId,@Param("completedAt") Instant completedAt);
    int failCleanupTask(@Param("cleanupTaskId") Long cleanupTaskId,@Param("errorCode") String errorCode,
        @Param("errorMessage") String errorMessage,@Param("nextRetryAt") Instant nextRetryAt);
}
