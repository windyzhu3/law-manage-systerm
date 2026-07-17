package com.law.todo.application.command;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class TodoDecisionCommands
{
    private static final String CODE="[A-Z][A-Z0-9_-]{1,63}";
    private static final String STATUS="OPEN|RESOLVED|CLOSED";
    private TodoDecisionCommands() { }

    public record CreateDecisionCommand(
            @NotBlank @Size(max=64) String actionId,
            @NotBlank @Pattern(regexp=CODE) String code,
            @NotBlank @Size(max=200) String title,
            @Size(max=1000) String description,
            @NotNull Boolean blocking,
            @NotBlank @Pattern(regexp=STATUS) String status,
            @Size(max=2000) String conclusion,
            @Size(max=2000) String resolution) { }

    public record UpdateDecisionCommand(
            @NotBlank @Size(max=64) String actionId,
            @NotNull @Positive Long decisionId,
            @NotNull @Min(0) Integer version,
            @NotBlank @Pattern(regexp=CODE) String code,
            @NotBlank @Size(max=200) String title,
            @Size(max=1000) String description,
            @NotNull Boolean blocking,
            @NotBlank @Pattern(regexp=STATUS) String status,
            @Size(max=2000) String conclusion,
            @Size(max=2000) String resolution) { }
}
