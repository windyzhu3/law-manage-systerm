package com.law.todo.application;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.spi.TodoCompletionHandler;
import com.law.todo.spi.TodoCompletionHandler.CompletionResult;

/** One completion-handler selection and preparation path for every terminal entry point. */
@Component
public class TodoCompletionOrchestrator
{
    private final List<TodoCompletionHandler> handlers;

    public TodoCompletionOrchestrator(List<TodoCompletionHandler> handlers)
    {this.handlers=handlers==null?List.of():List.copyOf(handlers);}

    public PreparedCompletion prepareHuman(TodoInstance todo,Map<String,Object> payload,
            Long operatorId,String operatorName)
    {return prepare(CompletionContext.human(todo,payload,operatorId,operatorName));}

    public PreparedCompletion prepareAutomatic(TodoInstance todo,Map<String,Object> payload,
            Long operatorId,String operatorName)
    {return prepare(CompletionContext.controlledAutomatic(todo,payload,operatorId,operatorName));}

    private PreparedCompletion prepare(CompletionContext context)
    {
        List<TodoCompletionHandler> supported=handlers.stream()
                .filter(handler->handler.supports(context.todo())).toList();
        if(supported.size()>1)
            throw new TodoException("TODO_COMPLETION_HANDLER_AMBIGUOUS",
                    "More than one completion handler supports this Todo");
        TodoCompletionHandler handler=supported.isEmpty()?null:supported.get(0);
        if(handler!=null)handler.prepare(context);
        return new PreparedCompletion(context,handler);
    }

    public CompletionResult handle(PreparedCompletion prepared)
    {
        CompletionResult result=prepared.handler()==null
                ?CompletionResult.completeTodo(prepared.context().payload())
                :prepared.handler().handle(prepared.context());
        if(result==null)
            throw new TodoException("TODO_COMPLETION_RESULT_REQUIRED",
                    "Completion handler did not return an authoritative result");
        return result;
    }

    public record PreparedCompletion(CompletionContext context,TodoCompletionHandler handler) { }
}
