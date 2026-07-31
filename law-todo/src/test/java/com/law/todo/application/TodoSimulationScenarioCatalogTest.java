package com.law.todo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoConfigurationMapper;
import com.law.todo.application.TodoSimulationEffectResolver.EffectKind;

@ExtendWith(MockitoExtension.class)
class TodoSimulationScenarioCatalogTest
{
    @Mock TodoConfigurationMapper mapper;

    @Test
    void returnsTheThreeRequiredTd001ScenariosInGovernedOrder()
    {
        when(mapper.selectConfigurationResourceItems("SIMULATION_SCENARIO","LEAD"))
                .thenReturn(List.of(
                        row(11L,"TD001_VALID","有效首联",10,"TD-004"),
                        row(12L,"TD001_SUSPECT_INVALID","疑似无效",20,"TD-002"),
                        row(13L,"TD001_UNREACHABLE","未接通",30,"TD-003")));

        var scenarios=new TodoSimulationScenarioCatalog(mapper).scenarios("TD-001","LEAD");

        assertThat(scenarios)
                .extracting(scenario->scenario.scenarioCode())
                .containsExactly("TD001_VALID","TD001_SUSPECT_INVALID","TD001_UNREACHABLE");
        assertThat(scenarios)
                .extracting(scenario->scenario.expectedNextTemplateCode())
                .containsExactly("TD-004","TD-002","TD-003");
        assertThat(scenarios).allMatch(scenario->scenario.requiredForPublish());
    }

    @Test
    void rejectsDuplicateActiveScenarioCodes()
    {
        when(mapper.selectConfigurationResourceItems("SIMULATION_SCENARIO","LEAD"))
                .thenReturn(List.of(
                        row(11L,"TD001_VALID","有效首联",10,"TD-004"),
                        row(12L,"TD001_VALID","重复场景",20,"TD-002")));

        assertThatThrownBy(()->new TodoSimulationScenarioCatalog(mapper).scenarios("TD-001","LEAD"))
                .isInstanceOf(TodoException.class)
                .hasMessageContaining("TODO_SIMULATION_SCENARIO_DUPLICATE");
    }

    @Test
    void parsesTerminalEffectWithoutRequiringALegacyNextTemplate()
    {
        when(mapper.selectConfigurationResourceItems("SIMULATION_SCENARIO","LEAD"))
                .thenReturn(List.of(effectRow("TD002_TRUE_INVALID","END",null,null)));

        var scenario=new TodoSimulationScenarioCatalog(mapper).scenarios("TD-002","LEAD").get(0);

        assertThat(scenario.expectedEffect().kind()).isEqualTo(EffectKind.END);
        assertThat(scenario.expectedNextTemplateCode()).isNull();
    }

    @Test
    void returnsAllGovernedTd002EffectsAndCompletionPayloads()
    {
        when(mapper.selectConfigurationResourceItems("SIMULATION_SCENARIO","LEAD"))
                .thenReturn(List.of(
                        td002Row(21L,"TD002_TRUE_INVALID",10,"END",null,
                                "TRUE_INVALID","确认无效",false),
                        td002Row(22L,"TD002_MISJUDGED_VALID",20,"NEXT_TEMPLATE","TD-001",
                                "MISJUDGED_VALID","复核为误判",false),
                        td002Row(23L,"TD002_OVERDUE_DEFAULT",30,"END",null,
                                "TRUE_INVALID","系统超时默认确认",true)));

        var scenarios=new TodoSimulationScenarioCatalog(mapper).scenarios("TD-002","LEAD");

        assertThat(scenarios).extracting(scenario->scenario.scenarioCode())
                .containsExactly("TD002_TRUE_INVALID","TD002_MISJUDGED_VALID",
                        "TD002_OVERDUE_DEFAULT");
        assertThat(scenarios).extracting(scenario->scenario.expectedEffect().kind())
                .containsExactly(EffectKind.END,EffectKind.NEXT_TEMPLATE,EffectKind.END);
        assertThat(scenarios.get(1).expectedEffect().targetTemplateCode()).isEqualTo("TD-001");
        assertThat(scenarios).extracting(scenario->scenario.completionPayload().get("reviewResult"))
                .containsExactly("TRUE_INVALID","MISJUDGED_VALID","TRUE_INVALID");
        assertThat(scenarios).allMatch(scenario->scenario.requiredForPublish());
    }

