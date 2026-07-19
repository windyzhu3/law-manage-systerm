package com.ruoyi.web.foundation;

import com.ruoyi.system.foundation.FoundationTestIdentityProvisioningService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@EnableConfigurationProperties(FoundationTestIdentityProperties.class)
@ConditionalOnProperty(prefix = "foundation.test-identities", name = "enabled", havingValue = "true")
public class FoundationTestIdentityConfiguration
{
    @Bean
    public FoundationTestIdentitySeeder foundationTestIdentitySeeder(
        FoundationTestIdentityProvisioningService provisioningService,
        FoundationTestIdentityProperties properties, Environment environment)
    {
        return new FoundationTestIdentitySeeder(provisioningService, properties, environment);
    }
}
