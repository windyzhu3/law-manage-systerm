package com.ruoyi.system.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.schedule.TodoScheduleService;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.mapper.BizLeadMapper;
import com.ruoyi.system.service.event.LeadFirstContactValidator;

@ExtendWith(MockitoExtension.class)
class LeadFirstContactValidatorTest
{
    @Mock BizLeadMapper mapper;
    @Mock TodoScheduleService schedules;

    @Test
    void rejects_lead_reassigned_after_todo_creation_by_template_code()
    {
        BizLead lead=activeLead();lead.setOwnerId(9L);
        when(mapper.selectLeadById(7L)).thenReturn(lead);
        TodoException error=assertThrows(TodoException.class,
                ()->validator().validate(todo("TD-001","任意标题"),Map.of()));
        assertEquals("LEAD_OWNER_CHANGED",error.getBusinessCode());
    }

    @Test
    void rejects_non_active_disposition_even_when_legacy_status_looks_contactable()
    {
        BizLead lead=activeLead();lead.setDisposition("DEAD_POOL");
        when(mapper.selectLeadById(7L)).thenReturn(lead);
        TodoException error=assertThrows(TodoException.class,
                ()->validator().validate(todo("LEAD_FIRST_CONTACT","任意标题"),Map.of()));
        assertEquals("LEAD_DISPOSITION_INVALID",error.getBusinessCode());
    }

    @Test
    void localized_title_cannot_select_a_handler_or_validator()
    {
        validator().validate(todo("OTHER_TEMPLATE","线索首联"),Map.of());
        verify(mapper,never()).selectLeadById(7L);
    }

    @Test
    void retry_validation_binds_persisted_schedule_occurrence_to_source_todo()
    {
        BizLead lead=activeLead();lead.setFirstContactResult("UNREACHABLE");
        when(mapper.selectLeadById(7L)).thenReturn(lead);
        TodoInstance todo=todo("TD-003","任意标题");todo.setOccurrenceKey("81:T1_AM:1");

        validator().validate(todo,Map.of("occurrenceId",999L));

        verify(schedules).requireOccurrenceIdForTodo("81:T1_AM:1",21L);
    }

    private LeadFirstContactValidator validator()
    {return new LeadFirstContactValidator(mapper,schedules);}

    private BizLead activeLead()
    {
        BizLead lead=new BizLead();lead.setLeadId(7L);lead.setOwnerId(8L);lead.setStatus("1");
        lead.setDelFlag("0");lead.setDisposition("ACTIVE");lead.setFirstContactStatus("PENDING");
        return lead;
    }

    private TodoInstance todo(String code,String title)
    {
        TodoInstance todo=new TodoInstance();todo.setTodoId(21L);todo.setTemplateCode(code);
        todo.setTitle(title);todo.setBusinessType("LEAD");todo.setBusinessId(7L);todo.setOwnerId(8L);
        return todo;
    }
}
