package com.law.todo.application.command;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class TodoAcceptanceEvidenceCommands
{
    private static final String SCENARIO_CODE = "[A-Z][A-Z0-9_]{1,63}";
    private static final String SCENARIO_STATUS = "DRAFT|IN_REVIEW|APPROVED|REJECTED";
    private static final String MAPPING_STATUS = "UNMAPPED|MAPPED|IN_REVIEW|APPROVED|REJECTED";

    private TodoAcceptanceEvidenceCommands() { }

    public record CreateScenarioCommand(@NotBlank @Size(max = 64) String actionId,
            @NotBlank @Pattern(regexp = SCENARIO_CODE) String scenarioCode,
            @NotBlank @Size(max = 200) String scenarioName,
            @NotBlank @Size(max = 500) String businessPath,
            @NotBlank String preconditionsJson, @NotBlank String stepsJson,
            @NotBlank String expectedOutcomesJson, @Size(max = 1000) String datasetRef,
            @Size(min = 64, max = 64) String datasetChecksum, @Positive Integer datasetVersion,
            @Positive Long ownerUserId, @Positive Long acceptorUserId, @Positive Long reviewerUserId,
            LocalDateTime dueAt, @NotBlank @Pattern(regexp = SCENARIO_STATUS) String status,
            @Size(max = 2000) String conclusion) { }

    public record UpdateScenarioCommand(@NotBlank @Size(max = 64) String actionId,
            @NotNull @Positive Long scenarioId, @NotNull @Min(0) Integer version,
            @NotBlank @Pattern(regexp = SCENARIO_CODE) String scenarioCode,
            @NotBlank @Size(max = 200) String scenarioName,
            @NotBlank @Size(max = 500) String businessPath,
            @NotBlank String preconditionsJson, @NotBlank String stepsJson,
            @NotBlank String expectedOutcomesJson, @Size(max = 1000) String datasetRef,
            @Size(min = 64, max = 64) String datasetChecksum, @Positive Integer datasetVersion,
            @Positive Long ownerUserId, @Positive Long acceptorUserId, @Positive Long reviewerUserId,
            LocalDateTime dueAt, @NotBlank @Pattern(regexp = SCENARIO_STATUS) String status,
            @Size(max = 2000) String conclusion) { }

    public record UpdateMappingCommand(@NotBlank @Size(max = 64) String actionId,
            @NotNull @Positive Long mappingId, @NotNull @Min(0) Integer version,
            @Positive Long scenarioId, @Size(max = 1000) String plannedTestRef,
            @Size(max = 2000) String evidenceNote, @Positive Long ownerUserId,
            @Positive Long reviewerUserId, LocalDateTime dueAt,
            @NotBlank @Pattern(regexp = MAPPING_STATUS) String status,
            @Size(max = 2000) String conclusion) { }

    public record MappingTarget(@NotNull @Positive Long mappingId, @NotNull @Min(0) Integer version) { }

    public record BatchBindMappingsCommand(@NotBlank @Size(max = 64) String actionId,
            @NotEmpty @Size(max = 114) List<@Valid MappingTarget> mappings,
            @NotNull @Positive Long scenarioId, @NotBlank @Size(max = 1000) String plannedTestRef,
            @Size(max = 2000) String evidenceNote) { }
}
