package com.ruoyi.system.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LeadProgressMapperContractTest
{
    @Test
    void progressFactReadbackUsesAnExplicitDomainProjection()
            throws Exception
    {
        String xml=new String(getClass().getResourceAsStream(
                "/mapper/system/BizLeadMapper.xml").readAllBytes(),StandardCharsets.UTF_8);

        for(String id:new String[]{"selectProgressFollowupByIdempotencyKey",
                "selectProgressFollowupByIdempotencyKeyForUpdate"})
        {
            String statement=statement(xml,id);
            assertFalse(statement.toLowerCase().contains("select *"),id+" must not depend on global mapper settings");
            for(String projection:new String[]{"followup_id as followupId","lead_id as leadId",
                    "progress_at as progressAt","source_todo_id as sourceTodoId",
                    "schedule_plan_id as schedulePlanId","idempotency_key as idempotencyKey"})
                assertTrue(statement.contains(projection),id+" is missing "+projection);
        }
    }

    private String statement(String xml,String id)
    {
        int start=xml.indexOf("id=\""+id+"\"");
        int end=start<0?-1:xml.indexOf("</select>",start);
        assertTrue(start>=0&&end>start,"Mapper statement not found: "+id);
        return xml.substring(start,end);
    }
}
