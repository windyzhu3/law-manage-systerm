package com.ruoyi.web.e2e;

import javax.sql.DataSource;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import com.law.file.application.FileObjectService;
import com.law.file.domain.FileObject.FileActor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;

@RestController
@Profile({"e2e","test"})
@ConditionalOnProperty(prefix="foundation.e2e-identity",name="enabled",havingValue="true")
@RequestMapping("/foundation/e2e")
public class E2eBackendIdentityController
{
    private final E2eBackendIdentityService service;
    private final E2eOwnedFileCleanupService cleanup;

    @Autowired
    public E2eBackendIdentityController(DataSource dataSource,E2eBackendIdentityProperties properties,
        @Value("${ruoyi.version:unknown}") String buildVersion,FileObjectService files,
        PlatformTransactionManager transactionManager)
    {
        this(new E2eBackendIdentityService(dataSource,properties,buildVersion),
            new E2eOwnedFileCleanupService(new JdbcTemplate(dataSource),files,
                new TransactionTemplate(transactionManager)));
    }

    E2eBackendIdentityController(E2eBackendIdentityService service,E2eOwnedFileCleanupService cleanup)
    {this.service=service;this.cleanup=cleanup;}

    @PreAuthorize("@ss.hasPermi('foundation:e2e:identity')")
    @GetMapping("/backend-identity")
    public AjaxResult identity(
        @RequestHeader("X-E2E-Identity-Secret") String secret,
        @RequestHeader("X-E2E-Run-Nonce") String nonce,
        @RequestHeader("X-E2E-Fixture-Marker") String marker)
    {return AjaxResult.success(service.identity(secret,nonce,marker));}

    @PreAuthorize("@ss.hasPermi('foundation:e2e:identity')")
    @PostMapping("/files/{fileObjectId}/retire-owned-fixture")
    public AjaxResult retireOwnedFixture(
        @PathVariable Long fileObjectId,
        @RequestHeader("X-E2E-Identity-Secret") String secret,
        @RequestHeader("X-E2E-Run-Nonce") String nonce,
        @RequestHeader("X-E2E-Fixture-Marker") String marker,
        @Valid @RequestBody OwnedFileCleanupRequest request)
    {
        E2eBackendIdentityService.BackendIdentity identity=service.identity(secret,nonce,marker);
        if(!request.database().equals(identity.catalog())||!request.database().equals(identity.schema()))
            throw new AccessDeniedException("E2E cleanup database identity mismatch");
        return AjaxResult.success(cleanup.retire(fileObjectId,
            new E2eOwnedFileCleanupService.CleanupCommand(
                request.actionId(),request.relationId(),request.runId()),
            new FileActor(SecurityUtils.getUserId(),SecurityUtils.getUsername(),SecurityUtils.getDeptId())));
    }

    public record OwnedFileCleanupRequest(
        @NotBlank String actionId,
        @NotNull @Positive Long relationId,
        @NotBlank String runId,
        @NotBlank String database){}
}
