package com.law.todo.application.command;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public final class TodoManagementCommands
{
    private TodoManagementCommands() { }

    public record TemplateCommand(
        Long templateId,
        @NotBlank String templateCode,
        @NotBlank String templateName,
        @NotBlank String businessType,
        @Pattern(regexp = "0|1") String status) { }

    public record TriggerCommand(
        Long triggerRuleId,
        @NotBlank String eventType,
        @NotNull @Positive Long templateId,
        @NotNull @Positive Long templateVersionId,
        @NotBlank String businessType,
        @Pattern(regexp = "Y|N") String enabled,
        String conditionJson) { }

    public record PublishCommand(
        @NotNull @Min(1) Integer versionNo,
        @NotBlank String ownerRuleJson,
        String dodRuleJson,
        String slaRuleJson,
        String nextRuleJson,
        String uiSchemaJson) { }

    public record CalendarCommand(
        Long calendarId,
        @NotBlank String calendarCode,
        @NotBlank String calendarName,
        @NotBlank String timezone,
        @NotBlank @Pattern(regexp = "[1-7](,[1-7])*") String workDays,
        @NotBlank @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d(:[0-5]\\d)?") String workStart,
        @NotBlank @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d(:[0-5]\\d)?") String workEnd,
        String exceptionJson,
        @Pattern(regexp = "0|1") String status) { }

    public record AttachmentCommand(
        String actionId,
        @NotBlank String attachmentType,
        @NotBlank String fileName,
        @NotBlank String fileUrl) { }

    public record ParticipantCommand(
        @NotBlank String participantType,
        @NotNull @Positive Long participantValue) { }
}
