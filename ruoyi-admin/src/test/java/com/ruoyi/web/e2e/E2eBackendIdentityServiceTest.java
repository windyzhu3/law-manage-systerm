package com.ruoyi.web.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;

class E2eBackendIdentityServiceTest
{
    @Test
    void identity_is_signed_from_the_actual_jdbc_database_and_run_markers() throws Exception
    {
        DataSource dataSource=mock(DataSource.class);
        Connection connection=mock(Connection.class);
        Statement statement=mock(Statement.class);
        ResultSet resultSet=mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getCatalog()).thenReturn("actual_catalog_e2e");
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("select database()")).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString(1)).thenReturn("actual_schema_e2e");
        var properties=new E2eBackendIdentityProperties();
        properties.setSecret("a-dedicated-e2e-secret-that-is-long-enough");

        var service=new E2eBackendIdentityService(dataSource,properties,"0.20.54");
        var identity=service.identity("a-dedicated-e2e-secret-that-is-long-enough","nonce-1","fixture-1");

        assertEquals("actual_catalog_e2e",identity.catalog());
        assertEquals("actual_schema_e2e",identity.schema());
        assertEquals("nonce-1",identity.nonce());
        assertEquals("fixture-1",identity.marker());
        assertEquals(E2eBackendIdentityService.proof(properties.getSecret(),identity),identity.proof());
    }

    @Test
    void wrong_secret_is_rejected_before_database_access()
    {
        DataSource dataSource=mock(DataSource.class);
        var properties=new E2eBackendIdentityProperties();
        properties.setSecret("a-dedicated-e2e-secret-that-is-long-enough");
        var service=new E2eBackendIdentityService(dataSource,properties,"0.20.54");

        assertThrows(AccessDeniedException.class,() -> service.identity("wrong","nonce-1","fixture-1"));
    }

    @Test
    void controller_is_profile_restricted_to_non_production_e2e_profiles()
    {
        Profile profile=E2eBackendIdentityController.class.getAnnotation(Profile.class);
        assertEquals(java.util.Set.of("e2e","test"),java.util.Set.of(profile.value()));
    }

    @Test
    void cleanup_rejects_a_wrong_secret_before_touching_the_file_lifecycle()
    {
        E2eBackendIdentityService identity=mock(E2eBackendIdentityService.class);
        E2eOwnedFileCleanupService cleanup=mock(E2eOwnedFileCleanupService.class);
        when(identity.identity("wrong","nonce-1","fixture-1"))
            .thenThrow(new AccessDeniedException("bad secret"));
        E2eBackendIdentityController controller=new E2eBackendIdentityController(identity,cleanup);

        assertThrows(AccessDeniedException.class,()->controller.retireOwnedFixture(
            7L,"wrong","nonce-1","fixture-1",
            new E2eBackendIdentityController.OwnedFileCleanupRequest(
                "cleanup-7-3",3L,"abc","actual_e2e")));
        verify(cleanup,never()).retire(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
    }

    @Test
    void cleanup_rejects_a_wrong_database_binding_before_touching_the_file_lifecycle()
    {
        E2eBackendIdentityService identity=mock(E2eBackendIdentityService.class);
        E2eOwnedFileCleanupService cleanup=mock(E2eOwnedFileCleanupService.class);
        when(identity.identity("secret","nonce-1","fixture-1")).thenReturn(
            new E2eBackendIdentityService.BackendIdentity(
                "actual_e2e","actual_e2e","nonce-1","fixture-1","0.20.57","proof"));
        E2eBackendIdentityController controller=new E2eBackendIdentityController(identity,cleanup);

        assertThrows(AccessDeniedException.class,()->controller.retireOwnedFixture(
            7L,"secret","nonce-1","fixture-1",
            new E2eBackendIdentityController.OwnedFileCleanupRequest(
                "cleanup-7-3",3L,"abc","neighbour_e2e")));
        verify(cleanup,never()).retire(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
    }

    @Test
    void cleanup_endpoint_requires_the_dedicated_identity_permission()
        throws Exception
    {
        var method=E2eBackendIdentityController.class.getMethod("retireOwnedFixture",
            Long.class,String.class,String.class,String.class,
            E2eBackendIdentityController.OwnedFileCleanupRequest.class);
        assertEquals("@ss.hasPermi('foundation:e2e:identity')",
            method.getAnnotation(PreAuthorize.class).value());
        assertEquals(java.util.List.of("/files/{fileObjectId}/retire-owned-fixture"),
            java.util.List.of(method.getAnnotation(PostMapping.class).value()));
    }
}
