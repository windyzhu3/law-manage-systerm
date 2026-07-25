package com.ruoyi.web.e2e;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

class E2eBackendIdentityPermissionSeederTest
{
    @Test void fixture_permission_is_provisioned_only_by_the_profile_restricted_runner()
    {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        new E2eBackendIdentityPermissionSeeder(jdbc).run(null);
        verify(jdbc).update(contains("foundation:e2e:identity"));
        Profile profile=E2eBackendIdentityPermissionSeeder.class.getAnnotation(Profile.class);
        org.junit.jupiter.api.Assertions.assertEquals(
            java.util.Set.of("e2e","test"),java.util.Set.of(profile.value()));
    }
}
