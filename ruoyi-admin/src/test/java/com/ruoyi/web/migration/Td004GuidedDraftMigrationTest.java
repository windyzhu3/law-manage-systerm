package com.ruoyi.web.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;

import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;

import com.ruoyi.system.service.event.LeadProgressHandoffTodoHandler;
import com.ruoyi.system.service.lead.LeadProgressCycleService;

import db.migration.V0_20_77__SeedTd004GuidedDraft;

class Td004GuidedDraftMigrationTest
{
    private static final String GOVERNED_RECIPE="{\"requiredFields\":[\"progressType\",\"progressAt\"],"
            +"\"requiredAttachments\":[\"FOLLOWUP_PROOF\"],"
            +"\"validatorRefs\":[],\"conditionalRules\":[]}";

    @Test
    void exposesTheNextForwardOnlyFlywayIdentity()
    {
        V0_20_77__SeedTd004GuidedDraft migration=new V0_20_77__SeedTd004GuidedDraft();

        assertThat(migration.getVersion().getVersion()).isEqualTo("0.20.77");
        assertThat(migration.getDescription()).isEqualTo("SeedTd004GuidedDraft");
    }

    @Test
    void runtimeRegistersTheExactTd004CompletionCapability()
    {
        LeadProgressHandoffTodoHandler handler=new LeadProgressHandoffTodoHandler(
                mock(LeadProgressCycleService.class));

        assertThat(handler.catalogCode()).isEqualTo("TD-004_COMPLETE");
    }

    @Test
    void failsClosedWhenTheCurrentPublishedTd004VersionIsUnavailable() throws Exception
    {
        Context context=contextWith(firstQuery(emptyRows()));

        assertThatThrownBy(()->new V0_20_77__SeedTd004GuidedDraft().migrate(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The current published TD-004 version is unavailable");
    }

    @Test
    void failsClosedWhenTheActiveProgressRecipeIsUnavailable() throws Exception
    {
        Context context=contextWith(firstQuery(publishedRows(2004L,2204L)),firstQuery(emptyRows()));

        assertThatThrownBy(()->new V0_20_77__SeedTd004GuidedDraft().migrate(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The active LEAD_PROGRESS_READY recipe is unavailable");
    }

    @Test
    void failsClosedWhenMoreThanOneActiveProgressRecipeExists() throws Exception
    {
        ResultSet recipeRows=mock(ResultSet.class);
        when(recipeRows.next()).thenReturn(true,true);
        when(recipeRows.getString(1)).thenReturn(GOVERNED_RECIPE);
        Context context=contextWith(firstQuery(publishedRows(2004L,2204L)),firstQuery(recipeRows));

        assertThatThrownBy(()->new V0_20_77__SeedTd004GuidedDraft().migrate(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("More than one active LEAD_PROGRESS_READY recipe was found");
    }

    @Test
    void failsClosedWhenAnIndependentTd004TriggerIsEnabled() throws Exception
    {
        ResultSet triggerRows=mock(ResultSet.class);
        when(triggerRows.next()).thenReturn(true);
        when(triggerRows.getLong(1)).thenReturn(1L);
        Context context=contextWith(firstQuery(publishedRows(2004L,2204L)),
                firstQuery(recipeRows(GOVERNED_RECIPE)),firstQuery(triggerRows));

        assertThatThrownBy(()->new V0_20_77__SeedTd004GuidedDraft().migrate(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TD-004 must not have an enabled independent trigger");
    }

    @Test
    void failsClosedWhenRequiredProgressFieldsAreMissingOrExtended() throws Exception
    {
        assertMalformedRecipe("{\"requiredFields\":[\"progressType\"],"
                +"\"requiredAttachments\":[\"FOLLOWUP_PROOF\"],"
                +"\"validatorRefs\":[],\"conditionalRules\":[]}");
        assertMalformedRecipe("{\"requiredFields\":[\"progressType\",\"progressAt\",\"remark\"],"
                +"\"requiredAttachments\":[\"FOLLOWUP_PROOF\"],"
                +"\"validatorRefs\":[],\"conditionalRules\":[]}");
    }

    @Test
    void failsClosedWhenProgressProofIsMissingOrDuplicated() throws Exception
    {
        assertMalformedRecipe("{\"requiredFields\":[\"progressType\",\"progressAt\"],"
                +"\"requiredAttachments\":[],\"validatorRefs\":[],\"conditionalRules\":[]}");
        assertMalformedRecipe("{\"requiredFields\":[\"progressType\",\"progressAt\"],"
                +"\"requiredAttachments\":[\"FOLLOWUP_PROOF\",\"FOLLOWUP_PROOF\"],"
                +"\"validatorRefs\":[],\"conditionalRules\":[]}");
    }

    @Test
    void failsClosedWhenUngovernedValidatorsOrConditionsAreAdded() throws Exception
    {
        assertMalformedRecipe("{\"requiredFields\":[\"progressType\",\"progressAt\"],"
                +"\"requiredAttachments\":[\"FOLLOWUP_PROOF\"],"
                +"\"validatorRefs\":[\"UnknownValidator\"],\"conditionalRules\":[]}");
        assertMalformedRecipe("{\"requiredFields\":[\"progressType\",\"progressAt\"],"
                +"\"requiredAttachments\":[\"FOLLOWUP_PROOF\"],\"validatorRefs\":[],"
                +"\"conditionalRules\":[{\"when\":{\"field\":\"progressType\","
                +"\"operator\":\"EQ\",\"value\":\"PHONE\"},"
                +"\"requiredFields\":[\"remark\"]}]}");
    }

    private void assertMalformedRecipe(String recipe) throws Exception
    {
        Context context=contextWith(firstQuery(publishedRows(2004L,2204L)),
                firstQuery(recipeRows(recipe)));

        assertThatThrownBy(()->new V0_20_77__SeedTd004GuidedDraft().migrate(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("LEAD_PROGRESS_READY does not match the governed progress completion contract");
    }

    private Context contextWith(PreparedStatement... statements) throws Exception
    {
        Context context=mock(Context.class);
        Connection connection=mock(Connection.class);
        when(context.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statements[0],
                Arrays.copyOfRange(statements,1,statements.length));
        return context;
    }

    private PreparedStatement firstQuery(ResultSet rows) throws Exception
    {
        PreparedStatement statement=mock(PreparedStatement.class);
        when(statement.executeQuery()).thenReturn(rows);
        return statement;
    }

    private ResultSet publishedRows(long templateId,long versionId) throws Exception
    {
        ResultSet rows=mock(ResultSet.class);
        when(rows.next()).thenReturn(true,false);
        when(rows.getLong(1)).thenReturn(templateId);
        when(rows.getLong(2)).thenReturn(versionId);
        return rows;
    }

    private ResultSet recipeRows(String recipe) throws Exception
    {
        ResultSet rows=mock(ResultSet.class);
        when(rows.next()).thenReturn(true,false);
        when(rows.getString(1)).thenReturn(recipe);
        return rows;
    }

    private ResultSet emptyRows()
    {return mock(ResultSet.class);}
}
