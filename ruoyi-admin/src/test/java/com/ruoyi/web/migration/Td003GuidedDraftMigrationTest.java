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

import db.migration.V0_20_74__SeedTd003GuidedDraft;

class Td003GuidedDraftMigrationTest
{
    @Test
    void exposesTheNextForwardOnlyFlywayIdentity() throws Exception
    {
        V0_20_74__SeedTd003GuidedDraft migration=new V0_20_74__SeedTd003GuidedDraft();

        assertThat(migration.getVersion().getVersion()).isEqualTo("0.20.74");
        assertThat(migration.getDescription()).isEqualTo("SeedTd003GuidedDraft");
    }

    @Test
    void failsClosedWhenTheCurrentPublishedTd003VersionIsUnavailable() throws Exception
    {
        Context context=mock(Context.class);
        Connection connection=mock(Connection.class);
        PreparedStatement select=mock(PreparedStatement.class);
        ResultSet rows=mock(ResultSet.class);
        when(context.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(select);
        when(select.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(false);

        assertThatThrownBy(()->new V0_20_74__SeedTd003GuidedDraft().migrate(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The current published TD-003 version is unavailable");
    }

    @Test
    void failsClosedWhenTheCurrentPublishedTd004TargetIsUnavailable() throws Exception
    {
        Context context=mock(Context.class);
        Connection connection=mock(Connection.class);
        PreparedStatement td003Select=mock(PreparedStatement.class);
        PreparedStatement td004Select=mock(PreparedStatement.class);
        ResultSet td003Rows=mock(ResultSet.class);
        ResultSet td004Rows=mock(ResultSet.class);
        when(context.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(td003Select,td004Select);
        when(td003Select.executeQuery()).thenReturn(td003Rows);
        when(td004Select.executeQuery()).thenReturn(td004Rows);
        when(td003Rows.next()).thenReturn(true,false);
        when(td003Rows.getLong(1)).thenReturn(2003L);
        when(td003Rows.getLong(2)).thenReturn(2203L);
        when(td004Rows.next()).thenReturn(false);

        assertThatThrownBy(()->new V0_20_74__SeedTd003GuidedDraft().migrate(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The current published TD-004 version is unavailable");
    }

    @Test
    void failsClosedWhenTheActiveRetryCompletionRecipeIsUnavailable() throws Exception
    {
        Context context=mock(Context.class);
        Connection connection=mock(Connection.class);
        PreparedStatement td003Select=mock(PreparedStatement.class);
        PreparedStatement td004Select=mock(PreparedStatement.class);
        PreparedStatement recipeSelect=mock(PreparedStatement.class);
        ResultSet td003Rows=publishedRows(2003L,2203L);
        ResultSet td004Rows=publishedRows(2004L,2204L);
        ResultSet recipeRows=mock(ResultSet.class);
        when(context.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString()))
                .thenReturn(td003Select,td004Select,recipeSelect);
        when(td003Select.executeQuery()).thenReturn(td003Rows);
        when(td004Select.executeQuery()).thenReturn(td004Rows);
        when(recipeSelect.executeQuery()).thenReturn(recipeRows);
        when(recipeRows.next()).thenReturn(false);

        assertThatThrownBy(()->new V0_20_74__SeedTd003GuidedDraft().migrate(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The active LEAD_RETRY_READY recipe is unavailable");
    }

    @Test
    void failsClosedWhenAnIndependentTd003TriggerIsEnabled() throws Exception
    {
        Context context=mock(Context.class);
        Connection connection=mock(Connection.class);
        PreparedStatement td003Select=mock(PreparedStatement.class);
        PreparedStatement td004Select=mock(PreparedStatement.class);
        PreparedStatement recipeSelect=mock(PreparedStatement.class);
        PreparedStatement triggerSelect=mock(PreparedStatement.class);
        ResultSet td003Rows=publishedRows(2003L,2203L);
        ResultSet td004Rows=publishedRows(2004L,2204L);
        ResultSet recipeRows=mock(ResultSet.class);
        ResultSet triggerRows=mock(ResultSet.class);
        when(context.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString()))
                .thenReturn(td003Select,td004Select,recipeSelect,triggerSelect);
        when(td003Select.executeQuery()).thenReturn(td003Rows);
        when(td004Select.executeQuery()).thenReturn(td004Rows);
        when(recipeSelect.executeQuery()).thenReturn(recipeRows);
        when(recipeRows.next()).thenReturn(true,false);
        when(recipeRows.getString(1)).thenReturn("{\"requiredFields\":[\"contactResult\"],"
                +"\"requiredAttachments\":[\"CONTACT_PROOF\"],"
                +"\"validatorRefs\":[\"LeadFirstContactValidator\"],"
                +"\"conditionalRules\":[{\"when\":{\"field\":\"contactResult\","
                +"\"operator\":\"EQ\",\"value\":\"CONNECTED\"},"
                +"\"requiredFields\":[\"name\",\"city\",\"demand\",\"visited\"]}]}");
        when(triggerSelect.executeQuery()).thenReturn(triggerRows);
        when(triggerRows.next()).thenReturn(true);
        when(triggerRows.getLong(1)).thenReturn(1L);

        assertThatThrownBy(()->new V0_20_74__SeedTd003GuidedDraft().migrate(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TD-003 must not have an enabled independent trigger");
    }

    @Test
    void failsClosedWhenRetryRecipeHasNoConnectedConditionalRule() throws Exception
    {
        assertMalformedRecipe("{\"requiredFields\":[\"contactResult\"],"
                +"\"requiredAttachments\":[\"CONTACT_PROOF\"],"
                +"\"validatorRefs\":[\"LeadFirstContactValidator\"],"
                +"\"conditionalRules\":[]}");
    }

    @Test
    void failsClosedWhenRetryRecipeUsesTheWrongOperatorOrOutcome() throws Exception
    {
        assertMalformedRecipe(recipe("NE","CONNECTED","name","city","demand","visited"));
        assertMalformedRecipe(recipe("EQ","UNREACHABLE","name","city","demand","visited"));
    }

    @Test
    void failsClosedWhenConnectedFieldsAreMissingOrExtended() throws Exception
    {
        assertMalformedRecipe(recipe("EQ","CONNECTED","name","city","demand"));
        assertMalformedRecipe(recipe("EQ","CONNECTED","name","city","demand","visited","mobile"));
    }

    @Test
    void failsClosedWhenContactProofIsNotRequired() throws Exception
    {
        assertMalformedRecipe("{\"requiredFields\":[\"contactResult\"],"
                +"\"requiredAttachments\":[],"
                +"\"validatorRefs\":[\"LeadFirstContactValidator\"],"
                +"\"conditionalRules\":[{\"when\":{\"field\":\"contactResult\","
                +"\"operator\":\"EQ\",\"value\":\"CONNECTED\"},"
                +"\"requiredFields\":[\"name\",\"city\",\"demand\",\"visited\"]}]} ");
    }

    @Test
    void failsClosedWhenBaseContactResultIsMissingOrDuplicated() throws Exception
    {
        assertMalformedRecipe(governedRecipeWithBaseFields(""));
        assertMalformedRecipe(governedRecipeWithBaseFields("\"contactResult\",\"contactResult\""));
    }

    @Test
    void failsClosedWhenTheConditionalRuleStructureIsMalformed() throws Exception
    {
        assertMalformedRecipe("{\"requiredFields\":[\"contactResult\"],"
                +"\"requiredAttachments\":[\"CONTACT_PROOF\"],"
                +"\"validatorRefs\":[\"LeadFirstContactValidator\"],"
                +"\"conditionalRules\":[\"CONNECTED\"]}");
    }

    private void assertMalformedRecipe(String recipe) throws Exception
    {
        Context context=mock(Context.class);
        Connection connection=mock(Connection.class);
        PreparedStatement td003Select=mock(PreparedStatement.class);
        PreparedStatement td004Select=mock(PreparedStatement.class);
        PreparedStatement recipeSelect=mock(PreparedStatement.class);
        ResultSet td003Rows=publishedRows(2003L,2203L);
        ResultSet td004Rows=publishedRows(2004L,2204L);
        ResultSet recipeRows=mock(ResultSet.class);
        when(context.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString()))
                .thenReturn(td003Select,td004Select,recipeSelect);
        when(td003Select.executeQuery()).thenReturn(td003Rows);
        when(td004Select.executeQuery()).thenReturn(td004Rows);
        when(recipeSelect.executeQuery()).thenReturn(recipeRows);
        when(recipeRows.next()).thenReturn(true,false);
        when(recipeRows.getString(1)).thenReturn(recipe);

        assertThatThrownBy(()->new V0_20_74__SeedTd003GuidedDraft().migrate(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("LEAD_RETRY_READY does not match the governed retry completion contract");
    }

    private String recipe(String operator,String value,String... requiredFields)
    {
        return "{\"requiredFields\":[\"contactResult\"],"
                +"\"requiredAttachments\":[\"CONTACT_PROOF\"],"
                +"\"validatorRefs\":[\"LeadFirstContactValidator\"],"
                +"\"conditionalRules\":[{\"when\":{\"field\":\"contactResult\","
                +"\"operator\":\""+operator+"\",\"value\":\""+value+"\"},"
                +"\"requiredFields\":[\""+String.join("\",\"",requiredFields)+"\"]}]}";
    }

    private String governedRecipeWithBaseFields(String fields)
    {
        return "{\"requiredFields\":["+fields+"],"
                +"\"requiredAttachments\":[\"CONTACT_PROOF\"],"
                +"\"validatorRefs\":[\"LeadFirstContactValidator\"],"
                +"\"conditionalRules\":[{\"when\":{\"field\":\"contactResult\","
                +"\"operator\":\"EQ\",\"value\":\"CONNECTED\"},"
                +"\"requiredFields\":[\"name\",\"city\",\"demand\",\"visited\"]}]}";
    }

    private ResultSet publishedRows(long templateId,long versionId) throws Exception
    {
        ResultSet rows=mock(ResultSet.class);
        when(rows.next()).thenReturn(true,false);
        when(rows.getLong(1)).thenReturn(templateId);
        when(rows.getLong(2)).thenReturn(versionId);
        return rows;
    }
}
