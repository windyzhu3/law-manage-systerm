package com.ruoyi.system.service.event;

import java.util.Map;
import org.springframework.stereotype.Component;
import com.law.business.lead.dto.LeadRetryCompleteCommand;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.schedule.TodoScheduleService;
import com.law.todo.spi.TodoCompletionHandler;
import com.ruoyi.system.service.lead.LeadRetryService;

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
        retries.completeWindow(command);
    }
}
