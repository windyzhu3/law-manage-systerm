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
import java.util.List;
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
import com.law.todo.application.command.TodoManagementCommands.TemplateToggleCommand;
import com.law.todo.application.command.TodoManagementCommands.TemplateMetadataCommand;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.domain.TodoException;
import com.law.todo.spi.TodoDictionaryValidationPort;
import com.law.todo.application.view.TriggerTemplateVersionCatalogView;
import com.law.todo.application.view.TodoConfigurationViews.RoutingTargetCatalogEntry;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness=Strictness.LENIENT)
class TodoTemplateServiceTest
{
    @Mock TodoMapper mapper;
    @BeforeEach void publishedVersion(){when(mapper.selectTriggerTemplateBinding(2L)).thenReturn(templateBinding(1L,"PUBLISHED","0"));when(mapper.selectEventCatalog("LEAD_ASSIGNED",1)).thenReturn(catalog());}
    @Test void createsTemplate(){when(mapper.insertTemplate(anyMap())).thenReturn(1);new TodoTemplateService(mapper).saveTemplate(new java.util.HashMap<>(Map.of("templateCode","T1","templateName","测试","businessType","LEAD")));verify(mapper).insertTemplate(anyMap());}
    @Test void savesTriggerRule(){when(mapper.insertTriggerRule(anyMap())).thenReturn(1);new TodoTemplateService(mapper).saveTrigger(new java.util.HashMap<>(Map.of("eventType","LEAD_ASSIGNED","templateId",1L,"templateVersionId",2L,"businessType","LEAD")));verify(mapper).insertTriggerRule(anyMap());}
    @Test void rejectsInvalidTemplateJson(){TodoException error=assertThrows(TodoException.class,()->new TodoTemplateService(mapper).publish(1L,1,"OWNER","{}","{}",null,"{}","admin"));assertEquals("TODO_TEMPLATE_JSON_INVALID",error.getBusinessCode());}
    @Test void publishesUiSchemaInImmutableVersion(){when(mapper.selectTemplateVersion(1L,1)).thenReturn(null);doAnswer(invocation->{Map<String,Object> value=invocation.getArgument(0);value.put("versionId",8L);return 1;}).when(mapper).insertTemplateVersion(anyMap());new TodoTemplateService(mapper).publish(1L,1,"\"OWNER\"","{}","{}",null,"{\"type\":\"form\"}","admin");verify(mapper).insertTemplateVersion(org.mockito.ArgumentMatchers.argThat(value->"{\"type\":\"form\"}".equals(value.get("uiSchemaJson"))));}

    @Test void triggerVersionCatalogReturnsOnlyPublishedMinimalProjection()
    {
        when(mapper.selectPublishedTemplateVersionCatalog(1L)).thenReturn(List.of(Map.of(
                "version_id",2L,"version_no",7,"status","PUBLISHED")));
        assertEquals(List.of(new TriggerTemplateVersionCatalogView(2L,7,"PUBLISHED")),
                new TodoTemplateService(mapper).listPublishedVersionCatalog(1L));
        verify(mapper).selectPublishedTemplateVersionCatalog(1L);
        verify(mapper,never()).selectTemplateVersions(1L);
    }

    @Test void routingTargetCatalogReturnsOnlyActiveTemplatesWithPublishedVersions()
    {
        when(mapper.selectRoutingTargetCatalog()).thenReturn(List.of(Map.of(
                "template_id",1L,"template_code","NEXT-A","template_name","Next A","business_type","CASE",
                "version_id",2L,"version_no",7,"status","PUBLISHED")));
        assertEquals(List.of(new RoutingTargetCatalogEntry(1L,"NEXT-A","Next A","CASE",2L,7,"PUBLISHED")),
                new TodoTemplateService(mapper).listRoutingTargetCatalog());
        verify(mapper).selectRoutingTargetCatalog();
    }

    @Test void templateToggleUsesAnAuditedStatusOnlyOptimisticMutation()
    {
        ledger(true);
        when(mapper.selectTemplateForUpdate(5L)).thenReturn(Map.of(
                "template_id",5L,"business_type","LEAD","status","0","version",3));
        when(mapper.updateTemplateStatusConditionally(anyMap())).thenReturn(1);

        new TodoTemplateService(mapper).toggleTemplate(5L,
                new TemplateToggleCommand("1","template-toggle-5",3),actor());

        verify(mapper).updateTemplateStatusConditionally(org.mockito.ArgumentMatchers.argThat(row ->
                row.size()==4&&Long.valueOf(5L).equals(row.get("templateId"))&&"1".equals(row.get("status"))
                        &&Integer.valueOf(3).equals(row.get("expectedVersion"))&&"alice".equals(row.get("updateBy"))));
    }