    @Test
    void returnsTheFourRequiredTd003EffectsWithProofAndAuthoritativeRetryContext()
    {
        when(mapper.selectConfigurationResourceItems("SIMULATION_SCENARIO","LEAD"))
                .thenReturn(List.of(
                        td003Row(31L,"TD003_CONNECTED",110,"NEXT_TEMPLATE","TD-004",
                                "CONNECTED","T0",1,true),
                        td003Row(32L,"TD003_CONTINUE_WINDOW",120,"RETAIN_CURRENT",null,
                                "CONTINUE_CURRENT_WINDOW","T0",2,false),
                        td003Row(33L,"TD003_NEXT_WINDOW",130,"SCHEDULE_NEXT",null,
                                "NEXT_WINDOW","T1_AM",1,false),
                        td003Row(34L,"TD003_EXHAUSTED",140,"END",null,
                                "EXHAUSTED","T2_PM",1,false)));

        var scenarios=new TodoSimulationScenarioCatalog(mapper).scenarios("TD-003","LEAD");

        assertThat(scenarios).extracting(scenario->scenario.scenarioCode())
                .containsExactly("TD003_CONNECTED","TD003_CONTINUE_WINDOW",
                        "TD003_NEXT_WINDOW","TD003_EXHAUSTED");
        assertThat(scenarios).extracting(scenario->scenario.expectedEffect().kind())
                .containsExactly(EffectKind.NEXT_TEMPLATE,EffectKind.RETAIN_CURRENT,
                        EffectKind.SCHEDULE_NEXT,EffectKind.END);
        assertThat(scenarios.get(0).expectedEffect().targetTemplateCode()).isEqualTo("TD-004");
        assertThat(scenarios).allMatch(scenario->scenario.requiredForPublish()
                &&scenario.editableFields().equals(List.of("contactResult"))
                &&scenario.requiredMaterials().equals(List.of("CONTACT_PROOF"))
                &&scenario.completionPayload().containsKey("attemptStage")
                &&scenario.completionPayload().containsKey("attemptCount"));
        assertThat(scenarios.get(0).completionPayload())
                .containsEntry("name","张女士")
                .containsEntry("city","上海")
                .containsEntry("demand","劳动争议咨询")
                .containsEntry("visited","NO");
        assertThat(scenarios.subList(1,scenarios.size()))
                .allMatch(scenario->!scenario.completionPayload().containsKey("name")
                        &&!scenario.completionPayload().containsKey("city")
                        &&!scenario.completionPayload().containsKey("demand")
                        &&!scenario.completionPayload().containsKey("visited"));
    }

    @Test
    void parsesExpectedValidationFailureWithItsStableErrorCode()
    {
        when(mapper.selectConfigurationResourceItems("SIMULATION_SCENARIO","LEAD"))
                .thenReturn(List.of(effectRow("TD004_PROOF_REQUIRED","EXPECTED_VALIDATION_FAILURE",null,
                        "TODO_DOD_ATTACHMENT_MISSING")));

        var scenario=new TodoSimulationScenarioCatalog(mapper).scenarios("TD-004","LEAD").get(0);

        assertThat(scenario.expectedEffect().kind()).isEqualTo(EffectKind.EXPECTED_VALIDATION_FAILURE);
        assertThat(scenario.expectedErrorCode()).isEqualTo("TODO_DOD_ATTACHMENT_MISSING");
    }

