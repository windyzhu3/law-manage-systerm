package com.law.file.repository;

import java.time.Instant;
import java.util.List;

import com.law.file.domain.FileObject;
import com.law.file.domain.FileObject.AccessLog;
import com.law.file.domain.FileObject.AccessToken;
import com.law.file.domain.FileObject.FileBusinessRelation;
import com.law.file.domain.FileObject.FileMaterial;
import com.law.file.domain.FileObject.FileUploadIntent;
import com.law.file.domain.FileObject.FileVersion;

public interface FileObjectRepository
{
    FileObject findById(Long fileObjectId);
    FileObject insertFileObject(FileObject value);
    int reserveNextVersion(Long fileObjectId,int expectedNextVersion,int expectedVersion);
    int activateVersion(Long fileObjectId,int versionNo,int minimumCurrentVersion);
    FileVersion findCurrentVersion(Long fileObjectId);
    FileVersion findVersionById(Long versionId);
    List<FileVersion> findVersions(Long fileObjectId);
    FileVersion insertVersion(FileVersion value);
    FileUploadIntent findUploadIntentByIdempotency(Long actorId,String idempotencyKey);
    FileUploadIntent findUploadIntentForUpdate(String uploadIntentId);
    int insertUploadIntent(FileUploadIntent value);
    int markUploadCompleted(String uploadIntentId,Long versionId);
    List<FileBusinessRelation> findActiveRelations(Long fileObjectId);
    FileBusinessRelation findRelation(Long fileObjectId,String businessType,Long businessId,String materialType,String visibility);
    FileBusinessRelation findRelationByAction(Long actorId,String actionId);
    FileBusinessRelation insertRelation(FileBusinessRelation relation);
    List<FileMaterial> findMaterials(String businessType,Long businessId,List<Long> fileObjectIds);
    AccessToken insertAccessToken(AccessToken token);
    AccessToken findAccessTokenForUpdate(String tokenHash);
    int consumeAccessToken(Long accessTokenId,Instant consumedAt);
    int insertAccessLog(AccessLog log);
    List<AccessLog> findAccessLogs(Long fileObjectId);
}
