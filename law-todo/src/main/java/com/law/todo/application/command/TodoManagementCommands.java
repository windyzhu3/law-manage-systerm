package com.law.todo.application.command;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public final class TodoManagementCommands
{
    private TodoManagementCommands() { }

    public record TemplateCommand(
        Long templateId,
        String templateCode,
        @NotBlank String templateName,
        String businessType,
        @Pattern(regexp = "0|1") String status) {
        @AssertTrue(message="templateCode and businessType are required when creating a template")
        public boolean isCreateIdentityValid()
        {return templateId!=null||(templateCode!=null&&!templateCode.isBlank()&&businessType!=null&&!businessType.isBlank());}
    }

    public record TemplateMetadataCommand(Long templateId,@NotBlank String templateName,
            @NotBlank String actionId,@NotNull @Min(0) Integer expectedVersion) { }

    public record TemplateToggleCommand(@NotBlank @Pattern(regexp="0|1") String status,
            @NotBlank String actionId,@NotNull @Min(0) Integer expectedVersion) { }

    public record TriggerCommand(
        Long triggerRuleId,
        @NotBlank String eventType,
        @NotNull @Positive Long templateId,
        @NotNull @Positive Long templateVersionId,
        @NotBlank String businessType,
        @Pattern(regexp = "Y|N") String enabled,
        String conditionJson,
        @Min(1) Integer payloadVersion,
        @NotBlank String actionId,
        @Min(0) Integer expectedVersion) {
        public TriggerCommand(Long triggerRuleId,String eventType,Long templateId,Long templateVersionId,String businessType,String enabled,String conditionJson)
        {this(triggerRuleId,eventType,templateId,templateVersionId,businessType,enabled,conditionJson,1,null,null);}
        public TriggerCommand(Long triggerRuleId,String eventType,Long templateId,Long templateVersionId,String businessType,String enabled,String conditionJson,Integer payloadVersion)
        {this(triggerRuleId,eventType,templateId,templateVersionId,businessType,enabled,conditionJson,payloadVersion,null,null);}
        @AssertTrue(message = "expectedVersion is required for trigger updates")
        public boolean isExpectedVersionPresentForUpdate(){return triggerRuleId==null||expectedVersion!=null;}
    }

    public record TriggerSortCommand(@NotBlank String actionId,@NotEmpty List<@Valid TriggerSortItem> items)
    {
        public TriggerSortCommand {items=items==null?null:List.copyOf(items);}
    }

    public record TriggerSortItem(@NotNull @Positive Long triggerRuleId,@NotNull @Min(0) Integer sortOrder,
            @NotNull @Min(0) Integer expectedVersion) { }
    public record TriggerToggleCommand(@NotBlank @Pattern(regexp="Y|N") String enabled,@NotBlank String actionId,@NotNull @Min(0) Integer expectedVersion) { }

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
        @Pattern(regexp = "0|1") String status,
        @NotBlank String actionId,
        @Min(0) Integer expectedVersion) {
        public CalendarCommand(Long calendarId,String calendarCode,String calendarName,String timezone,String workDays,String workStart,String workEnd,String exceptionJson,String status)
        {this(calendarId,calendarCode,calendarName,timezone,workDays,workStart,workEnd,exceptionJson,status,null,null);}
        @AssertTrue(message = "expectedVersion is required for calendar updates")
        public boolean isExpectedVersionPresentForUpdate(){return calendarId==null||expectedVersion!=null;}
    }

    public record AttachmentCommand(
        String actionId,
        @NotBlank String attachmentType,
        @NotBlank String fileName,
        @NotBlank String fileUrl) { }

    public record ParticipantCommand(
        @NotBlank String participantType,
        @NotNull @Positive Long participantValue) { }
}