    private Map<String,Object> row(long id,String code,String name,int sortOrder,String expected)
    {
        String payload="TD001_UNREACHABLE".equals(code)
                ?"{\"contactResult\":\"UNREACHABLE\",\"contactedAt\":\"${SIMULATION_NOW}\"}"
                :"{\"contactResult\":\""+("TD001_VALID".equals(code)?"VALID":"SUSPECT_INVALID")
                    +"\",\"contactedAt\":\"${SIMULATION_NOW}\"}";
        return Map.of(
                "resource_item_id",id,
                "resource_code",code,
                "resource_name",name,
                "business_type","LEAD",
                "status","ACTIVE",
                "sort_order",sortOrder,
                "value_json","{\"templateCode\":\"TD-001\",\"scenarioVersion\":1,"
                    +"\"completionPayload\":"+payload+",\"editableFields\":[\"contactResult\",\"contactedAt\"],"
                    +"\"requiredMaterials\":[],\"completionNodeKey\":\"TD-001\",\"occurrence\":1,"
                    +"\"expectedNextTemplateCode\":\""+expected+"\",\"requiredForPublish\":true}");
    }

    private Map<String,Object> effectRow(String code,String kind,String target,String errorCode)
    {
        String targetJson=target==null?"":" ,\"targetTemplateCode\":\""+target+"\"";
        String errorJson=errorCode==null?"":" ,\"expectedErrorCode\":\""+errorCode+"\"";
        return Map.of(
                "resource_item_id",21L,
                "resource_code",code,
                "resource_name",code,
                "business_type","LEAD",
                "status","ACTIVE",
                "sort_order",10,
                "value_json","{\"templateCode\":\""+(code.startsWith("TD002")?"TD-002":"TD-004")
                        +"\",\"scenarioVersion\":1,\"completionPayload\":{},\"editableFields\":[],"
                        +"\"requiredMaterials\":[],\"completionNodeKey\":\"task\",\"occurrence\":1,"
                        +"\"expectedEffect\":{\"kind\":\""+kind+"\""+targetJson+"}" +errorJson
                        +",\"requiredForPublish\":true}");
    }

    private Map<String,Object> td002Row(long id,String code,int sortOrder,String kind,String target,
            String result,String opinion,boolean automatic)
    {
        String targetJson=target==null?"":" ,\"targetTemplateCode\":\""+target+"\"";
        return Map.of(
                "resource_item_id",id,
                "resource_code",code,
                "resource_name",code,
                "business_type","LEAD",
                "status","ACTIVE",
                "sort_order",sortOrder,
                "value_json","{\"templateCode\":\"TD-002\",\"scenarioVersion\":1,"
                        +"\"completionPayload\":{\"reviewResult\":\""+result
                        +"\",\"reviewOpinion\":\""+opinion+"\"},\"editableFields\":[],"
                        +"\"requiredMaterials\":[],\"completionNodeKey\":\"td002\","
                        +"\"occurrence\":1,\"expectedEffect\":{\"kind\":\""+kind+"\""
                        +targetJson+"},\"automatic\":"+automatic
                        +",\"requiredForPublish\":true}");
    }

    private Map<String,Object> td003Row(long id,String code,int sortOrder,String kind,String target,
            String result,String stage,int attemptCount,boolean connected)
    {
        String targetJson=target==null?"":" ,\"targetTemplateCode\":\""+target+"\"";
        String connectedJson=connected
                ?",\"name\":\"张女士\",\"city\":\"上海\","
                        +"\"demand\":\"劳动争议咨询\",\"visited\":\"NO\""
                :"";
        return Map.of(
                "resource_item_id",id,
                "resource_code",code,
                "resource_name",code,
                "business_type","LEAD",
                "status","ACTIVE",
                "sort_order",sortOrder,
                "value_json","{\"templateCode\":\"TD-003\",\"scenarioVersion\":1,"
                        +"\"completionPayload\":{\"contactResult\":\""+result
                        +"\",\"attemptStage\":\""+stage+"\",\"attemptCount\":"
                        +attemptCount+connectedJson+"},\"editableFields\":[\"contactResult\"],"
                        +"\"requiredMaterials\":[\"CONTACT_PROOF\"],"
                        +"\"completionNodeKey\":\"td003\",\"occurrence\":1,"
                        +"\"expectedEffect\":{\"kind\":\""+kind+"\""+targetJson+"},"
                        +"\"requiredForPublish\":true}");
    }
}
