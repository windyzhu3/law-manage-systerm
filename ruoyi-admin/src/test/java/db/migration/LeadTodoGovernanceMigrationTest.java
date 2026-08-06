package db.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

class LeadTodoGovernanceMigrationTest
{
    @Test
    void leadAssignedExposesOnlyBusinessMeaningfulConfigurationFields()
    {
        JSONObject schema=schema("schemaVersion","assignmentId","ownerId","ownerDeptId","operatorId","leadId");

        V0_20_81__GovernLeadEventConfigurationFields.GovernedEvent governed=
                V0_20_81__GovernLeadEventConfigurationFields.govern("LEAD_ASSIGNED",schema);

        assertEquals("线索已分配",governed.eventName());
        assertEquals(Set.of("ownerId"),governed.ownerFields());
        assertTrue(governed.conditionFields().isEmpty());
        assertTrue(governed.defaultValueFields().isEmpty());
        assertField(governed.schema(),"ownerId","线索负责人","USER_ID","SYSTEM_USER");
        assertField(governed.schema(),"ownerDeptId","负责人部门","DEPT_ID","SYSTEM_DEPARTMENT");
        assertField(governed.schema(),"assignmentId","分配记录","SYSTEM_ID",null);
        assertEquals(Boolean.TRUE,governed.schema().getJSONObject("properties")
                .getJSONObject("assignmentId").getBoolean("x-read-only-business-context"));
    }

    @Test
    void suspectInvalidEventUsesReviewerAndReasonWithoutTechnicalIdentifiers()
    {
        JSONObject schema=schema("schemaVersion","reviewId","ownerId","reviewerId","reasonCode","operatorId","leadId");

        V0_20_81__GovernLeadEventConfigurationFields.GovernedEvent governed=
                V0_20_81__GovernLeadEventConfigurationFields.govern("LEAD_SUSPECT_INVALID_MARKED",schema);

        assertEquals(Set.of("reviewerId"),governed.ownerFields());
        assertEquals(Set.of("reasonCode"),governed.conditionFields());
        assertFalse(governed.conditionFields().contains("reviewId"));
        assertField(governed.schema(),"reasonCode","疑似无效原因","DICT","SYSTEM_DICTIONARY");
        assertEquals("law_lead_invalid_reason",governed.schema().getJSONObject("properties")
                .getJSONObject("reasonCode").getString("x-dict-type"));
    }

    @Test
    void repairBinderKeepsRetryScheduleOutOfImmediateTd001Outcomes()
    {
        JSONObject definition=td001Definition();

        V0_20_82__RepairLeadTemplateConfigurationDrafts.bindTd001(
                definition,101L,201L,301L,401L);

        JSONObject routing=definition.getJSONObject("routing").getJSONObject("config");
        JSONArray outcomes=routing.getJSONArray("businessOutcomes");
        JSONObject unreachable=outcomes.getJSONObject(2);
        assertEquals("contactResult",unreachable.getString("resultField"));
        assertEquals("UNREACHABLE",unreachable.getString("resultValue"));
        assertEquals("未接通",unreachable.getString("resultLabel"));
        assertFalse(unreachable.containsKey("field"));
        assertFalse(unreachable.containsKey("value"));
        assertEquals("contactResult",singlePredicate(unreachable).getString("field"));
        assertEquals("UNREACHABLE",singlePredicate(unreachable).getString("value"));
        assertEquals("END",unreachable.getString("effectKind"));
        assertNull(unreachable.get("targetVersionId"));
        assertNull(unreachable.get("targetTemplateCode"));
        assertEquals(301L,routing.getJSONObject("releaseDependencies").getLongValue("TD-003"));
        assertEquals(401L,outcomes.getJSONObject(0).getLongValue("targetVersionId"));
        assertEquals(201L,outcomes.getJSONObject(1).getLongValue("targetVersionId"));
        assertEquals(101L,taskVersion(routing,"TD-001"));
    }

