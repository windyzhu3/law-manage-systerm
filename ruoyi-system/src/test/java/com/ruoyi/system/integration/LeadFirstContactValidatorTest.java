package com.ruoyi.system.integration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.mapper.BizLeadMapper;
import com.ruoyi.system.service.event.LeadFirstContactValidator;

@ExtendWith(MockitoExtension.class)
class LeadFirstContactValidatorTest
{
    @Mock BizLeadMapper mapper;

    @Test void rejectsLeadThatWasReassignedAfterTodoCreation()
    {
        BizLead lead=new BizLead();lead.setLeadId(7L);lead.setOwnerId(9L);lead.setStatus("1");lead.setDelFlag("0");
        when(mapper.selectLeadById(7L)).thenReturn(lead);
        TodoInstance todo=new TodoInstance();todo.setTitle("线索首联");todo.setBusinessType("LEAD");todo.setBusinessId(7L);todo.setOwnerId(8L);

        assertThrows(TodoException.class,()->new LeadFirstContactValidator(mapper).validate(todo,Map.of("contactResult","connected")));
    }

    @Test void rejectsClosedLead()
    {
        BizLead lead=new BizLead();lead.setLeadId(7L);lead.setOwnerId(8L);lead.setStatus("5");lead.setDelFlag("0");
        when(mapper.selectLeadById(7L)).thenReturn(lead);
        TodoInstance todo=new TodoInstance();todo.setTitle("线索首联");todo.setBusinessType("LEAD");todo.setBusinessId(7L);todo.setOwnerId(8L);

        assertThrows(TodoException.class,()->new LeadFirstContactValidator(mapper).validate(todo,Map.of("contactResult","connected")));
    }
}
