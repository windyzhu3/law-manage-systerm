package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoDefinitionCommands.PublishDraftCommand;
import com.law.todo.application.command.TodoDefinitionCommands.RuleReference;
import com.law.todo.application.command.TodoDefinitionCommands.UpdateDraftCommand;
import com.law.todo.definition.catalog.TodoDecisionService;
import com.law.todo.definition.catalog.TodoEventCatalogService;
import com.law.todo.definition.compiler.TodoDefinitionCompiler;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoConfigurationMapper;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.spi.TodoAutoActionCapabilityRegistry;

@ExtendWith(MockitoExtension.class)
class TodoDefinitionRuleBindingTest
{
    @Mock TodoMapper mapper;
    @Mock TodoConfigurationMapper configMapper;
    private final Actor actor=new Actor(7L,"alice",3L);

    @Test void updateDraftReplacesReferencesOnlyAfterTheDefinitionRowAndKeepsTheirOrder()
    {
        when(mapper.selectTemplateVersionById(44L)).thenReturn(draft());
        when(mapper.selectCalendarByCode("DEFAULT")).thenReturn(Map.of("calendar_id",1L));
        claim("edit-44");
        when(mapper.updateTemplateVersionDraft(anyMap())).thenReturn(1);
        when(mapper.completeDefinitionAction(eq("edit-44"),anyString(),eq(44L))).thenReturn(1);
        when(configMapper.deleteDraftRuleRefs(44L)).thenReturn(2);
        when(configMapper.insertDraftRuleRef(anyMap())).thenReturn(1);
        UpdateDraftCommand command=new UpdateDraftCommand("edit-44",44L,"\"OWNER\"","{}","{\"calendarCode\":\"DEFAULT\",\"minutes\":30}",null,"{}",null,
                null,List.of(new RuleReference("DOD",12L,1),new RuleReference("SLA",8L,0),
                new RuleReference("DOD",11L,0)),"summary","impact");

        assertEquals(44L,service().updateDraft(command,actor));

        InOrder order=inOrder(mapper,configMapper);
        order.verify(mapper).updateTemplateVersionDraft(anyMap());
        order.verify(configMapper).deleteDraftRuleRefs(44L);
        ArgumentCaptor<Map<String,Object>> refs=ArgumentCaptor.forClass(Map.class);
        verify(configMapper,org.mockito.Mockito.times(3)).insertDraftRuleRef(refs.capture());
        assertEquals(List.of("SLA:8:0","DOD:11:0","DOD:12:1"),refs.getAllValues().stream()
                .map(ref->ref.get("refType")+":"+ref.get("refIdValue")+":"+ref.get("sortOrder")).toList());
    }

    @Test void publishingDraftEmbedsReferencedRuleSnapshots()
    {
        when(mapper.selectTemplateVersionById(44L)).thenReturn(draft());
        when(configMapper.selectDraftRuleRefs(44L)).thenReturn(List.of(ref("SLA",8L,0),ref("DOD",11L,0),ref("DOD",12L,1)));
        when(configMapper.selectSlaRule(8L)).thenReturn(slaRule());
        when(configMapper.selectDodRule(11L)).thenReturn(dodRule("A"));
        when(configMapper.selectDodRule(12L)).thenReturn(dodRule("B"));
        when(mapper.selectCalendarByCode("DEFAULT")).thenReturn(Map.of("calendar_id",1L));
        event();
        when(mapper.updateDefinitionDocument(anyMap())).thenReturn(1);
        when(mapper.updateDefinitionCompilation(anyMap())).thenReturn(1);
        when(mapper.insertDefinitionActionIfAbsent(anyMap())).thenReturn(1);
        when(mapper.publishTemplateVersionConditionally(eq(44L),anyString(),eq("alice"))).thenReturn(1);

        assertEquals(44L,service().publish(new PublishDraftCommand("publish-44",44L),actor));

        verify(mapper).publishTemplateVersionConditionally(eq(44L),anyString(),eq("alice"));
        verify(mapper).updateDefinitionDocument(org.mockito.ArgumentMatchers.argThat(row->
                String.valueOf(row.get("slaRuleJson")).contains("SLA-FIRST")
                &&String.valueOf(row.get("dodRuleJson")).contains("DOD-A")
                &&String.valueOf(row.get("dodRuleJson")).contains("DOD-B")));
    }

