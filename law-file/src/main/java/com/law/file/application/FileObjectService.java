package com.law.file.application;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import com.law.file.domain.FileException;
import com.law.file.domain.FileObject;
import com.law.file.domain.FileObject.AccessContent;
import com.law.file.domain.FileObject.AccessLog;
import com.law.file.domain.FileObject.AccessReceipt;
import com.law.file.domain.FileObject.AccessToken;
import com.law.file.domain.FileObject.FileActor;
import com.law.file.domain.FileObject.FileBusinessRelation;
import com.law.file.domain.FileObject.FileVersion;
import com.law.file.domain.FileObject.LifecycleAudit;
import com.law.file.domain.FileUploadUnavailableException;
import com.law.file.infrastructure.internal.FilePersistenceModel.RelationAction;
import com.law.file.infrastructure.internal.FilePersistenceModel.StoredVersion;
import com.law.file.infrastructure.internal.FilePersistenceModel.UploadIntent;
import com.law.file.repository.FileObjectRepository;
import com.law.file.security.FileAccessDeniedException;
import com.law.file.security.FileAccessPolicy;
import com.law.file.security.FileContentPolicy;
import com.law.file.spi.FileCleanupAuditPort;
import com.law.file.spi.FileStoragePort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class FileObjectService
{
    public record RegisterUploadCommand(String actionId,String originalFileName,String contentType,long expectedSize,
        String expectedSha256,String businessType,Long businessId,String materialType,String visibility) { }
    public record RegisterVersionCommand(String actionId,String originalFileName,String contentType,long expectedSize,
        String expectedSha256,String changeDescription) { }
    public record UploadIntentView(String uploadIntentId,Long fileObjectId,Long relationId,int versionNo,String status,Instant expiresAt) { }
    public record AccessTokenView(Long fileObjectId,Long relationId,String accessType,String token,Instant expiresAt) { }
    @FunctionalInterface public interface TokenGenerator { String generate(int bytes); }

    private final FileObjectRepository repository;
    private final FileStoragePort storage;
    private final FileAccessPolicy access;
    private final FileCleanupAuditPort cleanupAudit;
    private final FileContentPolicy contentPolicy;
    private final Clock clock;
    private final TokenGenerator tokens;

    @Autowired
    public FileObjectService(FileObjectRepository repository,FileStoragePort storage,FileAccessPolicy access,
        FileCleanupAuditPort cleanupAudit,FileContentPolicy contentPolicy)
    {this(repository,storage,access,cleanupAudit,contentPolicy,Clock.systemUTC(),FileObjectService::randomToken);}

    FileObjectService(FileObjectRepository repository,FileStoragePort storage,FileAccessPolicy access,
        FileCleanupAuditPort cleanupAudit,FileContentPolicy contentPolicy,Clock clock,TokenGenerator tokens)
    {
        this.repository=repository;this.storage=storage;this.access=access;this.cleanupAudit=cleanupAudit;
        this.contentPolicy=contentPolicy;this.clock=clock;this.tokens=tokens;
    }

    @Transactional public UploadIntentView registerUpload(RegisterUploadCommand command,FileActor actor)
    {
        validate(command);contentPolicy.validateRegistration(command.originalFileName(),command.contentType());
        access.requireCanWrite(command.businessType(),command.businessId(),actor);
        String fingerprint=requestFingerprint(command);
        UploadIntent prior=repository.findUploadIntentByIdempotency(actor.userId(),command.actionId());
        if(prior!=null)return replay(prior,fingerprint,relationId(prior.fileObjectId(),command,actor));
        RelationAction reused=repository.findRelationAction(actor.userId(),command.actionId());
        if(reused!=null)actionConflict();

        FileObject object=repository.insertFileObject(new FileObject(null,command.originalFileName(),0,2,"PENDING",actor.userId(),0));
        if(object==null||object.fileObjectId()==null)conflict("Unable to create file object");
        Scope scope=scope(command.visibility(),actor);
        FileBusinessRelation relation=repository.insertRelation(new FileBusinessRelation(null,object.fileObjectId(),
            command.businessType(),command.businessId(),command.materialType(),scope.visibility(),scope.deptId(),scope.userId(),
            actor.userId(),actor.deptId(),true));
        if(relation==null)conflict("Unable to relate file object");
        String relationFingerprint=relationFingerprint(object.fileObjectId(),command.businessType(),command.businessId(),
            command.materialType(),scope);
        insertRelationAction(new RelationAction(actor.userId(),command.actionId(),"RELATE",relation.relationId(),
            relationFingerprint,clock.instant()));
        lifecycle(object.fileObjectId(),null,relation.relationId(),command.actionId(),"RELATION_CREATED",null,actor);

        Instant expires=clock.instant().plus(Duration.ofDays(1));
        UploadIntent intent=new UploadIntent(UUID.randomUUID().toString(),command.actionId(),object.fileObjectId(),1,
            objectKey(),command.originalFileName(),command.contentType(),command.expectedSize(),
            command.expectedSha256().toLowerCase(Locale.ROOT),"Initial version",fingerprint,actor.userId(),
            "REGISTERED",null,expires);
        if(repository.insertUploadIntent(intent)!=1)conflict("Unable to register upload intent");
        return view(intent,relation.relationId());
    }

    @Transactional public UploadIntentView addVersion(Long fileObjectId,RegisterVersionCommand command,FileActor actor)
    {
        validate(command);contentPolicy.validateRegistration(command.originalFileName(),command.contentType());
        access.requireCanWrite(fileObjectId,actor);
        UploadIntent prior=repository.findUploadIntentByIdempotency(actor.userId(),command.actionId());
        String fingerprint=versionFingerprint(fileObjectId,command);
        if(prior!=null)return replay(prior,fingerprint,null);
        FileObject object=repository.findById(fileObjectId);if(object==null)notFound();
        int reserved=object.nextVersionNo();
        if(repository.reserveNextVersion(fileObjectId,reserved,object.version())!=1)
            conflict("File version changed; retry registration");
        Instant expires=clock.instant().plus(Duration.ofDays(1));
        UploadIntent intent=new UploadIntent(UUID.randomUUID().toString(),command.actionId(),fileObjectId,reserved,
            objectKey(),command.originalFileName(),command.contentType(),command.expectedSize(),
            command.expectedSha256().toLowerCase(Locale.ROOT),command.changeDescription(),fingerprint,actor.userId(),
            "REGISTERED",null,expires);
        if(repository.insertUploadIntent(intent)!=1)conflict("Unable to register version intent");
        return view(intent,null);
    }

    @Transactional(noRollbackFor=FileUploadUnavailableException.class)
    public FileVersion completeUpload(String uploadIntentId,InputStream input,String transportFileName,
        String transportContentType,FileActor actor)
    {
        UploadIntent intent=repository.findUploadIntentForUpdate(uploadIntentId);
        if(intent==null||!actor.userId().equals(intent.actorId()))throw new FileUploadUnavailableException();
        if("COMPLETED".equals(intent.status()))
        {
            StoredVersion completed=repository.findVersionById(intent.completedVersionId());
            if(completed==null)throw new FileUploadUnavailableException();
            return completed.metadata();
        }
        Instant now=clock.instant();
        if(!"REGISTERED".equals(intent.status()))throw new FileUploadUnavailableException();
        if(intent.expiresAt()==null||!intent.expiresAt().isAfter(now))
        {
            repository.expireUploadIntent(uploadIntentId,now);
            throw new FileUploadUnavailableException();
        }
        access.requireCanWrite(intent.fileObjectId(),actor);
        FileStoragePort.StagedObject staged=null;boolean published=false;
        try
        {
            staged=storage.stage(input,intent.expectedSize(),intent.expectedSha256());
            String verifiedContentType=contentPolicy.requireMatchingContent(intent.originalFileName(),intent.contentType(),
                transportFileName,transportContentType,storage.inspect(staged));
            FileVersion metadata=new FileVersion(null,intent.fileObjectId(),intent.targetVersionNo(),
                intent.originalFileName(),verifiedContentType,staged.size(),staged.sha256(),
                intent.changeDescription(),actor.userId(),now);
            StoredVersion inserted=repository.insertVersion(new StoredVersion(metadata,intent.objectKey()));
            if(inserted==null||inserted.metadata().fileVersionId()==null)conflict("File version already exists");
            if(repository.activateVersion(intent.fileObjectId(),intent.targetVersionNo(),Math.max(0,intent.targetVersionNo()-1))!=1)
                conflict("Unable to activate file version");
            if(repository.markUploadCompleted(uploadIntentId,inserted.metadata().fileVersionId())!=1)
                conflict("Upload was completed concurrently");
            storage.publish(staged,intent.objectKey());published=true;
            lifecycle(intent.fileObjectId(),inserted.metadata().fileVersionId(),null,intent.idempotencyKey(),
                "VERSION_CREATED",intent.changeDescription(),actor);
            registerRollbackCleanup(intent,staged,true,actor);
            return inserted.metadata();
        }
        catch(RuntimeException error)
        {
            if(!TransactionSynchronizationManager.isSynchronizationActive())
                cleanupFailedUpload(intent,staged,published,actor);
            else if(staged!=null)registerRollbackCleanup(intent,staged,published,actor);
            throw error;
        }
    }

    @Transactional public FileBusinessRelation relate(Long fileObjectId,String actionId,String businessType,Long businessId,
        String materialType,String visibility,FileActor actor)
    {
        if(invalidText(actionId,128))throw new FileException("FILE_ACTION_ID_REQUIRED","actionId is required");
        if(fileObjectId==null||fileObjectId<=0||invalidText(businessType,64)||businessId==null||businessId<=0
            ||invalidText(materialType,96))throw new FileException("FILE_RELATION_INVALID","Complete file relation metadata is required");
        Scope scope=scope(visibility,actor);
        String fingerprint=relationFingerprint(fileObjectId,businessType,businessId,materialType,scope);
        RelationAction prior=repository.findRelationAction(actor.userId(),actionId);
        if(prior!=null)return replayRelation(prior,"RELATE",fingerprint,fileObjectId);
        access.requireCanWrite(fileObjectId,actor);access.requireCanWrite(businessType,businessId,actor);
        FileBusinessRelation relation=repository.findRelation(fileObjectId,businessType,businessId,materialType,
            scope.visibility(),scope.deptId(),scope.userId());
        if(relation==null)
        {
            relation=repository.insertRelation(new FileBusinessRelation(null,fileObjectId,businessType,businessId,
                materialType,scope.visibility(),scope.deptId(),scope.userId(),actor.userId(),actor.deptId(),true));
            if(relation==null)conflict("Unable to relate file object");
        }
        insertRelationAction(new RelationAction(actor.userId(),actionId,"RELATE",relation.relationId(),fingerprint,clock.instant()));
        lifecycle(fileObjectId,null,relation.relationId(),actionId,"RELATION_CREATED",null,actor);
        return relation;
    }

    @Transactional public FileBusinessRelation revokeRelation(Long fileObjectId,Long relationId,String actionId,FileActor actor)
    {
        if(invalidText(actionId,128))throw new FileException("FILE_ACTION_ID_REQUIRED","actionId is required");
        String fingerprint=relationActionFingerprint("REVOKE",fileObjectId,relationId);
        RelationAction prior=repository.findRelationAction(actor.userId(),actionId);
        if(prior!=null)return replayRelation(prior,"REVOKE",fingerprint,fileObjectId);
        FileBusinessRelation relation=access.requireCanWriteRelation(fileObjectId,relationId,actor);
        if(repository.revokeRelation(relationId)!=1)conflict("File relation changed concurrently");
        insertRelationAction(new RelationAction(actor.userId(),actionId,"REVOKE",relationId,fingerprint,clock.instant()));
        lifecycle(fileObjectId,null,relationId,actionId,"RELATION_REVOKED",null,actor);
        return inactive(relation);
    }

    @Transactional public AccessTokenView issueAccessToken(Long fileObjectId,Long relationId,String accessType,FileActor actor)
    {
        access.requireCanReadRelation(fileObjectId,relationId,actor);
        String type=normalizeAccessType(accessType);
        StoredVersion version=repository.findCurrentVersion(fileObjectId);if(version==null)notFound();
        String raw=tokens.generate(32);Instant expires=clock.instant().plus(Duration.ofMinutes(2));
        AccessToken token=repository.insertAccessToken(new AccessToken(null,fileObjectId,
            version.metadata().fileVersionId(),relationId,type,sha256(raw),actor.userId(),actor.deptId(),expires,null));
        if(token==null||token.accessTokenId()==null)conflict("Unable to issue access token");
        return new AccessTokenView(fileObjectId,relationId,type,raw,expires);
    }

    @Transactional public AccessContent open(String rawToken,FileActor actor)
    {return open(rawToken,actor,null);}

    @Transactional public AccessContent open(String rawToken,FileActor actor,String clientIp)
    {
        if(rawToken==null||rawToken.length()<8||rawToken.length()>256)
            throw new FileException("FILE_ACCESS_TOKEN_INVALID","Invalid access token");
        AccessToken token=repository.findAccessTokenForUpdate(sha256(rawToken));
        Instant now=clock.instant();
        if(token==null||token.consumedAt()!=null||!token.expiresAt().isAfter(now))
            throw new FileException("FILE_ACCESS_TOKEN_INVALID","Access token is expired or already used");
        if(!actor.userId().equals(token.actorId())||!Objects.equals(actor.deptId(),token.actorDeptId()))
            throw new FileAccessDeniedException("Access token belongs to another actor");
        FileBusinessRelation relation=access.requireCanReadRelation(token.fileObjectId(),token.relationId(),actor);
        StoredVersion version=repository.findVersionById(token.fileVersionId());if(version==null)notFound();
        InputStream input=storage.read(version.objectKey());
        String session=UUID.randomUUID().toString();
        try
        {
            if(repository.consumeAccessToken(token.accessTokenId(),now)!=1)conflict("Access token was used concurrently");
            if(repository.insertAccessLog(accessLog(session,token,relation,"ACCESS_OPEN","OPENED",null,actor,clientIp,now))!=1)
                conflict("Unable to record file access");
        }
        catch(RuntimeException error){try{input.close();}catch(Exception ignored){}throw error;}
        AccessReceipt receipt=new AccessReceipt(session,token.fileObjectId(),token.fileVersionId(),token.relationId(),
            token.accessType(),actor.userId(),actor.deptId(),clientIp);
        FileVersion metadata=version.metadata();
        return new AccessContent(input,metadata.originalFileName(),metadata.contentType(),metadata.sizeBytes(),token.accessType(),receipt);
    }

    @Transactional public void completeAccess(AccessReceipt receipt,boolean success,String failureCode)
    {
        String event=receipt.accessType()+"_"+(success?"SUCCESS":"FAILED");
        AccessToken token=new AccessToken(null,receipt.fileObjectId(),receipt.fileVersionId(),receipt.relationId(),
            receipt.accessType(),null,receipt.actorId(),receipt.actorDeptId(),null,null);
        FileBusinessRelation relation=repository.findRelationById(receipt.relationId());
        if(relation==null)conflict("Access relation is unavailable");
        AccessLog log=accessLog(receipt.sessionId(),token,relation,event,success?"SUCCESS":"FAILED",
            success?null:safeFailure(failureCode),new FileActor(receipt.actorId(),"stream",receipt.actorDeptId()),
            receipt.clientIp(),clock.instant());
        if(repository.insertAccessLog(log)!=1)conflict("Unable to record access outcome");
    }

    @Transactional(readOnly=true) public List<FileVersion> versions(Long fileObjectId,FileActor actor)
    {access.requireCanRead(fileObjectId,actor);List<FileVersion> values=repository.findVersions(fileObjectId);return values==null?List.of():List.copyOf(values);}

    @Transactional(readOnly=true) public List<AccessLog> accessLogs(Long fileObjectId,FileActor actor)
    {access.requireCanRead(fileObjectId,actor);List<AccessLog> values=repository.findAccessLogs(fileObjectId);return values==null?List.of():List.copyOf(values);}

    @Transactional(readOnly=true) public List<LifecycleAudit> lifecycleAudits(Long fileObjectId,FileActor actor)
    {access.requireCanRead(fileObjectId,actor);List<LifecycleAudit> values=repository.findLifecycleAudits(fileObjectId);return values==null?List.of():List.copyOf(values);}

    private void registerRollbackCleanup(UploadIntent intent,FileStoragePort.StagedObject staged,
        boolean published,FileActor actor)
    {
        if(!TransactionSynchronizationManager.isSynchronizationActive())return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
        {
            @Override public void afterCompletion(int status)
            {
                if(status!=STATUS_COMMITTED)cleanupFailedUpload(intent,staged,published,actor);
            }
        });
    }

    private void cleanupFailedUpload(UploadIntent intent,FileStoragePort.StagedObject staged,boolean published,FileActor actor)
    {
        if(!published&&staged==null)return;
        String targetType=published?"OBJECT":"STAGED";
        String targetKey=published?intent.objectKey():staged.stagingKey();
        Long cleanupTaskId=cleanupAudit.beginCleanup(intent.fileObjectId(),intent.idempotencyKey(),targetType,targetKey,actor);
        try
        {
            if(published)storage.delete(intent.objectKey());else storage.abort(staged);
            cleanupAudit.recordCleanupSuccess(cleanupTaskId,"UPLOAD_ABORTED",actor);
        }
        catch(RuntimeException error)
        {
            String code=error instanceof FileException file?file.getBusinessCode():error.getClass().getSimpleName();
            cleanupAudit.recordCleanupFailure(cleanupTaskId,code,safeCleanupMessage(error.getMessage()),actor);
        }
    }

    private FileBusinessRelation replayRelation(RelationAction prior,String type,String fingerprint,Long expectedFileObjectId)
    {
        if(!type.equals(prior.actionType())||!fingerprint.equals(prior.requestFingerprint()))actionConflict();
        FileBusinessRelation relation=repository.findRelationById(prior.relationId());
        if(relation==null||!expectedFileObjectId.equals(relation.fileObjectId()))actionConflict();
        return relation;
    }

    private void insertRelationAction(RelationAction action)
    {
        if(repository.insertRelationAction(action)==1)return;
        RelationAction prior=repository.findRelationAction(action.actorId(),action.actionId());
        if(prior==null||!prior.actionType().equals(action.actionType())
            ||!prior.relationId().equals(action.relationId())
            ||!prior.requestFingerprint().equals(action.requestFingerprint()))actionConflict();
    }

    private void lifecycle(Long fileId,Long versionId,Long relationId,String actionId,String event,
        String details,FileActor actor)
    {
        if(repository.insertLifecycleAudit(new LifecycleAudit(null,fileId,versionId,relationId,actionId,event,
            details,actor.userId(),actor.deptId(),clock.instant()))!=1)conflict("Unable to write file lifecycle audit");
    }

    private static AccessLog accessLog(String session,AccessToken token,FileBusinessRelation relation,String event,
        String outcome,String failure,FileActor actor,String ip,Instant at)
    {
        return new AccessLog(null,session,token.fileObjectId(),token.fileVersionId(),relation.relationId(),
            relation.businessType(),relation.businessId(),token.accessType(),event,outcome,failure,
            actor.userId(),actor.deptId(),ip,at);
    }

    private UploadIntentView replay(UploadIntent prior,String fingerprint,Long relationId)
    {if(!fingerprint.equals(prior.requestFingerprint()))conflict("Idempotency key was reused with different metadata");return view(prior,relationId);}
    private static UploadIntentView view(UploadIntent intent,Long relationId)
    {return new UploadIntentView(intent.uploadIntentId(),intent.fileObjectId(),relationId,intent.targetVersionNo(),intent.status(),intent.expiresAt());}
    private Long relationId(Long fileObjectId,RegisterUploadCommand command,FileActor actor)
    {
        Scope expected=scope(command.visibility(),actor);
        List<FileBusinessRelation> relations=repository.findActiveRelations(fileObjectId);
        if(relations==null)return null;
        return relations.stream().filter(relation->relation.active()
                &&Objects.equals(command.businessType(),relation.businessType())
                &&Objects.equals(command.businessId(),relation.businessId())
                &&Objects.equals(command.materialType(),relation.materialType())
                &&Objects.equals(expected.visibility(),relation.visibility())
                &&Objects.equals(expected.deptId(),relation.scopeDeptId())
                &&Objects.equals(expected.userId(),relation.scopeUserId()))
            .map(FileBusinessRelation::relationId).findFirst().orElse(null);
    }
    private static FileBusinessRelation inactive(FileBusinessRelation relation)
    {return new FileBusinessRelation(relation.relationId(),relation.fileObjectId(),relation.businessType(),relation.businessId(),
        relation.materialType(),relation.visibility(),relation.scopeDeptId(),relation.scopeUserId(),
        relation.createdBy(),relation.createdDeptId(),false);}

    private record Scope(String visibility,Long deptId,Long userId) { }
    private static Scope scope(String visibility,FileActor actor)
    {
        String normalized=normalizeVisibility(visibility);
        return switch(normalized) {
            case "DEPARTMENT" -> {
                if(actor.deptId()==null)throw new FileException("FILE_DEPARTMENT_REQUIRED","Department visibility requires a department");
                yield new Scope(normalized,actor.deptId(),0L);
            }
            case "PRIVATE" -> new Scope(normalized,0L,actor.userId());
            default -> new Scope(normalized,0L,0L);
        };
    }

    static String relationActionFingerprint(String type,Long fileObjectId,Long relationId)
    {return sha256(type+"\u001f"+fileObjectId+"\u001f"+relationId);}
    private static String relationFingerprint(Long fileId,String type,Long businessId,String material,Scope scope)
    {return sha256(String.join("\u001f","RELATE",String.valueOf(fileId),type,String.valueOf(businessId),material,
        scope.visibility(),String.valueOf(scope.deptId()),String.valueOf(scope.userId())));}
    private static String normalizeVisibility(String value)
    {String v=value==null?"BUSINESS":value.toUpperCase(Locale.ROOT);if(!List.of("BUSINESS","DEPARTMENT","PRIVATE").contains(v))throw new FileException("FILE_VISIBILITY_INVALID","Invalid visibility");return v;}
    private static String normalizeAccessType(String value)
    {String v=value==null?"DOWNLOAD":value.toUpperCase(Locale.ROOT);if(!List.of("PREVIEW","DOWNLOAD").contains(v))throw new FileException("FILE_ACCESS_TYPE_INVALID","Invalid access type");return v;}
    private static void validate(RegisterUploadCommand c)
    {if(c==null||invalidText(c.actionId(),128)||invalidName(c.originalFileName())||invalidText(c.contentType(),160)
        ||c.expectedSize()<0||!sha(c.expectedSha256())||invalidText(c.businessType(),64)||c.businessId()==null
        ||c.businessId()<=0||invalidText(c.materialType(),96))throw new FileException("FILE_METADATA_INVALID","Complete upload metadata is required");}
    private static void validate(RegisterVersionCommand c)
    {if(c==null||invalidText(c.actionId(),128)||invalidName(c.originalFileName())||invalidText(c.contentType(),160)
        ||c.expectedSize()<0||!sha(c.expectedSha256())||invalidText(c.changeDescription(),500))
        throw new FileException("FILE_METADATA_INVALID","Complete version metadata is required");}
    private static boolean invalidName(String value)
    {return invalidText(value,255)||value.indexOf('\r')>=0||value.indexOf('\n')>=0||value.indexOf('\0')>=0;}
    private static boolean invalidText(String value,int max){return value==null||value.isBlank()||value.length()>max;}
    private static boolean sha(String value){return value!=null&&value.matches("(?i)[0-9a-f]{64}");}
    private static String versionFingerprint(Long id,RegisterVersionCommand c)
    {return sha256(String.join("\u001f",String.valueOf(id),c.originalFileName(),c.contentType(),
        String.valueOf(c.expectedSize()),c.expectedSha256().toLowerCase(Locale.ROOT),c.changeDescription()));}
    static String requestFingerprint(RegisterUploadCommand c)
    {return sha256(String.join("\u001f",c.originalFileName(),c.contentType(),String.valueOf(c.expectedSize()),
        c.expectedSha256().toLowerCase(Locale.ROOT),c.businessType(),String.valueOf(c.businessId()),
        c.materialType(),normalizeVisibility(c.visibility())));}
    private static String safeFailure(String value)
    {if(value==null||value.isBlank())return "STREAM_FAILED";return value.length()>120?value.substring(0,120):value;}
    private static String safeCleanupMessage(String value)
    {if(value==null||value.isBlank())return "Storage cleanup failed";return value.length()>500?value.substring(0,500):value;}
    private static String objectKey(){return "objects/"+UUID.randomUUID();}
    private static String randomToken(int bytes){byte[] value=new byte[bytes];new SecureRandom().nextBytes(value);return Base64.getUrlEncoder().withoutPadding().encodeToString(value);}
    private static String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static void notFound(){throw new FileException("FILE_OBJECT_NOT_FOUND","File object was not found");}
    private static void conflict(String message){throw new FileException("FILE_CONFLICT",message);}
    private static void actionConflict(){throw new FileException("FILE_ACTION_ID_CONFLICT","actionId is already bound to another relation action");}
}
