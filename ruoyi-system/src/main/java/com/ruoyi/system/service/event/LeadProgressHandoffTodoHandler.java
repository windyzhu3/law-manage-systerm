package com.ruoyi.system.service.event;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.law.business.lead.dto.LeadProgressCompleteCommand;
import com.law.todo.application.CompletionContext;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.spi.TodoCompletionHandler;
import com.law.todo.spi.TodoCompletionHandler.CompletionResult;
import com.law.todo.spi.TodoCompletionHandler.SimulationResult;
import com.ruoyi.system.service.lead.LeadProgressCycleService;
import com.ruoyi.system.service.lead.LeadProgressCycleService.ProgressCycleOutcome;

/** Typed TD-004 adapter. The service owns progress provenance and five-day recurrence. */
@Component
public class LeadProgressHandoffTodoHandler implements TodoCompletionHandler
{
    private final LeadProgressCycleService cycles;

    public LeadProgressHandoffTodoHandler(LeadProgressCycleService cycles)
    {this.cycles=cycles;}

    @Override public boolean supports(TodoInstance todo)
    {return todo!=null&&"TD-004".equals(todo.getTemplateCode());}

    @Override public String catalogCode(){return "TD-004_COMPLETE";}

    @Override public boolean supportsSimulation(){return true;}

    @Override public String simulationDescription()
    {return "返回实质进展结果与五天自循环效果，不写入跟进记录或调度计划";}

    @Override
    public SimulationResult simulate(TodoInstance todo,Map<String,Object> payload)
    {
        Map<String,Object> routing=new LinkedHashMap<>();
        if(payload!=null)routing.putAll(payload);
        routing.put("result","PROGRESS_RECORDED");
        return SimulationResult.none(routing);
    }

    @Override public void complete(TodoInstance todo,Map<String,Object> payload,Long operatorId,
            String operatorName)
    {execute(todo,payload);}

    @Override
    public CompletionResult handle(CompletionContext context)
    {
        ProgressCycleOutcome outcome=execute(context.todo(),context.payload());
        Map<String,Object> routing=new LinkedHashMap<>();
        routing.put("result","PROGRESS_RECORDED");
        routing.put("followupId",outcome.followupId());
        routing.put("schedulePlanId",outcome.schedulePlanId());
        routing.put("nextDueAt",outcome.nextDueAt().toString());
        routing.put("replayed",outcome.replayed());
        return CompletionResult.completeTodo(routing);
    }

    private ProgressCycleOutcome execute(TodoInstance todo,Map<String,Object> payload)
    {
        Map<String,Object> values=LeadTodoPayloadMapper.values(payload);
        LeadProgressCompleteCommand command=new LeadProgressCompleteCommand();
        command.setLeadId(todo.getBusinessId());
        command.setTodoId(todo.getTodoId());
        command.setProgressType(LeadTodoPayloadMapper.text(values,"progressType"));
        command.setProgressAt(dateTime(values.get("progressAt")));
        command.setRemark(LeadTodoPayloadMapper.text(values,"remark"));
        return cycles.complete(command,todo);
    }

    private LocalDateTime dateTime(Object value)
    {
        if(value instanceof LocalDateTime supplied)return supplied;
        if(value==null||String.valueOf(value).isBlank())return null;
        try{return LocalDateTime.parse(String.valueOf(value).trim().replace(' ','T'));}
        catch(RuntimeException invalid)
        {throw new TodoException("TODO_HANDLER_PAYLOAD_INVALID","progressAt must be an ISO local date-time");}
    }
}