    @Test void templateMetadataEditIsAuditedIdempotentAndOptimisticWithoutStatusMutation()
    {
        Ledger ledger=ledger(true);
        when(mapper.selectTemplateForUpdate(5L)).thenReturn(Map.of(
                "template_id",5L,"business_type","LEAD","status","0","version",3));
        when(mapper.updateTemplateMetadataConditionally(anyMap())).thenReturn(1);
        TemplateMetadataCommand command=new TemplateMetadataCommand(5L,"T-5","Template 5","LEAD","metadata-5",3);

        new TodoTemplateService(mapper).updateTemplateMetadata(command,actor());
        new TodoTemplateService(mapper).updateTemplateMetadata(command,actor());

        verify(mapper,times(1)).updateTemplateMetadataConditionally(org.mockito.ArgumentMatchers.argThat(row ->
                row.size()==6&&!row.containsKey("status")&&Long.valueOf(5L).equals(row.get("templateId"))
                        &&Integer.valueOf(3).equals(row.get("expectedVersion"))&&"alice".equals(row.get("updateBy"))));
        assertEquals("UPDATE_TEMPLATE",ledger.claim.get().get("actionType"));
    }

    @Test void templateMetadataEditRejectsStaleVersion()
    {
        ledger(true);when(mapper.selectTemplateForUpdate(5L)).thenReturn(Map.of(
                "template_id",5L,"business_type","LEAD","status","0","version",4));
        TodoException error=assertThrows(TodoException.class,()->new TodoTemplateService(mapper).updateTemplateMetadata(
                new TemplateMetadataCommand(5L,"T-5","Template 5","LEAD","metadata-stale",3),actor()));
        assertEquals("TODO_TEMPLATE_VERSION_CONFLICT",error.getBusinessCode());
        verify(mapper,never()).updateTemplateMetadataConditionally(anyMap());
    }

    @Test void templateToggleRejectsAStaleVersionWithoutMutatingStatus()
    {
        ledger(true);
        when(mapper.selectTemplateForUpdate(5L)).thenReturn(Map.of(
                "template_id",5L,"business_type","LEAD","status","0","version",4));

        TodoException error=assertThrows(TodoException.class,()->new TodoTemplateService(mapper).toggleTemplate(5L,
                new TemplateToggleCommand("1","template-toggle-stale",3),actor()));

        assertEquals("TODO_TEMPLATE_VERSION_CONFLICT",error.getBusinessCode());
        verify(mapper,never()).updateTemplateStatusConditionally(anyMap());
    }

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
        when(mapper.selectTriggerTemplateBinding(2L)).thenReturn(templateBinding(9L,"PUBLISHED","0"));
        TodoException otherTemplate=assertThrows(TodoException.class,
                ()->new TodoTemplateService(mapper).saveTrigger(trigger(null)));
        assertEquals("TODO_TRIGGER_VERSION_INVALID",otherTemplate.getBusinessCode());

