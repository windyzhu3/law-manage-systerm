package com.law.todo.spi;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import com.law.todo.domain.model.TodoInstance;

public interface TodoCompletionHandler
{
    boolean supports(TodoInstance todo);
    void complete(TodoInstance todo,Map<String,Object> payload,Long operatorId,String operatorName);
    default void complete(CompletionContext context)
    {
        complete(context.todo(),context.payload(),context.operatorId(),context.operatorName());
    }
    default String catalogCode(){return getClass().getSimpleName();}
    default boolean supportsSimulation(){return false;}
    default String simulationDescription(){return supportsSimulation()?"Dry-run supported":"Business mutation handler is not executed by definition simulation";}

    /**
     * Completion metadata assembled inside the Todo transaction boundary.
     * controlledAutomatic is never derived from client payload or actor field equality.
     */
    record CompletionContext(TodoInstance todo,Map<String,Object> payload,Long operatorId,
            String operatorName,boolean controlledAutomatic)
    {
        public CompletionContext
        {
            todo=Objects.requireNonNull(todo,"todo");
            payload=payload==null?Map.of():
                    Collections.unmodifiableMap(new LinkedHashMap<>(payload));
        }
    }
}
