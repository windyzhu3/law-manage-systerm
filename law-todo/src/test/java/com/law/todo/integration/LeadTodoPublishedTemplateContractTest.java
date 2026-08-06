package com.law.todo.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

class LeadTodoPublishedTemplateContractTest
{
    private static final String ROOT="/todo-definitions/v0.2/";
    private static final Path MIGRATION=Path.of("..","ruoyi-admin","src","main","resources",
            "db","migration","V0_20_51__publish_lead_todo_templates.sql");

    @Test void allFourLeadTemplatesAreReadyAndHaveExecutableHandlers() throws Exception
    {
        for(String code:List.of("TD-001","TD-002","TD-003","TD-004"))
        {
            JSONObject value=definition(code);
            assertEquals("READY",value.getString("foundationState"),code);
            assertEquals("READY",value.getString("productionState"),code);
            assertTrue(value.getJSONArray("blockers").isEmpty(),code);
            assertEquals("PRESENT",value.getJSONObject("handlerCapability")
                    .getString("repositoryStatus"),code);
            assertTrue(value.getJSONArray("businessDependencies").stream()
                    .map(JSONObject.class::cast)
                    .allMatch(item->"PRESENT".equals(item.getString("repositoryStatus"))),code);
        }
    }

    @Test void dynamicFormsUseGovernedDictionariesConditionsAndValidators() throws Exception
    {
        JSONObject td001=definition("TD-001").getJSONObject("definition");
        assertDict(td001,"contactResult","law_first_contact_result");
        assertDict(td001,"visited","law_yes_no_flag");
        assertShowWhen(td001,"name","contactResult","VALID");
        assertShowWhen(td001,"city","contactResult","VALID");
        assertShowWhen(td001,"demand","contactResult","VALID");
        assertShowWhen(td001,"visited","contactResult","VALID");
        assertTrue(td001.getJSONObject("dod").getJSONObject("config")
                .getJSONArray("validatorRefs").contains("LeadFirstContactValidator"));

        JSONObject td002=definition("TD-002").getJSONObject("definition");
        assertDict(td002,"reviewResult","law_lead_invalid_review_result");
        assertNotNull(td002.getJSONObject("dod").getJSONObject("config")
                .getJSONArray("validatorRefs"));

        JSONObject td003=definition("TD-003").getJSONObject("definition");
        assertDict(td003,"attemptStage","law_retry_stage");
        assertDict(td003,"contactResult","law_retry_result");
        assertTrue(td003.getJSONObject("dod").getJSONObject("config")
                .getJSONArray("validatorRefs").contains("LeadFirstContactValidator"));
        JSONObject schedule=td003.getJSONObject("sla").getJSONObject("config")
                .getJSONObject("schedule");
        assertEquals("Asia/Shanghai",schedule.getString("timezone"));
        assertEquals("TD-003",schedule.getString("targetTemplateCode"));
        assertEquals(List.of("T0","T1_AM","T1_NOON","T1_PM","T2_AM","T2_NOON","T2_PM"),
                schedule.getJSONArray("windows").stream().map(JSONObject.class::cast)
                        .map(item->item.getString("windowCode")).toList());
    }

    @Test void td001HasExecutableRoutesOnlyToPublishedLeadFlowTargets() throws Exception
    {
        JSONObject routing=definition("TD-001").getJSONObject("definition")
                .getJSONObject("routing").getJSONObject("config");
        JSONArray outcomes=routing.getJSONArray("businessOutcomes");
        assertNotNull(outcomes);
        assertEquals(3,outcomes.size());
        assertEquals("TD-004",outcome(outcomes,"VALID").getString("targetTemplateCode"));
        assertEquals("TD-002",outcome(outcomes,"SUSPECT_INVALID").getString("targetTemplateCode"));
        JSONObject unreachable=outcome(outcomes,"UNREACHABLE");
        assertEquals("END",unreachable.getString("effectKind"));
        assertFalse(unreachable.containsKey("targetTemplateCode"));
        assertEquals(3L,routing.getJSONObject("releaseDependencies").getLongValue("TD-003"));
        Set<String> targetCodes=routing.getJSONArray("nodes").stream()
                .map(JSONObject.class::cast)
                .filter(node->"TASK".equals(node.getString("type")))
                .map(node->node.getString("templateCode"))
                .filter(code->!"TD-001".equals(code))
                .collect(Collectors.toSet());
        assertEquals(Set.of("TD-002","TD-004"),targetCodes);
        Set<String> fields=routing.getJSONArray("edges").stream()
                .map(JSONObject.class::cast).filter(edge->edge.containsKey("condition"))
                .filter(edge->"firstResult".equals(edge.getString("from")))
                .map(edge->edge.getJSONObject("condition").getJSONObject("$expression")
                        .getJSONObject("root").getString("field"))
                .collect(Collectors.toSet());
        assertEquals(Set.of("contactResult"),fields);
        assertFalse(routing.toJSONString().contains("CONTRACT_SIGN"));
    }

