package com.ruoyi.web.controller.file;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import com.law.file.application.FileObjectService;
import com.law.file.application.FileMaterialQueryService;
import com.law.file.application.FileObjectService.RegisterUploadCommand;
import com.law.file.application.FileObjectService.RegisterVersionCommand;
import com.law.file.application.FileObjectService.RetireFileObjectCommand;
import com.law.file.domain.FileObject.AccessContent;
import com.law.file.domain.FileObject.AccessLog;
import com.law.file.domain.FileObject.LifecycleAudit;
import com.law.file.domain.FileObject.FileActor;
import com.law.file.domain.FileObject.FileBusinessRelation;
import com.law.file.domain.FileObject.FileVersion;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.ip.IpUtils;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/files")
public class FileObjectController extends BaseController
{
    private static final Set<String> INLINE_PREVIEW_TYPES=Set.of(
        "application/pdf","image/png","image/jpeg","image/gif","image/webp","text/plain");
    private final FileObjectService service;
    private final FileMaterialQueryService materials;
    public FileObjectController(FileObjectService service){this(service,null);}
    @Autowired
    public FileObjectController(FileObjectService service,FileMaterialQueryService materials)
    {this.service=service;this.materials=materials;}

    @PreAuthorize("@ss.hasPermi('file:object:upload')")
    @PostMapping("/register")
    public AjaxResult register(@Valid @RequestBody RegisterUploadRequest request)
    {return success(service.registerUpload(request.command(),actor()));}

    @PreAuthorize("@ss.hasPermi('file:object:upload')")
    @PostMapping("/{uploadIntentId}/complete")
    public AjaxResult complete(@PathVariable String uploadIntentId,@RequestParam("file") MultipartFile file) throws Exception
    {try(InputStream input=file.getInputStream()){return success(version(service.completeUpload(uploadIntentId,input,
        file.getOriginalFilename(),file.getContentType(),actor())));}}

    @PreAuthorize("@ss.hasPermi('file:object:upload')")
    @PostMapping("/{fileObjectId}/versions")
    public AjaxResult addVersion(@PathVariable Long fileObjectId,@Valid @RequestBody RegisterVersionRequest request)
    {return success(service.addVersion(fileObjectId,request.command(),actor()));}

    @PreAuthorize("@ss.hasPermi('file:object:relate')")
    @PostMapping("/{fileObjectId}/relations")
    public AjaxResult relate(@PathVariable Long fileObjectId,@Valid @RequestBody RelationRequest request)
    {return success(relation(service.relate(fileObjectId,request.actionId(),request.businessType(),request.businessId(),request.materialType(),request.visibility(),actor())));}

    @PreAuthorize("@ss.hasPermi('file:object:relate')")
    @PostMapping("/{fileObjectId}/relations/{relationId}/revoke")
    public AjaxResult revoke(@PathVariable Long fileObjectId,@PathVariable Long relationId,@Valid @RequestBody RevokeRelationRequest request)
    {return success(relation(service.revokeRelation(fileObjectId,relationId,request.actionId(),actor())));}

    @PreAuthorize("@ss.hasPermi('file:object:retire')")
    @PostMapping("/{fileObjectId}/retire")
    public AjaxResult retire(@PathVariable Long fileObjectId,@Valid @RequestBody RetireFileObjectRequest request)
    {return success(service.retire(fileObjectId,request.command(),actor()));}

    @PreAuthorize("@ss.hasPermi('file:object:read')")
    @PostMapping("/{fileObjectId}/access-token")
    public AjaxResult token(@PathVariable Long fileObjectId,@Valid @RequestBody AccessTokenRequest request)
    {return success(service.issueAccessToken(fileObjectId,request.relationId(),request.accessType(),actor()));}

