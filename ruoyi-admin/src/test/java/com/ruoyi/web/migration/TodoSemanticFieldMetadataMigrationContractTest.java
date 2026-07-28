package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

class TodoSemanticFieldMetadataMigrationContractTest
{
    private final String sql=resource("db/migration/V0_20_61__todo_semantic_field_metadata.sql");

    @Test void governsLeadDictionaryAndIdentitySemantics()
    {
        List.of("contactResult","reviewResult","attemptStage","visited")
                .forEach(field->assertTrue(sql.contains("'"+field+"'"),"Missing governed field "+field));
        List.of("law_first_contact_result","law_lead_invalid_review_result","law_retry_stage","law_yes_no_flag",
                "USER_ID","DEPT_ID","SYSTEM_USER","SYSTEM_DEPARTMENT")
                .forEach(value->assertTrue(sql.contains("'"+value+"'"),"Missing semantic metadata "+value));
    }

    @Test void enrichesTheExistingLeadAssignedSchemaWithoutChangingItsRequiredContract()
    {
        assertTrue(sql.contains("where event_type='LEAD_ASSIGNED' and payload_version=1"));
        assertTrue(sql.contains("'$.properties.ownerId.\"x-semantic-type\"'"));
        assertTrue(sql.contains("'$.properties.ownerDeptId.\"x-option-source\"'"));
        assertTrue(sql.contains("'$.properties.operatorId.\"x-semantic-type\"'"));
        assertTrue(sql.contains("json_set(payload_schema_json"));
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
