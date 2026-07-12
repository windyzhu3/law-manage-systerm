package com.law.todo.application.command;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public final class TodoDefinitionCommands
{
    private TodoDefinitionCommands() { }

    public record CopyTemplateCommand(@NotBlank String actionId,@NotBlank String newTemplateCode,@NotBlank String newTemplateName) { }
    public record CopyVersionCommand(@NotBlank String actionId,@NotNull @Min(1) Integer newVersionNo) { }
    public record UpdateDraftCommand(@NotBlank String actionId,@NotNull @Positive Long versionId,@NotBlank String ownerRuleJson,String dodRuleJson,String slaRuleJson,String nextRuleJson,String uiSchemaJson) { }
    public record PublishDraftCommand(@NotBlank String actionId,@NotNull @Positive Long versionId) { }
}
