package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LeadSourceGovernanceMigrationContractTest
{
    @Test
    void v52IsSchemaOnlyAndV53OwnsOneAtomicPermanentDataChange() throws Exception
    {
        String schema=resource("/db/migration/V0_20_52__lead_todo_flow_navigation.sql")
                .toLowerCase();
        String data=resource(
                "/db/migration/V0_20_53__lead_source_governance_and_navigation.sql")
                .toLowerCase();

        assertTrue(schema.contains(
                "create table biz_lead_source_governance_audit"));
        for(String forbidden:new String[] {
                "insert into","update biz_","delete ","sys_menu",
                "start transaction","failure_injection_point","commit;"
        })
        {
            assertFalse(schema.contains(forbidden),
                    "V0.20.52 schema boundary must not contain "+forbidden);
        }

        assertFalse(data.contains(
                "create table biz_lead_source_governance_audit"),
                "V0.20.53 must not repeat permanent schema DDL");
        for(String required:new String[] {
                "start transaction;",
                "insert into biz_lead_source_governance_audit",
                "select 'v0_20_53'",
                "insert into biz_lead_setting",
                "update biz_lead",
                "insert into biz_business_tag",
                "delete relation",
                "insert into biz_business_tag_rel",
                "insert into sys_menu",
                "insert into sys_role_menu",
                "failure_injection_point_before_commit",
                "commit;"
        })
        {
            assertTrue(data.contains(required),
                    "V0.20.53 atomic data migration is missing "+required);
        }
        assertEquals(1,occurrences(data,"start transaction;"));
        assertEquals(1,occurrences(data,"commit;"));
        assertTrue(data.indexOf("start transaction;")
                < data.indexOf("insert into biz_lead_source_governance_audit"));
        assertTrue(data.indexOf("failure_injection_point_before_commit")
                < data.indexOf("commit;"));
    }

    private String resource(String path) throws Exception
    {
        try(InputStream input=getClass().getResourceAsStream(path))
        {
            assertTrue(input!=null,"Missing migration resource "+path);
            return new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }
    }

    private int occurrences(String source,String token)
    {
        int count=0;
        for(int at=source.indexOf(token);at>=0;
                at=source.indexOf(token,at+token.length()))
        {
            count++;
        }
        return count;
    }
}
