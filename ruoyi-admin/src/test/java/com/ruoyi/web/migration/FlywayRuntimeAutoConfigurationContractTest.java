package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class FlywayRuntimeAutoConfigurationContractTest
{
    @Test
    void packagesSpringBootFlywayAutoConfigurationForApplicationStartup()
    {
        assertDoesNotThrow(() -> Class.forName(
            "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"));
    }
}
