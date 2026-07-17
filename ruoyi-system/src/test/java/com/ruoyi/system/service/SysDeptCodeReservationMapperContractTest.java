package com.ruoyi.system.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SysDeptCodeReservationMapperContractTest
{
    @Test void softDeletedDepartmentCodesRemainReserved() throws Exception
    {
        try(var source=getClass().getResourceAsStream("/mapper/system/SysDeptMapper.xml"))
        {
            String xml=new String(source.readAllBytes(),StandardCharsets.UTF_8).replaceAll("\\s+"," ");
            int start=xml.indexOf("<select id=\"checkDeptCodeUnique\"");
            int end=xml.indexOf("</select>",start);
            String query=xml.substring(start,end);
            assertTrue(query.contains("where dept_code=#{deptCode}"));
            assertFalse(query.contains("del_flag = '0'"),"soft-deleted department codes must not be reusable");
        }
    }
}
