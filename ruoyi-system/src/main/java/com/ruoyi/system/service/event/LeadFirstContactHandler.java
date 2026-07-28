package com.ruoyi.system.service.event;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.law.business.lead.dto.LeadFirstContactCommand;
import com.law.todo.application.CompletionContext;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.spi.TodoCompletionHandler;
import com.law.todo.spi.TodoCompletionHandler.CompletionResult;
import com.ruoyi.system.service.lead.LeadFirstContactService;
import com.ruoyi.system.service.lead.LeadFirstContactService.FirstContactOutcome;

/** Typed TD-001 adapter. Business facts and branch events remain owned by the lead service. */
@Component
public class LeadFirstContactHandler implements TodoCompletionHandler
{
    private final LeadFirstContactService firstContacts;

    public LeadFirstContactHandler(LeadFirstContactService firstContacts)
    {
        this.firstContacts=firstContacts;
    }

    @Override
    public boolean supports(TodoInstance todo)
    {
        return todo!=null&&("TD-001".equals(todo.getTemplateCode())
                ||"LEAD_FIRST_CONTACT".equals(todo.getTemplateCode()));
    }

    @Override public String catalogCode(){return "TD-001_COMPLETE";}
    @Override public boolean supportsSimulation(){return true;}
    @Override public String simulationDescription()
    {return "Pure dry-run reports the deferred retry Todo without writing lead data";}

    @Override
    public SimulationResult simulate(TodoInstance todo,Map<String,Object> payload)
    {
        String contactResult=LeadTodoPayloadMapper.text(payload,"contactResult");
        Map<String,Object> routing=contactResult==null?Map.of():Map.of("contactResult",contactResult);
        return "UNREACHABLE".equals(contactResult)
                ?SimulationResult.produces(routing,java.util.List.of("TD-003"))
                :SimulationResult.none(routing);
    }

    @Override
    public void complete(TodoInstance todo,Map<String,Object> payload,Long operatorId,String operatorName)
    {execute(todo,payload);}

    @Override
    public CompletionResult handle(CompletionContext context)
    {
        FirstContactOutcome outcome=execute(context.todo(),context.payload());
        Map<String,Object> routing=new LinkedHashMap<>();
        routing.put("contactResult",outcome.result());
        routing.put("ownerId",outcome.ownerId());
        if(outcome.reviewId()!=null)routing.put("reviewId",outcome.reviewId());
        if(outcome.reviewerId()!=null)routing.put("reviewerId",outcome.reviewerId());
        if(outcome.schedulePlanId()!=null)routing.put("planId",outcome.schedulePlanId());
        return CompletionResult.completeTodo(routing);
    }

    private FirstContactOutcome execute(TodoInstance todo,Map<String,Object> payload)
    {
        Map<String,Object> values=LeadTodoPayloadMapper.values(payload);
        LeadFirstContactCommand command=new LeadFirstContactCommand();
        command.setLeadId(todo.getBusinessId());
        command.setTodoId(todo.getTodoId());
        command.setContactResult(LeadTodoPayloadMapper.text(values,"contactResult"));
        command.setContactName(LeadTodoPayloadMapper.text(values,"contactName","name"));
        command.setCity(LeadTodoPayloadMapper.text(values,"city"));
        command.setLegalDemand(LeadTodoPayloadMapper.text(values,"legalDemand","demand"));
        command.setVisited(LeadTodoPayloadMapper.text(values,"visited"));
        command.setInvalidReasonCode(LeadTodoPayloadMapper.text(values,"invalidReasonCode","reasonCode"));
        command.setSalesExplanation(LeadTodoPayloadMapper.text(values,"salesExplanation"));
        command.setCallRecord(LeadTodoPayloadMapper.manualCall(todo,values));
        return firstContacts.complete(command);
    }
}
