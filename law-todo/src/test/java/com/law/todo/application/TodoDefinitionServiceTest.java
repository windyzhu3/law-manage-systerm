package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoDefinitionCommands.CopyTemplateCommand;
import com.law.todo.application.command.TodoDefinitionCommands.PublishDraftCommand;
import com.law.todo.application.command.TodoDefinitionCommands.UpdateDraftCommand;
import com.law.todo.domain.TodoException;
import com.law.todo.definition.catalog.TodoDecisionService;
import com.law.todo.definition.catalog.TodoEventCatalogService;
import com.law.todo.definition.codec.TodoDefinitionCodec;
import com.law.todo.definition.compiler.TodoDefinitionCompiler;
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
        registeredEvent();
        when(mapper.updateDefinitionCompilation(anyMap())).thenReturn(1);
        when(mapper.insertDefinitionActionIfAbsent(anyMap())).thenReturn(1);
        when(mapper.publishTemplateVersionConditionally(org.mockito.ArgumentMatchers.eq(9L),anyString(),org.mockito.ArgumentMatchers.eq("alice"))).thenReturn(1);

        assertEquals(9L,service().publish(new PublishDraftCommand("pub-1",9L),actor));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String,Object>> persisted=ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<String> publishedHash=ArgumentCaptor.forClass(String.class);
        verify(mapper).updateDefinitionCompilation(persisted.capture());
        verify(mapper).publishTemplateVersionConditionally(org.mockito.ArgumentMatchers.eq(9L),publishedHash.capture(),org.mockito.ArgumentMatchers.eq("alice"));
        assertEquals(persisted.getValue().get("definitionHash"),publishedHash.getValue());
        verify(mapper).insertDefinitionActionIfAbsent(anyMap());
    }

    @Test void preflightPersistsCompiledDefinitionAndValidationReport()
    {
        when(mapper.selectTemplateVersionById(9L)).thenReturn(draft(null,null));
        registeredEvent();
        when(mapper.updateDefinitionCompilation(anyMap())).thenReturn(1);

        TodoDefinitionService.PreflightResult result=service().preflight(9L);

        assertTrue(result.report().publishable());
        assertEquals(64,result.report().definitionHash().length());
        verify(mapper).updateDefinitionCompilation(anyMap());
    }

    @Test void unresolvedDecisionRejectsPublishAfterPersistingReport()
    {
        Map<String,Object> definition=draft(null,null);
        definition.put("decision_refs_json","[\"Q-001\"]");
        when(mapper.selectTemplateVersionById(9L)).thenReturn(definition);
        registeredEvent();
        when(mapper.selectDecisionByCode("Q-001")).thenReturn(Map.of(
                "decision_code","Q-001","status","OPEN","blocking","Y"));
        when(mapper.updateDefinitionCompilation(anyMap())).thenReturn(1);

        TodoException error=assertThrows(TodoException.class,
                ()->service().publish(new PublishDraftCommand("pub-blocked",9L),actor));

        assertEquals("TODO_DEFINITION_PREFLIGHT_FAILED",error.getBusinessCode());
        verify(mapper).updateDefinitionCompilation(anyMap());
        verify(mapper,never()).publishTemplateVersionConditionally(org.mockito.ArgumentMatchers.anyLong(),anyString(),anyString());
    }

    @Test void failedPreflightDoesNotRollBackItsPersistedReport() throws Exception
    {
        Transactional transaction=TodoDefinitionService.class
                .getMethod("publish",PublishDraftCommand.class,Actor.class)
                .getAnnotation(Transactional.class);

        assertTrue(transaction.noRollbackFor().length>0);
        assertTrue(TodoException.class.isAssignableFrom(transaction.noRollbackFor()[0]));
    }

    private TodoDefinitionService service(){return new TodoDefinitionService(mapper,compiler());}
    private TodoDefinitionCompiler compiler(){return new TodoDefinitionCompiler(new TodoDefinitionCodec(),new TodoEventCatalogService(mapper),new TodoDecisionService(mapper));}
    private void registeredEvent(){when(mapper.selectEventCatalog("LEAD_CREATED",1)).thenReturn(Map.of("event_type","LEAD_CREATED","payload_version",1,"payload_schema_json","{\"type\":\"object\"}","status","ACTIVE"));}
    private UpdateDraftCommand update(Long id){return new UpdateDraftCommand("edit-1",id,"\"OWNER\"","{}",null,null,"{}");}
    private Map<String,Object> draft(String sla,String next){Map<String,Object> value=new HashMap<>();value.put("version_id",9L);value.put("template_id",1L);value.put("version_no",2);value.put("status","DRAFT");value.put("template_code","TD-001");value.put("event_type","LEAD_CREATED");value.put("payload_version",1);value.put("owner_rule_json","\"OWNER\"");value.put("dod_rule_json","{}");value.put("sla_rule_json",sla);value.put("next_rule_json",next);value.put("ui_schema_json","{}");return value;}
}
