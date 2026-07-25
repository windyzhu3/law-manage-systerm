package com.ruoyi.system.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.business.lead.dto.LeadInvalidReviewCommand;
import com.law.todo.application.TodoAutoActionService;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.spi.TodoCompletionHandler.CompletionContext;
import com.ruoyi.system.service.event.LeadInvalidReviewTodoHandler;
import com.ruoyi.system.service.event.LeadTodoSourceContextService;
import com.ruoyi.system.service.lead.LeadInvalidReviewService;

@ExtendWith(MockitoExtension.class)
class LeadInvalidReviewTodoHandlerTest
{
    @Mock LeadInvalidReviewService reviews;
    @Mock LeadTodoSourceContextService contexts;

    @Test
    void exposes_governed_catalog_and_maps_human_review()
    {
        LeadInvalidReviewTodoHandler handler = new LeadInvalidReviewTodoHandler(reviews,contexts);
        TodoInstance todo = todo();
        when(contexts.requireInvalidReview(todo,61L)).thenReturn(
                new LeadTodoSourceContextService.InvalidReviewContext(61L,11L));

        assertTrue(handler.supports(todo));
        assertEquals("TD-002_COMPLETE", handler.catalogCode());
        handler.complete(todo, Map.of(
                "reviewId", 61L,
                "reviewResult", "MISJUDGED_VALID",
                "reviewOpinion", "复核为有效",
                "automatic", true), 9L, "manager");

        ArgumentCaptor<LeadInvalidReviewCommand> captured =
                ArgumentCaptor.forClass(LeadInvalidReviewCommand.class);
        verify(reviews).review(captured.capture());
        LeadInvalidReviewCommand command = captured.getValue();
        assertEquals(7L, command.getLeadId());
        assertEquals(11L, command.getTodoId());
        assertEquals(61L, command.getReviewId());
        assertEquals("MISJUDGED_VALID", command.getReviewResult());
        assertEquals("复核为有效", command.getReviewComment());
        verifyNoMoreInteractions(reviews);
    }

    @Test
    void automatic_review_uses_only_the_controlled_service_actor_and_fixed_result()
    {
        LeadInvalidReviewTodoHandler handler = new LeadInvalidReviewTodoHandler(reviews,contexts);
        TodoInstance todo=todo();
        when(contexts.requireInvalidReview(todo,61L)).thenReturn(
                new LeadTodoSourceContextService.InvalidReviewContext(61L,11L));

        handler.complete(new CompletionContext(todo,Map.of(
                "reviewId", 61L,
                "reviewResult", "MISJUDGED_VALID",
                "automatic", false), TodoAutoActionService.SERVICE_ACTOR.userId(),
                TodoAutoActionService.SERVICE_ACTOR.userName(),true));

        verify(reviews).reviewAutomatically(7L, 61L, 11L,
                TodoAutoActionService.SERVICE_ACTOR);
        verifyNoMoreInteractions(reviews);
    }

    @Test
    void lookalike_actor_values_on_a_human_completion_cannot_select_automatic_review()
    {
        LeadInvalidReviewTodoHandler handler=new LeadInvalidReviewTodoHandler(reviews,contexts);
        TodoInstance todo=todo();
        when(contexts.requireInvalidReview(todo,61L)).thenReturn(
                new LeadTodoSourceContextService.InvalidReviewContext(61L,11L));

        handler.complete(todo,Map.of("reviewId",61L,"reviewResult","TRUE_INVALID",
                "reviewOpinion","人工复核"),TodoAutoActionService.SERVICE_ACTOR.userId(),
                TodoAutoActionService.SERVICE_ACTOR.userName());

        verify(reviews).review(any(LeadInvalidReviewCommand.class));
        verifyNoMoreInteractions(reviews);
    }

    private TodoInstance todo()
    {
        TodoInstance todo = new TodoInstance();
        todo.setTodoId(21L);
        todo.setTemplateCode("TD-002");
        todo.setBusinessType("LEAD");
        todo.setBusinessId(7L);
        todo.setOwnerId(9L);
        todo.setOwnerDeptId(3L);
        return todo;
    }
}
