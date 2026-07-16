package com.law.todo.extension;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class TodoExtensionCommands
{
    private TodoExtensionCommands() { }

    public enum DurationUnit { MINUTES, HOURS, CALENDAR_DAYS, WORKING_DAYS }
    public enum ExtensionStatus { PENDING, APPROVED, REJECTED, CANCELLED }

    /** An unapproved request never changes governed SLA time. */
    public enum PendingSlaMode { CONTINUE }

    public record DurationPolicy(long policyVersionId,long value,@NotNull DurationUnit unit)
    {
        public DurationPolicy
        {
            if(policyVersionId<=0)throw new IllegalArgumentException("policyVersionId must be positive");
            if(value<=0)throw new IllegalArgumentException("value must be positive");
        }
    }

    public record CyclePolicy(long policyVersionId,long interval,@NotNull DurationUnit unit,int maxOccurrences)
    {
        public CyclePolicy
        {
            if(policyVersionId<=0)throw new IllegalArgumentException("policyVersionId must be positive");
            if(interval<=0)throw new IllegalArgumentException("interval must be positive");
            if(maxOccurrences<=0)throw new IllegalArgumentException("maxOccurrences must be positive");
        }
    }

    public record CycleOccurrence(String occurrenceKey,int occurrenceNo,LocalDateTime dueAt,long policyVersionId) { }

    public record RequestCommand(@NotBlank @Size(max=64) String actionId,@NotNull LocalDateTime requestedDueAt,
            @NotBlank @Size(max=1000) String reason,List<@NotNull Long> proofFileObjectIds)
    {
        public RequestCommand { proofFileObjectIds=proofFileObjectIds==null?List.of():List.copyOf(proofFileObjectIds); }
    }

    public record DecisionCommand(@NotBlank @Size(max=64) String actionId,@NotBlank @Size(max=1000) String reason) { }

    public record ExtensionView(Long extensionId,Long todoId,ExtensionStatus status,LocalDateTime originalDueAt,
            LocalDateTime requestedDueAt,LocalDateTime approvedDueAt,List<Long> proofFileObjectIds,
            Long requesterId,Long decisionActorId,String decisionReason,Long policyVersionId,
            PendingSlaMode pendingSlaMode) { }
}