    @Test
    void repairBinderRestoresTd002GovernedEventAndReopenTarget()
    {
        JSONObject definition=td002Definition();

        V0_20_82__RepairLeadTemplateConfigurationDrafts.bindTd002(definition,202L,102L);

        assertEquals("LEAD_SUSPECT_INVALID_MARKED",
                definition.getJSONObject("event").getString("eventType"));
        JSONObject routing=definition.getJSONObject("routing").getJSONObject("config");
        assertEquals(202L,taskVersion(routing,"TD-002"));
        assertEquals(102L,taskVersion(routing,"TD-001"));
        JSONObject reopened=routing.getJSONArray("businessOutcomes").getJSONObject(1);
        assertEquals("reviewResult",reopened.getString("resultField"));
        assertEquals("MISJUDGED_VALID",reopened.getString("resultValue"));
        assertEquals("误判有效",reopened.getString("resultLabel"));
        assertFalse(reopened.containsKey("field"));
        assertFalse(reopened.containsKey("value"));
        assertEquals(102L,reopened.getLongValue("targetVersionId"));
    }

    private JSONObject schema(String... fields)
    {
        JSONObject properties=new JSONObject();
        for(String field:fields)
        {
            JSONObject property=new JSONObject();
            property.put("type",field.equals("reasonCode")?"string":"integer");
            properties.put(field,property);
        }
        JSONObject schema=new JSONObject();
        schema.put("type","object");
        schema.put("properties",properties);
        return schema;
    }

    private void assertField(JSONObject schema,String field,String title,String semantic,String optionSource)
    {
        JSONObject property=schema.getJSONObject("properties").getJSONObject(field);
        assertEquals(title,property.getString("title"));
        assertFalse(property.getString("description").isBlank());
        assertEquals(semantic,property.getString("x-semantic-type"));
        if(optionSource==null)assertNull(property.get("x-option-source"));
        else assertEquals(optionSource,property.getString("x-option-source"));
    }

    private JSONObject td001Definition()
    {
        return JSONObject.parseObject("""
                {"event":{"eventType":"LEAD_ASSIGNED"},"routing":{"config":{
                  "businessOutcomes":[
                    {"value":"VALID","effectKind":"NEXT_TEMPLATE","targetTemplateCode":"TD-004","targetVersionId":4},
                    {"value":"SUSPECT_INVALID","effectKind":"NEXT_TEMPLATE","targetTemplateCode":"TD-002","targetVersionId":2},
                    {"value":"UNREACHABLE","effectKind":"NEXT_TEMPLATE","targetTemplateCode":"TD-003","targetVersionId":3}],
                  "nodes":[
                    {"type":"TASK","templateCode":"TD-001","templateVersionId":1},
                    {"type":"TASK","templateCode":"TD-002","templateVersionId":2},
                    {"type":"TASK","templateCode":"TD-004","templateVersionId":4},
                    {"type":"TASK","templateCode":"TD-001","templateVersionId":1}]
                }}}
                """);
    }

    private JSONObject td002Definition()
    {
        return JSONObject.parseObject("""
                {"event":{"eventType":"LEAD_FIRST_CONTACT_UNREACHABLE"},"routing":{"config":{
                  "businessOutcomes":[
                    {"value":"TRUE_INVALID","effectKind":"END"},
                    {"value":"MISJUDGED_VALID","effectKind":"NEXT_TEMPLATE","targetTemplateCode":"TD-001"}],
                  "nodes":[
                    {"type":"TASK","templateCode":"TD-002","templateVersionId":2},
                    {"type":"TASK","templateCode":"TD-001","templateVersionId":1}]
                }}}
                """);
    }

    private long taskVersion(JSONObject routing,String templateCode)
    {
        for(Object raw:routing.getJSONArray("nodes"))
        {
            JSONObject node=(JSONObject)raw;
            if(templateCode.equals(node.getString("templateCode")))
                return node.getLongValue("templateVersionId");
        }
        return 0L;
    }

    private JSONObject singlePredicate(JSONObject outcome)
    {
        return outcome.getJSONObject("condition").getJSONObject("$expression")
                .getJSONObject("root").getJSONArray("conditions").getJSONObject(0);
    }
}