        when(mapper.selectTriggerTemplateBinding(2L)).thenReturn(templateBinding(1L,"DRAFT","0"));
        TodoException draft=assertThrows(TodoException.class,
                ()->new TodoTemplateService(mapper).saveTrigger(trigger(null)));
        assertEquals("TODO_TRIGGER_VERSION_INVALID",draft.getBusinessCode());
        verify(mapper,never()).insertTriggerRule(anyMap());
    }

    @Test void enabledTriggerCreateRejectsInactiveTemplate()
    {
        when(mapper.selectTriggerTemplateBinding(2L)).thenReturn(templateBinding(1L,"PUBLISHED","1"));
        TodoException error=assertThrows(TodoException.class,()->new TodoTemplateService(mapper).saveTrigger(trigger(null)));
        assertEquals("TODO_TRIGGER_TEMPLATE_INACTIVE",error.getBusinessCode());verify(mapper,never()).insertTriggerRule(anyMap());
    }

    @Test void enabledTriggerUpdateRejectsInactiveTemplate()
    {
        when(mapper.selectTriggerTemplateBinding(2L)).thenReturn(templateBinding(1L,"PUBLISHED","1"));
        Map<String,Object> value=trigger(null);value.put("triggerRuleId",41L);value.put("expectedVersion",3);value.put("enabled","Y");
        TodoException error=assertThrows(TodoException.class,()->new TodoTemplateService(mapper).saveTrigger(value));
        assertEquals("TODO_TRIGGER_TEMPLATE_INACTIVE",error.getBusinessCode());verify(mapper,never()).updateTriggerRule(anyMap());
    }

    @Test void disabledDraftRuleMayRetainInactiveTemplate()
    {
        when(mapper.selectTriggerTemplateBinding(2L)).thenReturn(templateBinding(1L,"PUBLISHED","1"));when(mapper.insertTriggerRule(anyMap())).thenReturn(1);
        Map<String,Object> value=trigger(null);value.put("enabled","N");
        assertEquals(1,new TodoTemplateService(mapper).saveTrigger(value));verify(mapper).insertTriggerRule(anyMap());
    }

    @Test void enabledTriggerCreateAcceptsActiveTemplate()
    {
        when(mapper.insertTriggerRule(anyMap())).thenReturn(1);
        assertEquals(1,new TodoTemplateService(mapper).saveTrigger(trigger(null)));verify(mapper).insertTriggerRule(anyMap());
    }

    @Test void triggerSaveRejectsBusinessTypeThatDoesNotMatchActiveEventCatalog()
    {
        when(mapper.selectEventCatalog("LEAD_ASSIGNED",1)).thenReturn(catalog("CONTRACT"));
        TodoException error=assertThrows(TodoException.class,()->new TodoTemplateService(mapper).saveTrigger(trigger(null)));
        assertEquals("TODO_TRIGGER_BUSINESS_TYPE_MISMATCH",error.getBusinessCode());verify(mapper,never()).insertTriggerRule(anyMap());
    }

    @Test void enablingTriggerRejectsInactiveEventCatalog()
    {
        ledger(true);when(mapper.selectTriggerBindingForUpdate(41L)).thenReturn(binding("0",1L,"PUBLISHED",null,3));
        when(mapper.selectEventCatalog("LEAD_ASSIGNED",1)).thenReturn(null);
        TodoException error=assertThrows(TodoException.class,()->new TodoTemplateService(mapper)
                .toggleTrigger(41L,toggle("enable-inactive-event",3),actor()));
        assertEquals("TODO_EVENT_CATALOG_REQUIRED",error.getBusinessCode());verify(mapper,never()).updateTriggerRuleEnabledConditionally(anyMap());
    }

    @Test void enablingTriggerRejectsDisabledBusinessType()
    {
        ledger(true);when(mapper.selectTriggerBindingForUpdate(41L)).thenReturn(binding("0",1L,"PUBLISHED",null,3));
        TodoDictionaryValidationPort dictionaries=org.mockito.Mockito.mock(TodoDictionaryValidationPort.class);
        when(dictionaries.isEnabled("law_todo_business_type","LEAD")).thenReturn(false);
        TodoException error=assertThrows(TodoException.class,()->new TodoTemplateService(mapper,
                new TodoEventCatalogService(mapper),new ConditionValidator(),dictionaries)
                .toggleTrigger(41L,toggle("enable-disabled-business",3),actor()));
        assertEquals("TODO_TEMPLATE_BUSINESS_TYPE_INVALID",error.getBusinessCode());verify(mapper,never()).updateTriggerRuleEnabledConditionally(anyMap());
    }

    @Test void enablingTriggerRejectsBusinessTypeThatDoesNotMatchActiveEventCatalog()
    {
        ledger(true);when(mapper.selectTriggerBindingForUpdate(41L)).thenReturn(binding("0",1L,"PUBLISHED",null,3));
        when(mapper.selectEventCatalog("LEAD_ASSIGNED",1)).thenReturn(catalog("CONTRACT"));
        TodoException error=assertThrows(TodoException.class,()->new TodoTemplateService(mapper)
                .toggleTrigger(41L,toggle("enable-business-mismatch",3),actor()));
        assertEquals("TODO_TRIGGER_BUSINESS_TYPE_MISMATCH",error.getBusinessCode());verify(mapper,never()).updateTriggerRuleEnabledConditionally(anyMap());
    }

    @Test void enablingTriggerRejectsInactiveTemplate()
    {
        ledger(true);when(mapper.selectTriggerBindingForUpdate(41L)).thenReturn(binding("1",1L,"PUBLISHED",null,3));
        TodoException error=assertThrows(TodoException.class,()->new TodoTemplateService(mapper)
                .toggleTrigger(41L,toggle("enable-inactive-template",3),actor()));
        assertEquals("TODO_TRIGGER_TEMPLATE_INACTIVE",error.getBusinessCode());verify(mapper,never()).updateTriggerRuleEnabledConditionally(anyMap());
    }

    @Test void enablingTriggerRejectsNonPublishedVersion()
    {
        ledger(true);when(mapper.selectTriggerBindingForUpdate(41L)).thenReturn(binding("0",1L,"DRAFT",null,3));
        TodoException error=assertThrows(TodoException.class,()->new TodoTemplateService(mapper)
                .toggleTrigger(41L,toggle("enable-draft-version",3),actor()));
        assertEquals("TODO_TRIGGER_VERSION_INVALID",error.getBusinessCode());verify(mapper,never()).updateTriggerRuleEnabledConditionally(anyMap());
    }

    @Test void enablingTriggerRejectsVersionBelongingToAnotherTemplate()
    {
        ledger(true);when(mapper.selectTriggerBindingForUpdate(41L)).thenReturn(binding("0",9L,"PUBLISHED",null,3));
        TodoException error=assertThrows(TodoException.class,()->new TodoTemplateService(mapper)
                .toggleTrigger(41L,toggle("enable-mismatched-version",3),actor()));
        assertEquals("TODO_TRIGGER_VERSION_INVALID",error.getBusinessCode());verify(mapper,never()).updateTriggerRuleEnabledConditionally(anyMap());
    }

    @Test void enablingTriggerRejectsConditionInvalidForCurrentEventSchema()
    {
        ledger(true);when(mapper.selectTriggerBindingForUpdate(41L)).thenReturn(binding("0",1L,"PUBLISHED","{\"unknown\":true}",3));
        TodoException error=assertThrows(TodoException.class,()->new TodoTemplateService(mapper)
                .toggleTrigger(41L,toggle("enable-invalid-condition",3),actor()));
        assertEquals("TODO_CONDITION_FIELD_UNKNOWN",error.getBusinessCode());verify(mapper,never()).updateTriggerRuleEnabledConditionally(anyMap());
    }

    @Test void enablingEligibleTriggerUsesStatusOnlyConditionalMutation()
    {
        ledger(true);when(mapper.selectTriggerBindingForUpdate(41L)).thenReturn(binding("0",1L,"PUBLISHED","{\"stage\":\"READY\"}",3));
        when(mapper.updateTriggerRuleEnabledConditionally(anyMap())).thenReturn(1);
        new TodoTemplateService(mapper).toggleTrigger(41L,toggle("enable-valid",3),actor());
        verify(mapper).updateTriggerRuleEnabledConditionally(org.mockito.ArgumentMatchers.argThat(row ->
                row.size()==4&&Long.valueOf(41L).equals(row.get("triggerRuleId"))&&"Y".equals(row.get("enabled"))
                        &&Integer.valueOf(3).equals(row.get("expectedVersion"))&&"alice".equals(row.get("updateBy"))));
    }

    @Test void enablingTriggerPreservesOptimisticConcurrency()
    {
        ledger(true);when(mapper.selectTriggerBindingForUpdate(41L)).thenReturn(binding("0",1L,"PUBLISHED",null,4));
        TodoException error=assertThrows(TodoException.class,()->new TodoTemplateService(mapper)
                .toggleTrigger(41L,toggle("enable-stale",3),actor()));
        assertEquals("TODO_TRIGGER_VERSION_CONFLICT",error.getBusinessCode());verify(mapper,never()).updateTriggerRuleEnabledConditionally(anyMap());
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
        return catalog("LEAD");
    }
    private Map<String,Object> catalog(String businessType)
    {return Map.of("status","ACTIVE","business_object_type",businessType,"payload_schema_json","{\"type\":\"object\",\"properties\":{\"stage\":{\"type\":\"string\"},\"amount\":{\"type\":\"number\"}}}");}

    private TriggerCommand command(Long id,String businessType,String actionId,int version)
    {return new TriggerCommand(id,"LEAD_ASSIGNED",1L,2L,businessType,"Y",null,1,actionId,version);}
    private com.law.todo.application.command.TodoManagementCommands.TriggerToggleCommand toggle(String actionId,int version)
    {return new com.law.todo.application.command.TodoManagementCommands.TriggerToggleCommand("Y",actionId,version);}
    private Map<String,Object> binding(String templateStatus,Long versionTemplateId,String versionStatus,String condition,int version)
    {
        Map<String,Object> row=new HashMap<>();row.put("trigger_rule_id",41L);row.put("event_type","LEAD_ASSIGNED");row.put("payload_version",1);
        row.put("template_id",1L);row.put("template_version_id",2L);row.put("business_type","LEAD");row.put("condition_json",condition);
        row.put("trigger_version",version);row.put("template_status",templateStatus);row.put("version_template_id",versionTemplateId);row.put("version_status",versionStatus);return row;
    }
    private Map<String,Object> templateBinding(Long templateId,String versionStatus,String templateStatus)
    {return Map.of("version_template_id",templateId,"version_status",versionStatus,"template_status",templateStatus);}
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
