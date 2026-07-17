package com.law.file.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.law.file.domain.FileObject;
import com.law.file.domain.FileObject.AccessLog;
import com.law.file.domain.FileObject.AccessToken;
import com.law.file.domain.FileObject.FileBusinessRelation;
import com.law.file.domain.FileObject.FileMaterial;
import com.law.file.domain.FileObject.FileUploadIntent;
import com.law.file.domain.FileObject.FileVersion;
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
        if(mapper.insertObject(row)!=1)return null;return new FileObject(longValue(row,"fileObjectId"),value.logicalName(),0,2,value.status(),value.createdBy(),0);
    }
    @Override public int reserveNextVersion(Long id,int next,int version){return mapper.reserveNextVersion(id,next,version);}
    @Override public int activateVersion(Long id,int versionNo,int minimum){return mapper.activateVersion(id,versionNo,minimum);}
    @Override public FileVersion findCurrentVersion(Long id){return version(mapper.selectCurrentVersion(id));}
    @Override public FileVersion findVersionById(Long id){return version(mapper.selectVersionById(id));}
    @Override public List<FileVersion> findVersions(Long id){List<Map<String,Object>> rows=mapper.selectVersions(id);return rows==null?List.of():rows.stream().map(MyBatisFileObjectRepository::version).toList();}
    @Override public FileVersion insertVersion(FileVersion value)
    {
        Map<String,Object> row=versionRow(value);if(mapper.insertVersion(row)!=1)return null;
        return new FileVersion(longValue(row,"fileVersionId"),value.fileObjectId(),value.versionNo(),value.objectKey(),value.originalFileName(),value.contentType(),value.sizeBytes(),value.sha256(),value.createdBy(),value.createdAt());
    }
    @Override public FileUploadIntent findUploadIntentByIdempotency(Long actor,String key){return intent(mapper.selectIntentByIdempotency(actor,key));}
    @Override public FileUploadIntent findUploadIntentForUpdate(String id){return intent(mapper.selectIntentForUpdate(id));}
    @Override public int insertUploadIntent(FileUploadIntent value){return mapper.insertUploadIntent(intentRow(value));}
    @Override public int markUploadCompleted(String id,Long versionId){return mapper.markUploadCompleted(id,versionId);}
    @Override public List<FileBusinessRelation> findActiveRelations(Long id){return relations(mapper.selectActiveRelations(id));}
    @Override public FileBusinessRelation findRelation(Long id,String type,Long businessId,String material,String visibility)
    {Map<String,Object> q=new HashMap<>();q.put("fileObjectId",id);q.put("businessType",type);q.put("businessId",businessId);q.put("materialType",material);q.put("visibility",visibility);return relation(mapper.selectRelation(q));}
    @Override public FileBusinessRelation findRelationByAction(Long actorId,String actionId){return relation(mapper.selectRelationByAction(actorId,actionId));}
    @Override public FileBusinessRelation insertRelation(FileBusinessRelation value)
    {Map<String,Object> row=relationRow(value);if(mapper.insertRelation(row)!=1)return null;return new FileBusinessRelation(longValue(row,"relationId"),value.actionId(),value.fileObjectId(),value.businessType(),value.businessId(),value.materialType(),value.visibility(),value.createdBy(),value.createdDeptId(),true);}
    @Override public List<FileMaterial> findMaterials(String type,Long businessId,List<Long> ids)
    {if(ids==null||ids.isEmpty())return List.of();return mapper.selectMaterials(type,businessId,ids).stream().map(r->new FileMaterial(longValue(r,"fileObjectId"),text(r,"materialType"))).toList();}
    @Override public AccessToken insertAccessToken(AccessToken value)
    {Map<String,Object> row=tokenRow(value);if(mapper.insertAccessToken(row)!=1)return null;return new AccessToken(longValue(row,"accessTokenId"),value.fileObjectId(),value.fileVersionId(),value.accessType(),value.tokenHash(),value.actorId(),value.actorDeptId(),value.expiresAt(),null);}
    @Override public AccessToken findAccessTokenForUpdate(String hash){return token(mapper.selectAccessTokenForUpdate(hash));}
    @Override public int consumeAccessToken(Long id,Instant at){return mapper.consumeAccessToken(id,at);}
    @Override public int insertAccessLog(AccessLog value)
    {Map<String,Object> row=new HashMap<>();row.put("fileObjectId",value.fileObjectId());row.put("fileVersionId",value.fileVersionId());row.put("businessType",value.businessType());row.put("businessId",value.businessId());row.put("accessType",value.accessType());row.put("actorId",value.actorId());row.put("actorDeptId",value.actorDeptId());row.put("clientIp",value.clientIp());row.put("accessedAt",value.accessedAt());return mapper.insertAccessLog(row);}
    @Override public List<AccessLog> findAccessLogs(Long id){List<Map<String,Object>> rows=mapper.selectAccessLogs(id);return rows==null?List.of():rows.stream().map(MyBatisFileObjectRepository::accessLog).toList();}

    private static FileObject object(Map<String,Object> r){return r==null?null:new FileObject(longValue(r,"fileObjectId"),text(r,"logicalName"),integer(r,"currentVersionNo"),integer(r,"nextVersionNo"),text(r,"status"),longValue(r,"createdBy"),integer(r,"version"));}
    private static FileVersion version(Map<String,Object> r){return r==null?null:new FileVersion(longValue(r,"fileVersionId"),longValue(r,"fileObjectId"),integer(r,"versionNo"),text(r,"objectKey"),text(r,"originalFileName"),text(r,"contentType"),number(r,"sizeBytes"),text(r,"sha256"),longValue(r,"createdBy"),instant(r,"createdAt"));}
    private static FileUploadIntent intent(Map<String,Object> r){return r==null?null:new FileUploadIntent(text(r,"uploadIntentId"),text(r,"idempotencyKey"),longValue(r,"fileObjectId"),integer(r,"targetVersionNo"),text(r,"objectKey"),text(r,"originalFileName"),text(r,"contentType"),number(r,"expectedSize"),text(r,"expectedSha256"),text(r,"requestFingerprint"),longValue(r,"actorId"),text(r,"status"),longValue(r,"completedVersionId"));}
    private static FileBusinessRelation relation(Map<String,Object> r){return r==null?null:new FileBusinessRelation(longValue(r,"relationId"),text(r,"actionId"),longValue(r,"fileObjectId"),text(r,"businessType"),longValue(r,"businessId"),text(r,"materialType"),text(r,"visibility"),longValue(r,"createdBy"),longValue(r,"createdDeptId"),bool(r,"active"));}
    private static List<FileBusinessRelation> relations(List<Map<String,Object>> rows){if(rows==null)return List.of();List<FileBusinessRelation> values=new ArrayList<>();for(Map<String,Object> row:rows)values.add(relation(row));return List.copyOf(values);}
    private static AccessToken token(Map<String,Object> r){return r==null?null:new AccessToken(longValue(r,"accessTokenId"),longValue(r,"fileObjectId"),longValue(r,"fileVersionId"),text(r,"accessType"),text(r,"tokenHash"),longValue(r,"actorId"),longValue(r,"actorDeptId"),instant(r,"expiresAt"),instant(r,"consumedAt"));}
    private static AccessLog accessLog(Map<String,Object> r){return r==null?null:new AccessLog(longValue(r,"accessLogId"),longValue(r,"fileObjectId"),longValue(r,"fileVersionId"),text(r,"businessType"),longValue(r,"businessId"),text(r,"accessType"),longValue(r,"actorId"),longValue(r,"actorDeptId"),text(r,"clientIp"),instant(r,"accessedAt"));}
    private static Map<String,Object> versionRow(FileVersion v){Map<String,Object> r=new HashMap<>();r.put("fileObjectId",v.fileObjectId());r.put("versionNo",v.versionNo());r.put("objectKey",v.objectKey());r.put("originalFileName",v.originalFileName());r.put("contentType",v.contentType());r.put("sizeBytes",v.sizeBytes());r.put("sha256",v.sha256());r.put("createdBy",v.createdBy());r.put("createdAt",v.createdAt());return r;}
    private static Map<String,Object> intentRow(FileUploadIntent v){Map<String,Object> r=new HashMap<>();r.put("uploadIntentId",v.uploadIntentId());r.put("idempotencyKey",v.idempotencyKey());r.put("fileObjectId",v.fileObjectId());r.put("targetVersionNo",v.targetVersionNo());r.put("objectKey",v.objectKey());r.put("originalFileName",v.originalFileName());r.put("contentType",v.contentType());r.put("expectedSize",v.expectedSize());r.put("expectedSha256",v.expectedSha256());r.put("requestFingerprint",v.requestFingerprint());r.put("actorId",v.actorId());r.put("status",v.status());return r;}
    private static Map<String,Object> relationRow(FileBusinessRelation v){Map<String,Object> r=new HashMap<>();r.put("actionId",v.actionId());r.put("fileObjectId",v.fileObjectId());r.put("businessType",v.businessType());r.put("businessId",v.businessId());r.put("materialType",v.materialType());r.put("visibility",v.visibility());r.put("createdBy",v.createdBy());r.put("createdDeptId",v.createdDeptId());return r;}
    private static Map<String,Object> tokenRow(AccessToken v){Map<String,Object> r=new HashMap<>();r.put("fileObjectId",v.fileObjectId());r.put("fileVersionId",v.fileVersionId());r.put("accessType",v.accessType());r.put("tokenHash",v.tokenHash());r.put("actorId",v.actorId());r.put("actorDeptId",v.actorDeptId());r.put("expiresAt",v.expiresAt());return r;}
    private static Object value(Map<String,Object> r,String key){if(r.containsKey(key))return r.get(key);String snake=key.replaceAll("([A-Z])","_$1").toLowerCase();return r.get(snake);}
    private static String text(Map<String,Object> r,String k){Object v=value(r,k);return v==null?null:String.valueOf(v);}private static Long longValue(Map<String,Object> r,String k){Object v=value(r,k);return v==null?null:Long.valueOf(String.valueOf(v));}private static int integer(Map<String,Object> r,String k){Object v=value(r,k);return v==null?0:Integer.parseInt(String.valueOf(v));}private static long number(Map<String,Object> r,String k){Object v=value(r,k);return v==null?0:Long.parseLong(String.valueOf(v));}private static boolean bool(Map<String,Object> r,String k){Object v=value(r,k);return v instanceof Boolean b?b:v!=null&&(v.equals(1)||"1".equals(String.valueOf(v))||"true".equalsIgnoreCase(String.valueOf(v)));}
    private static Instant instant(Map<String,Object> r,String k){Object v=value(r,k);if(v==null)return null;if(v instanceof Instant i)return i;if(v instanceof Timestamp t)return t.toInstant();if(v instanceof LocalDateTime l)return l.atZone(ZoneId.systemDefault()).toInstant();throw new IllegalArgumentException("Unsupported timestamp "+v.getClass());}
}
