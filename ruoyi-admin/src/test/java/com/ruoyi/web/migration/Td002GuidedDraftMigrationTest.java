package com.ruoyi.web.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;

import db.migration.V0_20_73__SeedTd002GuidedDraft;

class Td002GuidedDraftMigrationTest
{
    @Test
    void exposesTheExpectedFlywayIdentity()
    {
        V0_20_73__SeedTd002GuidedDraft migration=new V0_20_73__SeedTd002GuidedDraft();

        assertThat(migration.getVersion().getVersion()).isEqualTo("0.20.73");
        assertThat(migration.getDescription()).isEqualTo("SeedTd002GuidedDraft");
    }

    @Test
    void failsClosedWhenTheCurrentPublishedTd002VersionIsUnavailable() throws Exception
    {
        Context context=mock(Context.class);
        Connection connection=mock(Connection.class);
        PreparedStatement select=mock(PreparedStatement.class);
        ResultSet rows=mock(ResultSet.class);
        when(context.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(select);
        when(select.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(false);

        assertThatThrownBy(()->new V0_20_73__SeedTd002GuidedDraft().migrate(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The current published TD-002 version is unavailable");
    }

    @Test
    void failsClosedWhenTheCurrentPublishedTd001VersionIsUnavailable() throws Exception
    {
        Context context=mock(Context.class);
        Connection connection=mock(Connection.class);
        PreparedStatement td002Select=mock(PreparedStatement.class);
        PreparedStatement td001Select=mock(PreparedStatement.class);
        ResultSet td002Rows=mock(ResultSet.class);
        ResultSet td001Rows=mock(ResultSet.class);
        when(context.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(td002Select,td001Select);
        when(td002Select.executeQuery()).thenReturn(td002Rows);
        when(td001Select.executeQuery()).thenReturn(td001Rows);
        when(td002Rows.next()).thenReturn(true,false);
        when(td002Rows.getLong(1)).thenReturn(2002L);
        when(td002Rows.getLong(2)).thenReturn(2202L);
        when(td001Rows.next()).thenReturn(false);

        assertThatThrownBy(()->new V0_20_73__SeedTd002GuidedDraft().migrate(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The current published TD-001 version is unavailable");
    }
}
