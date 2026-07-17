package com.law.todo.application.command;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public final class TodoDefinitionCommands
{
    private TodoDefinitionCommands() { }

    public record CopyTemplateCommand(@NotBlank String actionId,@NotBlank String newTemplateCode,@NotBlank String newTemplateName) { }
    public record CopyVersionCommand(@NotBlank String actionId,@NotNull @Min(1) Integer newVersionNo) { }
    public record UpdateDraftCommand(@NotBlank String actionId,@NotNull @Positive Long versionId,
            String ownerRuleJson,String dodRuleJson,String slaRuleJson,String nextRuleJson,String uiSchemaJson,
            String definitionJson)
    {
        public UpdateDraftCommand(String actionId,Long versionId,String definitionJson)
        { this(actionId,versionId,null,null,null,null,null,definitionJson); }
        /** Compatibility constructor for callers that still send the legacy projections. */
        public UpdateDraftCommand(String actionId,Long versionId,String ownerRuleJson,String dodRuleJson,String slaRuleJson,String nextRuleJson,String uiSchemaJson)
        { this(actionId,versionId,ownerRuleJson,dodRuleJson,slaRuleJson,nextRuleJson,uiSchemaJson,null); }
    }
    public record PublishDraftCommand(@NotBlank String actionId,@NotNull @Positive Long versionId) { }
    public record SimulateDefinitionCommand(
            @NotEmpty Map<String,Object> payload,
            @NotBlank String businessType,
            @NotNull @Positive Long businessId,
            @NotNull LocalDateTime effectiveAt,
            List<@Valid VirtualTaskCompletionSample> taskCompletions)
    {
        public SimulateDefinitionCommand(Map<String,Object> payload,String businessType,Long businessId,
                LocalDateTime effectiveAt)
        {
            this(payload,businessType,businessId,effectiveAt,List.of());
        }

        public SimulateDefinitionCommand
        {
            payload=payload==null?Map.of():java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(payload));
            taskCompletions=taskCompletions==null?List.of():List.copyOf(taskCompletions);
        }
    }
    public record VirtualTaskCompletionSample(
            @NotBlank String nodeKey,
            @NotNull @PositiveOrZero Integer occurrence,
            @NotNull Map<String,Object> payload,
            @NotNull LocalDateTime completedAt)
    {
        public VirtualTaskCompletionSample
        {
            payload=payload==null?null:java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(payload));
        }
    }
    public record RollbackDraftCommand(@NotBlank String actionId,@NotNull @Min(1) Integer newVersionNo) { }
}
