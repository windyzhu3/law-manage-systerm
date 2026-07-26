package com.ruoyi.system.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LeadDeadPoolQueueMapperContractTest
{
    @Test
    void exactInvalidReviewerCanListAndRestoreTheirCrossDepartmentDeadPoolLead()
            throws Exception
    {
        String xml = new String(Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("mapper/system/BizLeadMapper.xml").readAllBytes(),
                StandardCharsets.UTF_8);

        assertTrue(select(xml, "selectLeadDeadPoolQueue")
                .contains("review.reviewer_id=#{currentUserId}"));
        assertTrue(select(xml, "countDeadPoolInDataScope")
                .contains("review.reviewer_id=#{currentUserId}"));
    }

    private String select(String xml, String id)
    {
        int start = xml.indexOf("<select id=\"" + id + "\"");
        int end = xml.indexOf("</select>", start);
        return xml.substring(start, end);
    }
}
