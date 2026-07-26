package com.ruoyi.system.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LeadRetryQueueMapperContractTest
{
    @Test
    void pendingFilterSelectsOnlyMaterializedActionableOccurrences()
            throws Exception
    {
        String select = retryQueueSelect();

        assertTrue(select.contains("status == 'PENDING'"));
        assertTrue(select.contains("occurrence.todo_id is not null"));
        assertTrue(select.contains("occurrence.status in ('RETRY','CLAIMED','MATERIALIZED')"));
        assertTrue(select.contains("t.status not in ('COMPLETED','CANCELLED')"));
    }

    @Test
    void terminalResultFiltersUseOccurrenceResultInsteadOfScheduleStatus()
            throws Exception
    {
        String select = retryQueueSelect();

        assertTrue(select.contains(
                "status == 'CONNECTED' or status == 'EXHAUSTED'"));
        assertTrue(select.contains("occurrence.result_code=#{status}"));
    }

    private String retryQueueSelect() throws Exception
    {
        String xml = new String(Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("mapper/system/BizLeadMapper.xml").readAllBytes(),
                StandardCharsets.UTF_8);
        int start = xml.indexOf("<select id=\"selectLeadRetryQueue\"");
        int end = xml.indexOf("</select>", start);
        return xml.substring(start, end);
    }
}
