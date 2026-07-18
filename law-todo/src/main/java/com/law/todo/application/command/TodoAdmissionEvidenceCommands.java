package com.law.todo.application.command;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public final class TodoAdmissionEvidenceCommands
{
    private static final String STATUS="OPEN|IN_REVIEW|APPROVED|REJECTED";
    private TodoAdmissionEvidenceCommands() { }

    public record UpdateAdmissionEvidenceCommand(
            @NotBlank @Size(max=64) String actionId,
            @NotNull @Positive Long evidenceId,
            @Min(0) int version,
            @Positive Long ownerUserId,
            @Positive Long reviewerUserId,
            LocalDateTime dueAt,
            @NotBlank @Pattern(regexp=STATUS) String status,
            @Size(max=1000) String artifactRef,
            @Size(max=2000) String conclusion) { }
}
