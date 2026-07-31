package com.law.todo.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.law.todo.application.TodoBusinessOutcomeCatalogService.BusinessOutcomeOption;
import com.law.todo.application.TodoBusinessOutcomeCatalogService.BusinessOutcomeSet;
import com.law.todo.application.TodoSimulationEffectResolver.EffectKind;
import com.law.todo.application.TodoSimulationEffectResolver.SimulationEffect;
import com.law.todo.domain.TodoException;

class TodoSimulationEffectResolverTest
{
    private final TodoSimulationEffectResolver resolver=new TodoSimulationEffectResolver();

    @ParameterizedTest
    @CsvSource(value={
            "NEXT_TEMPLATE|TD-004",
            "END|NULL",
            "RETAIN_CURRENT|NULL",
            "SCHEDULE_NEXT|NULL",
            "SCHEDULE_SELF|TD-004",
            "EXPECTED_VALIDATION_FAILURE|NULL"
    },delimiter='|',nullValues="NULL")
    void parsesEveryGovernedExpectedEffect(String kind,String target)
    {
        SimulationEffect effect=resolver.expected(json(kind,target));

        assertThat(effect.kind()).isEqualTo(EffectKind.valueOf(kind));
        assertThat(effect.targetTemplateCode()).isEqualTo(target);
    }

    @Test
    void normalizesTheLegacyNextTemplateFieldDuringTheCompatibilityWindow()
    {
        SimulationEffect effect=resolver.expected(
                JSON.parseObject("{\"expectedNextTemplateCode\":\"TD-004\"}"));

        assertThat(effect).isEqualTo(new SimulationEffect(EffectKind.NEXT_TEMPLATE,"TD-004",null));
    }

    @Test
    void generatedNextTodoHasPriorityOverTerminalOutcomeMetadata()
    {
        BusinessOutcomeSet outcomes=new BusinessOutcomeSet("reviewResult","复核结果","复核动作",
                List.of(new BusinessOutcomeOption("TRUE_INVALID","确认无效","END",null,null,null)),
                "TD002_GOVERNED_OUTCOMES");

        SimulationEffect effect=resolver.actual("TD-001",outcomes,
                Map.of("reviewResult","TRUE_INVALID"),true);

        assertThat(effect).isEqualTo(new SimulationEffect(EffectKind.NEXT_TEMPLATE,"TD-001",null));
    }

    @ParameterizedTest
    @CsvSource(value={
            "RETAIN_CURRENT|NULL",
            "SCHEDULE_NEXT|NULL",
            "SCHEDULE_SELF|TD-004",
            "END|NULL"
    },delimiter='|',nullValues="NULL")
    void resolvesOnlyGovernedOutcomeMetadata(String kind,String target)
    {
        BusinessOutcomeSet outcomes=new BusinessOutcomeSet("result","处理结果","受治理动作",
                List.of(new BusinessOutcomeOption("RESULT","受治理结果",kind,target,null,null)),
                "GOVERNED_OUTCOMES");

        SimulationEffect effect=resolver.actual(null,outcomes,Map.of("result","RESULT"),true);

        assertThat(effect.kind()).isEqualTo(EffectKind.valueOf(kind));
        assertThat(effect.targetTemplateCode()).isEqualTo(target);
        assertThat(effect.actionCode()).isEqualTo("RESULT");
    }

    @Test
    void validationFailurePassesOnlyForTheExactExpectedBusinessCode()
    {
        SimulationEffect expected=new SimulationEffect(EffectKind.EXPECTED_VALIDATION_FAILURE,null,null);
        SimulationEffect actual=resolver.validationFailure(new TodoException(
                "TODO_DOD_ATTACHMENT_MISSING","Proof is required"));

        assertThat(resolver.matches(expected,actual,true,"TODO_DOD_ATTACHMENT_MISSING")).isTrue();
        assertThat(resolver.matches(expected,actual,true,"TODO_OTHER_ERROR")).isFalse();
        assertThat(resolver.matches(expected,actual,false,"TODO_DOD_ATTACHMENT_MISSING")).isFalse();
    }

    private JSONObject json(String kind,String target)
    {
        JSONObject effect=new JSONObject();
        effect.put("kind",kind);
        if(target!=null)effect.put("targetTemplateCode",target);
        JSONObject value=new JSONObject();
        value.put("expectedEffect",effect);
        return value;
    }
}
