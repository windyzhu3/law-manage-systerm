package com.law.todo.spi;

import java.util.Map;
import com.law.todo.domain.model.TodoInstance;

public interface TodoCompletionHandler
{
    boolean supports(TodoInstance todo);
    void complete(TodoInstance todo,Map<String,Object> payload,Long operatorId,String operatorName);
}
