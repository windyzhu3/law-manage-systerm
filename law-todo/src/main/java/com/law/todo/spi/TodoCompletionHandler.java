package com.law.todo.spi;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;
import com.law.todo.application.CompletionContext;
import com.law.todo.domain.model.TodoInstance;

public interface TodoCompletionHandler
{
    boolean supports(TodoInstance todo);
    void complete(TodoInstance todo,Map<String,Object> payload,Long operatorId,String operatorName);
    default CompletionResult handle(CompletionContext context)
    {
        complete(context.todo(),context.payload(),context.operatorId(),context.operatorName());
        return CompletionResult.completeTodo(context.payload());
    }
    default String catalogCode(){return getClass().getSimpleName();}
    default boolean supportsSimulation(){return false;}
    default String simulationDescription(){return supportsSimulation()?"Dry-run supported":"Business mutation handler is not executed by definition simulation";}

    /** Server-authoritative terminality and routing input returned by the business handler. */
    record CompletionResult(boolean completeTodo,Map<String,Object> routingPayload)
    {
        public CompletionResult
        {
            routingPayload=routingPayload==null?Map.of():
                    Collections.unmodifiableMap(new LinkedHashMap<>(routingPayload));
        }
        public static CompletionResult completeTodo(Map<String,Object> routingPayload)
        {return new CompletionResult(true,routingPayload);}
        public static CompletionResult retainCurrentTodo(Map<String,Object> routingPayload)
        {return new CompletionResult(false,routingPayload);}
    }
}
