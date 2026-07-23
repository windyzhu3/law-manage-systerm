package com.law.todo.application.command;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.alibaba.fastjson2.JSON;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public final class TodoConfigurationCommands
{
    private TodoConfigurationCommands() { }

    public record SlaRuleCommand(Long slaRuleId,@NotBlank String ruleCode,@NotBlank String ruleName,
            @NotBlank String slaType,@NotNull @Positive Integer durationValue,@NotBlank String durationUnit,
            @NotBlank String calendarCode,@NotBlank String startStrategy,
            @NotNull @Positive Integer softRemindPercent,@NotNull @Positive Integer hardRemindPercent,
            @NotNull @Positive Integer escalatePercent,String pausePolicyJson,String escalationPolicyJson,
            String autoActionJson,@NotBlank @Pattern(regexp="0|1") String status,@NotBlank String actionId,
            @NotNull @PositiveOrZero Integer expectedVersion)
    {
        @AssertTrue(message="SLA thresholds must be ordered")
        public boolean isThresholdsOrdered()
        {
            return softRemindPercent != null && hardRemindPercent != null && escalatePercent != null
                    && softRemindPercent <= hardRemindPercent && hardRemindPercent <= escalatePercent;
        }

        @AssertTrue(message="SLA policy JSON is invalid")
        public boolean isPolicyJsonValid()
        {
            return validOptionalJson(pausePolicyJson) && validOptionalJson(escalationPolicyJson)
                    && validOptionalJson(autoActionJson);
        }
    }

    public record DodRuleCommand(Long dodRuleId,@NotBlank String ruleCode,@NotBlank String ruleName,
            @NotBlank String ruleType,@NotBlank String requiredFieldsJson,
            @NotBlank String requiredAttachmentsJson,@NotBlank String conditionalRulesJson,
            @NotBlank String validatorRefsJson,@NotBlank String errorMessagesJson,
            @NotBlank @Pattern(regexp="0|1") String status,@NotBlank String actionId,
            @NotNull @PositiveOrZero Integer expectedVersion)
    {
        @AssertTrue(message="DoD rule JSON is invalid")
        public boolean isRuleJsonValid()
        {
            return JSON.isValidArray(requiredFieldsJson) && JSON.isValidArray(requiredAttachmentsJson)
                    && JSON.isValidArray(conditionalRulesJson) && JSON.isValidArray(validatorRefsJson)
                    && JSON.isValidObject(errorMessagesJson);
        }
    }

    public record TemplateDraftRuleCommand(@NotBlank String actionId,@NotNull @Positive Long versionId,
            @NotNull @PositiveOrZero Integer expectedVersion,@NotNull List<@Valid RuleReference> ruleReferences)
    {
        public TemplateDraftRuleCommand
        {
            ruleReferences=ruleReferences==null?null:List.copyOf(ruleReferences);
        }
    }

    public record RuleReference(@NotBlank String type,@NotNull @Positive Long id,
            @NotNull @PositiveOrZero Integer order,String configJson)
    {
        @AssertTrue(message="Rule reference configuration must be a JSON object")
        public boolean isConfigJsonValid()
        {
            return configJson==null || JSON.isValidObject(configJson);
        }
    }

    public record ConfigurationResourceCommand(Long resourceItemId,
            @NotBlank @Pattern(regexp="FIELD|MATERIAL|DOD_RECIPE") String resourceType,
            @NotBlank String resourceCode,@NotBlank String resourceName,String description,
            @NotBlank @Pattern(regexp="LEAD|CUSTOMER|CONTRACT|CASE|MATTER") String businessType,
            @NotBlank String valueJson,@NotBlank @Pattern(regexp="ACTIVE|DISABLED") String status,
            @NotNull @PositiveOrZero Integer sortOrder,@NotBlank String actionId,
            @NotNull @PositiveOrZero Integer expectedVersion)
    {
        @AssertTrue(message="Configuration resource value must be a JSON object")
        public boolean isValueJsonValid(){return JSON.isValidObject(valueJson);}
    }

    public record JourneyPayloadCommand(@NotNull @Positive Long templateId,@NotNull @Positive Long versionId,
            @NotBlank String eventType,@NotNull @Positive Integer payloadVersion,@NotBlank String businessType,
            @NotNull Long businessId,Map<String,Object> manualOverrides,@NotBlank String expectedDefinitionHash)
    {
        public JourneyPayloadCommand { manualOverrides=immutableMap(manualOverrides); }
        @AssertTrue(message="Simulation business ID must not be zero")
        public boolean isBusinessIdValid(){return businessId!=null&&businessId.longValue()!=0L;}

        @Override
        public String toString()
        {
            return "JourneyPayloadCommand[templateId="+templateId+", versionId="+versionId
                    +", payloadVersion="+payloadVersion+", businessId="+businessId
                    +", manualOverrideCount="+manualOverrides.size()+"]";
        }
    }

    public record JourneySimulationCommand(@NotNull @Positive Long templateId,@NotNull @Positive Long versionId,
            @NotBlank String eventType,@NotNull @Positive Integer payloadVersion,@NotBlank String businessType,
            @NotNull Long businessId,Map<String,Object> manualOverrides,@NotNull LocalDateTime effectiveAt,
            @Valid List<TodoDefinitionCommands.VirtualTaskCompletionSample> taskCompletions,
            @NotBlank String expectedDefinitionHash)
    {
        public JourneySimulationCommand
        {
            manualOverrides=immutableMap(manualOverrides);
            taskCompletions=immutableTaskCompletions(taskCompletions);
        }
        @AssertTrue(message="Simulation business ID must not be zero")
        public boolean isBusinessIdValid(){return businessId!=null&&businessId.longValue()!=0L;}

        @Override
        public String toString()
        {
            return "JourneySimulationCommand[templateId="+templateId+", versionId="+versionId
                    +", payloadVersion="+payloadVersion+", businessId="+businessId
                    +", manualOverrideCount="+manualOverrides.size()
                    +", taskCompletionCount="+taskCompletions.size()+"]";
        }
    }

    public record ConfigurationSimulationCommand(@NotBlank String requestId,@NotNull @Positive Long versionId,
            @NotBlank String eventType,@NotNull @Positive Integer payloadVersion,
            @NotBlank String businessType,@NotNull @Positive Long businessId,
            @NotEmpty Map<String,Object> payload,@NotNull LocalDateTime effectiveAt,
            @Valid List<TodoDefinitionCommands.VirtualTaskCompletionSample> taskCompletions,
            @NotBlank String expectedDefinitionHash)
    {
        public ConfigurationSimulationCommand(String requestId,Long versionId,String eventType,String businessType,
                Long businessId,Map<String,Object> payload,LocalDateTime effectiveAt,
                List<TodoDefinitionCommands.VirtualTaskCompletionSample> taskCompletions)
        {this(requestId,versionId,eventType,1,businessType,businessId,payload,effectiveAt,taskCompletions,null);}
        public ConfigurationSimulationCommand(String requestId,Long versionId,String eventType,String businessType,
                Long businessId,Map<String,Object> payload,LocalDateTime effectiveAt,
                List<TodoDefinitionCommands.VirtualTaskCompletionSample> taskCompletions,String expectedDefinitionHash)
        {this(requestId,versionId,eventType,1,businessType,businessId,payload,effectiveAt,taskCompletions,expectedDefinitionHash);}
        public ConfigurationSimulationCommand
        {
            payload=immutableMap(payload);
            taskCompletions=immutableTaskCompletions(taskCompletions);
        }

        public TodoDefinitionCommands.SimulateDefinitionCommand toDefinitionCommand()
        {
            return new TodoDefinitionCommands.SimulateDefinitionCommand(payload,businessType,businessId,effectiveAt,
                    taskCompletions);
        }

        @Override
        public String toString()
        {
            return "ConfigurationSimulationCommand[versionId="+versionId+", payloadVersion="+payloadVersion
                    +", businessId="+businessId+", payloadFieldCount="+payload.size()
                    +", taskCompletionCount="+taskCompletions.size()+"]";
        }
    }

    private static boolean validOptionalJson(String json)
    {
        return json==null || JSON.isValidObject(json) || JSON.isValidArray(json);
    }

    private static Map<String,Object> immutableMap(Map<String,Object> source)
    {
        if(source==null)return Map.of();
        Map<String,Object> copy=new java.util.LinkedHashMap<>();
        source.forEach((key,value)->copy.put(key,immutableValue(value)));
        return java.util.Collections.unmodifiableMap(copy);
    }

    private static List<TodoDefinitionCommands.VirtualTaskCompletionSample> immutableTaskCompletions(
            List<TodoDefinitionCommands.VirtualTaskCompletionSample> source)
    {
        if(source==null)return List.of();
        List<TodoDefinitionCommands.VirtualTaskCompletionSample> copy=new java.util.ArrayList<>();
        for (TodoDefinitionCommands.VirtualTaskCompletionSample completion : source)
        {
            if(completion==null)copy.add(null);
            else copy.add(new TodoDefinitionCommands.VirtualTaskCompletionSample(completion.nodeKey(),completion.occurrence(),
                    completion.payload()==null?null:immutableMap(completion.payload()),completion.completedAt()));
        }
        return List.copyOf(copy);
    }

    private static Object immutableValue(Object value)
    {
        if(value instanceof Map<?,?> map)
        {
            Map<Object,Object> copy=new java.util.LinkedHashMap<>();
            map.forEach((key,nested)->copy.put(key,immutableValue(nested)));
            return java.util.Collections.unmodifiableMap(copy);
        }
        if(value instanceof List<?> list)
            return java.util.Collections.unmodifiableList(list.stream().map(TodoConfigurationCommands::immutableValue).toList());
        return value;
    }
}
