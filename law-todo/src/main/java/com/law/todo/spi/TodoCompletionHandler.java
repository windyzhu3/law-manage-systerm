package com.law.todo.spi;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import com.law.todo.application.CompletionContext;
import com.law.todo.domain.model.TodoInstance;

public interface TodoCompletionHandler
{
    boolean supports(TodoInstance todo);
    /** Acquire and revalidate authoritative business locks before any Todo mutation. */
    default void prepare(CompletionContext context) { }
    void complete(TodoInstance todo,Map<String,Object> payload,Long operatorId,String operatorName);
    default CompletionResult handle(CompletionContext context)
    {
        complete(context.todo(),context.payload(),context.operatorId(),context.operatorName());
        return CompletionResult.completeTodo(context.payload());
    }
    default String catalogCode(){return getClass().getSimpleName();}
    default boolean supportsSimulation(){return false;}
    default String simulationDescription(){return supportsSimulation()?"Dry-run supported":"Business mutation handler is not executed by definition simulation";}
    default SimulationResult simulate(TodoInstance todo,Map<String,Object> payload)
    {return SimulationResult.none(payload);}

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

    /**
     * Pure completion outcome used by configuration simulation. Implementations must never persist
     * business data; produced template codes describe deferred Todos created by the business
     * lifecycle rather than by the routing graph.
     */
    record SimulationResult(Map<String,Object> routingPayload,List<String> producedTemplateCodes)
    {
        public SimulationResult
        {
            routingPayload=routingPayload==null?Map.of():
                    Collections.unmodifiableMap(new LinkedHashMap<>(routingPayload));
            producedTemplateCodes=producedTemplateCodes==null?List.of():List.copyOf(producedTemplateCodes);
        }
        public static SimulationResult none(Map<String,Object> routingPayload)
        {return new SimulationResult(routingPayload,List.of());}
        public static SimulationResult produces(Map<String,Object> routingPayload,List<String> templateCodes)
        {return new SimulationResult(routingPayload,templateCodes);}
    }
}
