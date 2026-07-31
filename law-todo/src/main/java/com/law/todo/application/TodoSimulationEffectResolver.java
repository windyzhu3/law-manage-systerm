package com.law.todo.application;

import java.util.Map;
import java.util.Objects;

import com.alibaba.fastjson2.JSONObject;
import com.law.todo.application.TodoBusinessOutcomeCatalogService.BusinessOutcomeOption;
import com.law.todo.application.TodoBusinessOutcomeCatalogService.BusinessOutcomeSet;
import com.law.todo.domain.TodoException;

/** Normalizes governed scenario expectations and observed dry-run effects. */
public class TodoSimulationEffectResolver
{
    public enum EffectKind
    {
        NEXT_TEMPLATE,END,RETAIN_CURRENT,SCHEDULE_NEXT,SCHEDULE_SELF,
        EXPECTED_VALIDATION_FAILURE
    }

    public record SimulationEffect(EffectKind kind,String targetTemplateCode,String actionCode)
    {
        public SimulationEffect
        {
            if(kind==null)
                throw new TodoException("TODO_SIMULATION_EFFECT_KIND_REQUIRED",
                        "Simulation effect kind is required");
            targetTemplateCode=blankToNull(targetTemplateCode);
            actionCode=blankToNull(actionCode);
            if((kind==EffectKind.NEXT_TEMPLATE||kind==EffectKind.SCHEDULE_SELF)
                    &&targetTemplateCode==null)
                throw new TodoException("TODO_SIMULATION_EFFECT_TARGET_REQUIRED",
                        "Simulation effect requires a target template");
        }
    }

    public SimulationEffect expected(JSONObject value)
    {
        if(value==null)return null;
        JSONObject configured=value.getJSONObject("expectedEffect");
        if(configured!=null)
        {
            String kind=configured.getString("kind");
            if(kind==null||kind.isBlank())
                throw new TodoException("TODO_SIMULATION_EFFECT_KIND_REQUIRED",
                        "Simulation effect kind is required");
            try
            {
                return new SimulationEffect(EffectKind.valueOf(kind),
                        configured.getString("targetTemplateCode"),null);
            }
            catch(IllegalArgumentException invalid)
            {
                throw new TodoException("TODO_SIMULATION_EFFECT_KIND_INVALID",
                        "Unsupported simulation effect kind "+kind);
            }
        }
        String legacy=value.getString("expectedNextTemplateCode");
        return legacy==null||legacy.isBlank()?null:
                new SimulationEffect(EffectKind.NEXT_TEMPLATE,legacy,null);
    }

    public SimulationEffect actual(String generatedNextTemplateCode,BusinessOutcomeSet outcomeSet,
            Map<String,Object> completionPayload,boolean normalGraphEnded)
    {
        if(generatedNextTemplateCode!=null&&!generatedNextTemplateCode.isBlank())
            return new SimulationEffect(EffectKind.NEXT_TEMPLATE,generatedNextTemplateCode,null);
        BusinessOutcomeOption option=selectedOutcome(outcomeSet,completionPayload);
        if(option!=null&&supportsMetadataEffect(option.effectKind()))
            return new SimulationEffect(effectKind(option.effectKind()),option.targetTemplateCode(),option.value());
        return normalGraphEnded?new SimulationEffect(EffectKind.END,null,null):null;
    }

    public SimulationEffect validationFailure(RuntimeException failure)
    {
        return new SimulationEffect(EffectKind.EXPECTED_VALIDATION_FAILURE,null,errorCode(failure));
    }

    public boolean matches(SimulationEffect expected,SimulationEffect actual,
            boolean definitionHashMatches,String expectedErrorCode)
    {
        if(!definitionHashMatches||expected==null||actual==null
                ||expected.kind()!=actual.kind()
                ||!Objects.equals(expected.targetTemplateCode(),actual.targetTemplateCode()))return false;
        return expected.kind()!=EffectKind.EXPECTED_VALIDATION_FAILURE
                ||Objects.equals(blankToNull(expectedErrorCode),actual.actionCode());
    }

    private BusinessOutcomeOption selectedOutcome(BusinessOutcomeSet outcomes,Map<String,Object> payload)
    {
        if(outcomes==null||!outcomes.available()||payload==null)return null;
        Object selected=payload.get(outcomes.resultField());
        if(selected==null)return null;
        return outcomes.options().stream()
                .filter(option->Objects.equals(option.value(),String.valueOf(selected)))
                .findFirst().orElse(null);
    }

    private boolean supportsMetadataEffect(String kind)
    {
        return "RETAIN_CURRENT".equals(kind)||"SCHEDULE_NEXT".equals(kind)
                ||"SCHEDULE_SELF".equals(kind)||"END".equals(kind);
    }

    private EffectKind effectKind(String value)
    {
        try{return EffectKind.valueOf(value);}
        catch(IllegalArgumentException invalid)
        {
            throw new TodoException("TODO_SIMULATION_EFFECT_KIND_INVALID",
                    "Unsupported simulation effect kind "+value);
        }
    }

    private String errorCode(RuntimeException failure)
    {
        if(failure instanceof TodoException todo)return blankToNull(todo.getBusinessCode());
        String message=failure==null?null:failure.getMessage();
        if(message==null)return failure==null?null:failure.getClass().getSimpleName();
        int separator=message.indexOf(':');
        String candidate=(separator<0?message:message.substring(0,separator)).trim();
        return candidate.startsWith("TODO_")?candidate:failure.getClass().getSimpleName();
    }

    private static String blankToNull(String value)
    {return value==null||value.isBlank()?null:value;}
}
