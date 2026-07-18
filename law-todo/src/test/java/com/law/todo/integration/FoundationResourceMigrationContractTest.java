package com.law.todo.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class FoundationResourceMigrationContractTest
{
    private static final Path MIGRATION=Path.of("..","ruoyi-admin","src","main","resources","db","migration",
            "V0_20_17__foundation_resource_readiness.sql");
    private static final Path RUNTIME_MIGRATION=Path.of("..","ruoyi-admin","src","main","resources","db","migration",
            "V0_20_24__foundation_confirmed_runtime_resources.sql");

    @Test void requirementCatalogDoesNotPromoteUnapprovedRuntimeResources() throws Exception
    {
        String sql=Files.readString(MIGRATION).toLowerCase().replaceAll("--[^\r\n]*","");

        assertFalse(sql.contains("insert into sys_dict_type"));
        assertFalse(sql.contains("insert into sys_dict_data"));
        assertFalse(sql.contains("insert into sys_role"));
        assertTrue(sql.contains("'law_business_line'"));
        assertTrue(sql.contains("'non_litigation'"));
        assertTrue(sql.contains("'execution_manager'"));
        assertTrue(sql.contains("'enforcement_primary_assistant'"));
        assertTrue(sql.contains("'conflicting','q-003'"));
    }

    @Test void confirmedRuntimeMigrationPromotesOnlyApprovedResourcesWithoutGrants() throws Exception
    {
        String sql=Files.readString(RUNTIME_MIGRATION).toLowerCase().replaceAll("--[^\r\n]*","");

        assertTrue(sql.contains("'law_business_line'"));
        assertTrue(sql.contains("'non_litigation'"));
        assertTrue(sql.contains("'comprehensive'"));
        assertTrue(sql.contains("'execution'"));
        assertTrue(sql.contains("'sales'"));
        assertFalse(sql.contains("insert into sys_role_menu"));
        assertFalse(sql.contains("insert into sys_role_dept"));
        assertFalse(sql.contains("insert into sys_user_role"));
        assertFalse(sql.contains("update todo_foundation_resource_requirement"));
        assertEquals(Set.of("law_business_line"),matches(sql,Pattern.compile("'(law_[a-z0-9_]+)'"),1));
        assertEquals(Set.of("sales"),matches(sql,Pattern.compile("select\\s+'[^']+'\\s*,\\s*'([a-z0-9_]+)'\\s*,\\s*35"),1));
    }

    private Set<String> matches(String input,Pattern pattern,int group)
    {
        Matcher matcher=pattern.matcher(input);
        java.util.HashSet<String> values=new java.util.HashSet<>();
        while(matcher.find())values.add(matcher.group(group));
        return Set.copyOf(values);
    }
}
