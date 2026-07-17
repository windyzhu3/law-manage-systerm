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
import java.util.UUID;

import com.law.file.domain.FileException;
import com.law.file.domain.FileObject;
import com.law.file.domain.FileObject.AccessContent;
import com.law.file.domain.FileObject.AccessLog;
import com.law.file.domain.FileObject.AccessToken;
import com.law.file.domain.FileObject.FileActor;
import com.law.file.domain.FileObject.FileBusinessRelation;
import com.law.file.domain.FileObject.FileUploadIntent;
import com.law.file.domain.FileObject.FileVersion;
import com.law.file.repository.FileObjectRepository;
import com.law.file.security.FileAccessPolicy;
import com.law.file.spi.FileStoragePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FileObjectService
{
    public record RegisterUploadCommand(String idempotencyKey,String originalFileName,String contentType,long expectedSize,
        String expectedSha256,String businessType,Long businessId,String materialType,String visibility) { }
    public record RegisterVersionCommand(String idempotencyKey,String originalFileName,String contentType,long expectedSize,String expectedSha256) { }
    public record UploadIntentView(String uploadIntentId,Long fileObjectId,int versionNo,String status) { }
    public record AccessTokenView(Long fileObjectId,String accessType,String token,Instant expiresAt) { }
    @FunctionalInterface public interface TokenGenerator { String generate(int bytes); }

    private final FileObjectRepository repository;private final FileStoragePort storage;private final FileAccessPolicy access;
    private final Clock clock;private final TokenGenerator tokens;
    public FileObjectService(FileObjectRepository repository,FileStoragePort storage,FileAccessPolicy access)
    {this(repository,storage,access,Clock.systemUTC(),FileObjectService::randomToken);}
    FileObjectService(FileObjectRepository repository,FileStoragePort storage,FileAccessPolicy access,Clock clock,TokenGenerator tokens)
    {this.repository=repository;this.storage=storage;this.access=access;this.clock=clock;this.tokens=tokens;}

    @Transactional public UploadIntentView registerUpload(RegisterUploadCommand command,FileActor actor)
    {
        validate(command);access.requireCanWrite(command.businessType(),command.businessId(),actor);
        String fingerprint=requestFingerprint(command);
        FileUploadIntent prior=repository.findUploadIntentByIdempotency(actor.userId(),command.idempotencyKey());
        if(prior!=null)return replay(prior,fingerprint);
        FileObject object=repository.insertFileObject(new FileObject(null,command.originalFileName(),0,2,"PENDING",actor.userId(),0));
        if(object==null||object.fileObjectId()==null)conflict("Unable to create file object");
        FileBusinessRelation relation=new FileBusinessRelation(null,command.idempotencyKey(),object.fileObjectId(),command.businessType(),command.businessId(),
            command.materialType(),normalizeVisibility(command.visibility()),actor.userId(),actor.deptId(),true);
        relation=repository.insertRelation(relation);if(relation==null)conflict("Unable to relate file object");
        FileUploadIntent intent=new FileUploadIntent(UUID.randomUUID().toString(),command.idempotencyKey(),object.fileObjectId(),1,objectKey(),command.originalFileName(),
            command.contentType(),command.expectedSize(),command.expectedSha256().toLowerCase(Locale.ROOT),fingerprint,actor.userId(),"REGISTERED",null);
        if(repository.insertUploadIntent(intent)!=1)conflict("Unable to register upload intent");
        return view(intent);
    }

    @Transactional public UploadIntentView addVersion(Long fileObjectId,RegisterVersionCommand command,FileActor actor)
    {
        validate(command);access.requireCanWrite(fileObjectId,actor);
        FileUploadIntent prior=repository.findUploadIntentByIdempotency(actor.userId(),command.idempotencyKey());
        String fingerprint=versionFingerprint(fileObjectId,command);
        if(prior!=null)return replay(prior,fingerprint);
        FileObject object=repository.findById(fileObjectId);if(object==null)notFound();
        int reserved=object.nextVersionNo();
        if(repository.reserveNextVersion(fileObjectId,reserved,object.version())!=1)conflict("File version changed; retry registration");
        FileUploadIntent intent=new FileUploadIntent(UUID.randomUUID().toString(),command.idempotencyKey(),fileObjectId,reserved,objectKey(),command.originalFileName(),
            command.contentType(),command.expectedSize(),command.expectedSha256().toLowerCase(Locale.ROOT),fingerprint,actor.userId(),"REGISTERED",null);
        if(repository.insertUploadIntent(intent)!=1)conflict("Unable to register version intent");
        return view(intent);
    }

    @Transactional public FileVersion completeUpload(String uploadIntentId,InputStream input,FileActor actor)
    {
        FileUploadIntent intent=repository.findUploadIntentForUpdate(uploadIntentId);if(intent==null)notFound();
        if(!actor.userId().equals(intent.actorId()))throw new com.law.file.security.FileAccessDeniedException("Upload intent belongs to another actor");
        if("COMPLETED".equals(intent.status())) {
            FileVersion completed=repository.findVersionById(intent.completedVersionId());if(completed==null)conflict("Completed version is unavailable");return completed;
        }
        if(!"REGISTERED".equals(intent.status()))conflict("Upload intent is not completable");
        access.requireCanWrite(intent.fileObjectId(),actor);
        FileStoragePort.StoredObject stored=storage.store(input,intent.objectKey(),intent.expectedSize(),intent.expectedSha256());
        FileVersion version=new FileVersion(null,intent.fileObjectId(),intent.targetVersionNo(),stored.objectKey(),intent.originalFileName(),
            intent.contentType(),stored.size(),stored.sha256(),actor.userId(),clock.instant());
        version=repository.insertVersion(version);if(version==null||version.fileVersionId()==null)conflict("File version already exists");
        if(repository.activateVersion(intent.fileObjectId(),intent.targetVersionNo(),Math.max(0,intent.targetVersionNo()-1))!=1)
            conflict("Unable to activate file version");
        if(repository.markUploadCompleted(uploadIntentId,version.fileVersionId())!=1)conflict("Upload was completed concurrently");
        return version;
    }

    @Transactional public FileBusinessRelation relate(Long fileObjectId,String actionId,String businessType,Long businessId,String materialType,String visibility,FileActor actor)
    {
        if(invalidText(actionId,128))throw new FileException("FILE_ACTION_ID_REQUIRED","actionId is required");
        if(fileObjectId==null||fileObjectId<=0||invalidText(businessType,64)||businessId==null||businessId<=0||invalidText(materialType,96))
            throw new FileException("FILE_RELATION_INVALID","Complete file relation metadata is required");
        access.requireCanWrite(fileObjectId,actor);access.requireCanWrite(businessType,businessId,actor);
        String normalized=normalizeVisibility(visibility);
        FileBusinessRelation replay=repository.findRelationByAction(actor.userId(),actionId);
        if(replay!=null) {
            if(replay.fileObjectId().equals(fileObjectId)&&replay.businessType().equals(businessType)&&replay.businessId().equals(businessId)
                    &&replay.materialType().equals(materialType)&&replay.visibility().equals(normalized))return replay;
            conflict("actionId is already used by another file relation");
        }
        FileBusinessRelation existing=repository.findRelation(fileObjectId,businessType,businessId,materialType,normalized);
        if(existing!=null)return existing;
        FileBusinessRelation relation=new FileBusinessRelation(null,actionId,fileObjectId,businessType,businessId,materialType,normalized,actor.userId(),actor.deptId(),true);
        FileBusinessRelation inserted=repository.insertRelation(relation);if(inserted==null) {
            existing=repository.findRelation(fileObjectId,businessType,businessId,materialType,normalized);
            if(existing!=null)return existing;conflict("Unable to relate file object");
        }
        return inserted;
    }

    @Transactional public AccessTokenView issueAccessToken(Long fileObjectId,String accessType,FileActor actor)
    {
        access.requireCanRead(fileObjectId,actor);String type=normalizeAccessType(accessType);
        FileVersion version=repository.findCurrentVersion(fileObjectId);if(version==null)notFound();
        String raw=tokens.generate(32);Instant expires=clock.instant().plus(Duration.ofMinutes(2));
        AccessToken token=repository.insertAccessToken(new AccessToken(null,fileObjectId,version.fileVersionId(),type,sha256(raw),actor.userId(),actor.deptId(),expires,null));
        if(token==null||token.accessTokenId()==null)conflict("Unable to issue access token");
        return new AccessTokenView(fileObjectId,type,raw,expires);
    }

    @Transactional public AccessContent open(String rawToken,FileActor actor)
    {return open(rawToken,actor,null);}
    @Transactional public AccessContent open(String rawToken,FileActor actor,String clientIp)
    {
        if(rawToken==null||rawToken.length()<8||rawToken.length()>256)throw new FileException("FILE_ACCESS_TOKEN_INVALID","Invalid access token");
        AccessToken token=repository.findAccessTokenForUpdate(sha256(rawToken));
        if(token==null||token.consumedAt()!=null||!token.expiresAt().isAfter(clock.instant()))
            throw new FileException("FILE_ACCESS_TOKEN_INVALID","Access token is expired or already used");
        if(!actor.userId().equals(token.actorId())||!java.util.Objects.equals(actor.deptId(),token.actorDeptId()))
            throw new com.law.file.security.FileAccessDeniedException("Access token belongs to another actor");
        List<FileBusinessRelation> authorized=access.requireCanRead(token.fileObjectId(),actor);
        FileVersion version=repository.findVersionById(token.fileVersionId());if(version==null)notFound();
        InputStream input=storage.read(version.objectKey());
        try {
            if(repository.consumeAccessToken(token.accessTokenId(),clock.instant())!=1)conflict("Access token was used concurrently");
            FileBusinessRelation context=authorized.get(0);
            if(repository.insertAccessLog(new AccessLog(null,token.fileObjectId(),token.fileVersionId(),context.businessType(),context.businessId(),token.accessType(),actor.userId(),actor.deptId(),clientIp,clock.instant()))!=1)
                conflict("Unable to record file access");
        } catch(RuntimeException error){try{input.close();}catch(Exception ignored){}throw error;}
        return new AccessContent(input,version.originalFileName(),version.contentType(),version.sizeBytes());
    }

    @Transactional(readOnly=true) public List<FileVersion> versions(Long fileObjectId,FileActor actor)
    {access.requireCanRead(fileObjectId,actor);List<FileVersion> values=repository.findVersions(fileObjectId);return values==null?List.of():List.copyOf(values);}
    @Transactional(readOnly=true) public List<AccessLog> accessLogs(Long fileObjectId,FileActor actor)
    {access.requireCanRead(fileObjectId,actor);List<AccessLog> values=repository.findAccessLogs(fileObjectId);return values==null?List.of():List.copyOf(values);}

    private UploadIntentView replay(FileUploadIntent prior,String fingerprint)
    {if(!fingerprint.equals(prior.requestFingerprint()))conflict("Idempotency key was reused with different metadata");return view(prior);}
    private static UploadIntentView view(FileUploadIntent intent){return new UploadIntentView(intent.uploadIntentId(),intent.fileObjectId(),intent.targetVersionNo(),intent.status());}
    private static String normalizeVisibility(String value){String v=value==null?"BUSINESS":value.toUpperCase(Locale.ROOT);if(!List.of("BUSINESS","DEPARTMENT","PRIVATE").contains(v))throw new FileException("FILE_VISIBILITY_INVALID","Invalid visibility");return v;}
    private static String normalizeAccessType(String value){String v=value==null?"DOWNLOAD":value.toUpperCase(Locale.ROOT);if(!List.of("PREVIEW","DOWNLOAD").contains(v))throw new FileException("FILE_ACCESS_TYPE_INVALID","Invalid access type");return v;}
    private static void validate(RegisterUploadCommand c){if(c==null||invalidText(c.idempotencyKey(),128)||invalidName(c.originalFileName())||invalidText(c.contentType(),160)||c.expectedSize()<0||!sha(c.expectedSha256())||invalidText(c.businessType(),64)||c.businessId()==null||c.businessId()<=0||invalidText(c.materialType(),96))throw new FileException("FILE_METADATA_INVALID","Complete upload metadata is required");}
    private static void validate(RegisterVersionCommand c){if(c==null||invalidText(c.idempotencyKey(),128)||invalidName(c.originalFileName())||invalidText(c.contentType(),160)||c.expectedSize()<0||!sha(c.expectedSha256()))throw new FileException("FILE_METADATA_INVALID","Complete version metadata is required");}
    private static boolean invalidName(String value){return invalidText(value,255)||value.indexOf('\r')>=0||value.indexOf('\n')>=0||value.indexOf('\0')>=0;}
    private static boolean invalidText(String value,int max){return blank(value)||value.length()>max;}
    private static boolean blank(String value){return value==null||value.isBlank();}private static boolean sha(String value){return value!=null&&value.matches("(?i)[0-9a-f]{64}");}
    private static String versionFingerprint(Long id,RegisterVersionCommand c){return sha256(String.join("\u001f",String.valueOf(id),c.originalFileName(),c.contentType(),String.valueOf(c.expectedSize()),c.expectedSha256()));}
    static String requestFingerprint(RegisterUploadCommand c){return sha256(String.join("\u001f",c.originalFileName(),c.contentType(),String.valueOf(c.expectedSize()),c.expectedSha256().toLowerCase(Locale.ROOT),c.businessType(),String.valueOf(c.businessId()),c.materialType(),normalizeVisibility(c.visibility())));}
    private static String objectKey(){return "objects/"+UUID.randomUUID();}
    private static String randomToken(int bytes){byte[] value=new byte[bytes];new SecureRandom().nextBytes(value);return Base64.getUrlEncoder().withoutPadding().encodeToString(value);}
    private static String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static void notFound(){throw new FileException("FILE_OBJECT_NOT_FOUND","File object was not found");}private static void conflict(String message){throw new FileException("FILE_CONFLICT",message);}
}
