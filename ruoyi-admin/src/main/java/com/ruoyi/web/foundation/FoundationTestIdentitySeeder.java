package com.ruoyi.web.foundation;

import com.ruoyi.system.foundation.FoundationTestIdentityErrorCode;
import com.ruoyi.system.foundation.FoundationTestIdentityException;
import com.ruoyi.system.foundation.FoundationTestIdentityProvisioningResult;
import com.ruoyi.system.foundation.FoundationTestIdentityProvisioningService;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;

public class FoundationTestIdentitySeeder implements ApplicationRunner
{
    private static final Logger LOG = LoggerFactory.getLogger(FoundationTestIdentitySeeder.class);

    private final FoundationTestIdentityProvisioningService provisioningService;
    private final FoundationTestIdentityProperties properties;
    private final Environment environment;

    public FoundationTestIdentitySeeder(FoundationTestIdentityProvisioningService provisioningService,
        FoundationTestIdentityProperties properties, Environment environment)
    {
        this.provisioningService = provisioningService;
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args)
    {
        String[] activeProfiles = environment.getActiveProfiles();
        if (hasProfile(activeProfiles, "prod")
            || (!hasProfile(activeProfiles, "local") && !hasProfile(activeProfiles, "test")
                && !hasProfile(activeProfiles, "e2e")))
        {
            throw new FoundationTestIdentityException(
                FoundationTestIdentityErrorCode.FOUNDATION_TEST_IDENTITIES_PROFILE_FORBIDDEN, null);
        }

        FoundationTestIdentityProvisioningResult result = provisioningService.provision(properties.getPassword());
        LOG.info("Foundation test identities seeded: created={}, reused={}, repaired={}", result.created(),
            result.reused(), result.repaired());
    }

    private boolean hasProfile(String[] profiles, String expectedProfile)
    {
        return Arrays.stream(profiles).anyMatch(expectedProfile::equals);
    }
}