    @PreAuthorize("@ss.hasPermi('file:object:read')")
    @GetMapping("/materials")
    public AjaxResult materials(@RequestParam String businessType,@RequestParam Long businessId)
    {
        if(materials==null)throw new IllegalStateException("File material query service is unavailable");
        return success(materials.list(businessType,businessId,actor()));
    }

    @PreAuthorize("@ss.hasPermi('file:object:read')")
    @GetMapping("/{fileObjectId}/preview-token")
    public AjaxResult previewToken(@PathVariable Long fileObjectId,@RequestParam Long relationId)
    {return success(service.issueAccessToken(fileObjectId,relationId,"PREVIEW",actor()));}

    @PreAuthorize("@ss.hasPermi('file:object:read')")
    @GetMapping("/{fileObjectId}/download-token")
    public AjaxResult downloadToken(@PathVariable Long fileObjectId,@RequestParam Long relationId)
    {return success(service.issueAccessToken(fileObjectId,relationId,"DOWNLOAD",actor()));}

    @PreAuthorize("@ss.hasPermi('file:object:read')")
    @GetMapping("/{fileObjectId}/versions")
    public AjaxResult versions(@PathVariable Long fileObjectId)
    {return success(service.versions(fileObjectId,actor()).stream().map(FileObjectController::version).toList());}

    @PreAuthorize("@ss.hasPermi('file:object:audit')")
    @GetMapping("/{fileObjectId}/access-logs")
    public AjaxResult accessLogs(@PathVariable Long fileObjectId)
    {return success(service.accessLogs(fileObjectId,actor()).stream().map(FileObjectController::audit).toList());}

    @PreAuthorize("@ss.hasPermi('file:object:audit')")
    @GetMapping("/{fileObjectId}/lifecycle-audits")
    public AjaxResult lifecycleAudits(@PathVariable Long fileObjectId)
    {return success(service.lifecycleAudits(fileObjectId,actor()).stream().map(FileObjectController::lifecycle).toList());}

