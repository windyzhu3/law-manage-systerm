package com.ruoyi.system.service.event;

import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.schedule.TodoScheduleService;
import com.law.todo.spi.TodoBusinessValidator;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.mapper.BizLeadMapper;

/**
 * Runtime guard for governed first-contact/retry templates. Selection never depends on localized
 * titles; authoritative business services still enforce the final optimistic transition.
 */
@Component
public class LeadFirstContactValidator implements TodoBusinessValidator
{
    private static final Set<String> FIRST_CONTACT=Set.of("TD-001","LEAD_FIRST_CONTACT");
    private final BizLeadMapper mapper;
    private final TodoScheduleService schedules;

    public LeadFirstContactValidator(BizLeadMapper mapper){this(mapper,null);}

    @Autowired
    public LeadFirstContactValidator(BizLeadMapper mapper,TodoScheduleService schedules)
    {this.mapper=mapper;this.schedules=schedules;}

    @Override public boolean supports(String businessType){return "LEAD".equals(businessType);}

    @Override
    public void validate(TodoInstance todo,Map<String,Object> payload)
    {
        String code=todo==null?null:todo.getTemplateCode();
        if(!FIRST_CONTACT.contains(code)&&!"TD-003".equals(code))return;
        BizLead lead=mapper.selectLeadById(todo.getBusinessId());
        if(lead==null||"2".equals(lead.getDelFlag()))
            fail("LEAD_NOT_FOUND","Associated lead does not exist or was deleted");
        if(!"ACTIVE".equals(lead.getDisposition()))
            fail("LEAD_DISPOSITION_INVALID","Lead is no longer active");
        if(todo.getOwnerId()==null||!todo.getOwnerId().equals(lead.getOwnerId()))
            fail("LEAD_OWNER_CHANGED","Lead owner changed; cancel this Todo and regenerate it");
        if(FIRST_CONTACT.contains(code))
        {
            String status=lead.getFirstContactStatus();
            if(status!=null&&!"PENDING".equals(status))
                fail("LEAD_FIRST_CONTACT_STATE_INVALID","Lead is not awaiting first contact");
            return;
        }
        if(!"UNREACHABLE".equals(lead.getFirstContactResult()))
            fail("LEAD_RETRY_STATE_INVALID","Lead is not in retry state");
        if(schedules==null)
            fail("TODO_SCHEDULE_CONTEXT_REQUIRED","Retry validator requires schedule context");
        schedules.requireOccurrenceIdForTodo(todo.getOccurrenceKey(),todo.getTodoId());
    }

    private void fail(String code,String message){throw new TodoException(code,message);}
}
