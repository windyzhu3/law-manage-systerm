package com.law.file.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.law.file.domain.FileObject;
import com.law.file.domain.FileObject.AccessLog;
import com.law.file.domain.FileObject.AccessToken;
import com.law.file.domain.FileObject.FileBusinessRelation;
import com.law.file.domain.FileObject.FileVersion;
import com.law.file.domain.FileObject.LifecycleAudit;
import com.law.file.infrastructure.internal.FilePersistenceModel.RelationAction;
import com.law.file.infrastructure.internal.FilePersistenceModel.CleanupTask;
import com.law.file.infrastructure.internal.FilePersistenceModel.StoredVersion;
import com.law.file.infrastructure.internal.FilePersistenceModel.UploadIntent;
import com.law.file.mapper.FileObjectMapper;
import com.law.file.repository.FileObjectRepository;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisFileObjectRepository implements FileObjectRepository
{
    private final FileObjectMapper mapper;
    public MyBatisFileObjectRepository(FileObjectMapper mapper){this.mapper=mapper;}
    @Override public FileObject findById(Long id){return object(mapper.selectObject(id));}
    @Override public FileObject insertFileObject(FileObject value)
    {
        Map<String,Object> row=new HashMap<>();row.put("logicalName",value.logicalName());row.put("status",value.status());row.put("createdBy",value.createdBy());
        if(mapper.insertObject(row)!=1)return null;
        return new FileObject(longValue(row,"fileObjectId"),value.logicalName(),0,2,value.status(),value.createdBy(),0);
    }
    @Override public int reserveNextVersion(Long id,int next,int version){return mapper.reserveNextVersion(id,next,version);}
    @Override public int activateVersion(Long id,int versionNo,int minimum){return mapper.activateVersion(id,versionNo,minimum);}
    @Override public StoredVersion findCurrentVersion(Long id){return storedVersion(mapper.selectCurrentVersion(id));}
    @Override public StoredVersion findVersionById(Long id){return storedVersion(mapper.selectVersionById(id));}
    @Override public List<FileVersion> findVersions(Long id)
    {List<Map<String,Object>> rows=mapper.selectVersions(id);return rows==null?List.of():rows.stream().map(MyBatisFileObjectRepository::version).toList();}
    @Override public StoredVersion insertVersion(StoredVersion value)
    {
        Map<String,Object> row=versionRow(value);if(mapper.insertVersion(row)!=1)return null;
        FileVersion v=value.metadata();
        return new StoredVersion(new FileVersion(longValue(row,"fileVersionId"),v.fileObjectId(),v.versionNo(),
            v.originalFileName(),v.contentType(),v.sizeBytes(),v.sha256(),v.changeDescription(),v.createdBy(),v.createdAt()),value.objectKey());
    }
    @Override public UploadIntent findUploadIntentByIdempotency(Long actor,String key){return intent(mapper.selectIntentByIdempotency(actor,key));}
    @Override public UploadIntent findUploadIntentForUpdate(String id){return intent(mapper.selectIntentForUpdate(id));}
    @Override public int insertUploadIntent(UploadIntent value){return mapper.insertUploadIntent(intentRow(value));}
    @Override public int expireUploadIntent(String id,Instant at){return mapper.expireUploadIntent(id,at);}
    @Override public int markUploadCompleted(String id,Long versionId){return mapper.markUploadCompleted(id,versionId);}
    @Override public List<FileBusinessRelation> findActiveRelations(Long id)
    {List<Map<String,Object>> rows=mapper.selectActiveRelations(id);return rows==null?List.of():rows.stream().map(MyBatisFileObjectRepository::relation).toList();}
    @Override public List<FileBusinessRelation> findActiveRelations(String type,Long businessId)
    {List<Map<String,Object>> rows=mapper.selectActiveBusinessRelations(type,businessId);return rows==null?List.of():rows.stream().map(MyBatisFileObjectRepository::relation).toList();}
    @Override public FileBusinessRelation findRelation(Long id,String type,Long businessId,String material,String visibility,Long dept,Long user)
    {
        Map<String,Object> q=new HashMap<>();q.put("fileObjectId",id);q.put("businessType",type);q.put("businessId",businessId);
        q.put("materialType",material);q.put("visibility",visibility);q.put("scopeDeptId",dept);q.put("scopeUserId",user);
        return relation(mapper.selectRelation(q));
    }
    @Override public FileBusinessRelation findRelationById(Long id){return relation(mapper.selectRelationById(id));}
    @Override public FileBusinessRelation insertRelation(FileBusinessRelation value)
    {
        Map<String,Object> row=relationRow(value);if(mapper.insertRelation(row)!=1)
            return findRelation(value.fileObjectId(),value.businessType(),value.businessId(),value.materialType(),value.visibility(),value.scopeDeptId(),value.scopeUserId());
        return new FileBusinessRelation(longValue(row,"relationId"),value.fileObjectId(),value.businessType(),value.businessId(),
            value.materialType(),value.visibility(),value.scopeDeptId(),value.scopeUserId(),value.createdBy(),value.createdDeptId(),true);
    }
    @Override public int revokeRelation(Long id){return mapper.revokeRelation(id);}
    @Override public RelationAction findRelationAction(Long actor,String action){return relationAction(mapper.selectRelationAction(actor,action));}
    @Override public int insertRelationAction(RelationAction value){return mapper.insertRelationAction(relationActionRow(value));}
    @Override public AccessToken insertAccessToken(AccessToken value)
    {
        Map<String,Object> row=tokenRow(value);if(mapper.insertAccessToken(row)!=1)return null;
        return new AccessToken(longValue(row,"accessTokenId"),value.fileObjectId(),value.fileVersionId(),value.relationId(),
            value.accessType(),value.tokenHash(),value.actorId(),value.actorDeptId(),value.expiresAt(),null);
    }
    @Override public AccessToken findAccessTokenForUpdate(String hash){return token(mapper.selectAccessTokenForUpdate(hash));}
    @Override public int consumeAccessToken(Long id,Instant at){return mapper.consumeAccessToken(id,at);}
    @Override public int insertAccessLog(AccessLog value){return mapper.insertAccessLog(accessLogRow(value));}
    @Override public List<AccessLog> findAccessLogs(Long id)
    {List<Map<String,Object>> rows=mapper.selectAccessLogs(id);return rows==null?List.of():rows.stream().map(MyBatisFileObjectRepository::accessLog).toList();}
    @Override public int insertLifecycleAudit(LifecycleAudit value){return mapper.insertLifecycleAudit(lifecycleRow(value));}
    @Override public List<LifecycleAudit> findLifecycleAudits(Long id)
    {List<Map<String,Object>> rows=mapper.selectLifecycleAudits(id);return rows==null?List.of():rows.stream().map(MyBatisFileObjectRepository::lifecycle).toList();}
    @Override public CleanupTask insertCleanupTask(CleanupTask value)
    {
        Map<String,Object> row=cleanupRow(value);if(mapper.insertCleanupTask(row)!=1)return null;
        return new CleanupTask(longValue(row,"cleanupTaskId"),value.fileObjectId(),value.actionId(),value.targetType(),
            value.targetKey(),value.status(),value.retryCount(),value.lastErrorCode(),value.lastErrorMessage(),
            value.nextRetryAt(),value.actorId(),value.actorDeptId(),value.createdAt());
    }
    @Override public CleanupTask findCleanupTaskById(Long id){return cleanup(mapper.selectCleanupTaskById(id));}
    @Override public List<CleanupTask> findRetryableCleanupTasks(Instant at,int limit)
    {List<Map<String,Object>> rows=mapper.selectRetryableCleanupTasks(at,limit);return rows==null?List.of():rows.stream().map(MyBatisFileObjectRepository::cleanup).toList();}
    @Override public int completeCleanupTask(Long id,Instant at){return mapper.completeCleanupTask(id,at);}
    @Override public int failCleanupTask(Long id,String code,String message,Instant next)
    {return mapper.failCleanupTask(id,code,message,next);}

    private static FileObject object(Map<String,Object> r){return r==null?null:new FileObject(longValue(r,"fileObjectId"),text(r,"logicalName"),integer(r,"currentVersionNo"),integer(r,"nextVersionNo"),text(r,"status"),longValue(r,"createdBy"),integer(r,"version"));}
    private static FileVersion version(Map<String,Object> r){return r==null?null:new FileVersion(longValue(r,"fileVersionId"),longValue(r,"fileObjectId"),integer(r,"versionNo"),text(r,"originalFileName"),text(r,"contentType"),number(r,"sizeBytes"),text(r,"sha256"),text(r,"changeDescription"),longValue(r,"createdBy"),instant(r,"createdAt"));}
    private static StoredVersion storedVersion(Map<String,Object> r){return r==null?null:new StoredVersion(version(r),text(r,"objectKey"));}
    private static UploadIntent intent(Map<String,Object> r){return r==null?null:new UploadIntent(text(r,"uploadIntentId"),text(r,"idempotencyKey"),longValue(r,"fileObjectId"),integer(r,"targetVersionNo"),text(r,"objectKey"),text(r,"originalFileName"),text(r,"contentType"),number(r,"expectedSize"),text(r,"expectedSha256"),text(r,"changeDescription"),text(r,"requestFingerprint"),longValue(r,"actorId"),text(r,"status"),longValue(r,"completedVersionId"),instant(r,"expiresAt"));}
    private static FileBusinessRelation relation(Map<String,Object> r){return r==null?null:new FileBusinessRelation(longValue(r,"relationId"),longValue(r,"fileObjectId"),text(r,"businessType"),longValue(r,"businessId"),text(r,"materialType"),text(r,"visibility"),longValue(r,"scopeDeptId"),longValue(r,"scopeUserId"),longValue(r,"createdBy"),longValue(r,"createdDeptId"),bool(r,"active"));}
    private static RelationAction relationAction(Map<String,Object> r){return r==null?null:new RelationAction(longValue(r,"actorId"),text(r,"actionId"),text(r,"actionType"),longValue(r,"relationId"),text(r,"requestFingerprint"),instant(r,"createdAt"));}
    private static AccessToken token(Map<String,Object> r){return r==null?null:new AccessToken(longValue(r,"accessTokenId"),longValue(r,"fileObjectId"),longValue(r,"fileVersionId"),longValue(r,"relationId"),text(r,"accessType"),text(r,"tokenHash"),longValue(r,"actorId"),longValue(r,"actorDeptId"),instant(r,"expiresAt"),instant(r,"consumedAt"));}
    private static AccessLog accessLog(Map<String,Object> r){return r==null?null:new AccessLog(longValue(r,"accessLogId"),text(r,"accessSessionId"),longValue(r,"fileObjectId"),longValue(r,"fileVersionId"),longValue(r,"relationId"),text(r,"businessType"),longValue(r,"businessId"),text(r,"accessType"),text(r,"eventType"),text(r,"outcome"),text(r,"failureCode"),longValue(r,"actorId"),longValue(r,"actorDeptId"),text(r,"clientIp"),instant(r,"accessedAt"));}
    private static LifecycleAudit lifecycle(Map<String,Object> r){return r==null?null:new LifecycleAudit(longValue(r,"lifecycleAuditId"),longValue(r,"fileObjectId"),longValue(r,"fileVersionId"),longValue(r,"relationId"),text(r,"actionId"),text(r,"eventType"),text(r,"details"),longValue(r,"actorId"),longValue(r,"actorDeptId"),instant(r,"occurredAt"));}
    private static CleanupTask cleanup(Map<String,Object> r){return r==null?null:new CleanupTask(longValue(r,"cleanupTaskId"),longValue(r,"fileObjectId"),text(r,"actionId"),text(r,"targetType"),text(r,"targetKey"),text(r,"status"),integer(r,"retryCount"),text(r,"lastErrorCode"),text(r,"lastErrorMessage"),instant(r,"nextRetryAt"),longValue(r,"actorId"),longValue(r,"actorDeptId"),instant(r,"createdAt"));}

    private static Map<String,Object> versionRow(StoredVersion stored){FileVersion v=stored.metadata();Map<String,Object> r=new HashMap<>();r.put("fileObjectId",v.fileObjectId());r.put("versionNo",v.versionNo());r.put("objectKey",stored.objectKey());r.put("originalFileName",v.originalFileName());r.put("contentType",v.contentType());r.put("sizeBytes",v.sizeBytes());r.put("sha256",v.sha256());r.put("changeDescription",v.changeDescription());r.put("createdBy",v.createdBy());r.put("createdAt",v.createdAt());return r;}
    private static Map<String,Object> intentRow(UploadIntent v){Map<String,Object> r=new HashMap<>();r.put("uploadIntentId",v.uploadIntentId());r.put("idempotencyKey",v.idempotencyKey());r.put("fileObjectId",v.fileObjectId());r.put("targetVersionNo",v.targetVersionNo());r.put("objectKey",v.objectKey());r.put("originalFileName",v.originalFileName());r.put("contentType",v.contentType());r.put("expectedSize",v.expectedSize());r.put("expectedSha256",v.expectedSha256());r.put("changeDescription",v.changeDescription());r.put("requestFingerprint",v.requestFingerprint());r.put("actorId",v.actorId());r.put("status",v.status());r.put("expiresAt",v.expiresAt());return r;}
    private static Map<String,Object> relationRow(FileBusinessRelation v){Map<String,Object> r=new HashMap<>();r.put("fileObjectId",v.fileObjectId());r.put("businessType",v.businessType());r.put("businessId",v.businessId());r.put("materialType",v.materialType());r.put("visibility",v.visibility());r.put("scopeDeptId",v.scopeDeptId());r.put("scopeUserId",v.scopeUserId());r.put("createdBy",v.createdBy());r.put("createdDeptId",v.createdDeptId());return r;}
    private static Map<String,Object> relationActionRow(RelationAction v){Map<String,Object> r=new HashMap<>();r.put("actorId",v.actorId());r.put("actionId",v.actionId());r.put("actionType",v.actionType());r.put("relationId",v.relationId());r.put("requestFingerprint",v.requestFingerprint());r.put("createdAt",v.createdAt());return r;}
    private static Map<String,Object> tokenRow(AccessToken v){Map<String,Object> r=new HashMap<>();r.put("fileObjectId",v.fileObjectId());r.put("fileVersionId",v.fileVersionId());r.put("relationId",v.relationId());r.put("accessType",v.accessType());r.put("tokenHash",v.tokenHash());r.put("actorId",v.actorId());r.put("actorDeptId",v.actorDeptId());r.put("expiresAt",v.expiresAt());return r;}
    private static Map<String,Object> accessLogRow(AccessLog v){Map<String,Object> r=new HashMap<>();r.put("accessSessionId",v.accessSessionId());r.put("fileObjectId",v.fileObjectId());r.put("fileVersionId",v.fileVersionId());r.put("relationId",v.relationId());r.put("businessType",v.businessType());r.put("businessId",v.businessId());r.put("accessType",v.accessType());r.put("eventType",v.eventType());r.put("outcome",v.outcome());r.put("failureCode",v.failureCode());r.put("actorId",v.actorId());r.put("actorDeptId",v.actorDeptId());r.put("clientIp",v.clientIp());r.put("accessedAt",v.accessedAt());return r;}
    private static Map<String,Object> lifecycleRow(LifecycleAudit v){Map<String,Object> r=new HashMap<>();r.put("fileObjectId",v.fileObjectId());r.put("fileVersionId",v.fileVersionId());r.put("relationId",v.relationId());r.put("actionId",v.actionId());r.put("eventType",v.eventType());r.put("details",v.details());r.put("actorId",v.actorId());r.put("actorDeptId",v.actorDeptId());r.put("occurredAt",v.occurredAt());return r;}
    private static Map<String,Object> cleanupRow(CleanupTask v){Map<String,Object> r=new HashMap<>();r.put("fileObjectId",v.fileObjectId());r.put("actionId",v.actionId());r.put("targetType",v.targetType());r.put("targetKey",v.targetKey());r.put("status",v.status());r.put("retryCount",v.retryCount());r.put("nextRetryAt",v.nextRetryAt());r.put("actorId",v.actorId());r.put("actorDeptId",v.actorDeptId());r.put("createdAt",v.createdAt());return r;}
    private static Object value(Map<String,Object> r,String key){if(r.containsKey(key))return r.get(key);String snake=key.replaceAll("([A-Z])","_$1").toLowerCase();return r.get(snake);}
    private static String text(Map<String,Object> r,String k){Object v=value(r,k);return v==null?null:String.valueOf(v);}
    private static Long longValue(Map<String,Object> r,String k){Object v=value(r,k);return v==null?null:Long.valueOf(String.valueOf(v));}
    private static int integer(Map<String,Object> r,String k){Object v=value(r,k);return v==null?0:Integer.parseInt(String.valueOf(v));}
    private static long number(Map<String,Object> r,String k){Object v=value(r,k);return v==null?0:Long.parseLong(String.valueOf(v));}
    private static boolean bool(Map<String,Object> r,String k){Object v=value(r,k);return v instanceof Boolean b?b:v!=null&&(v.equals(1)||"1".equals(String.valueOf(v))||"true".equalsIgnoreCase(String.valueOf(v)));}
    private static Instant instant(Map<String,Object> r,String k){Object v=value(r,k);if(v==null)return null;if(v instanceof Instant i)return i;if(v instanceof Timestamp t)return t.toInstant();if(v instanceof LocalDateTime l)return l.atZone(ZoneId.systemDefault()).toInstant();throw new IllegalArgumentException("Unsupported timestamp "+v.getClass());}
}
