package com.law.todo.application;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.annotation.Autowired;
import com.law.todo.definition.catalog.TodoEventCatalogService;
import com.law.todo.expression.ConditionValidator;
import com.law.todo.application.command.TodoManagementCommands.TriggerCommand;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.domain.TodoException;
import com.law.todo.spi.TodoDictionaryValidationPort;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness=Strictness.LENIENT)
class TodoTemplateServiceTest
{
    @Mock TodoMapper mapper;
    @BeforeEach void publishedVersion(){when(mapper.selectTemplateVersionById(2L)).thenReturn(Map.of("template_id",1L,"status","PUBLISHED"));when(mapper.selectEventCatalog("LEAD_ASSIGNED",1)).thenReturn(catalog());}
    @Test void createsTemplate(){when(mapper.insertTemplate(anyMap())).thenReturn(1);new TodoTemplateService(mapper).saveTemplate(new java.util.HashMap<>(Map.of("templateCode","T1","templateName","测试","businessType","LEAD")));verify(mapper).insertTemplate(anyMap());}
    @Test void savesTriggerRule(){when(mapper.insertTriggerRule(anyMap())).thenReturn(1);new TodoTemplateService(mapper).saveTrigger(new java.util.HashMap<>(Map.of("eventType","LEAD_ASSIGNED","templateId",1L,"templateVersionId",2L,"businessType","LEAD")));verify(mapper).insertTriggerRule(anyMap());}
    @Test void rejectsInvalidTemplateJson(){TodoException error=assertThrows(TodoException.class,()->new TodoTemplateService(mapper).publish(1L,1,"OWNER","{}","{}",null,"{}","admin"));assertEquals("TODO_TEMPLATE_JSON_INVALID",error.getBusinessCode());}
    @Test void publishesUiSchemaInImmutableVersion(){when(mapper.selectTemplateVersion(1L,1)).thenReturn(null);doAnswer(invocation->{Map<String,Object> value=invocation.getArgument(0);value.put("versionId",8L);return 1;}).when(mapper).insertTemplateVersion(anyMap());new TodoTemplateService(mapper).publish(1L,1,"\"OWNER\"","{}","{}",null,"{\"type\":\"form\"}","admin");verify(mapper).insertTemplateVersion(org.mockito.ArgumentMatchers.argThat(value->"{\"type\":\"form\"}".equals(value.get("uiSchemaJson"))));}

    @Test void springConstructorExplicitlyInjectsConditionValidationDependencies() throws Exception
    {
        assertTrue(TodoTemplateService.class.getConstructor(TodoMapper.class,
                TodoEventCatalogService.class,ConditionValidator.class,com.law.todo.spi.TodoDictionaryValidationPort.class)
                .isAnnotationPresent(Autowired.class));
    }

    @Test void mapTriggerSaveRejectsUndeclaredConditionField()
    {
        when(mapper.selectEventCatalog("LEAD_ASSIGNED",1)).thenReturn(catalog());
        Map<String,Object> update=trigger("{\"unknown\":\"READY\"}");
        update.put("triggerRuleId",4L);
        TodoException error=assertThrows(TodoException.class,
                ()->new TodoTemplateService(mapper).saveTrigger(update));
        assertEquals("TODO_CONDITION_FIELD_UNKNOWN",error.getBusinessCode());
        verify(mapper,never()).updateTriggerRule(anyMap());
    }

    @Test void triggerSaveRejectsInvalidTypedPredicate()
    {
        ledger(true);
        when(mapper.selectEventCatalog("LEAD_ASSIGNED",1)).thenReturn(catalog());
        String condition="{\"$expression\":{\"version\":1,\"root\":{\"field\":\"amount\",\"operator\":\"GT\",\"value\":true}}}";
        TodoException error=assertThrows(TodoException.class,
                ()->new TodoTemplateService(mapper).saveTrigger(new TriggerCommand(null,
                        "LEAD_ASSIGNED",1L,2L,"LEAD","Y",condition,1,"trigger-invalid",0),actor()));
        assertEquals("TODO_CONDITION_VALUE_TYPE_INVALID",error.getBusinessCode());
    }

    @Test void triggerCreateReplaysAppliedActionWithoutDuplicateWrite()
    {
        Ledger ledger=ledger(true);
        when(mapper.insertTriggerRule(anyMap())).thenAnswer(invocation->{invocation.<Map<String,Object>>getArgument(0).put("triggerRuleId",41L);return 1;});
        TriggerCommand command=command(null,"LEAD","trigger-create",0);

        TodoTemplateService service=new TodoTemplateService(mapper);
        assertEquals(1,service.saveTrigger(command,actor()));
        assertEquals(1,service.saveTrigger(command,actor()));

        verify(mapper,times(1)).insertTriggerRule(anyMap());
        assertEquals(41L,ledger.entityId.get());
    }

