package com.law.todo.application.command;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public final class TodoOperationCommands
{
    private TodoOperationCommands() {}
    public record ForceCommand(@NotBlank String actionId,@NotBlank String reason,Map<String,Object> payload)
    {public ForceCommand{payload=payload==null?Map.of():Map.copyOf(payload);}}
    public record BatchTransferCommand(@NotBlank String actionId,@NotEmpty List<Long> todoIds,@NotNull Long targetOwnerId,@NotBlank String reason)
    {public BatchTransferCommand{todoIds=todoIds==null?List.of():List.copyOf(todoIds);}}
    public record SlaWaiverCommand(@NotBlank String actionId,@NotNull @Future LocalDateTime newDueAt,@NotBlank String reason) {}
}