    @Test void publishingRejectsDisabledReferencedRulesBeforePersistingAPublishedSnapshot()
    {
        when(mapper.selectTemplateVersionById(44L)).thenReturn(draft());
        when(configMapper.selectDraftRuleRefs(44L)).thenReturn(List.of(ref("SLA",8L,0)));
        Map<String,Object> disabled=slaRule();disabled.put("status","1");
        when(configMapper.selectSlaRule(8L)).thenReturn(disabled);

        TodoException error=assertThrows(TodoException.class,
                ()->service().publish(new PublishDraftCommand("publish-disabled",44L),actor));

        assertEquals("TODO_TEMPLATE_RULE_DISABLED",error.getBusinessCode());
        verify(mapper,never()).updateDefinitionDocument(anyMap());
        verify(mapper,never()).publishTemplateVersionConditionally(eq(44L),anyString(),anyString());
    }

    private void claim(String actionId)
    {
        AtomicReference<Map<String,Object>> action=new AtomicReference<>();
        when(mapper.insertDefinitionActionClaim(anyMap())).thenAnswer(invocation->{Map<String,Object> stored=
                new HashMap<>(invocation.getArgument(0));stored.put("actionStatus","CLAIMED");action.set(stored);return 1;});
        when(mapper.selectDefinitionActionForUpdate(actionId)).thenAnswer(invocation->action.get());
    }

    private TodoDefinitionService service()
    {
        TodoDefinitionCompiler compiler=new TodoDefinitionCompiler(new com.law.todo.definition.codec.TodoDefinitionCodec(),
                new TodoEventCatalogService(mapper),new TodoDecisionService(mapper),
                new com.law.todo.expression.ConditionValidator(),new TodoAutoActionCapabilityRegistry(List.of()));
        return new TodoDefinitionService(mapper,compiler,configMapper);
    }

    private void event(){when(mapper.selectEventCatalog("LEAD_CREATED",1)).thenReturn(Map.of("event_type","LEAD_CREATED",
            "payload_version",1,"payload_schema_json","{\"type\":\"object\",\"additionalProperties\":true}","status","ACTIVE"));}
    private Map<String,Object> ref(String type,long id,int order){return Map.of("ref_type",type,"ref_id_value",id,"sort_order",order);}
    private Map<String,Object> slaRule(){return new HashMap<>(Map.ofEntries(Map.entry("sla_rule_id",8L),Map.entry("rule_code","SLA-FIRST"),Map.entry("rule_name","First"),
            Map.entry("sla_type","RESPONSE"),Map.entry("duration_value",30),Map.entry("duration_unit","MINUTE"),Map.entry("calendar_code","DEFAULT"),
            Map.entry("start_strategy","TODO_CREATED"),Map.entry("soft_remind_percent",80),Map.entry("hard_remind_percent",100),Map.entry("escalate_percent",150),Map.entry("status","0")));}
    private Map<String,Object> dodRule(String suffix){return new HashMap<>(Map.of("dod_rule_id",suffix.equals("A")?11L:12L,
            "rule_code","DOD-"+suffix,"rule_name","DoD "+suffix,"rule_type","TASK","required_fields_json","[]",
            "required_attachments_json","[]","conditional_rules_json","[]","validator_refs_json","[]","error_messages_json","{}","status","0"));}
    private Map<String,Object> draft(){Map<String,Object> value=new HashMap<>();value.put("version_id",44L);value.put("template_id",1L);value.put("version_no",2);value.put("status","DRAFT");value.put("template_code","TD-001");value.put("event_type","LEAD_CREATED");value.put("payload_version",1);value.put("owner_rule_json","\"OWNER\"");value.put("dod_rule_json","{}");value.put("sla_rule_json",null);value.put("next_rule_json",null);value.put("ui_schema_json","{}");return value;}
}
