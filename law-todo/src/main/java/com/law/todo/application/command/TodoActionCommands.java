package com.law.todo.application.command;

import java.util.Map;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Collections;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class TodoActionCommands
{
    private TodoActionCommands() { }
    public record Actor(@NotNull Long userId,@NotBlank String userName,Long deptId) { }
    public record ActionCommand(@NotBlank String actionId,String opinion,Map<String,Object> fields,
            List<Long> fileObjectIds)
    {
        public ActionCommand
        {
            fields=fields==null?Map.of():Collections.unmodifiableMap(new LinkedHashMap<>(fields));
            fileObjectIds=fileObjectIds==null?List.of():List.copyOf(fileObjectIds);
        }
        /** Compatibility adapter for historical callers whose payload is the form field map. */
        public ActionCommand(String actionId,String opinion,Map<String,Object> payload)
        {
            this(actionId,opinion,payload,List.of());
        }
        /** Compatibility alias for completion handlers and transfer commands. */
        @Deprecated public Map<String,Object> payload(){return fields;}
    }
}
