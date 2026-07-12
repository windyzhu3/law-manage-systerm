package com.law.todo.application.command;

import java.util.Map;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class TodoActionCommands
{
    private TodoActionCommands() { }
    public record Actor(@NotNull Long userId,@NotBlank String userName,Long deptId) { }
    public record ActionCommand(@NotBlank String actionId,String opinion,Map<String,Object> payload)
    {
        public ActionCommand { payload=payload==null?Map.of():Map.copyOf(payload); }
    }
}
