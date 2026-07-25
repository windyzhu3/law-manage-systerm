package com.ruoyi.system.service.event;

import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;
import com.ruoyi.system.domain.BizLeadInvalidReview;
import com.ruoyi.system.domain.BusinessEventRecord;
import com.ruoyi.system.mapper.BusinessEventMapper;
import com.ruoyi.system.mapper.LeadFlowMapper;

/** Resolves non-editable branch identities from the Todo's persisted provenance. */
@Service
public class LeadTodoSourceContextService
{
    private final BusinessEventMapper events;
    private final LeadFlowMapper facts;

    public LeadTodoSourceContextService(BusinessEventMapper events,LeadFlowMapper facts)
    {this.events=events;this.facts=facts;}

    @Transactional(readOnly=true)
    public InvalidReviewContext requireInvalidReview(TodoInstance todo,Long submittedReviewId)
    {
        require(todo!=null&&todo.getTodoId()!=null&&"TD-002".equals(todo.getTemplateCode())
                &&"LEAD".equals(todo.getBusinessType())&&todo.getBusinessId()!=null);
        BizLeadInvalidReview review;
        if(todo.getTriggerEventId()!=null&&!todo.getTriggerEventId().isBlank())
        {
            BusinessEventRecord event=events.selectEventById(eventId(todo.getTriggerEventId()));
            require(event!=null&&"LEAD_SUSPECT_INVALID_MARKED".equals(event.getEventType())
                    &&"LEAD".equals(event.getAggregateType())
                    &&todo.getBusinessId().equals(event.getAggregateId()));
            Map<String,Object> payload=eventPayload(event.getPayload());
            Long reviewId=positive(payload.get("reviewId"));
            require(submittedReviewId==null||submittedReviewId.equals(reviewId));
            review=facts.selectInvalidReviewById(reviewId);
        }
        else
        {
            require(todo.getPreviousTodoId()!=null);
            review=facts.selectInvalidReviewBySourceTodo(todo.getBusinessId(),todo.getPreviousTodoId());
            require(review!=null&&(submittedReviewId==null||submittedReviewId.equals(review.getReviewId())));
        }
        require(review!=null&&review.getReviewId()!=null&&review.getTodoId()!=null
                &&todo.getBusinessId().equals(review.getLeadId())
                &&todo.getOwnerId()!=null&&todo.getOwnerId().equals(review.getReviewerId()));
        if(todo.getPreviousTodoId()!=null)require(todo.getPreviousTodoId().equals(review.getTodoId()));
        return new InvalidReviewContext(review.getReviewId(),review.getTodoId());
    }

    private Long eventId(String value)
    {
        try{return positive(value);}
        catch(RuntimeException invalid){throw error();}
    }
    private Map<String,Object> eventPayload(String value)
    {
        if(value==null||value.isBlank())return Map.of();
        try
        {
            Map<String,Object> payload=JSON.parseObject(value);
            return payload==null?Map.of():payload;
        }
        catch(RuntimeException invalid){throw error();}
    }
    private Long positive(Object value)
    {
        try
        {
            Long result=Long.valueOf(String.valueOf(value));
            if(result<=0)throw new NumberFormatException();
            return result;
        }
        catch(RuntimeException invalid){throw error();}
    }
    private void require(boolean condition){if(!condition)throw error();}
    private TodoException error()
    {return new TodoException("LEAD_INVALID_REVIEW_SOURCE_CONTEXT_INVALID",
            "Invalid-review Todo provenance does not match its business fact");}

    public record InvalidReviewContext(Long reviewId,Long sourceTodoId) { }
}
