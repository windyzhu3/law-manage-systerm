package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoDefinitionCommands.CopyTemplateCommand;
import com.law.todo.application.command.TodoDefinitionCommands.PublishDraftCommand;
import com.law.todo.application.command.TodoDefinitionCommands.UpdateDraftCommand;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoMapper;

@ExtendWith(MockitoExtension.class)
class TodoDefinitionServiceTest
{
    @Mock TodoMapper mapper;
    private final Actor actor=new Actor(7L,"alice",3L);

    @Test void publishedVersionIsImmutable()
    {
        when(mapper.selectTemplateVersionById(9L)).thenReturn(Map.of("version_id",9L,"status","PUBLISHED"));
        TodoException error=assertThrows(TodoException.class,()->service().updateDraft(update(9L),actor));
        assertEquals("TODO_TEMPLATE_VERSION_IMMUTABLE",error.getBusinessCode());
    }

    @Test void copyTemplateCreatesStableIndependentDefinition()
    {
        when(mapper.selectTemplateById(1L)).thenReturn(Map.of("template_id",1L,"business_type","CONTRACT","status","0"));
        when(mapper.insertDefinitionActionIfAbsent(anyMap())).thenReturn(1);
        when(mapper.insertTemplate(anyMap())).thenAnswer(invocation->{Map<String,Object> value=invocation.getArgument(0);value.put("templateId",12L);return 1;});

        Long id=service().copyTemplate(1L,new CopyTemplateCommand("copy-1","CONTRACT_REVIEW_CUSTOM","自定义合同审核"),actor);

        assertEquals(12L,id);verify(mapper).insertTemplate(anyMap());verify(mapper).insertDefinitionActionIfAbsent(anyMap());
    }

    @Test void publishRejectsUnknownSlaCalendar()
    {
        when(mapper.selectTemplateVersionById(9L)).thenReturn(draft("{\"calendarCode\":\"MISSING\",\"minutes\":60}",null));
        when(mapper.selectCalendarByCode("MISSING")).thenReturn(null);
        TodoException error=assertThrows(TodoException.class,()->service().publish(new PublishDraftCommand("pub-1",9L),actor));
        assertEquals("TODO_SLA_CALENDAR_NOT_FOUND",error.getBusinessCode());
    }

    @Test void publishUsesConditionalStateChangeAndAudit()
    {
        when(mapper.selectTemplateVersionById(9L)).thenReturn(draft(null,null));
        when(mapper.insertDefinitionActionIfAbsent(anyMap())).thenReturn(1);
        when(mapper.publishTemplateVersionConditionally(9L,"alice")).thenReturn(1);

        assertEquals(9L,service().publish(new PublishDraftCommand("pub-1",9L),actor));
        verify(mapper).publishTemplateVersionConditionally(9L,"alice");verify(mapper).insertDefinitionActionIfAbsent(anyMap());
    }

    private TodoDefinitionService service(){return new TodoDefinitionService(mapper);}
    private UpdateDraftCommand update(Long id){return new UpdateDraftCommand("edit-1",id,"\"OWNER\"","{}",null,null,"{}");}
    private Map<String,Object> draft(String sla,String next){Map<String,Object> value=new HashMap<>();value.put("version_id",9L);value.put("template_id",1L);value.put("version_no",2);value.put("status","DRAFT");value.put("owner_rule_json","\"OWNER\"");value.put("dod_rule_json","{}");value.put("sla_rule_json",sla);value.put("next_rule_json",next);value.put("ui_schema_json","{}");return value;}
}
