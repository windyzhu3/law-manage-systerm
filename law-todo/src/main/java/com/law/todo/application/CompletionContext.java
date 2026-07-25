package com.law.todo.application;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import com.law.todo.domain.model.TodoInstance;

/**
 * Immutable handler input. Only the fenced Todo application package can create the controlled
 * automatic capability; integration handlers can inspect it but cannot manufacture it.
 */
public final class CompletionContext
{
    private final TodoInstance todo;
    private final Map<String,Object> payload;
    private final Long operatorId;
    private final String operatorName;
    private final boolean controlledAutomatic;

    private CompletionContext(TodoInstance todo,Map<String,Object> payload,Long operatorId,
            String operatorName,boolean controlledAutomatic)
    {
        this.todo=Objects.requireNonNull(todo,"todo");
        this.payload=payload==null?Map.of():
                Collections.unmodifiableMap(new LinkedHashMap<>(payload));
        this.operatorId=operatorId;
        this.operatorName=operatorName;
        this.controlledAutomatic=controlledAutomatic;
    }

    public static CompletionContext human(TodoInstance todo,Map<String,Object> payload,
            Long operatorId,String operatorName)
    {
        return new CompletionContext(todo,payload,operatorId,operatorName,false);
    }

    static CompletionContext controlledAutomatic(TodoInstance todo,Map<String,Object> payload,
            Long operatorId,String operatorName)
    {
        return new CompletionContext(todo,payload,operatorId,operatorName,true);
    }

    public TodoInstance todo(){return todo;}
    public Map<String,Object> payload(){return payload;}
    public Long operatorId(){return operatorId;}
    public String operatorName(){return operatorName;}
    public boolean controlledAutomatic(){return controlledAutomatic;}
}
