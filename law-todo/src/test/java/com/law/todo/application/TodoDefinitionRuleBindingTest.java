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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.alibaba.fastjson2.JSON;

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
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class TodoDefinitionRuleBindingTest
{
    @Mock TodoMapper mapper;
    @Mock TodoConfigurationMapper configMapper;
    private final Actor actor=new Actor(7L,"alice",3L);

    @BeforeEach void lockedVersionUsesTheTestDraft()
    {org.mockito.Mockito.lenient().when(mapper.selectTemplateVersionForUpdate(org.mockito.ArgumentMatchers.anyLong()))
            .thenAnswer(invocation->mapper.selectTemplateVersionById(invocation.getArgument(0)));
     org.mockito.Mockito.lenient().when(mapper.selectEventCatalog("LEAD_CREATED",1)).thenReturn(Map.of(
             "event_type","LEAD_CREATED","payload_version",1,"business_object_type","LEAD",
             "payload_schema_json","{\"type\":\"object\",\"additionalProperties\":true}","status","ACTIVE"));}

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
                null,List.of(new RuleReference("DOD",12L,2),new RuleReference("SLA",8L,0),
                new RuleReference("DOD",11L,1)),"summary","impact");
        when(configMapper.selectSlaRule(8L)).thenReturn(slaRule());when(configMapper.selectDodRule(11L)).thenReturn(dodRule("A"));when(configMapper.selectDodRule(12L)).thenReturn(dodRule("B"));

        assertEquals(44L,service().updateDraft(command,actor));

        InOrder order=inOrder(mapper,configMapper);
        order.verify(mapper).updateTemplateVersionDraft(anyMap());
        order.verify(configMapper).deleteDraftRuleRefs(44L);
        ArgumentCaptor<Map<String,Object>> refs=ArgumentCaptor.forClass(Map.class);
        verify(configMapper,org.mockito.Mockito.times(3)).insertDraftRuleRef(refs.capture());
        assertEquals(List.of("SLA:8:0","DOD:11:1","DOD:12:2"),refs.getAllValues().stream()
                .map(ref->ref.get("refType")+":"+ref.get("refIdValue")+":"+ref.get("sortOrder")).toList());
    }

    @Test void publishingDraftEmbedsReferencedRuleSnapshots()
    {
        when(mapper.selectTemplateVersionById(44L)).thenReturn(draft());
        when(configMapper.selectDraftRuleRefs(44L)).thenReturn(List.of(ref("SLA",8L,0),ref("DOD",11L,1),ref("DOD",12L,2)));
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

    @Test void publishingLocksTheVersionBeforeReadingReferencesAndWritingTheSnapshot()
    {
        when(mapper.selectTemplateVersionForUpdate(44L)).thenReturn(draft());
        when(configMapper.selectDraftRuleRefs(44L)).thenReturn(List.of(ref("SLA",8L,0)));
        when(configMapper.selectSlaRule(8L)).thenReturn(slaRule());
        when(mapper.selectCalendarByCode("DEFAULT")).thenReturn(Map.of("calendar_id",1L));event();
        when(mapper.updateDefinitionDocument(anyMap())).thenReturn(1);when(mapper.updateDefinitionCompilation(anyMap())).thenReturn(1);
        when(mapper.insertDefinitionActionIfAbsent(anyMap())).thenReturn(1);when(mapper.publishTemplateVersionConditionally(eq(44L),anyString(),eq("alice"))).thenReturn(1);

        assertEquals(44L,service().publish(new PublishDraftCommand("locked-publish",44L),actor));

        InOrder order=inOrder(mapper,configMapper);
        order.verify(mapper).selectTemplateVersionForUpdate(44L);
        order.verify(configMapper).selectDraftRuleRefs(44L);
        order.verify(mapper).updateDefinitionDocument(anyMap());
    }

    @Test void invalidDraftBindingsAreRejectedBeforeDelete()
    {
        readyUpdate("duplicate-bindings");when(configMapper.selectSlaRule(8L)).thenReturn(slaRule());
        TodoException duplicate=assertThrows(TodoException.class,()->service().updateDraft(update("duplicate-bindings",
                List.of(new RuleReference("SLA",8L,0),new RuleReference("SLA",8L,1))),actor));

        assertEquals("TODO_TEMPLATE_RULE_BINDING_INVALID",duplicate.getBusinessCode());
        verify(configMapper,never()).deleteDraftRuleRefs(44L);
    }

    @Test void equalNumericIdsFromDifferentRuleLibrariesAreValidBindings()
    {
        readyUpdate("cross-library-id");
        when(configMapper.selectSlaRule(1L)).thenReturn(slaRule(1L));
        Map<String,Object> dod=dodRule("SAME-ID");dod.put("dod_rule_id",1L);
        when(configMapper.selectDodRule(1L)).thenReturn(dod);
        when(configMapper.deleteDraftRuleRefs(44L)).thenReturn(2);
        when(configMapper.insertDraftRuleRef(anyMap())).thenReturn(1);
        when(mapper.completeDefinitionAction(eq("cross-library-id"),anyString(),eq(44L))).thenReturn(1);

        assertEquals(44L,service().updateDraft(update("cross-library-id",List.of(
                new RuleReference("SLA",1L,0),new RuleReference("DOD",1L,1))),actor));

        verify(configMapper,org.mockito.Mockito.times(2)).insertDraftRuleRef(anyMap());
    }

    @Test void equalBindingOrdersAndStaleDefinitionWritesCannotReplaceReferences()
    {
        readyUpdate("equal-order");when(configMapper.selectSlaRule(8L)).thenReturn(slaRule());
        TodoException duplicateOrder=assertThrows(TodoException.class,()->service().updateDraft(update("equal-order",
                List.of(new RuleReference("SLA",8L,0),new RuleReference("DOD",11L,0))),actor));
        assertEquals("TODO_TEMPLATE_RULE_BINDING_INVALID",duplicateOrder.getBusinessCode());verify(configMapper,never()).deleteDraftRuleRefs(44L);

        Map<String,Object> stale=draft();when(mapper.selectTemplateVersionById(44L)).thenReturn(stale);when(mapper.selectCalendarByCode("DEFAULT")).thenReturn(Map.of("calendar_id",1L));claim("stale-bindings");when(mapper.updateTemplateVersionDraft(anyMap())).thenReturn(0);
        TodoException conflict=assertThrows(TodoException.class,()->service().updateDraft(update("stale-bindings",List.of()),actor));
        assertEquals("TODO_TEMPLATE_VERSION_CONFLICT",conflict.getBusinessCode());verify(configMapper,never()).deleteDraftRuleRefs(44L);
    }

    @Test void missingDisabledUnsupportedAndMultipleSlaBindingsAreRejectedBeforeDelete()
    {
        assertRejectedBeforeDelete("missing",List.of(new RuleReference("DOD",11L,0)),"TODO_TEMPLATE_RULE_NOT_FOUND");
        Map<String,Object> disabled=slaRule();disabled.put("status","1");
        assertRejectedBeforeDelete("disabled",List.of(new RuleReference("SLA",8L,0)),"TODO_TEMPLATE_RULE_DISABLED",disabled);
        assertRejectedBeforeDelete("unsupported",List.of(new RuleReference("NEXT",8L,0)),"TODO_TEMPLATE_RULE_TYPE_INVALID");
        assertRejectedBeforeDelete("two-sla",List.of(new RuleReference("SLA",8L,0),new RuleReference("SLA",9L,1)),"TODO_TEMPLATE_RULE_BINDING_INVALID",slaRule(),slaRule(9L));
    }

    @Test void wrongLibraryTypeIsReportedAsNotFoundWithoutReplacingBindings()
    {
        Map<String,Object> oppositeLibrary=dodRule("A");oppositeLibrary.put("dod_rule_id",8L);
        readyUpdate("wrong-library");org.mockito.Mockito.lenient().when(configMapper.selectDodRule(8L)).thenReturn(oppositeLibrary);

        TodoException error=assertThrows(TodoException.class,()->service().updateDraft(update("wrong-library",List.of(new RuleReference("SLA",8L,0))),actor));

        assertEquals("TODO_TEMPLATE_RULE_NOT_FOUND",error.getBusinessCode());
        verify(configMapper,never()).deleteDraftRuleRefs(44L);
    }

    @Test void draftSaveRejectsAnInactiveEventVersionBeforeWriting()
    {
        Map<String,Object> current=snapshotDraft();current.put("business_type","LEAD");
        when(mapper.selectTemplateVersionById(44L)).thenReturn(current);
        when(mapper.selectEventCatalog("LEAD_CREATED",1)).thenReturn(Map.of(
                "event_type","LEAD_CREATED","payload_version",1,"business_object_type","LEAD",
                "payload_schema_json","{}","status","INACTIVE"));

        TodoException error=assertThrows(TodoException.class,()->service().updateDraft(
                new UpdateDraftCommand("inactive-event",44L,String.valueOf(current.get("definition_json"))),actor));

        assertEquals("TODO_EVENT_CATALOG_REQUIRED",error.getBusinessCode());
        verify(mapper,never()).updateTemplateVersionDraft(anyMap());
    }

    @Test void aDraftReferenceInsertFailurePropagatesAndStopsLaterInserts() throws Exception
    {
        readyUpdate("partial-insert");when(configMapper.selectSlaRule(8L)).thenReturn(slaRule());when(configMapper.selectDodRule(11L)).thenReturn(dodRule("A"));when(configMapper.selectDodRule(12L)).thenReturn(dodRule("B"));
        when(configMapper.deleteDraftRuleRefs(44L)).thenReturn(2);
        when(configMapper.insertDraftRuleRef(anyMap())).thenReturn(1).thenThrow(new IllegalStateException("second insert failed"));

        IllegalStateException error=assertThrows(IllegalStateException.class,()->service().updateDraft(update("partial-insert",
                List.of(new RuleReference("SLA",8L,0),new RuleReference("DOD",11L,1),new RuleReference("DOD",12L,2))),actor));

        assertEquals("second insert failed",error.getMessage());
        verify(configMapper,org.mockito.Mockito.times(2)).insertDraftRuleRef(anyMap());
        verify(mapper,never()).completeDefinitionAction(eq("partial-insert"),anyString(),eq(44L));
        assertEquals(true,TodoDefinitionService.class.getMethod("updateDraft",UpdateDraftCommand.class,Actor.class)
                .isAnnotationPresent(Transactional.class));
    }

    @Test void snapshotNormalizesRuntimeFieldsRoundTripsCanonicallyAndIsolatedFromLibraryMutation()
    {
        Map<String,Object> current=snapshotDraft();when(mapper.selectTemplateVersionForUpdate(44L)).thenReturn(current);
        Map<String,Object> sla=slaRule();sla.put("pause_policy_json","{\"pause\":true}");sla.put("escalation_policy_json","{\"level\":2}");sla.put("auto_action_json","{\"actionType\":\"NOTIFY\"}");
        Map<String,Object> first=dodRule("A");first.put("required_fields_json","[\"base\",\"a\"]");first.put("error_messages_json","{\"a\":\"first\"}");
        Map<String,Object> second=dodRule("B");second.put("required_fields_json","[\"a\",\"b\"]");second.put("error_messages_json","{\"a\":\"second\",\"b\":\"second\"}");
        when(configMapper.selectDraftRuleRefs(44L)).thenReturn(List.of(ref("SLA",8L,0),ref("DOD",11L,1),ref("DOD",12L,2)));
        when(configMapper.selectSlaRule(8L)).thenReturn(sla);when(configMapper.selectDodRule(11L)).thenReturn(first);when(configMapper.selectDodRule(12L)).thenReturn(second);
        when(mapper.selectCalendarByCode("DEFAULT")).thenReturn(Map.of("calendar_id",1L));event();when(mapper.updateDefinitionDocument(anyMap())).thenReturn(1);when(mapper.updateDefinitionCompilation(anyMap())).thenReturn(1);when(mapper.insertDefinitionActionIfAbsent(anyMap())).thenReturn(1);when(mapper.publishTemplateVersionConditionally(eq(44L),anyString(),eq("alice"))).thenReturn(1);

        service().publish(new PublishDraftCommand("immutable-snapshot",44L),actor);

        ArgumentCaptor<Map<String,Object>> snapshot=ArgumentCaptor.forClass(Map.class);verify(mapper).updateDefinitionDocument(snapshot.capture());
        String persisted=String.valueOf(snapshot.getValue().get("definitionJson"));
        com.law.todo.definition.codec.TodoDefinitionCodec codec=new com.law.todo.definition.codec.TodoDefinitionCodec();
        com.law.todo.definition.model.TodoDefinitionDocument saved=codec.read(persisted);
        assertEquals(persisted,codec.canonicalJson(saved));
        assertEquals("RESPONSE",saved.sla().config().get("slaType"));assertEquals(Map.of("pause",true),saved.sla().config().get("pausePolicy"));
        assertEquals(List.of("base","a","b"),saved.dod().config().get("requiredFields"));assertEquals(List.of("TASK"),saved.dod().config().get("ruleTypes"));
        assertEquals("second",((Map<?,?>)saved.dod().config().get("errorMessages")).get("a"));
        assertEquals(JSON.parseObject(JSON.toJSONString(saved.sla().config())),JSON.parseObject(String.valueOf(snapshot.getValue().get("slaRuleJson"))));
        assertEquals(JSON.parseObject(JSON.toJSONString(saved.dod().config())),JSON.parseObject(String.valueOf(snapshot.getValue().get("dodRuleJson"))));
        Map<String,Object> persistedVersion=new HashMap<>(current);persistedVersion.put("status","PUBLISHED");persistedVersion.put("definition_json",persisted);persistedVersion.put("definitionJson",persisted);
        persistedVersion.put("sla_rule_json",snapshot.getValue().get("slaRuleJson"));persistedVersion.put("dod_rule_json",snapshot.getValue().get("dodRuleJson"));
        sla.put("rule_code","CHANGED");sla.put("duration_value",999);first.put("required_fields_json","[\"changed\"]");
        org.mockito.Mockito.clearInvocations(mapper,configMapper);when(mapper.selectTemplateVersionById(44L)).thenReturn(persistedVersion);
        service().preflight(44L);
        verify(mapper,never()).updateDefinitionCompilation(anyMap());
        verify(configMapper,never()).selectDraftRuleRefs(44L);verify(configMapper,never()).selectSlaRule(org.mockito.ArgumentMatchers.anyLong());verify(configMapper,never()).selectDodRule(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test void noReferencePublishKeepsTheCanonicalDefinitionAndHashPathUnchanged()
    {
        Map<String,Object> current=snapshotDraft();when(mapper.selectTemplateVersionForUpdate(44L)).thenReturn(current);when(configMapper.selectDraftRuleRefs(44L)).thenReturn(List.of());when(mapper.selectCalendarByCode("DEFAULT")).thenReturn(Map.of("calendar_id",1L));event();
        when(mapper.updateDefinitionCompilation(anyMap())).thenReturn(1);when(mapper.insertDefinitionActionIfAbsent(anyMap())).thenReturn(1);when(mapper.publishTemplateVersionConditionally(eq(44L),anyString(),eq("alice"))).thenReturn(1);

        service().publish(new PublishDraftCommand("no-refs",44L),actor);

        verify(mapper,never()).updateDefinitionDocument(anyMap());ArgumentCaptor<Map<String,Object>> compiled=ArgumentCaptor.forClass(Map.class);verify(mapper).updateDefinitionCompilation(compiled.capture());
        String canonical=new com.law.todo.definition.codec.TodoDefinitionCodec().canonicalJson(new com.law.todo.definition.codec.TodoDefinitionCodec().read(String.valueOf(current.get("definition_json"))));
        assertEquals(canonical,compiled.getValue().get("definitionJson"));assertEquals(64,String.valueOf(compiled.getValue().get("definitionHash")).length());
    }

    @Test void standalonePreflightCompilesTheSameOrderedRuleSnapshotAsPublish()
    {
        when(mapper.selectTemplateVersionById(44L)).thenReturn(snapshotDraft());
        when(configMapper.selectDraftRuleRefs(44L)).thenReturn(List.of(ref("SLA",8L,0),ref("DOD",11L,1)));
        when(configMapper.selectSlaRule(8L)).thenReturn(slaRule());
        when(configMapper.selectDodRule(11L)).thenReturn(dodRule("A"));
        event();
        when(mapper.updateDefinitionDocument(anyMap())).thenReturn(1);
        when(mapper.updateDefinitionCompilation(anyMap())).thenReturn(1);

        service().preflight(44L);

        ArgumentCaptor<Map<String,Object>> snapshot=ArgumentCaptor.forClass(Map.class);
        verify(mapper).updateDefinitionDocument(snapshot.capture());
        ArgumentCaptor<Map<String,Object>> compilation=ArgumentCaptor.forClass(Map.class);
        verify(mapper).updateDefinitionCompilation(compilation.capture());
        assertEquals(snapshot.getValue().get("definitionJson"),compilation.getValue().get("definitionJson"));
        org.junit.jupiter.api.Assertions.assertTrue(String.valueOf(compilation.getValue().get("definitionJson")).contains("SLA-FIRST"));
        org.junit.jupiter.api.Assertions.assertTrue(String.valueOf(compilation.getValue().get("definitionJson")).contains("DOD-A"));
    }

    @Test void publishedPreflightIsStrictlyReadOnly()
    {
        Map<String,Object> published=snapshotDraft();published.put("status","PUBLISHED");
        published.put("compiled_json",published.get("definition_json"));
        published.put("definition_hash","a".repeat(64));
        when(mapper.selectTemplateVersionById(44L)).thenReturn(published);
        event();

        service().preflight(44L);

        verify(mapper,never()).updateDefinitionDocument(anyMap());
        verify(mapper,never()).updateDefinitionCompilation(anyMap());
        verify(configMapper,never()).selectDraftRuleRefs(44L);
    }

    @Test void publishUsesTheSameBoundSnapshotHashForCompilationClaimAndConditionalPublish()
    {
        Map<String,Object> current=snapshotDraft();when(mapper.selectTemplateVersionForUpdate(44L)).thenReturn(current);
        when(configMapper.selectDraftRuleRefs(44L)).thenReturn(List.of(ref("SLA",8L,0)));when(configMapper.selectSlaRule(8L)).thenReturn(slaRule());
        when(mapper.selectCalendarByCode("DEFAULT")).thenReturn(Map.of("calendar_id",1L));event();
        when(mapper.updateDefinitionDocument(anyMap())).thenReturn(1);when(mapper.updateDefinitionCompilation(anyMap())).thenReturn(1);
        when(mapper.insertDefinitionActionIfAbsent(anyMap())).thenReturn(1);when(mapper.publishTemplateVersionConditionally(eq(44L),anyString(),eq("alice"))).thenReturn(1);

        service().publish(new PublishDraftCommand("hash-plumbing",44L),actor);

        ArgumentCaptor<Map<String,Object>> snapshot=ArgumentCaptor.forClass(Map.class);verify(mapper).updateDefinitionDocument(snapshot.capture());
        ArgumentCaptor<Map<String,Object>> compilation=ArgumentCaptor.forClass(Map.class);verify(mapper).updateDefinitionCompilation(compilation.capture());
        ArgumentCaptor<Map<String,Object>> action=ArgumentCaptor.forClass(Map.class);verify(mapper).insertDefinitionActionIfAbsent(action.capture());
        ArgumentCaptor<String> publishedHash=ArgumentCaptor.forClass(String.class);verify(mapper).publishTemplateVersionConditionally(eq(44L),publishedHash.capture(),eq("alice"));
        String compilerHash=String.valueOf(compilation.getValue().get("definitionHash"));
        assertEquals(snapshot.getValue().get("definitionJson"),compilation.getValue().get("definitionJson"));
        assertEquals(compilerHash,JSON.parseObject(String.valueOf(action.getValue().get("payloadJson"))).getString("definitionHash"));
        assertEquals(compilerHash,publishedHash.getValue());
    }

    @Test void publishRejectsAStalePreflightHashBeforeClaimingTheRelease()
    {
        Map<String,Object> current=snapshotDraft();when(mapper.selectTemplateVersionForUpdate(44L)).thenReturn(current);
        when(configMapper.selectDraftRuleRefs(44L)).thenReturn(List.of());event();
        when(mapper.updateDefinitionCompilation(anyMap())).thenReturn(1);

        TodoException error=assertThrows(TodoException.class,()->service().publish(
                new PublishDraftCommand("stale-gate",44L,"stale-hash"),actor));

        assertEquals("TODO_TEMPLATE_PREFLIGHT_STALE",error.getBusinessCode());
        verify(mapper,never()).insertDefinitionActionIfAbsent(anyMap());
        verify(mapper,never()).publishTemplateVersionConditionally(eq(44L),anyString(),anyString());
    }

    @Test void compatibilityDraftUpdatePreservesMetadataUnlessExplicitlyCleared()
    {
        Map<String,Object> current=draft();current.put("change_summary","old summary");current.put("impact_scope","old impact");when(mapper.selectTemplateVersionById(44L)).thenReturn(current);
        when(mapper.selectCalendarByCode("DEFAULT")).thenReturn(Map.of("calendar_id",1L));claim("metadata");when(mapper.updateTemplateVersionDraft(anyMap())).thenReturn(1);when(mapper.completeDefinitionAction(eq("metadata"),anyString(),eq(44L))).thenReturn(1);

        service().updateDraft(new UpdateDraftCommand("metadata",44L,"\"OWNER\"","{}","{\"calendarCode\":\"DEFAULT\",\"minutes\":30}",null,"{}"),actor);

        ArgumentCaptor<Map<String,Object>> update=ArgumentCaptor.forClass(Map.class);verify(mapper).updateTemplateVersionDraft(update.capture());
        assertEquals("old summary",update.getValue().get("changeSummary"));assertEquals("old impact",update.getValue().get("impactScope"));
        claim("clear-metadata");when(configMapper.deleteDraftRuleRefs(44L)).thenReturn(1);when(mapper.completeDefinitionAction(eq("clear-metadata"),anyString(),eq(44L))).thenReturn(1);
        service().updateDraft(new UpdateDraftCommand("clear-metadata",44L,"\"OWNER\"","{}","{\"calendarCode\":\"DEFAULT\",\"minutes\":30}",null,"{}",null,null,List.of(),"",""),actor);
        ArgumentCaptor<Map<String,Object>> cleared=ArgumentCaptor.forClass(Map.class);verify(mapper,org.mockito.Mockito.times(2)).updateTemplateVersionDraft(cleared.capture());assertEquals("",cleared.getAllValues().get(1).get("changeSummary"));assertEquals("",cleared.getAllValues().get(1).get("impactScope"));
    }

    private void claim(String actionId)
    {
        AtomicReference<Map<String,Object>> action=new AtomicReference<>();
        when(mapper.insertDefinitionActionClaim(anyMap())).thenAnswer(invocation->{Map<String,Object> stored=
                new HashMap<>(invocation.getArgument(0));stored.put("actionStatus","CLAIMED");action.set(stored);return 1;});
        when(mapper.selectDefinitionActionForUpdate(actionId)).thenAnswer(invocation->action.get());
    }

    private void readyUpdate(String actionId){when(mapper.selectTemplateVersionById(44L)).thenReturn(draft());when(mapper.selectCalendarByCode("DEFAULT")).thenReturn(Map.of("calendar_id",1L));claim(actionId);when(mapper.updateTemplateVersionDraft(anyMap())).thenReturn(1);}
    private UpdateDraftCommand update(String action,List<RuleReference> refs){return new UpdateDraftCommand(action,44L,"\"OWNER\"","{}","{\"calendarCode\":\"DEFAULT\",\"minutes\":30}",null,"{}",null,null,refs,null,null);}
    private void assertRejectedBeforeDelete(String action,List<RuleReference> refs,String code,Map<String,Object>... rules){readyUpdate(action);for(Map<String,Object> rule:rules){Long id=Long.valueOf(String.valueOf(rule.get("sla_rule_id")));org.mockito.Mockito.lenient().when(configMapper.selectSlaRule(id)).thenReturn(rule);}TodoException error=assertThrows(TodoException.class,()->service().updateDraft(update(action,refs),actor));assertEquals(code,error.getBusinessCode());verify(configMapper,never()).deleteDraftRuleRefs(44L);}

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
    private Map<String,Object> slaRule(long id){Map<String,Object> rule=slaRule();rule.put("sla_rule_id",id);rule.put("rule_code","SLA-"+id);return rule;}
    private Map<String,Object> dodRule(String suffix){return new HashMap<>(Map.of("dod_rule_id",suffix.equals("A")?11L:12L,
            "rule_code","DOD-"+suffix,"rule_name","DoD "+suffix,"rule_type","TASK","required_fields_json","[]",
            "required_attachments_json","[]","conditional_rules_json","[]","validator_refs_json","[]","error_messages_json","{}","status","0"));}
    private Map<String,Object> draft(){Map<String,Object> value=new HashMap<>();value.put("version_id",44L);value.put("template_id",1L);value.put("version_no",2);value.put("status","DRAFT");value.put("template_code","TD-001");value.put("event_type","LEAD_CREATED");value.put("payload_version",1);value.put("owner_rule_json","\"OWNER\"");value.put("dod_rule_json","{}");value.put("sla_rule_json",null);value.put("next_rule_json",null);value.put("ui_schema_json","{}");return value;}
    private Map<String,Object> snapshotDraft(){Map<String,Object> value=draft();value.put("sla_rule_json","{\"calendarCode\":\"DEFAULT\",\"minutes\":30}");value.put("definition_json","{\"schemaVersion\":1,\"templateCode\":\"TD-001\",\"event\":{\"eventType\":\"LEAD_CREATED\",\"payloadVersion\":1,\"condition\":{}},\"owner\":{\"config\":{\"type\":\"PAYLOAD\",\"operand\":\"ownerId\"}},\"dod\":{\"config\":{\"requiredFields\":[\"base\"]}},\"sla\":{\"config\":{\"calendarCode\":\"DEFAULT\",\"minutes\":30}},\"ui\":{\"config\":{\"fields\":[\"base\",\"a\",\"b\"]}},\"routing\":{\"config\":{}},\"autoActions\":[],\"decisionRefs\":[],\"acceptanceRefs\":[]}");return value;}
}