    @Test void triggerUpdateReplaysAppliedActionWithoutDuplicateWrite()
    {
        ledger(true);when(mapper.updateTriggerRule(anyMap())).thenReturn(1);
        TriggerCommand command=command(41L,"LEAD","trigger-update",3);

        TodoTemplateService service=new TodoTemplateService(mapper);
        assertEquals(1,service.saveTrigger(command,actor()));
        assertEquals(1,service.saveTrigger(command,actor()));

        verify(mapper,times(1)).updateTriggerRule(anyMap());
    }

    @Test void triggerActionIdCannotBeReusedForDifferentRequestOrActor()
    {
        ledger(true);when(mapper.insertTriggerRule(anyMap())).thenAnswer(invocation->{invocation.<Map<String,Object>>getArgument(0).put("triggerRuleId",42L);return 1;});
        TodoTemplateService service=new TodoTemplateService(mapper);
        assertEquals(1,service.saveTrigger(command(null,"LEAD","trigger-conflict",0),actor()));

        TodoException payload=assertThrows(TodoException.class,
                ()->service.saveTrigger(command(null,"CONTRACT","trigger-conflict",0),actor()));
        assertEquals("TODO_TRIGGER_ACTION_CONFLICT",payload.getBusinessCode());
        TodoException operator=assertThrows(TodoException.class,
                ()->service.saveTrigger(command(null,"LEAD","trigger-conflict",0),new Actor(8L,"bob",3L)));
        assertEquals("TODO_TRIGGER_ACTION_CONFLICT",operator.getBusinessCode());
    }

    @Test void triggerRejectsStaleVersionAndDoesNotCompleteAction()
    {
        ledger(true);when(mapper.updateTriggerRule(anyMap())).thenReturn(0);
        TodoException error=assertThrows(TodoException.class,
                ()->new TodoTemplateService(mapper).saveTrigger(command(41L,"LEAD","trigger-stale",2),actor()));
        assertEquals("TODO_TRIGGER_VERSION_CONFLICT",error.getBusinessCode());
        verify(mapper,never()).completeDefinitionAction(anyString(),anyString(),anyLong());
    }

    @Test void triggerUpdateRequiresExplicitExpectedVersion()
    {
        TriggerCommand command=new TriggerCommand(41L,"LEAD_ASSIGNED",1L,2L,"LEAD","Y",null,1,"trigger-no-version",null);
        TodoException error=assertThrows(TodoException.class,
                ()->new TodoTemplateService(mapper).saveTrigger(command,actor()));
        assertEquals("TODO_TRIGGER_VERSION_REQUIRED",error.getBusinessCode());
        verify(mapper,never()).insertDefinitionActionClaim(anyMap());
    }

    @Test void triggerRejectsPreviouslyIncompleteClaim()
    {
        ledger(false);
        TodoException error=assertThrows(TodoException.class,
                ()->new TodoTemplateService(mapper).saveTrigger(command(null,"LEAD","trigger-incomplete",0),actor()));
        assertEquals("TODO_TRIGGER_ACTION_CONFLICT",error.getBusinessCode());
        verify(mapper,never()).insertTriggerRule(anyMap());
    }

    @Test void triggerSaveRejectsMalformedExpressionEnvelope()
    {
        when(mapper.selectEventCatalog("LEAD_ASSIGNED",1)).thenReturn(catalog());
        String condition="{\"$expression\":{\"version\":1,\"root\":{\"field\":\"amount\",\"operator\":\"EQ\",\"value\":1},\"extra\":true}}";
        TodoException error=assertThrows(TodoException.class,
                ()->new TodoTemplateService(mapper).saveTrigger(trigger(condition)));
        assertEquals("TODO_CONDITION_INVALID",error.getBusinessCode());
    }

    @Test void springConstructorRejectsUnknownOrDisabledBusinessTypeBeforeTriggerWrites()
    {
        TodoDictionaryValidationPort dictionaries=org.mockito.Mockito.mock(TodoDictionaryValidationPort.class);
        when(dictionaries.isEnabled("law_todo_business_type","UNKNOWN")).thenReturn(false);
        TodoException error=assertThrows(TodoException.class,()->new TodoTemplateService(mapper,
                new TodoEventCatalogService(mapper),new ConditionValidator(),dictionaries)
                .saveTrigger(command(null,"UNKNOWN","trigger-disabled",0),actor()));
        assertEquals("TODO_TEMPLATE_BUSINESS_TYPE_INVALID",error.getBusinessCode());
        verify(mapper,never()).insertDefinitionActionClaim(anyMap());verify(mapper,never()).insertTriggerRule(anyMap());
    }

    @Test void triggerSaveAcceptsLegacyFlatMapWhenSchemaDeclaresFields()
    {
        when(mapper.selectEventCatalog("LEAD_ASSIGNED",1)).thenReturn(catalog());
        when(mapper.insertTriggerRule(anyMap())).thenReturn(1);
        assertEquals(1,new TodoTemplateService(mapper).saveTrigger(trigger("{\"stage\":\"READY\"}")));
        verify(mapper).insertTriggerRule(anyMap());
    }