    @Test void publishedOwnersAreStableAvailableBusinessAuthorities() throws Exception
    {
        assertOwner("TD-001","PAYLOAD","ownerId");
        assertOwner("TD-002","PAYLOAD","reviewerId");
        assertOwner("TD-003","BUSINESS_OWNER",null);
        assertOwner("TD-004","BUSINESS_OWNER",null);
    }

    @Test void td003IsCreatedOnlyByScheduleAndNeverByOrdinaryGraphTransitions() throws Exception
    {
        String td001=definition("TD-001").getJSONObject("definition")
                .getJSONObject("routing").getJSONObject("config").toJSONString();
        String td003=definition("TD-003").getJSONObject("definition")
                .getJSONObject("routing").getJSONObject("config").toJSONString();

        assertFalse(td001.contains("\"templateCode\":\"TD-003\""));
        assertFalse(td003.contains("\"to\":\"nextTd003\""));
        assertFalse(td003.contains("\"templateCode\":\"TD-003\"")
                &&td003.indexOf("\"templateCode\":\"TD-003\"")
                !=td003.lastIndexOf("\"templateCode\":\"TD-003\""));
    }

    @Test void repositoryRouteNumbersAreCompilerFixturesAndProductionRequiresMigrationBinding()
            throws Exception
    {
        for(String code:List.of("TD-001","TD-002","TD-003","TD-004"))
        {
            JSONObject routing=definition(code).getJSONObject("definition")
                    .getJSONObject("routing").getJSONObject("config");
            assertEquals("MIGRATION_DYNAMIC",routing.getString("identityBinding"),code);
        }
        JSONObject schedule=definition("TD-003").getJSONObject("definition")
                .getJSONObject("sla").getJSONObject("config").getJSONObject("schedule");
        assertEquals("MIGRATION_DYNAMIC",schedule.getString("identityBinding"));
    }

    @Test void td002DefaultIsControlledAndSlaEscalationStartsOnlyAt150Percent() throws Exception
    {
        JSONObject td002=definition("TD-002").getJSONObject("definition");
        JSONObject defaultAction=td002.getJSONArray("autoActions").getJSONObject(0)
                .getJSONObject("config");
        assertEquals("COMPLETE_DEFAULT",defaultAction.getString("actionType"));
        assertEquals("TRUE_INVALID",defaultAction.getJSONObject("fields")
                .getString("reviewResult"));
        assertTrue(defaultAction.getInteger("maxAttempts") > 0);
        assertTrue(defaultAction.getInteger("claimTimeoutMinutes") > 0);

        JSONObject td001=definition("TD-001").getJSONObject("definition");
        Set<String> escalations=td001.getJSONArray("autoActions").stream()
                .map(JSONObject.class::cast).map(value->value.getJSONObject("config"))
                .filter(value->"ESCALATE".equals(value.getString("actionType")))
                .map(value->value.getString("triggerAt")).collect(Collectors.toSet());
        assertEquals(Set.of("SLA_150"),escalations);
        assertFalse(escalations.contains("SLA_80"));
        assertFalse(escalations.contains("SLA_100"));
    }

