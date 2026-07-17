package com.law.todo.spi;

import java.util.Map;
import com.law.todo.domain.model.TodoInstance;

public interface TodoCompletionHandler
{
    boolean supports(TodoInstance todo);
    void complete(TodoInstance todo,Map<String,Object> payload,Long operatorId,String operatorName);
    default String catalogCode(){return getClass().getSimpleName();}
    default boolean supportsSimulation(){return false;}
    default String simulationDescription(){return supportsSimulation()?"Dry-run supported":"Business mutation handler is not executed by definition simulation";}
}
