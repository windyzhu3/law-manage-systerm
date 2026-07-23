package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

class TodoPhaseTwoJourneyMetadataMigrationContractTest
{
    private final String sql=resource("db/migration/V0_20_42__todo_phase_two_journey_metadata.sql");
    private final String normalized=sql.replaceAll("\\s+"," ").toLowerCase();

    @Test
    void upsertsJourneyMetadataIdempotentlyWithoutRenamingExistingRecipeCodes()
    {
        assertTrue(normalized.contains("insert into todo_configuration_resource_item"));
        assertTrue(normalized.contains("on duplicate key update"));
        List.of("LEAD_FIRST_CONTACT_READY","CUSTOMER_PROGRESS_READY","CONTRACT_SIGN_READY",
                "CASE_ACCEPT_READY","MATTER_ARCHIVE_READY")
                .forEach(code->assertTrue(sql.contains("'"+code+"'"),"Missing preserved recipe code "+code));
        assertFalse(normalized.contains("delete from todo_configuration_resource_item"));
        assertFalse(normalized.contains("set resource_code="));
    }

    @Test
    void seedsContextualMetadataForEveryV02BusinessFamily()
    {
        List.of("'LEAD'","'CUSTOMER'","'CONTRACT'","'CASE'","'MATTER'")
                .forEach(type->assertTrue(sql.contains(type),"Missing business family "+type));
        List.of("businessActions","templateStages","recommendationPriority","requiredFields",
                "requiredAttachments","conditionalRules","validatorRefs","employeeInstructions")
                .forEach(key->assertTrue(sql.contains("'"+key+"'"),"Missing recipe metadata "+key));
    }

    @Test
    void keepsCanonicalLeadFirstContactFieldsAndReadableEmployeeInstructions()
    {
        assertTrue(sql.contains("json_array('contactedAt','contactResult')"));
        assertFalse(sql.contains("contactTime"));
        assertTrue(sql.contains("记录联系时间并填写跟进结果"));
        assertTrue(sql.contains("json_array('FIRST_CONTACT')"));
        assertTrue(sql.contains("json_array('LEAD_FOLLOWUP')"));
    }

    private String resource(String path)
    {
        try(InputStream input=getClass().getClassLoader().getResourceAsStream(path))
        {
            assertNotNull(input,()->"Migration resource must exist: "+path);
            return new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }
        catch(IOException exception)
        {
            throw new AssertionError("Unable to read migration resource: "+path,exception);
        }
    }
}