    @Test void td002ExposesTheGovernedReviewEffectsUsedByGuidedConfiguration() throws Exception
    {
        JSONObject td002=definition("TD-002").getJSONObject("definition");
        assertEquals("LEAD_SUSPECT_INVALID_MARKED",
                td002.getJSONObject("event").getString("eventType"));
        JSONObject owner=td002.getJSONObject("owner").getJSONObject("config");
        assertEquals("PAYLOAD",owner.getString("type"));
        assertEquals("reviewerId",owner.getString("field"));
        JSONObject dod=td002.getJSONObject("dod").getJSONObject("config");
        assertEquals(List.of("reviewResult","reviewOpinion"),
                dod.getJSONArray("requiredFields").toJavaList(String.class));
        JSONObject sla=td002.getJSONObject("sla").getJSONObject("config");
        assertEquals(1440,sla.getIntValue("minutes"));
        assertEquals("COMPLETE_DEFAULT",sla.getString("onDue"));

        JSONArray outcomes=td002.getJSONObject("routing").getJSONObject("config")
                .getJSONArray("businessOutcomes");
        assertNotNull(outcomes);
        assertEquals(2,outcomes.size());
        JSONObject trueInvalid=outcome(outcomes,"TRUE_INVALID");
        assertEquals("reviewResult",trueInvalid.getString("field"));
        assertEquals("END",trueInvalid.getString("effectKind"));
        assertEquals("MOVE_DEAD_POOL",trueInvalid.getString("businessAction"));
        JSONObject misjudged=outcome(outcomes,"MISJUDGED_VALID");
        assertEquals("reviewResult",misjudged.getString("field"));
        assertEquals("NEXT_TEMPLATE",misjudged.getString("effectKind"));
        assertEquals("TD-001",misjudged.getString("targetTemplateCode"));
        assertEquals("REOPEN_FIRST_CONTACT",misjudged.getString("businessAction"));
    }

    @Test void td003ExposesOnlyEditableContactResultAndFourGovernedRetryEffects()
            throws Exception
    {
        JSONObject td003=definition("TD-003").getJSONObject("definition");
        assertEquals("LEAD_RETRY_WINDOW_DUE",
                td003.getJSONObject("event").getString("eventType"));
        JSONObject owner=td003.getJSONObject("owner").getJSONObject("config");
        assertEquals("BUSINESS_OWNER",owner.getString("type"));
        assertEquals("LEAD",owner.getString("businessType"));
        JSONObject dod=td003.getJSONObject("dod").getJSONObject("config");
        assertEquals(List.of("contactResult"),
                dod.getJSONArray("requiredFields").toJavaList(String.class));
        assertEquals(Boolean.TRUE,uiField(td003,"attemptStage").getBoolean("readOnly"));
        assertEquals(Boolean.TRUE,uiField(td003,"attemptCount").getBoolean("readOnly"));

        JSONArray outcomes=td003.getJSONObject("routing").getJSONObject("config")
                .getJSONArray("businessOutcomes");
        assertNotNull(outcomes);
        assertEquals(4,outcomes.size());
        assertEquals("NEXT_TEMPLATE",outcome(outcomes,"CONNECTED").getString("effectKind"));
        assertEquals("TD-004",outcome(outcomes,"CONNECTED").getString("targetTemplateCode"));
        assertEquals("RETAIN_CURRENT",
                outcome(outcomes,"CONTINUE_CURRENT_WINDOW").getString("effectKind"));
        assertEquals("SCHEDULE_NEXT",outcome(outcomes,"NEXT_WINDOW").getString("effectKind"));
        assertEquals("END",outcome(outcomes,"EXHAUSTED").getString("effectKind"));
    }

    @Test void td004RecordsProgressAndSchedulesItselfWithoutAGraphLoop() throws Exception
    {
        JSONObject packaged=definition("TD-004");
        JSONObject td004=packaged.getJSONObject("definition");
        assertEquals("LEAD_FIRST_CONTACT_VALID",
                td004.getJSONObject("event").getString("eventType"));
        JSONObject owner=td004.getJSONObject("owner").getJSONObject("config");
        assertEquals("BUSINESS_OWNER",owner.getString("type"));
        assertEquals("LEAD",owner.getString("businessType"));

        JSONObject dod=td004.getJSONObject("dod").getJSONObject("config");
        assertEquals(List.of("progressType","progressAt"),
                dod.getJSONArray("requiredFields").toJavaList(String.class));
        assertEquals(List.of("FOLLOWUP_PROOF"),dod.getJSONArray("materials").stream()
                .map(JSONObject.class::cast).map(item->item.getString("type")).toList());
        assertTrue(dod.getJSONArray("conditionalRequired").isEmpty());
        assertTrue(dod.getJSONArray("validatorRefs").isEmpty());

        JSONObject routing=td004.getJSONObject("routing").getJSONObject("config");
        JSONArray outcomes=routing.getJSONArray("businessOutcomes");
        assertNotNull(outcomes);
        assertEquals(1,outcomes.size());
        JSONObject progress=outcome(outcomes,"PROGRESS_RECORDED");
        assertEquals("result",progress.getString("field"));
        assertEquals("SCHEDULE_SELF",progress.getString("effectKind"));
        assertEquals("TD-004",progress.getString("targetTemplateCode"));
        assertEquals("REFRESH_FIVE_DAY_WINDOW",progress.getString("businessAction"));

        assertEquals(List.of("td004","end"),routing.getJSONArray("nodes").stream()
                .map(JSONObject.class::cast).map(node->node.getString("key")).toList());
        assertEquals(List.of("td004-end"),routing.getJSONArray("edges").stream()
                .map(JSONObject.class::cast).map(edge->edge.getString("key")).toList());
        assertEquals("TD-004_COMPLETE",packaged.getJSONObject("handlerCapability")
                .getString("requiredCode"));
    }

