package com.ruoyi.web.foundation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.ruoyi.system.foundation.FoundationTestIdentityProvisioningService;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class FoundationTestIdentityConfigurationTest
{
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(FoundationTestIdentityConfiguration.class)
        .withBean(FoundationTestIdentityProvisioningService.class,
            () -> mock(FoundationTestIdentityProvisioningService.class));

    @Test
    void doesNotCreateSeederWhenFeatureIsDisabledByDefault()
    {
        contextRunner.run(context -> assertFalse(context.containsBean("foundationTestIdentitySeeder")));
    }

    @Test
    void createsSeederOnlyWhenFeatureIsExplicitlyEnabled()
    {
        contextRunner.withPropertyValues("foundation.test-identities.enabled=true")
            .run(context -> {
                assertNotNull(context.getBean(FoundationTestIdentitySeeder.class));
                assertEquals(true, context.getBean(FoundationTestIdentityProperties.class).isEnabled());
            });
    }

    @Test
    void propertiesContainOnlyEnabledAndPasswordWithoutPasswordToString()
    {
        Set<String> fieldNames = Arrays.stream(FoundationTestIdentityProperties.class.getDeclaredFields())
            .map(Field::getName)
            .collect(Collectors.toSet());

        assertEquals(Set.of("enabled", "password"), fieldNames);
        assertThrows(NoSuchMethodException.class,
            () -> FoundationTestIdentityProperties.class.getDeclaredMethod("toString"));
    }
}
