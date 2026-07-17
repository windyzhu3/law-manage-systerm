package com.law.todo.spi;

import java.util.Map;
import com.law.todo.domain.model.TodoInstance;

@FunctionalInterface
public interface TodoBusinessValidator
{
    void validate(TodoInstance todo,Map<String,Object> payload);
    default boolean supports(String businessType){return true;}
    default String catalogCode(){return getClass().getSimpleName();}
    default String catalogDescription(){return "Server-side business completion validator";}
}
