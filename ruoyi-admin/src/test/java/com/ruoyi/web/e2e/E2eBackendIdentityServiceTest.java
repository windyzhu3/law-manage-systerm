package com.ruoyi.web.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;

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
}
