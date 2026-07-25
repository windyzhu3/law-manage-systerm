package com.ruoyi.system.service.event;

import java.util.Map;
import org.springframework.stereotype.Component;
import com.law.business.lead.dto.LeadFirstContactCommand;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.spi.TodoCompletionHandler;
import com.ruoyi.system.service.lead.LeadFirstContactService;

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

    @Override
    public void complete(TodoInstance todo,Map<String,Object> payload,Long operatorId,String operatorName)
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
        firstContacts.complete(command);
    }
}
