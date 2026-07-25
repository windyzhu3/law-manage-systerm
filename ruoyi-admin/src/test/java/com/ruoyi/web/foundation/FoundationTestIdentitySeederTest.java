package com.ruoyi.web.foundation;

import static com.ruoyi.system.foundation.FoundationTestIdentityErrorCode.FOUNDATION_TEST_IDENTITIES_PROFILE_FORBIDDEN;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ruoyi.system.foundation.FoundationTestIdentityException;
import com.ruoyi.system.foundation.FoundationTestIdentityProvisioningResult;
import com.ruoyi.system.foundation.FoundationTestIdentityProvisioningService;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.mock.env.MockEnvironment;

class FoundationTestIdentitySeederTest
{
    private static final String PASSWORD = "Task5SensitivePassword!";

    private FoundationTestIdentityProvisioningService provisioningService;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp()
    {
        provisioningService = mock(FoundationTestIdentityProvisioningService.class);
        when(provisioningService.provision(PASSWORD)).thenReturn(new FoundationTestIdentityProvisioningResult(3, 4, 5));
        Logger logger = (Logger) LoggerFactory.getLogger(FoundationTestIdentitySeeder.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown()
    {
        Logger logger = (Logger) LoggerFactory.getLogger(FoundationTestIdentitySeeder.class);
        logger.detachAppender(logAppender);
        logAppender.stop();
    }

    @ParameterizedTest
    @MethodSource("allowedProfiles")
    void provisionsOnceForAllowedProfiles(String profiles)
    {
        FoundationTestIdentitySeeder seeder = seeder(profiles.split(","));

        assertDoesNotThrow(() -> seeder.run(null));

        verify(provisioningService, times(1)).provision(eq(PASSWORD));
        List<String> messages = logAppender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertEquals(List.of("Foundation test identities seeded: created=3, reused=4, repaired=5"), messages);
        assertFalse(messages.stream().anyMatch(message -> message.contains(PASSWORD)));
    }

    @ParameterizedTest
    @MethodSource("forbiddenProfiles")
    void rejectsForbiddenProfilesBeforeProvisioningWithoutLeakingPassword(String profiles)
    {
        FoundationTestIdentitySeeder seeder = seeder(profiles.split(","));

        FoundationTestIdentityException exception = assertThrows(FoundationTestIdentityException.class,
            () -> seeder.run(null));

        assertEquals(FOUNDATION_TEST_IDENTITIES_PROFILE_FORBIDDEN, exception.getCode());
        assertFalse(exception.getMessage().contains(PASSWORD));
        assertFalse(logAppender.list.stream().map(ILoggingEvent::getFormattedMessage)
            .anyMatch(message -> message.contains(PASSWORD)));
        verifyNoInteractions(provisioningService);
    }

    private FoundationTestIdentitySeeder seeder(String... profiles)
    {
        FoundationTestIdentityProperties properties = new FoundationTestIdentityProperties();
        properties.setEnabled(true);
        properties.setPassword(PASSWORD);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return new FoundationTestIdentitySeeder(provisioningService, properties, environment);
    }

    private static Stream<String> allowedProfiles()
    {
        return Stream.of("local", "test", "e2e", "local,test");
    }

    private static Stream<String> forbiddenProfiles()
    {
        return Stream.of("druid", "prod", "local,prod", "local-dev");
    }
}
