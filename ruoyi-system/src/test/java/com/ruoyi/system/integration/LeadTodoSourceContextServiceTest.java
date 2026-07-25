package com.ruoyi.system.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.alibaba.fastjson2.JSON;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;
import com.ruoyi.system.domain.BizLeadInvalidReview;
import com.ruoyi.system.domain.BusinessEventRecord;
import com.ruoyi.system.mapper.BusinessEventMapper;
import com.ruoyi.system.mapper.LeadFlowMapper;
import com.ruoyi.system.service.event.LeadTodoSourceContextService;

@ExtendWith(MockitoExtension.class)
class LeadTodoSourceContextServiceTest
{
    @Mock BusinessEventMapper events;
    @Mock LeadFlowMapper facts;

    @Test
    void derives_review_and_original_source_todo_from_the_trigger_event()
    {
        TodoInstance todo=todo();todo.setTriggerEventId("101");
        BusinessEventRecord event=event(101L,7L,61L);
        BizLeadInvalidReview review=review(61L,7L,11L);
        when(events.selectEventById(101L)).thenReturn(event);
        when(facts.selectInvalidReviewById(61L)).thenReturn(review);

        var context=new LeadTodoSourceContextService(events,facts)
                .requireInvalidReview(todo,null);

        assertEquals(61L,context.reviewId());
        assertEquals(11L,context.sourceTodoId());
    }

    @Test
    void submitted_hidden_review_id_cannot_override_the_trigger_event()
    {
        TodoInstance todo=todo();todo.setTriggerEventId("101");
        when(events.selectEventById(101L)).thenReturn(event(101L,7L,61L));

        TodoException error=assertThrows(TodoException.class,
                ()->new LeadTodoSourceContextService(events,facts)
                        .requireInvalidReview(todo,999L));

        assertEquals("LEAD_INVALID_REVIEW_SOURCE_CONTEXT_INVALID",error.getBusinessCode());
    }

    @Test
    void graph_created_review_must_match_the_previous_todo()
    {
        TodoInstance todo=todo();todo.setPreviousTodoId(11L);
        BizLeadInvalidReview review=review(61L,7L,11L);
        when(facts.selectInvalidReviewBySourceTodo(7L,11L)).thenReturn(review);

        assertEquals(61L,new LeadTodoSourceContextService(events,facts)
                .requireInvalidReview(todo,61L).reviewId());
    }

    @Test
    void persisted_reviewer_must_match_the_todo_owner()
    {
        TodoInstance todo=todo();todo.setTriggerEventId("101");todo.setOwnerId(99L);
        when(events.selectEventById(101L)).thenReturn(event(101L,7L,61L));
        when(facts.selectInvalidReviewById(61L)).thenReturn(review(61L,7L,11L));

        TodoException error=assertThrows(TodoException.class,
                ()->new LeadTodoSourceContextService(events,facts)
                        .requireInvalidReview(todo,61L));

        assertEquals("LEAD_INVALID_REVIEW_SOURCE_CONTEXT_INVALID",error.getBusinessCode());
    }

    private TodoInstance todo()
    {
        TodoInstance todo=new TodoInstance();todo.setTodoId(21L);todo.setTemplateCode("TD-002");
        todo.setBusinessType("LEAD");todo.setBusinessId(7L);todo.setOwnerId(9L);return todo;
    }

    private BusinessEventRecord event(Long eventId,Long leadId,Long reviewId)
    {
        BusinessEventRecord value=new BusinessEventRecord();value.setEventId(eventId);
        value.setEventType("LEAD_SUSPECT_INVALID_MARKED");value.setAggregateType("LEAD");
        value.setAggregateId(leadId);value.setPayload(JSON.toJSONString(Map.of(
                "schemaVersion",1,"leadId",leadId,"reviewId",reviewId)));
        return value;
    }

    private BizLeadInvalidReview review(Long reviewId,Long leadId,Long sourceTodoId)
    {
        BizLeadInvalidReview value=new BizLeadInvalidReview();value.setReviewId(reviewId);
        value.setLeadId(leadId);value.setTodoId(sourceTodoId);value.setReviewerId(9L);
        value.setStatus("PENDING");
        return value;
    }
}
