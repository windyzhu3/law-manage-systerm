package com.law.todo.application.command;

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
}