    @Test void triggerManagementRequiresAnActiveCatalogForConditions()
    {
        when(mapper.selectEventCatalog("LEAD_ASSIGNED",1)).thenReturn(null);
        TodoException error=assertThrows(TodoException.class,
                ()->new TodoTemplateService(mapper).saveTrigger(trigger("{\"stage\":\"READY\"}")));
        assertEquals("TODO_EVENT_CATALOG_REQUIRED",error.getBusinessCode());
        verify(mapper,never()).insertTriggerRule(anyMap());
    }

    @Test void triggerRequiresActiveCatalogEvenWithoutCondition()
    {
        when(mapper.selectEventCatalog("LEAD_ASSIGNED",1)).thenReturn(null);
        Map<String,Object> value=trigger(null);
        TodoException error=assertThrows(TodoException.class,
                ()->new TodoTemplateService(mapper).saveTrigger(value));
        assertEquals("TODO_EVENT_CATALOG_REQUIRED",error.getBusinessCode());
        verify(mapper,never()).insertTriggerRule(anyMap());
    }

    @Test void triggerRequiresPublishedVersionBelongingToSelectedTemplate()
    {
        when(mapper.selectTemplateVersionById(2L)).thenReturn(Map.of("template_id",9L,"status","PUBLISHED"));
        TodoException otherTemplate=assertThrows(TodoException.class,
                ()->new TodoTemplateService(mapper).saveTrigger(trigger(null)));
        assertEquals("TODO_TRIGGER_VERSION_INVALID",otherTemplate.getBusinessCode());

        when(mapper.selectTemplateVersionById(2L)).thenReturn(Map.of("template_id",1L,"status","DRAFT"));
        TodoException draft=assertThrows(TodoException.class,
                ()->new TodoTemplateService(mapper).saveTrigger(trigger(null)));
        assertEquals("TODO_TRIGGER_VERSION_INVALID",draft.getBusinessCode());
        verify(mapper,never()).insertTriggerRule(anyMap());
    }

    private Map<String,Object> trigger(String conditionJson)
    {
        Map<String,Object> trigger=new HashMap<>(Map.of("eventType","LEAD_ASSIGNED","templateId",1L,
                "templateVersionId",2L,"businessType","LEAD"));
        trigger.put("conditionJson",conditionJson);
        return trigger;
    }

    private Map<String,Object> catalog()
    {
        return Map.of("status","ACTIVE","payload_schema_json","{\"type\":\"object\",\"properties\":{\"stage\":{\"type\":\"string\"},\"amount\":{\"type\":\"number\"}}}");
    }

    private TriggerCommand command(Long id,String businessType,String actionId,int version)
    {return new TriggerCommand(id,"LEAD_ASSIGNED",1L,2L,businessType,"Y",null,1,actionId,version);}
    private Actor actor(){return new Actor(7L,"alice",2L);}

    private Ledger ledger(boolean insertFirst)
    {
        Ledger ledger=new Ledger();AtomicBoolean first=new AtomicBoolean(insertFirst);
        when(mapper.insertDefinitionActionClaim(anyMap())).thenAnswer(invocation->{
            Map<String,Object> action=new HashMap<>(invocation.getArgument(0));
            if(ledger.claim.get()==null)ledger.claim.set(action);
            return first.compareAndSet(true,false)?1:0;
        });
        when(mapper.selectDefinitionActionForUpdate(anyString())).thenAnswer(invocation->locked(ledger));
        when(mapper.completeDefinitionAction(anyString(),anyString(),anyLong())).thenAnswer(invocation->{ledger.applied.set(true);ledger.entityId.set(invocation.getArgument(2));return 1;});
        return ledger;
    }

    private Map<String,Object> locked(Ledger ledger)
    {
        Map<String,Object> action=ledger.claim.get();if(action==null)return null;
        Map<String,Object> row=new HashMap<>();row.put("action_type",action.get("actionType"));row.put("entity_type",action.get("entityType"));
        row.put("source_entity_id",action.get("sourceEntityId"));row.put("operator_id",action.get("operatorId"));
        row.put("operator_name",action.get("operatorName"));row.put("operator_dept_id",action.get("operatorDeptId"));
        row.put("request_fingerprint",action.get("requestFingerprint"));row.put("action_status",ledger.applied.get()?"APPLIED":"CLAIMED");
        if(ledger.entityId.get()!=null)row.put("entity_id",ledger.entityId.get());return row;
    }

    private static final class Ledger
    {private final AtomicReference<Map<String,Object>> claim=new AtomicReference<>();private final AtomicBoolean applied=new AtomicBoolean();private final AtomicReference<Long> entityId=new AtomicReference<>();}
}
