package com.law.todo.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.law.todo.application.TodoConfigurationResourceCatalogService.DodRecipeResource;

class TodoDodRecipeMatcherTest
{
    private final TodoDodRecipeMatcher matcher=new TodoDodRecipeMatcher();

    @Test
    void ranksExactActionThenStageThenConfiguredPriority()
    {
        List<DodRecipeResource> result=matcher.match(List.of(
                recipe("GLOBAL_GENERIC","全局通用","ALL",List.of(),List.of(),1,List.of("填写处理结果")),
                recipe("LEAD_GENERIC","线索通用","LEAD",List.of(),List.of("LEAD_FOLLOWUP"),10,List.of("填写线索处理结果")),
                recipe("LEAD_OTHER","其他动作","LEAD",List.of("QUALIFY"),List.of("LEAD_FOLLOWUP"),500,List.of("不应出现")),
                recipe("LEAD_FIRST_CONTACT","首联完成","LEAD",List.of("FIRST_CONTACT"),List.of("LEAD_FOLLOWUP"),100,
                        List.of("记录联系时间并填写跟进结果"))),
                "LEAD","FIRST_CONTACT","LEAD_FOLLOWUP");

        assertThat(result).extracting(DodRecipeResource::code)
                .containsExactly("LEAD_FIRST_CONTACT","LEAD_GENERIC","GLOBAL_GENERIC");
        assertThat(result.get(0).employeeInstructions()).contains("记录联系时间并填写跟进结果");
    }

    @Test
    void resolvesEqualScoresByNameThenCodeAndRejectsOtherBusinessTypes()
    {
        List<DodRecipeResource> result=matcher.match(List.of(
                recipe("CUSTOMER_ONLY","客户配方","CUSTOMER",List.of(),List.of(),20,List.of()),
                recipe("LEAD_Z","相同名称","LEAD",List.of(),List.of(),20,List.of()),
                recipe("LEAD_A","相同名称","LEAD",List.of(),List.of(),20,List.of()),
                recipe("LEAD_B","不同名称","LEAD",List.of(),List.of(),20,List.of())),
                "LEAD","FIRST_CONTACT","LEAD_FOLLOWUP");

        assertThat(result).extracting(DodRecipeResource::code)
                .containsExactly("LEAD_B","LEAD_A","LEAD_Z");
    }

    @Test
    void legacyRecipeConstructionDefaultsToWildcardContext()
    {
        DodRecipeResource legacy=new DodRecipeResource("LEGACY","历史配方","历史配方","LEAD",
                List.of("contactedAt"),List.of(),List.of(),List.of());

        assertThat(matcher.match(List.of(legacy),"LEAD","FIRST_CONTACT","LEAD_FOLLOWUP"))
                .containsExactly(legacy);
        assertThat(legacy.businessActions()).isEmpty();
        assertThat(legacy.employeeInstructions()).isEmpty();
    }

    private DodRecipeResource recipe(String code,String name,String businessType,List<String> actions,List<String> stages,
            int priority,List<String> instructions)
    {
        return new DodRecipeResource(code,name,name,businessType,actions,stages,priority,
                List.of("contactedAt"),List.of(),List.of(),List.<Map<String,Object>>of(),instructions);
    }
}
