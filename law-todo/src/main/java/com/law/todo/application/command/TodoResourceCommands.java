package com.law.todo.application.command;

import java.util.List;
import java.util.Map;

import com.alibaba.fastjson2.JSON;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public final class TodoResourceCommands
{
    private TodoResourceCommands() { }

    public record EventResourceCommand(Long eventCatalogId,@NotBlank String eventType,
            @NotNull @Positive Integer payloadVersion,@NotBlank String eventName,String description,
            @NotBlank String businessObjectType,@NotBlank String sourceModule,
            @NotBlank String payloadSchemaJson,@NotBlank String samplePayloadJson,
            @NotBlank @Pattern(regexp="DRAFT|ACTIVE|DISABLED") String status,
            @NotBlank String actionId,@NotNull @PositiveOrZero Integer expectedVersion)
    {
        @AssertTrue(message="Event payload schema and sample must be JSON objects")
        public boolean isJsonValid()
        {return JSON.isValidObject(payloadSchemaJson)&&JSON.isValidObject(samplePayloadJson);}
    }

    public record EventResourceStatusCommand(@NotBlank @Pattern(regexp="ACTIVE|DISABLED") String status,
            @NotBlank String actionId,@NotNull @Min(0) Integer expectedVersion) { }

    public record SimpleDodRuleCommand(Long dodRuleId,@NotBlank String ruleCode,@NotBlank String ruleName,
            @NotBlank String ruleType,@NotBlank String businessType,@NotNull List<@NotBlank String> requiredFields,
            @NotNull List<@NotBlank String> requiredAttachments,@NotNull List<@NotBlank String> validatorRefs,
            @NotNull List<Map<String,Object>> conditionalRules,
            @NotBlank @Pattern(regexp="0|1") String status,@NotBlank String actionId,
            @NotNull @PositiveOrZero Integer expectedVersion)
    {
        public SimpleDodRuleCommand
        {
            requiredFields=requiredFields==null?List.of():List.copyOf(requiredFields);
            requiredAttachments=requiredAttachments==null?List.of():List.copyOf(requiredAttachments);
            validatorRefs=validatorRefs==null?List.of():List.copyOf(validatorRefs);
            conditionalRules=conditionalRules==null?List.of():List.copyOf(conditionalRules);
        }
    }
}