    @Test void migrationPublishesInDependencyOrderAndOwnsTheSingleAssignmentTrigger()
            throws Exception
    {
        assertTrue(Files.exists(MIGRATION));
        String sql=Files.readString(MIGRATION);
        int td004=sql.indexOf("(1,'TD-004'");
        int td002=sql.indexOf("(2,'TD-002'");
        int td003=sql.indexOf("(3,'TD-003'");
        int td001=sql.indexOf("(4,'TD-001'");
        assertTrue(td004>=0&&td004<td002&&td002<td003&&td003<td001);
        assertTrue(sql.contains("insert into todo_template_version"));
        assertTrue(sql.contains("json_set"));
        assertTrue(sql.contains("template_version_id"));
        assertTrue(sql.contains("template_code='LEAD_FIRST_CONTACT'"));
        assertTrue(sql.contains("event_type='LEAD_ASSIGNED'"));
        assertFalse(sql.contains("CONTRACT_SIGN"));
        assertFalse(sql.toLowerCase().contains("update todo_template_version"));
        assertTrue(sql.toLowerCase().contains("start transaction"));
        assertTrue(sql.toLowerCase().contains("commit;"));
    }

    private static void assertDict(JSONObject definition,String key,String dictType)
    {
        JSONObject field=definition.getJSONObject("ui").getJSONObject("config")
                .getJSONArray("fields").stream().map(JSONObject.class::cast)
                .filter(value->key.equals(value.getString("key"))).findFirst().orElseThrow();
        assertEquals("dict",field.getString("type"));
        assertEquals(dictType,field.getString("dictType"));
    }

    private static void assertShowWhen(JSONObject definition,String key,String controlling,
            String value)
    {
        JSONObject field=definition.getJSONObject("ui").getJSONObject("config")
                .getJSONArray("fields").stream().map(JSONObject.class::cast)
                .filter(item->key.equals(item.getString("key"))).findFirst().orElseThrow();
        JSONObject showWhen=field.getJSONObject("showWhen");
        assertNotNull(showWhen,key);
        assertEquals(controlling,showWhen.getString("field"));
        assertEquals(value,showWhen.getString("equals"));
    }

    private static void assertOwner(String code,String type,String field) throws Exception
    {
        JSONObject owner=definition(code).getJSONObject("definition")
                .getJSONObject("owner").getJSONObject("config");
        assertEquals(type,owner.getString("type"),code);
        assertEquals(field,owner.getString("field"),code);
        assertEquals(Boolean.FALSE,owner.getBoolean("skipUnavailable"),code);
        assertEquals(Boolean.FALSE,owner.getBoolean("useDelegation"),code);
        assertEquals(Boolean.TRUE,owner.getBoolean("requireAvailable"),code);
        assertFalse(owner.containsKey("fallback"),code);
    }

    private static JSONObject outcome(JSONArray outcomes,String value)
    {
        return outcomes.stream().map(JSONObject.class::cast)
                .filter(item->value.equals(item.getString("value")))
                .findFirst().orElseThrow();
    }

    private static JSONObject uiField(JSONObject definition,String key)
    {
        return definition.getJSONObject("ui").getJSONObject("config")
                .getJSONArray("fields").stream().map(JSONObject.class::cast)
                .filter(item->key.equals(item.getString("key"))).findFirst().orElseThrow();
    }

    private static JSONObject definition(String code) throws Exception
    {
        try(InputStream input=LeadTodoPublishedTemplateContractTest.class
                .getResourceAsStream(ROOT+code+".json"))
        {
            assertNotNull(input,code);
            return JSON.parseObject(new String(input.readAllBytes(),StandardCharsets.UTF_8));
        }
    }
}
