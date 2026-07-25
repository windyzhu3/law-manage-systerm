package com.ruoyi.web.e2e;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.common.core.domain.AjaxResult;

@RestController
@Profile({"e2e","test"})
@ConditionalOnProperty(prefix="foundation.e2e-identity",name="enabled",havingValue="true")
@RequestMapping("/foundation/e2e")
public class E2eBackendIdentityController
{
    private final E2eBackendIdentityService service;

    public E2eBackendIdentityController(DataSource dataSource,E2eBackendIdentityProperties properties,
        @Value("${ruoyi.version:unknown}") String buildVersion)
    {this.service=new E2eBackendIdentityService(dataSource,properties,buildVersion);}

    @PreAuthorize("@ss.hasPermi('foundation:e2e:identity')")
    @GetMapping("/backend-identity")
    public AjaxResult identity(
        @RequestHeader("X-E2E-Identity-Secret") String secret,
        @RequestHeader("X-E2E-Run-Nonce") String nonce,
        @RequestHeader("X-E2E-Fixture-Marker") String marker)
    {return AjaxResult.success(service.identity(secret,nonce,marker));}
}
