package com.ruoyi.system.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class TodoBusinessDirectoryMapperXmlContractTest
{
    private final String xml=read("src/main/resources/mapper/system/TodoBusinessDirectoryMapper.xml");

    @Test void listCountAndLookupShareOneActorScopedSource()
    {
        assertTrue(xml.contains("<sql id=\"visibleDirectorySource\">"));
        assertTrue(occurrences(xml,"<include refid=\"visibleDirectorySource\"/>")>=3);
        assertTrue(xml.contains("ur.user_id=#{currentUserId}"));
        assertTrue(xml.contains("b.dept_id=#{currentDeptId}"));
        assertTrue(xml.contains("find_in_set(m.perms,#{permissions})"));
        assertTrue(xml.contains("r.data_scope='5' and b.owner_id=#{currentUserId}"));
        assertTrue(xml.contains("b.main_lawyer_id=#{currentUserId}"));
        assertTrue(xml.contains("b.assistant_lawyer_ids"));
        assertTrue(xml.contains("limit #{limit} offset #{offset}"));
        assertFalse(xml.contains("select *"));
    }

    @Test void supportsEveryConfiguredBusinessTypeInsideTheAuthorizedSqlBoundary()
    {
        for(String type:List.of("LEAD","CUSTOMER","CONTRACT","CASE","MATTER"))
            assertTrue(xml.contains("businessType == '"+type+"'"),()->"Missing business type "+type);
    }

    @Test void payloadProjectionUsesColumnsThatExistInTheGovernedBusinessSchema()
    {
        assertTrue(xml.contains("select a.log_id from biz_lead_assignment_log"));
        assertFalse(xml.contains("a.assignment_id from biz_lead_assignment_log"));
        assertFalse(xml.matches("(?s).*biz_lead_followup\\s+f.*?f\\.del_flag.*"));
        assertTrue(xml.contains("approval_approver_id"));
        assertTrue(xml.contains("rejection_reason_code"));
        assertTrue(xml.contains("rejection_reason"));
        assertTrue(xml.contains("node_type"));
    }

    private int occurrences(String value,String token){return (value.length()-value.replace(token,"").length())/token.length();}
    private String read(String relative)
    {try{return Files.readString(Path.of(relative));}catch(Exception failure){throw new AssertionError(failure);}}
}
