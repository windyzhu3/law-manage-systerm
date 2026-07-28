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
}