    @PreAuthorize("@ss.hasPermi('file:object:read')")
    @GetMapping("/access/{token}")
    public ResponseEntity<StreamingResponseBody> open(@PathVariable String token)
    {
        AccessContent content=service.open(token,actor(),IpUtils.getIpAddr());
        StreamingResponseBody body=output->{
            try(InputStream input=content.input()) {
                input.transferTo(output);
            } catch(Exception error) {
                try{service.completeAccess(content.receipt(),false,error.getClass().getSimpleName());}catch(RuntimeException ignored){}
                throw error;
            }
            service.completeAccess(content.receipt(),true,null);
        };
        ResponsePolicy policy=responsePolicy(content);
        ContentDisposition disposition=policy.inline()
            ?ContentDisposition.inline().filename(content.fileName(),StandardCharsets.UTF_8).build()
            :ContentDisposition.attachment().filename(content.fileName(),StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(policy.mediaType()).contentLength(content.sizeBytes())
            .header(HttpHeaders.CONTENT_DISPOSITION,disposition.toString())
            .header(HttpHeaders.CACHE_CONTROL,"no-store")
            .header("X-Content-Type-Options","nosniff")
            .header("Content-Security-Policy","sandbox; default-src 'none'")
            .header("Referrer-Policy","no-referrer").body(body);
    }

    private static ResponsePolicy responsePolicy(AccessContent content)
    {
        MediaType declared=parseMediaType(content.contentType());
        String normalized=(declared.getType()+"/"+declared.getSubtype()).toLowerCase(Locale.ROOT);
        boolean preview="PREVIEW".equals(content.accessType());
        boolean inline=preview&&INLINE_PREVIEW_TYPES.contains(normalized);
        return new ResponsePolicy(preview&&!inline?MediaType.APPLICATION_OCTET_STREAM:declared,inline);
    }

    private static MediaType parseMediaType(String value)
    {try{return MediaType.parseMediaType(value);}catch(Exception ignored){return MediaType.APPLICATION_OCTET_STREAM;}}

    private FileActor actor(){return new FileActor(SecurityUtils.getUserId(),SecurityUtils.getUsername(),SecurityUtils.getDeptId());}
    private static VersionView version(FileVersion value){return new VersionView(value.fileObjectId(),value.fileVersionId(),value.versionNo(),value.originalFileName(),value.contentType(),value.sizeBytes(),value.sha256(),value.changeDescription(),value.createdAt());}
    private static RelationView relation(FileBusinessRelation value){return new RelationView(value.relationId(),value.fileObjectId(),value.businessType(),value.businessId(),value.materialType(),value.visibility());}
    private static AuditView audit(AccessLog value){return new AuditView(value.accessLogId(),value.accessSessionId(),value.fileObjectId(),value.fileVersionId(),value.relationId(),value.businessType(),value.businessId(),value.accessType(),value.eventType(),value.outcome(),value.failureCode(),value.actorId(),value.actorDeptId(),value.clientIp(),value.accessedAt());}
    private static LifecycleView lifecycle(LifecycleAudit value){return new LifecycleView(value.lifecycleAuditId(),value.fileObjectId(),value.fileVersionId(),value.relationId(),value.actionId(),value.eventType(),value.details(),value.actorId(),value.actorDeptId(),value.occurredAt());}

    public record RegisterUploadRequest(@NotBlank String actionId,@NotBlank String originalFileName,@NotBlank String contentType,
        @PositiveOrZero long expectedSize,@Pattern(regexp="(?i)[0-9a-f]{64}") String expectedSha256,@NotBlank String businessType,
        @NotNull @Positive Long businessId,@NotBlank String materialType,String visibility)
    {RegisterUploadCommand command(){return new RegisterUploadCommand(actionId,originalFileName,contentType,expectedSize,expectedSha256,businessType,businessId,materialType,visibility);}}
    public record RegisterVersionRequest(@NotBlank String actionId,@NotBlank String originalFileName,@NotBlank String contentType,
        @PositiveOrZero long expectedSize,@Pattern(regexp="(?i)[0-9a-f]{64}") String expectedSha256,@NotBlank String changeDescription)
    {RegisterVersionCommand command(){return new RegisterVersionCommand(actionId,originalFileName,contentType,expectedSize,expectedSha256,changeDescription);}}
    public record RelationRequest(@NotBlank String actionId,@NotBlank String businessType,@NotNull @Positive Long businessId,@NotBlank String materialType,String visibility) { }
    public record RevokeRelationRequest(@NotBlank String actionId) { }
    public record RetireFileObjectRequest(@NotBlank String actionId,@NotNull @Positive Long relationId,
        boolean retireObjectIfUnreferenced)
    {RetireFileObjectCommand command(){return new RetireFileObjectCommand(actionId,relationId,retireObjectIfUnreferenced);}}
    public record AccessTokenRequest(@NotNull @Positive Long relationId,@Pattern(regexp="(?i)PREVIEW|DOWNLOAD") String accessType) { }
    public record VersionView(Long fileObjectId,Long fileVersionId,int versionNo,String originalFileName,String contentType,long sizeBytes,String sha256,String changeDescription,java.time.Instant createdAt) { }
    public record RelationView(Long relationId,Long fileObjectId,String businessType,Long businessId,String materialType,String visibility) { }
    public record AuditView(Long accessLogId,String accessSessionId,Long fileObjectId,Long fileVersionId,Long relationId,String businessType,Long businessId,String accessType,String eventType,String outcome,String failureCode,Long actorId,Long actorDeptId,String clientIp,java.time.Instant accessedAt) { }
    public record LifecycleView(Long lifecycleAuditId,Long fileObjectId,Long fileVersionId,Long relationId,String actionId,String eventType,String details,Long actorId,Long actorDeptId,java.time.Instant occurredAt) { }
    private record ResponsePolicy(MediaType mediaType,boolean inline) { }
}
