package com.ruoyi.system.service.event;

import java.util.Map;
import org.springframework.stereotype.Component;
import com.law.business.lead.dto.LeadInvalidReviewCommand;
import com.law.todo.application.CompletionContext;
import com.law.todo.application.TodoAutoActionService;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.spi.TodoCompletionHandler;
import com.law.todo.spi.TodoCompletionHandler.CompletionResult;
import com.ruoyi.system.service.lead.LeadInvalidReviewService;

/** Typed TD-002 adapter. Automatic defaulting is selected only by the fenced service actor. */
@Component
public class LeadInvalidReviewTodoHandler implements TodoCompletionHandler
{
    private final LeadInvalidReviewService reviews;
    private final LeadTodoSourceContextService contexts;

    public LeadInvalidReviewTodoHandler(LeadInvalidReviewService reviews,
            LeadTodoSourceContextService contexts)
    {this.reviews=reviews;this.contexts=contexts;}

    @Override public boolean supports(TodoInstance todo)
    {return todo!=null&&"TD-002".equals(todo.getTemplateCode());}

    @Override public String catalogCode(){return "TD-002_COMPLETE";}

    @Override
    public void complete(TodoInstance todo,Map<String,Object> payload,Long operatorId,String operatorName)
    {
        reviewHuman(todo,payload);
    }

    @Override
    public CompletionResult handle(CompletionContext completion)
    {
        if(!completion.controlledAutomatic())
        {
            reviewHuman(completion.todo(),completion.payload());
            return CompletionResult.completeTodo(completion.payload());
        }
        TodoInstance todo=completion.todo();
        Map<String,Object> values=LeadTodoPayloadMapper.values(completion.payload());
        LeadTodoSourceContextService.InvalidReviewContext context=source(todo,values);
        reviews.reviewAutomatically(todo.getBusinessId(),context.reviewId(),context.sourceTodoId(),
                TodoAutoActionService.SERVICE_ACTOR);
        return CompletionResult.completeTodo(Map.of(
                "reviewResult","TRUE_INVALID",
                "reviewId",context.reviewId(),
                "sourceTodoId",context.sourceTodoId()));
    }

    private void reviewHuman(TodoInstance todo,Map<String,Object> payload)
    {
        Map<String,Object> values=LeadTodoPayloadMapper.values(payload);
        LeadTodoSourceContextService.InvalidReviewContext context=source(todo,values);
        LeadInvalidReviewCommand command=new LeadInvalidReviewCommand();
        command.setLeadId(todo.getBusinessId());
        command.setTodoId(context.sourceTodoId());
        command.setReviewId(context.reviewId());
        command.setReviewResult(LeadTodoPayloadMapper.text(values,"reviewResult"));
        command.setReviewComment(LeadTodoPayloadMapper.text(values,"reviewComment","reviewOpinion"));
        reviews.review(command);
    }

    private LeadTodoSourceContextService.InvalidReviewContext source(TodoInstance todo,
            Map<String,Object> values)
    {
        return contexts.requireInvalidReview(todo,LeadTodoPayloadMapper.number(values,"reviewId"));
    }
}
