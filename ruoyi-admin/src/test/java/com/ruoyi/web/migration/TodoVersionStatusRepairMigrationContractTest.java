package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TodoVersionStatusRepairMigrationContractTest
{
    private final String baseline=resource("db/migration/V0_20_30__todo_configuration_center.sql");
    private final String repair=resource("db/migration/V0_20_32__todo_version_status_repair.sql");

    @Test void repairsTheVersionStatusDictionaryForwardOnlyAndIdempotently()
    {
        assertTrue(baseline.contains("'rolled_back','law_todo_version_status'"),"Historical migration must remain unchanged");
        assertTrue(baseline.contains("'draft','law_todo_version_status'")&&baseline.contains("'published','law_todo_version_status'"),
                "Draft and published remain the canonical pre-existing values");
        assertTrue(repair.contains("'retired','law_todo_version_status'"));
        assertTrue(repair.contains("where not exists"),"Repair insert must be rerunnable without duplicates");
        assertTrue(repair.contains("dict_value='rolled_back'")&&repair.contains("status='1'"),
                "The invalid rolled-back dictionary option must be disabled");
        assertFalse(repair.contains("delete from sys_dict_data"),"Forward repair must preserve migration history");
        assertFalse(repair.contains("dict_value='draft'")||repair.contains("dict_value='published'"),
                "The repair must not disable or rewrite valid draft/published options");
    }

    private String resource(String path)
    {
        try(InputStream input=getClass().getClassLoader().getResourceAsStream(path))
        {
            assertNotNull(input,()->"Migration resource must exist: "+path);
            return new String(input.readAllBytes(),StandardCharsets.UTF_8).toLowerCase();
        }
        catch(IOException exception){throw new AssertionError("Unable to read migration resource: "+path,exception);}
    }
}
