package com.ruoyi.system.service.event;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.law.business.lead.dto.LeadRetryCompleteCommand;
import com.law.todo.application.CompletionContext;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.schedule.TodoScheduleService;
import com.law.todo.spi.TodoCompletionHandler;
import com.law.todo.spi.TodoCompletionHandler.CompletionResult;
import com.ruoyi.system.service.lead.LeadRetryService;
import com.ruoyi.system.service.lead.LeadRetryService.RetryOutcome;

/** Typed TD-003 adapter. Schedule identity is derived from the persisted Todo, never the payload. */
@Component
public class LeadRetryTodoHandler implements TodoCompletionHandler
{
    private final LeadRetryService retries;
    private final TodoScheduleService schedules;

    public LeadRetryTodoHandler(LeadRetryService retries,TodoScheduleService schedules)
    {this.retries=retries;this.schedules=schedules;}

    @Override public boolean supports(TodoInstance todo)
    {return todo!=null&&"TD-003".equals(todo.getTemplateCode());}

    @Override public String catalogCode(){return "TD-003_COMPLETE";}

    @Override
    public void complete(TodoInstance todo,Map<String,Object> payload,Long operatorId,String operatorName)
    {execute(todo,payload);}

    @Override
    public CompletionResult handle(CompletionContext context)
    {
        RetryOutcome outcome=execute(context.todo(),context.payload());
        Map<String,Object> routing=new LinkedHashMap<>();
        routing.put("result",outcome.result());
        if(outcome.retryRecordId()!=null)routing.put("retryRecordId",outcome.retryRecordId());
        if(outcome.nextStage()!=null)routing.put("nextStage",outcome.nextStage());
        routing.put("attemptNo",outcome.attemptNo());
        routing.put("replayed",outcome.replayed());
        if("CONNECTED".equals(outcome.result()))routing.put("ownerId",context.todo().getOwnerId());
        return "CONTINUE_CURRENT_WINDOW".equals(outcome.result())
                ?CompletionResult.retainCurrentTodo(routing)
                :CompletionResult.completeTodo(routing);
    }

    private RetryOutcome execute(TodoInstance todo,Map<String,Object> payload)
    {
        Map<String,Object> values=LeadTodoPayloadMapper.values(payload);
        LeadRetryCompleteCommand command=new LeadRetryCompleteCommand();
        command.setLeadId(todo.getBusinessId());
        command.setTodoId(todo.getTodoId());
        command.setOccurrenceId(schedules.requireOccurrenceIdForTodo(todo.getOccurrenceKey(),todo.getTodoId()));
        command.setResult(LeadTodoPayloadMapper.text(values,"result","contactResult"));
        command.setContactName(LeadTodoPayloadMapper.text(values,"contactName","name"));
        command.setCity(LeadTodoPayloadMapper.text(values,"city"));
        command.setLegalDemand(LeadTodoPayloadMapper.text(values,"legalDemand","demand"));
        command.setVisited(LeadTodoPayloadMapper.text(values,"visited"));
        command.setCallRecord(LeadTodoPayloadMapper.manualCall(todo,values));
        return retries.completeWindow(command);
    }
}
