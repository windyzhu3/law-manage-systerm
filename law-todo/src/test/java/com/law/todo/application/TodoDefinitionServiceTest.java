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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoDefinitionCommands.CopyTemplateCommand;
import com.law.todo.application.command.TodoDefinitionCommands.CopyVersionCommand;
import com.law.todo.application.command.TodoDefinitionCommands.PublishDraftCommand;
import com.law.todo.application.command.TodoDefinitionCommands.UpdateDraftCommand;
import com.law.todo.application.command.TodoDefinitionCommands.RollbackDraftCommand;
import com.law.todo.domain.TodoException;
import com.law.todo.definition.catalog.TodoDecisionService;
import com.law.todo.definition.catalog.TodoEventCatalogService;
import com.law.todo.definition.codec.TodoDefinitionCodec;
import com.law.todo.definition.compiler.TodoDefinitionCompiler;
import com.law.todo.definition.model.TodoDefinitionDocument;
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

    @Test void updateDraftAtomicallyPersistsCanonicalDocumentAndLegacyProjections()
    {
        Map<String,Object> current=draft(null,null);
        when(mapper.selectTemplateVersionById(9L)).thenReturn(current);
        AtomicReference<Map<String,Object>> action=new AtomicReference<>();
        when(mapper.insertDefinitionActionClaim(anyMap())).thenAnswer(invocation->{Map<String,Object> recorded=new HashMap<>(invocation.getArgument(0));recorded.put("actionStatus","CLAIMED");action.set(recorded);return 1;});
        when(mapper.selectDefinitionActionForUpdate("edit-document")).thenAnswer(invocation->action.get());
        when(mapper.updateTemplateVersionDraft(anyMap())).thenReturn(1);
        when(mapper.completeDefinitionAction(org.mockito.ArgumentMatchers.eq("edit-document"),anyString(),org.mockito.ArgumentMatchers.eq(9L))).thenReturn(1);
        String document="""
                {"schemaVersion":1,"templateCode":"TD-001",
                 "event":{"eventType":"LEAD_CREATED","payloadVersion":1,"condition":{"priority":"HIGH"}},
                 "owner":{"config":{"type":"PAYLOAD","operand":"ownerId"}},
                 "dod":{"config":{"requiredFields":["summary"]}},"sla":{"config":{"calendarCode":"DEFAULT","minutes":30}},
                 "ui":{"config":{"formCode":"TD-001"}},"routing":{"config":{}},
                 "autoActions":[{"config":{"ruleKey":"remind","actionType":"NOTIFY"}}],
                 "decisionRefs":["D-1"],"acceptanceRefs":["A-1"]}
                """;

        assertEquals(9L,service().updateDraft(new UpdateDraftCommand("edit-document",9L,document),actor));

        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String,Object>> updated=ArgumentCaptor.forClass(Map.class);
        verify(mapper).updateTemplateVersionDraft(updated.capture());
        Map<String,Object> value=updated.getValue();
        TodoDefinitionDocument saved=new TodoDefinitionCodec().read(String.valueOf(value.get("definitionJson")));
        assertEquals("HIGH",saved.event().condition().get("priority"));
        assertEquals("NOTIFY",saved.autoActions().get(0).config().get("actionType"));
        assertEquals(List.of("D-1"),saved.decisionRefs());
        assertEquals(List.of("A-1"),saved.acceptanceRefs());
        assertTrue(String.valueOf(value.get("ownerRuleJson")).contains("ownerId"));
        assertTrue(String.valueOf(value.get("uiSchemaJson")).contains("TD-001"));
    }

    @Test void updateDraftUsesSourceTokenAndReturnsTheRecordedResultForAnIdenticalReplay()
    {
        String currentDocument="""
                {"schemaVersion":1,"templateCode":"TD-001","event":{"eventType":"LEAD_CREATED","payloadVersion":1,"condition":{}},
                "owner":{"config":{"type":"PAYLOAD","operand":"ownerId"}},"dod":{"config":{}},"sla":{"config":{"calendarCode":"DEFAULT","minutes":30}},"ui":{"config":{}},"routing":{"config":{}},"autoActions":[],"decisionRefs":[],"acceptanceRefs":[]}
                """;
        Map<String,Object> current=draft(null,null);current.put("definition_json",currentDocument);
        when(mapper.selectCalendarByCode("DEFAULT")).thenReturn(Map.of("calendar_id",1L));
        AtomicReference<Map<String,Object>> persisted=new AtomicReference<>(current);
        when(mapper.selectTemplateVersionById(9L)).thenAnswer(invocation->persisted.get());
        Map<String,Map<String,Object>> actions=new HashMap<>();
        when(mapper.insertDefinitionActionClaim(anyMap())).thenAnswer(invocation->{
            Map<String,Object> incoming=invocation.getArgument(0);
            actions.computeIfAbsent(String.valueOf(incoming.get("actionId")),ignored->{Map<String,Object> recorded=new HashMap<>(incoming);recorded.put("actionStatus","CLAIMED");return recorded;});return 1;
        });
        when(mapper.selectDefinitionActionForUpdate(anyString())).thenAnswer(invocation->actions.get(invocation.getArgument(0)));
        when(mapper.updateTemplateVersionDraft(anyMap())).thenAnswer(invocation->{Map<String,Object> saved=new HashMap<>(persisted.get());saved.put("definition_json",invocation.<Map<String,Object>>getArgument(0).get("definitionJson"));persisted.set(saved);return 1;});
        when(mapper.completeDefinitionAction(org.mockito.ArgumentMatchers.eq("edit-token"),anyString(),org.mockito.ArgumentMatchers.eq(9L)))
                .thenAnswer(invocation->{ Map<String,Object> action=actions.get("edit-token");action.put("action_status","APPLIED");action.put("entity_id",9L);return 1; });
        String replacement=currentDocument.replace("ownerId","assigneeId");
        UpdateDraftCommand command=new UpdateDraftCommand("edit-token",9L,null,null,null,null,null,replacement,currentDocument);

        assertEquals(9L,service().updateDraft(command,actor));
        assertEquals(9L,service().updateDraft(command,actor));
        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String,Object>> update=ArgumentCaptor.forClass(Map.class);
        verify(mapper).updateTemplateVersionDraft(update.capture());
        assertEquals(currentDocument,update.getValue().get("expectedDefinitionJson"));
        verify(mapper).completeDefinitionAction(org.mockito.ArgumentMatchers.eq("edit-token"),anyString(),org.mockito.ArgumentMatchers.eq(9L));
        TodoException conflict=assertThrows(TodoException.class,()->service().updateDraft(
                new UpdateDraftCommand("edit-token",9L,null,null,null,null,null,replacement.replace("assigneeId","reviewerId"),currentDocument),actor));
        assertEquals("TODO_DEFINITION_ACTION_CONFLICT",conflict.getBusinessCode());
        TodoException stale=assertThrows(TodoException.class,()->service().updateDraft(
                new UpdateDraftCommand("stale-token",9L,null,null,null,null,null,replacement,currentDocument),actor));
        assertEquals("TODO_TEMPLATE_VERSION_CONFLICT",stale.getBusinessCode());
    }

    @Test void updateDraftRejectsAnUnknownNestedStableDepartmentReferenceBeforeSaving()
    {
        Map<String,Object> current=draft(null,null);when(mapper.selectTemplateVersionById(9L)).thenReturn(current);
        String document="""
                {"schemaVersion":1,"templateCode":"TD-001","event":{"eventType":"LEAD_CREATED","payloadVersion":1,"condition":{}},
                "owner":{"config":{"type":"PAYLOAD","operand":"ownerId","cc":[{"type":"DEPT","departmentCode":"RETIRED_DEPT"}]}},
                "dod":{"config":{}},"sla":{"config":{"calendarCode":"DEFAULT","minutes":30}},"ui":{"config":{}},"routing":{"config":{}},"autoActions":[],"decisionRefs":[],"acceptanceRefs":[]}
                """;
        when(mapper.selectDepartmentIdByCode("RETIRED_DEPT")).thenReturn(null);

        TodoException error=assertThrows(TodoException.class,()->service().updateDraft(new UpdateDraftCommand("bad-owner",9L,document),actor));

        assertEquals("TODO_OWNER_DEPARTMENT_CODE_NOT_FOUND",error.getBusinessCode());
        verify(mapper,never()).updateTemplateVersionDraft(anyMap());
    }

    @Test void copyCanonicalVersionPreservesTheWholeDocumentAndSynchronizesProjections()
    {
        String canonical="""
                {"schemaVersion":1,"templateCode":"TD-001",
                 "event":{"eventType":"LEAD_CREATED","payloadVersion":3,"condition":{"stage":"NEW"}},
                 "owner":{"config":{"type":"USER","userId":7}},
                 "dod":{"config":{"requiredFields":["summary"]}},
                 "sla":{"config":{"minutes":60}},"ui":{"config":{"fields":["summary"]}},
                 "routing":{"config":{"start":"review"}},
                 "autoActions":[{"config":{"action":"REMIND"}}],
                 "decisionRefs":["Q-001"],"acceptanceRefs":["AC-001"]}
                """;
        Map<String,Object> source=new HashMap<>();source.put("version_id",9L);source.put("definition_schema_version",1);source.put("definition_json",canonical);source.put("owner_rule_json","{\"stale\":true}");source.put("dod_rule_json","{}");source.put("sla_rule_json","{}");source.put("next_rule_json","{}");source.put("ui_schema_json","{}");
        when(mapper.selectTemplateVersion(1L,2)).thenReturn(source);
        when(mapper.insertDefinitionActionIfAbsent(anyMap())).thenReturn(1);
        when(mapper.insertTemplateVersion(anyMap())).thenAnswer(invocation->{Map<String,Object> value=invocation.getArgument(0);value.put("versionId",10L);return 1;});

        assertEquals(10L,service().copyVersion(1L,2,new CopyVersionCommand("copy-version",3),actor));

        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String,Object>> inserted=ArgumentCaptor.forClass(Map.class);
        verify(mapper).insertTemplateVersion(inserted.capture());
        Map<String,Object> target=inserted.getValue();
        TodoDefinitionDocument copied=new TodoDefinitionCodec().read(String.valueOf(target.get("definitionJson")));
        assertEquals(1,target.get("definitionSchemaVersion"));
        assertEquals(canonical,target.get("definitionJson"));
        assertEquals("LEAD_CREATED",copied.event().eventType());
        assertEquals(3,copied.event().payloadVersion());
        assertEquals(List.of("Q-001"),copied.decisionRefs());
        assertEquals("REMIND",copied.autoActions().get(0).config().get("action"));
        assertEquals(List.of("AC-001"),copied.acceptanceRefs());
        assertTrue(String.valueOf(target.get("ownerRuleJson")).contains("USER"));
        assertTrue(!String.valueOf(target.get("ownerRuleJson")).contains("stale"));
        assertEquals(null,target.get("compiledJson"));
        assertEquals(null,target.get("definitionHash"));
        assertEquals(null,target.get("validationReportJson"));
    }

    @Test void copyLegacyVersionAdaptsSelectedTriggerAndDefaultsPayloadVersionOne()
    {
        Map<String,Object> source=new HashMap<>();source.put("version_id",9L);source.put("template_code","TD-LEGACY");source.put("event_type","CONTRACT_SUBMITTED");source.put("condition_json","{\"stage\":\"READY\"}");source.put("owner_rule_json","\"USER:7\"");source.put("dod_rule_json","{}");source.put("sla_rule_json","{}");source.put("next_rule_json","{}");source.put("ui_schema_json","{}");
        when(mapper.selectTemplateVersion(1L,2)).thenReturn(source);
        when(mapper.insertDefinitionActionIfAbsent(anyMap())).thenReturn(1);
        when(mapper.insertTemplateVersion(anyMap())).thenAnswer(invocation->{Map<String,Object> value=invocation.getArgument(0);value.put("versionId",10L);return 1;});

        service().copyVersion(1L,2,new CopyVersionCommand("copy-legacy",3),actor);

        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String,Object>> inserted=ArgumentCaptor.forClass(Map.class);
        verify(mapper).insertTemplateVersion(inserted.capture());
        TodoDefinitionDocument copied=new TodoDefinitionCodec().read(String.valueOf(inserted.getValue().get("definitionJson")));
        assertEquals("CONTRACT_SUBMITTED",copied.event().eventType());
        assertEquals(1,copied.event().payloadVersion());
        assertEquals("READY",copied.event().condition().get("stage"));
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

    @Test void blocked_definition_cannot_be_published()
    {
        Map<String,Object> blocked=draft(null,null);blocked.put("status","BLOCKED");
        when(mapper.selectTemplateVersionById(9L)).thenReturn(blocked);

        TodoException error=assertThrows(TodoException.class,
                ()->service().publish(new PublishDraftCommand("pub-blocked-state",9L),actor));

        assertEquals("TODO_TEMPLATE_VERSION_IMMUTABLE",error.getBusinessCode());
        verify(mapper,never()).publishTemplateVersionConditionally(org.mockito.ArgumentMatchers.anyLong(),anyString(),anyString());
    }

    @Test void rollbackCreatesANewDraftWithoutMutatingPublishedHistory()
    {
        Map<String,Object> source=draft(null,null);source.put("status","PUBLISHED");source.put("definition_json",new TodoDefinitionCodec().canonicalJson(
                new com.law.todo.definition.codec.LegacyDefinitionAdapter().fromLegacy(source)));
        when(mapper.selectTemplateVersionById(9L)).thenReturn(source);
        when(mapper.insertDefinitionActionClaim(anyMap())).thenReturn(1);
        when(mapper.selectDefinitionActionForUpdate("rollback-1")).thenReturn(new HashMap<>(Map.of(
                "action_id","rollback-1","action_type","ROLLBACK_DRAFT","source_entity_id",9L,
                "operator_id",7L,"operator_name","alice","action_status","CLAIMED",
                "request_fingerprint",rollbackFingerprint(9L,1L,3,actor))));
        when(mapper.insertTemplateVersion(anyMap())).thenAnswer(invocation->{Map<String,Object> value=invocation.getArgument(0);value.put("versionId",12L);return 1;});
        when(mapper.completeDefinitionAction("rollback-1",rollbackFingerprint(9L,1L,3,actor),12L)).thenReturn(1);

        assertEquals(12L,service().rollbackDraft(9L,new RollbackDraftCommand("rollback-1",3),actor));

        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String,Object>> inserted=ArgumentCaptor.forClass(Map.class);
        verify(mapper).insertTemplateVersion(inserted.capture());
        assertEquals("DRAFT",inserted.getValue().get("status"));assertEquals(9L,inserted.getValue().get("sourceVersionId"));
        assertEquals(null,inserted.getValue().get("compiledJson"));
        verify(mapper,never()).updateDefinitionDocument(anyMap());
    }

    @Test void rollbackReplayWithTheSameFingerprintReturnsTheRecordedDraft()
    {
        Map<String,Object> source=draft(null,null);source.put("status","PUBLISHED");
        when(mapper.selectTemplateVersionById(9L)).thenReturn(source);
        when(mapper.insertDefinitionActionClaim(anyMap())).thenReturn(0);
        when(mapper.selectDefinitionActionForUpdate("rollback-same")).thenReturn(Map.of(
                "action_id","rollback-same","action_type","ROLLBACK_DRAFT","source_entity_id",9L,
                "entity_id",12L,"operator_id",7L,"operator_name","alice","action_status","APPLIED",
                "request_fingerprint",rollbackFingerprint(9L,1L,3,actor)));

        assertEquals(12L,service().rollbackDraft(9L,new RollbackDraftCommand("rollback-same",3),actor));

        verify(mapper,never()).insertTemplateVersion(anyMap());
    }

    @Test void rollbackRejectsAnActionIdPreviouslyClaimedByCopyOrAnotherRequest()
    {
        Map<String,Object> source=draft(null,null);source.put("status","PUBLISHED");
        when(mapper.selectTemplateVersionById(9L)).thenReturn(source);
        when(mapper.insertDefinitionActionClaim(anyMap())).thenReturn(0);
        when(mapper.selectDefinitionActionForUpdate("shared-action")).thenReturn(Map.of(
                "action_id","shared-action","action_type","COPY_VERSION","source_entity_id",9L,
                "entity_id",12L,"operator_id",7L,"operator_name","alice","action_status","APPLIED",
                "request_fingerprint","different"));

        TodoException error=assertThrows(TodoException.class,()->service().rollbackDraft(9L,
                new RollbackDraftCommand("shared-action",3),actor));

        assertEquals("TODO_DEFINITION_ACTION_CONFLICT",error.getBusinessCode());
        verify(mapper,never()).insertTemplateVersion(anyMap());
    }

    @Test void rollbackCompletesItsClaimOnlyAfterTheDraftHasBeenInserted()
    {
        Map<String,Object> source=draft(null,null);source.put("status","PUBLISHED");source.put("definition_json",new TodoDefinitionCodec().canonicalJson(
                new com.law.todo.definition.codec.LegacyDefinitionAdapter().fromLegacy(source)));
        when(mapper.selectTemplateVersionById(9L)).thenReturn(source);
        when(mapper.insertDefinitionActionClaim(anyMap())).thenReturn(1);
        when(mapper.selectDefinitionActionForUpdate("rollback-atomic")).thenReturn(new HashMap<>(Map.of(
                "action_id","rollback-atomic","action_type","ROLLBACK_DRAFT","source_entity_id",9L,
                "operator_id",7L,"operator_name","alice","action_status","CLAIMED",
                "request_fingerprint",rollbackFingerprint(9L,1L,3,actor))));
        when(mapper.insertTemplateVersion(anyMap())).thenAnswer(invocation->{Map<String,Object> value=invocation.getArgument(0);value.put("versionId",12L);return 1;});
        when(mapper.completeDefinitionAction("rollback-atomic",rollbackFingerprint(9L,1L,3,actor),12L)).thenReturn(1);

        assertEquals(12L,service().rollbackDraft(9L,new RollbackDraftCommand("rollback-atomic",3),actor));

        org.mockito.InOrder order=org.mockito.Mockito.inOrder(mapper);
        order.verify(mapper).insertDefinitionActionClaim(anyMap());
        order.verify(mapper).selectDefinitionActionForUpdate("rollback-atomic");
        order.verify(mapper).insertTemplateVersion(anyMap());
        order.verify(mapper).completeDefinitionAction("rollback-atomic",rollbackFingerprint(9L,1L,3,actor),12L);
    }

    @Test void rollback_target_version_collision_from_a_different_action_has_a_stable_business_error()
    {
        Map<String,Object> source=draft(null,null);source.put("status","PUBLISHED");
        when(mapper.selectTemplateVersionById(9L)).thenReturn(source);
        when(mapper.insertDefinitionActionClaim(anyMap())).thenReturn(1);
        when(mapper.selectDefinitionActionForUpdate("rollback-racer-2")).thenReturn(new HashMap<>(Map.of(
                "action_id","rollback-racer-2","action_type","ROLLBACK_DRAFT","source_entity_id",9L,
                "operator_id",7L,"operator_name","alice","action_status","CLAIMED",
                "request_fingerprint",rollbackFingerprint(9L,1L,3,actor))));
        when(mapper.insertTemplateVersion(anyMap())).thenThrow(new DuplicateKeyException("uk_todo_template_version"));

        TodoException error=assertThrows(TodoException.class,
                ()->service().rollbackDraft(9L,new RollbackDraftCommand("rollback-racer-2",3),actor));

        assertEquals("TODO_DEFINITION_VERSION_CONFLICT",error.getBusinessCode());
        verify(mapper,never()).completeDefinitionAction(anyString(),anyString(),org.mockito.ArgumentMatchers.anyLong());
    }

    private String rollbackFingerprint(long source,long template,int version,Actor actor)
    {
        java.util.Map<String,Object> value=new java.util.TreeMap<>();
        value.put("actionType","ROLLBACK_DRAFT");value.put("actorDeptId",actor.deptId());value.put("actorId",actor.userId());
        value.put("actorName",actor.userName());value.put("sourceVersionId",source);value.put("targetTemplateId",template);value.put("targetVersionNo",version);
        return TodoDefinitionSimulationService.sha256(com.alibaba.fastjson2.JSON.toJSONString(value));
    }

    private TodoDefinitionService service(){return new TodoDefinitionService(mapper,compiler());}
    private TodoDefinitionCompiler compiler(){return new TodoDefinitionCompiler(new TodoDefinitionCodec(),new TodoEventCatalogService(mapper),new TodoDecisionService(mapper));}
    private void registeredEvent(){when(mapper.selectEventCatalog("LEAD_CREATED",1)).thenReturn(Map.of("event_type","LEAD_CREATED","payload_version",1,"payload_schema_json","{\"type\":\"object\"}","status","ACTIVE"));}
    private UpdateDraftCommand update(Long id){return new UpdateDraftCommand("edit-1",id,"\"OWNER\"","{}",null,null,"{}");}
    private Map<String,Object> draft(String sla,String next){Map<String,Object> value=new HashMap<>();value.put("version_id",9L);value.put("template_id",1L);value.put("version_no",2);value.put("status","DRAFT");value.put("template_code","TD-001");value.put("event_type","LEAD_CREATED");value.put("payload_version",1);value.put("owner_rule_json","\"OWNER\"");value.put("dod_rule_json","{}");value.put("sla_rule_json",sla);value.put("next_rule_json",next);value.put("ui_schema_json","{}");return value;}
}
