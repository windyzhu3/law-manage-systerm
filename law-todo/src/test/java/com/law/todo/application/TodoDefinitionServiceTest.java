package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoDefinitionCommands.CopyTemplateCommand;
import com.law.todo.application.command.TodoDefinitionCommands.CreateTemplateCommand;
import com.law.todo.application.command.TodoDefinitionCommands.ImportTemplateCommand;
import com.law.todo.application.command.TodoDefinitionCommands.CopyVersionCommand;
import com.law.todo.application.command.TodoDefinitionCommands.PublishDraftCommand;
import com.law.todo.application.command.TodoDefinitionCommands.UpdateDraftCommand;
import com.law.todo.application.command.TodoDefinitionCommands.RollbackDraftCommand;
import com.law.todo.application.command.TodoDefinitionCommands.ReleaseDraftCommand;
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
    @Mock TodoSimulationReadinessService simulationReadiness;
    @Mock TodoBusinessOutcomeCatalogService businessOutcomes;
    private final Actor actor=new Actor(7L,"alice",3L);

    @BeforeEach void lockedVersionUsesTheExistingVersionStub()
    {org.mockito.Mockito.lenient().when(mapper.selectTemplateVersionForUpdate(org.mockito.ArgumentMatchers.anyLong()))
            .thenAnswer(invocation->mapper.selectTemplateVersionById(invocation.getArgument(0)));
     org.mockito.Mockito.lenient().when(mapper.selectEventCatalog(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyInt()))
            .thenAnswer(invocation->Map.of("event_type",invocation.<String>getArgument(0),"payload_version",invocation.<Integer>getArgument(1),
                    "payload_schema_json","{\"type\":\"object\",\"additionalProperties\":true}","status","ACTIVE"));}

    @Test void publishedVersionIsImmutable()
    {
        when(mapper.selectTemplateVersionById(9L)).thenReturn(Map.of("version_id",9L,"status","PUBLISHED"));
        TodoException error=assertThrows(TodoException.class,()->service().updateDraft(update(9L),actor));
        assertEquals("TODO_TEMPLATE_VERSION_IMMUTABLE",error.getBusinessCode());
    }

    @Test void copyTemplateCreatesStableIndependentDefinition()
    {
        when(mapper.selectTemplateById(1L)).thenReturn(Map.of("template_id",1L,"business_type","CONTRACT","status","0"));
        AtomicReference<Map<String,Object>> action=new AtomicReference<>();when(mapper.insertDefinitionActionClaim(anyMap())).thenAnswer(invocation->{Map<String,Object> value=new HashMap<>(invocation.getArgument(0));value.put("actionStatus","CLAIMED");action.set(value);return 1;});when(mapper.selectDefinitionActionForUpdate("copy-1")).thenAnswer(invocation->action.get());
        when(mapper.insertTemplate(anyMap())).thenAnswer(invocation->{Map<String,Object> value=invocation.getArgument(0);value.put("templateId",12L);return 1;});
        when(mapper.selectTemplateVersions(1L)).thenReturn(List.of());when(mapper.insertTemplateVersion(anyMap())).thenAnswer(invocation->{Map<String,Object> value=invocation.getArgument(0);value.put("versionId",22L);return 1;});when(mapper.completeDefinitionAction(org.mockito.ArgumentMatchers.eq("copy-1"),anyString(),org.mockito.ArgumentMatchers.eq(22L))).thenReturn(1);

        Long id=service().copyTemplate(1L,new CopyTemplateCommand("copy-1","CONTRACT_REVIEW_CUSTOM","自定义合同审核"),actor);

        assertEquals(12L,id);verify(mapper).insertTemplate(anyMap());verify(mapper).insertDefinitionActionClaim(anyMap());verify(mapper).insertTemplateVersion(anyMap());
    }

    @Test void copyTemplateRewritesCanonicalCodeAndClearsCompiledArtifacts()
    {
        when(mapper.selectTemplateById(1L)).thenReturn(Map.of("template_id",1L,"business_type","CONTRACT","status","0"));
        templateDraftLedger("copy-canonical");
        when(mapper.insertTemplate(anyMap())).thenAnswer(invocation->{Map<String,Object> row=invocation.getArgument(0);row.put("templateId",12L);return 1;});
        String sourceJson=canonical("CONTRACT_REVIEW");
        Map<String,Object> sourceVersion=new HashMap<>();sourceVersion.put("version_id",9L);sourceVersion.put("definition_schema_version",1);sourceVersion.put("definition_json",sourceJson);
        sourceVersion.put("owner_rule_json","{}");sourceVersion.put("dod_rule_json","{}");sourceVersion.put("sla_rule_json","{}");sourceVersion.put("next_rule_json","{}");sourceVersion.put("ui_schema_json","{}");sourceVersion.put("compiled_json","{\"old\":true}");sourceVersion.put("definition_hash","a".repeat(64));sourceVersion.put("validation_report_json","{\"old\":true}");
        when(mapper.selectTemplateVersions(1L)).thenReturn(List.of(sourceVersion));
        when(mapper.insertTemplateVersion(anyMap())).thenAnswer(invocation->{Map<String,Object> row=invocation.getArgument(0);row.put("versionId",22L);return 1;});

        TodoDefinitionService.TemplateDraftResult result=service().copyTemplateDraft(1L,
                new CopyTemplateCommand("copy-canonical","CONTRACT_CUSTOM","Custom"),actor);

        assertEquals(12L,result.templateId());assertEquals(22L,result.versionId());
        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String,Object>> inserted=ArgumentCaptor.forClass(Map.class);
        verify(mapper).insertTemplateVersion(inserted.capture());
        assertEquals("CONTRACT_CUSTOM",new TodoDefinitionCodec().read(String.valueOf(inserted.getValue().get("definitionJson"))).templateCode());
        assertEquals(null,inserted.getValue().get("compiledJson"));assertEquals(null,inserted.getValue().get("definitionHash"));assertEquals(null,inserted.getValue().get("validationReportJson"));
    }

    @Test void createTemplateDraftReplaysTheSameAggregateClaimAndReturnsBothIds()
    {
        templateDraftLedger("create-template");
        when(mapper.insertTemplate(anyMap())).thenAnswer(invocation->{Map<String,Object> row=invocation.getArgument(0);row.put("templateId",12L);return 1;});
        when(mapper.insertTemplateVersion(anyMap())).thenAnswer(invocation->{Map<String,Object> row=invocation.getArgument(0);row.put("versionId",22L);return 1;});
        when(mapper.selectTemplateVersionById(22L)).thenReturn(Map.of("template_id",12L));
        CreateTemplateCommand command=new CreateTemplateCommand("create-template","LEAD_NEW","New lead","LEAD");

        TodoDefinitionService.TemplateDraftResult first=service().createTemplateDraft(command,actor);
        TodoDefinitionService.TemplateDraftResult replay=service().createTemplateDraft(command,actor);

        assertEquals(new TodoDefinitionService.TemplateDraftResult(12L,22L),first);assertEquals(first,replay);
        verify(mapper,org.mockito.Mockito.times(1)).insertTemplate(anyMap());verify(mapper,org.mockito.Mockito.times(1)).insertTemplateVersion(anyMap());
    }

    @Test void importCreatesOnlyANewDraftAndPersistsTheCanonicalDefinition()
    {
        templateDraftLedger("import-template");
        when(mapper.insertTemplate(anyMap())).thenAnswer(invocation->{Map<String,Object> row=invocation.getArgument(0);row.put("templateId",12L);return 1;});
        when(mapper.insertTemplateVersion(anyMap())).thenAnswer(invocation->{Map<String,Object> row=invocation.getArgument(0);row.put("versionId",22L);return 1;});
        ImportTemplateCommand command=new ImportTemplateCommand("import-template",1,"LEAD_IMPORTED","Imported lead",
                "LEAD",canonical("LEAD_IMPORTED"),List.of(),"Imported for review","LEAD");

        TodoDefinitionService.TemplateDraftResult result=service().importTemplateDraft(command,actor);

        assertEquals(new TodoDefinitionService.TemplateDraftResult(12L,22L),result);
        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String,Object>> version=ArgumentCaptor.forClass(Map.class);
        verify(mapper).insertTemplateVersion(version.capture());
        assertEquals("DRAFT",version.getValue().get("status"));
        assertEquals("LEAD_IMPORTED",new TodoDefinitionCodec().read(String.valueOf(version.getValue().get("definitionJson"))).templateCode());
        verify(mapper,never()).publishTemplateVersionConditionally(org.mockito.ArgumentMatchers.anyLong(),anyString(),anyString());
    }

    @Test void importRejectsAnEnvelopeWhoseTemplateCodeDoesNotMatchTheCanonicalDocument()
    {
        ImportTemplateCommand command=new ImportTemplateCommand("import-mismatch",1,"LEAD_IMPORTED","Imported lead",
                "LEAD",canonical("OTHER_CODE"),List.of(),null,null);

        TodoException error=assertThrows(TodoException.class,()->service().importTemplateDraft(command,actor));

        assertEquals("TODO_TEMPLATE_CODE_MISMATCH",error.getBusinessCode());
        verify(mapper,never()).insertTemplate(anyMap());
    }

    @Test void aggregateClaimRejectsDifferentCreateRequestAndLeavesNoSecondWrite()
    {
        templateDraftLedger("aggregate-conflict");
        when(mapper.insertTemplate(anyMap())).thenAnswer(invocation->{Map<String,Object> row=invocation.getArgument(0);row.put("templateId",12L);return 1;});
        when(mapper.insertTemplateVersion(anyMap())).thenAnswer(invocation->{Map<String,Object> row=invocation.getArgument(0);row.put("versionId",22L);return 1;});
        TodoDefinitionService current=service();
        current.createTemplateDraft(new CreateTemplateCommand("aggregate-conflict","LEAD_NEW","New lead","LEAD"),actor);

        TodoException error=assertThrows(TodoException.class,()->current.createTemplateDraft(
                new CreateTemplateCommand("aggregate-conflict","LEAD_OTHER","Other","LEAD"),actor));

        assertEquals("TODO_DEFINITION_ACTION_CONFLICT",error.getBusinessCode());
        verify(mapper,org.mockito.Mockito.times(1)).insertTemplate(anyMap());verify(mapper,org.mockito.Mockito.times(1)).insertTemplateVersion(anyMap());
    }

    @Test void productionConstructorRejectsDisabledTemplateBusinessTypeBeforeAggregateClaim()
    {
        com.law.todo.spi.TodoDictionaryValidationPort dictionaries=org.mockito.Mockito.mock(com.law.todo.spi.TodoDictionaryValidationPort.class);
        when(dictionaries.isEnabled("law_todo_business_type","DISABLED")).thenReturn(false);
        TodoDefinitionService current=new TodoDefinitionService(mapper,compiler(),null,dictionaries);

        TodoException error=assertThrows(TodoException.class,()->current.createTemplateDraft(
                new CreateTemplateCommand("disabled-template","T_DISABLED","Disabled","DISABLED"),actor));

        assertEquals("TODO_TEMPLATE_BUSINESS_TYPE_INVALID",error.getBusinessCode());verify(mapper,never()).insertDefinitionActionClaim(anyMap());verify(mapper,never()).insertTemplate(anyMap());
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
        assertEquals("alice",value.get("updateBy"));
    }

    @Test void updateDraftRejectsBusinessOutcomeThatDoesNotTargetTheExactEditableVersion()
    {
        Map<String,Object> current=draft(null,null);current.put("template_code","TD-004");
        current.put("business_type","LEAD");
        when(mapper.selectTemplateVersionById(9L)).thenReturn(current);
        when(businessOutcomes.validate(org.mockito.ArgumentMatchers.eq("TD-004"),
                org.mockito.ArgumentMatchers.eq("LEAD"),org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.any())).thenReturn(List.of(
                        new TodoBusinessOutcomeCatalogService.OutcomeIssue(
                                "TODO_ROUTING_TARGET_VERSION_INVALID",
                                "routing.businessOutcomes[0].targetVersionId",
                                "Self schedule must target the editable version")));
        TodoDefinitionService service=new TodoDefinitionService(mapper,compiler(),null,
                (type,value)->true,null,businessOutcomes);

        TodoException error=assertThrows(TodoException.class,()->service.updateDraft(
                new UpdateDraftCommand("edit-td004",9L,canonical("TD-004")),actor));

        assertEquals("TODO_ROUTING_TARGET_VERSION_INVALID",error.getBusinessCode());
        verify(businessOutcomes).validate(org.mockito.ArgumentMatchers.eq("TD-004"),
                org.mockito.ArgumentMatchers.eq("LEAD"),org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.any());
        verify(mapper,never()).updateTemplateVersionDraft(anyMap());
    }

    @Test void updateDraftRejectsAnEventThatIsIncompatibleWithTheGovernedTemplate()
    {
        Map<String,Object> current=draft(null,null);current.put("template_code","TD-002");
        current.put("business_type","LEAD");
        when(mapper.selectTemplateVersionById(9L)).thenReturn(current);
        TodoDefinitionService service=new TodoDefinitionService(mapper,compiler(),null,
                (type,value)->true,null,businessOutcomes,new TodoTemplateEventPolicy());

        TodoException error=assertThrows(TodoException.class,()->service.updateDraft(
                new UpdateDraftCommand("edit-td002-wrong-event",9L,canonical("TD-002")),actor));

        assertEquals("TODO_TEMPLATE_EVENT_INCOMPATIBLE",error.getBusinessCode());
        verify(mapper,never()).updateTemplateVersionDraft(anyMap());
    }

    @Test void updateDraftAcceptsWindowScheduledSlaWithoutScalarMinutes()
    {
        String document="""
                {"schemaVersion":1,"templateCode":"TD-001",
                 "event":{"eventType":"LEAD_CREATED","payloadVersion":1,"condition":{}},
                 "owner":{"config":{}},"dod":{"config":{}},
                 "sla":{"config":{"calendarCode":"DEFAULT","schedule":{"windows":[
                   {"windowCode":"T0","dayOffset":0,"startOffsetMinutes":0,"durationMinutes":120,"maxAttempts":3},
                   {"windowCode":"T1_AM","dayOffset":1,"startTime":"09:00:00","endTime":"11:00:00","maxAttempts":1}
                 ]}}},
                 "ui":{"config":{}},"routing":{"config":{}},"autoActions":[],"decisionRefs":[],"acceptanceRefs":[]}
                """;
        Map<String,Object> current=draft(null,null);current.put("business_type","LEAD");
        current.put("definition_json",document);
        when(mapper.selectTemplateVersionById(9L)).thenReturn(current);
        when(mapper.selectCalendarByCode("DEFAULT")).thenReturn(Map.of("calendar_id",1L));
        templateDraftLedger("edit-window-schedule");
        when(mapper.updateTemplateVersionDraft(anyMap())).thenReturn(1);

        Long result=service().updateDraft(new UpdateDraftCommand("edit-window-schedule",9L,
                null,null,null,null,null,document,document),actor);

        assertEquals(9L,result);
        verify(mapper).updateTemplateVersionDraft(anyMap());
    }

    @Test void updateDraftAcceptsScalarOnlySla()
    {
        String document=definitionWithSla("\"minutes\":60");
        Map<String,Object> current=draft(null,null);current.put("business_type","LEAD");
        current.put("definition_json",document);
        when(mapper.selectTemplateVersionById(9L)).thenReturn(current);
        when(mapper.selectCalendarByCode("DEFAULT")).thenReturn(Map.of("calendar_id",1L));
        templateDraftLedger("edit-scalar-sla");
        when(mapper.updateTemplateVersionDraft(anyMap())).thenReturn(1);

        assertEquals(9L,service().updateDraft(new UpdateDraftCommand("edit-scalar-sla",9L,
                null,null,null,null,null,document,document),actor));
        verify(mapper).updateTemplateVersionDraft(anyMap());
    }

    @Test void updateDraftRejectsStructurallyInvalidScheduleWindows()
    {
        String document="""
                {"schemaVersion":1,"templateCode":"TD-001",
                 "event":{"eventType":"LEAD_FIRST_CONTACT_UNREACHABLE","payloadVersion":1,"condition":{}},
                 "owner":{"config":{}},"dod":{"config":{}},
                 "sla":{"config":{"calendarCode":"DEFAULT","schedule":{"windows":[{}]}}},
                 "ui":{"config":{}},"routing":{"config":{}},"autoActions":[],"decisionRefs":[],"acceptanceRefs":[]}
                """;
        Map<String,Object> current=draft(null,null);current.put("business_type","LEAD");
        current.put("definition_json",document);
        when(mapper.selectTemplateVersionById(9L)).thenReturn(current);
        when(mapper.selectCalendarByCode("DEFAULT")).thenReturn(Map.of("calendar_id",1L));

        TodoException error=assertThrows(TodoException.class,()->service().updateDraft(
                new UpdateDraftCommand("edit-invalid-window-schedule",9L,
                        null,null,null,null,null,document,document),actor));

        assertEquals("TODO_SCHEDULE_RULE_INVALID",error.getBusinessCode());
        verify(mapper,never()).updateTemplateVersionDraft(anyMap());
    }

    @Test void updateDraftRejectsEveryPresentInvalidOrMixedScheduleInsteadOfUsingScalarFallback()
    {
        List<String> invalidSla=List.of(
                "\"minutes\":60,\"schedule\":{\"windows\":[{}]}",
                "\"schedule\":{\"windows\":\"T0\"}",
                "\"schedule\":{\"windows\":[]}",
                "\"schedule\":[\"T0\"]");
        when(mapper.selectCalendarByCode("DEFAULT")).thenReturn(Map.of("calendar_id",1L));
        for(int index=0;index<invalidSla.size();index++)
        {
            String actionId="edit-invalid-schedule-"+index;
            String document=definitionWithSla(invalidSla.get(index));
            Map<String,Object> current=draft(null,null);current.put("business_type","LEAD");
            current.put("definition_json",document);
            when(mapper.selectTemplateVersionById(9L)).thenReturn(current);

            TodoException error=assertThrows(TodoException.class,()->service().updateDraft(
                    new UpdateDraftCommand(actionId,9L,
                            null,null,null,null,null,document,document),actor));

            assertEquals("TODO_SCHEDULE_RULE_INVALID",error.getBusinessCode());
        }
        verify(mapper,never()).updateTemplateVersionDraft(anyMap());
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

    @Test void preflightUsesTheUnifiedSimulationReadinessGate()
    {
        when(mapper.selectTemplateVersionById(9L)).thenReturn(draft(null,null));
        registeredEvent();
        when(mapper.updateDefinitionCompilation(anyMap())).thenReturn(1);
        when(simulationReadiness.applyPreflightGate(
                org.mockito.ArgumentMatchers.eq(9L),org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation->{
                    var report=invocation.<com.law.todo.definition.compiler.DefinitionValidationReport>getArgument(1);
                    return new com.law.todo.definition.compiler.DefinitionValidationReport(
                            List.of(new com.law.todo.definition.compiler.DefinitionValidationReport.ValidationIssue(
                                    "TODO_REQUIRED_SIMULATION_SCENARIOS_INCOMPLETE","simulation.scenarios",
                                    "TD001_VALID")),report.warnings(),report.compiledJson(),report.definitionHash());
                });
        TodoDefinitionService service=new TodoDefinitionService(mapper,compiler(),null,
                (type,value)->true,simulationReadiness);

        var result=service.preflight(9L);

        assertFalse(result.publishable());
        assertTrue(result.report().errors().stream().anyMatch(issue->
                "TODO_REQUIRED_SIMULATION_SCENARIOS_INCOMPLETE".equals(issue.code())));
    }

    @Test void preflightAndSimulationGateUseTheExactEditableVersionForBusinessOutcomes()
    {
        Map<String,Object> current=draft(null,null);current.put("template_code","TD-004");
        current.put("business_type","LEAD");current.put("definition_json",canonical("TD-004"));
        current.put("definition_hash","a".repeat(64));
        when(mapper.selectTemplateVersionById(9L)).thenReturn(current);
        registeredEvent();when(mapper.updateDefinitionCompilation(anyMap())).thenReturn(1);
        var issue=new TodoBusinessOutcomeCatalogService.OutcomeIssue(
                "TODO_ROUTING_TARGET_VERSION_INVALID",
                "routing.businessOutcomes[0].targetVersionId",
                "Self schedule must target the editable version");
        when(businessOutcomes.validate(org.mockito.ArgumentMatchers.eq("TD-004"),
                org.mockito.ArgumentMatchers.eq("LEAD"),org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.any())).thenReturn(List.of(issue));
        TodoDefinitionService service=new TodoDefinitionService(mapper,compiler(),null,
                (type,value)->true,null,businessOutcomes);

        TodoDefinitionService.PreflightResult preflight=service.preflight(9L);
        TodoException simulation=assertThrows(TodoException.class,
                ()->service.assertSimulationGate(9L,"a".repeat(64)));

        assertFalse(preflight.publishable());
        assertTrue(preflight.report().errors().stream().anyMatch(error->
                "TODO_ROUTING_TARGET_VERSION_INVALID".equals(error.code())));
        assertEquals("TODO_ROUTING_TARGET_VERSION_INVALID",simulation.getBusinessCode());
        verify(businessOutcomes,org.mockito.Mockito.atLeast(2)).validate(
                org.mockito.ArgumentMatchers.eq("TD-004"),org.mockito.ArgumentMatchers.eq("LEAD"),
                org.mockito.ArgumentMatchers.eq(9L),org.mockito.ArgumentMatchers.any());
    }

    @Test void draftPublishPreflightAllowsItsOwnStartTaskVersion()
    {
        Map<String,Object> current=draft(null,null);
        current.put("definition_json", """
                {"schemaVersion":1,"templateCode":"TD-001",
                 "event":{"eventType":"LEAD_CREATED","payloadVersion":1,"condition":{}},
                 "owner":{"config":{"type":"PAYLOAD","operand":"ownerId"}},
                 "dod":{"config":{}},"sla":{"config":{}},"ui":{"config":{}},
                 "routing":{"config":{"start":"start","nodes":[
                   {"key":"start","type":"TASK","templateVersionId":9},
                   {"key":"end","type":"END"}],
                   "edges":[{"key":"start-end","from":"start","to":"end"}]}},
                 "autoActions":[],"decisionRefs":[],"acceptanceRefs":[]}
                """);
        when(mapper.selectTemplateVersionById(9L)).thenReturn(current);
        registeredEvent();
        when(mapper.updateDefinitionCompilation(anyMap())).thenReturn(1);

        TodoDefinitionService.PreflightResult result=service().preflight(9L);

        assertTrue(result.report().publishable());
        assertTrue(result.report().errors().stream().noneMatch(issue ->
                "TODO_ROUTE_TASK_VERSION_NOT_PUBLISHED".equals(issue.code())));
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

        assertEquals("TODO_PUBLISH_BLOCKED",error.getBusinessCode());
        verify(mapper).updateDefinitionCompilation(anyMap());
        verify(mapper,never()).publishTemplateVersionConditionally(org.mockito.ArgumentMatchers.anyLong(),anyString(),anyString());
    }

    @Test void publishRequiresAReasonWhenFreshPreflightContainsWarnings()
    {
        Map<String,Object> current=draft(null,null);
        when(mapper.selectTemplateVersionById(9L)).thenReturn(current);
        TodoDefinitionCompiler warningCompiler=org.mockito.Mockito.mock(TodoDefinitionCompiler.class);
        when(warningCompiler.compile(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.law.todo.definition.compiler.DefinitionValidationReport(List.of(),
                        List.of(new com.law.todo.definition.compiler.DefinitionValidationReport.ValidationIssue(
                                "TODO_WARNING","event.condition","Review the broad trigger")),
                        canonical("TD-001"),"a".repeat(64)));
        when(mapper.updateDefinitionCompilation(anyMap())).thenReturn(1);
        TodoDefinitionService service=new TodoDefinitionService(mapper,warningCompiler);

        TodoException missing=assertThrows(TodoException.class,()->service.publish(
                new PublishDraftCommand("pub-warning",9L,"a".repeat(64),null),actor));

        assertEquals("TODO_PUBLISH_WARNING_REASON_REQUIRED",missing.getBusinessCode());
        verify(mapper).updateDefinitionCompilation(anyMap());
        verify(mapper,never()).publishTemplateVersionConditionally(org.mockito.ArgumentMatchers.anyLong(),anyString(),anyString());
    }

    @Test void warningPublicationStoresTrimmedReasonAfterPersistingFreshPreflight()
    {
        Map<String,Object> current=draft(null,null);
        when(mapper.selectTemplateVersionById(9L)).thenReturn(current);
        TodoDefinitionCompiler warningCompiler=org.mockito.Mockito.mock(TodoDefinitionCompiler.class);
        when(warningCompiler.compile(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.law.todo.definition.compiler.DefinitionValidationReport(List.of(),
                        List.of(new com.law.todo.definition.compiler.DefinitionValidationReport.ValidationIssue(
                                "TODO_WARNING","event.condition","Review the broad trigger")),
                        canonical("TD-001"),"a".repeat(64)));
        when(mapper.updateDefinitionCompilation(anyMap())).thenReturn(1);
        when(mapper.insertDefinitionActionIfAbsent(anyMap())).thenReturn(1);
        when(mapper.publishTemplateVersionConditionally(9L,"a".repeat(64),"alice")).thenReturn(1);
        TodoDefinitionService service=new TodoDefinitionService(mapper,warningCompiler);

        assertEquals(9L,service.publish(new PublishDraftCommand(
                "pub-warning-reason",9L,"a".repeat(64),"  Reviewed with operations  "),actor));

        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String,Object>> action=ArgumentCaptor.forClass(Map.class);
        verify(mapper).insertDefinitionActionIfAbsent(action.capture());
        assertEquals("Reviewed with operations",
                JSON.parseObject(String.valueOf(action.getValue().get("payloadJson"))).getString("warningReason"));
        org.mockito.InOrder order=org.mockito.Mockito.inOrder(mapper);
        order.verify(mapper).updateDefinitionCompilation(anyMap());
        order.verify(mapper).insertDefinitionActionIfAbsent(anyMap());
        order.verify(mapper).publishTemplateVersionConditionally(9L,"a".repeat(64),"alice");
    }

    @Test void prdCatalogueBlocksPublishingTemplatesWhoseBusinessHandlersAreMissing()
    {
        Map<String,Object> invalidReview=blockedPrdDraft(20L,"TD-002");
        Map<String,Object> discountApproval=blockedPrdDraft(60L,"TD-006");
        when(mapper.selectTemplateVersionById(20L)).thenReturn(invalidReview);
        when(mapper.selectTemplateVersionById(60L)).thenReturn(discountApproval);
        stubPublishedLeadRouteTargets();
        when(mapper.selectEventCatalog(anyString(),org.mockito.ArgumentMatchers.eq(1))).thenAnswer(invocation->Map.of(
                "event_type",invocation.getArgument(0),"payload_version",1,
                "payload_schema_json","{\"type\":\"object\",\"additionalProperties\":true}","status","ACTIVE"));
        when(mapper.updateDefinitionCompilation(anyMap())).thenReturn(1);
        TodoDefinitionService service=service();

        for(long versionId:new long[]{20L,60L})
        {
            TodoDefinitionService.PreflightResult preflight=service.preflight(versionId);
            assertFalse(preflight.publishable());
            assertTrue(preflight.report().errors().stream()
                    .anyMatch(issue->"TODO_PRD_TEMPLATE_BLOCKED".equals(issue.code())));
            TodoException error=assertThrows(TodoException.class,()->service.publish(
                    new PublishDraftCommand("prd-blocked-"+versionId,versionId),actor));
            assertEquals("TODO_PUBLISH_BLOCKED",error.getBusinessCode());
        }
        verify(mapper,never()).publishTemplateVersionConditionally(org.mockito.ArgumentMatchers.anyLong(),anyString(),anyString());
    }

    @Test void prdProductionBlockCannotBeBypassedByMarkingFoundationReady()
    {
        Map<String,Object> blocked=blockedPrdDraft(20L,"TD-002");
        blocked.put("prd_foundation_state","READY");
        blocked.put("prd_production_state","BLOCKED");
        when(mapper.selectTemplateVersionById(20L)).thenReturn(blocked);
        stubPublishedLeadRouteTargets();
        when(mapper.selectEventCatalog(anyString(),org.mockito.ArgumentMatchers.eq(1))).thenAnswer(invocation->Map.of(
                "event_type",invocation.getArgument(0),"payload_version",1,
                "payload_schema_json","{\"type\":\"object\",\"additionalProperties\":true}","status","ACTIVE"));
        when(mapper.updateDefinitionCompilation(anyMap())).thenReturn(1);

        TodoDefinitionService.PreflightResult result=service().preflight(20L);

        assertFalse(result.publishable());
        assertTrue(result.report().errors().stream()
                .anyMatch(issue->"TODO_PRD_TEMPLATE_BLOCKED".equals(issue.code())));
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

    @Test void updateDraftAcceptsConfigurationCenterDocumentWithEmptyRuleSections()
    {
        Map<String,Object> current=draft(null,null);
        current.put("template_code","E2E_TODO_CONFIG");
        current.put("business_type","LEAD");
        when(mapper.selectTemplateVersionById(9L)).thenReturn(current);
        AtomicReference<Map<String,Object>> action=new AtomicReference<>();
        when(mapper.insertDefinitionActionClaim(anyMap())).thenAnswer(invocation->{
            Map<String,Object> recorded=new HashMap<>(invocation.getArgument(0));
            recorded.put("actionStatus","CLAIMED");action.set(recorded);return 1;
        });
        when(mapper.selectDefinitionActionForUpdate("edit-configuration-center")).thenAnswer(invocation->action.get());
        when(mapper.updateTemplateVersionDraft(anyMap())).thenReturn(1);
        when(mapper.completeDefinitionAction(org.mockito.ArgumentMatchers.eq("edit-configuration-center"),anyString(),org.mockito.ArgumentMatchers.eq(9L))).thenReturn(1);
        String document="""
                {"schemaVersion":1,"templateCode":"E2E_TODO_CONFIG",
                 "event":{"eventType":"LEAD_ASSIGNED","payloadVersion":1,"condition":{}},
                 "owner":{"config":{"type":"BUSINESS_OWNER","candidates":[],"cc":[],"skipUnavailable":true,"useDelegation":true}},
                 "dod":{"config":{"composition":"ALL","systemDerivedFields":[]}},"sla":{"config":{}},
                 "ui":{"config":{"fields":["contactResult"],"businessStage":"LEAD","templateType":"STANDARD","priority":"NORMAL","description":""}},
                 "routing":{"config":{"nodes":[],"edges":[]}},"autoActions":[],"decisionRefs":[],"acceptanceRefs":[]}
                """;

        assertEquals(9L,service().updateDraft(new UpdateDraftCommand("edit-configuration-center",9L,document),actor));

        verify(mapper).updateTemplateVersionDraft(anyMap());
    }

    @Test void releaseRollbackAllocatesTheNextVersionWhileHoldingTheTemplateLock()
    {
        Map<String,Object> source=draft(null,null);source.put("status","PUBLISHED");source.put("definition_json",new TodoDefinitionCodec().canonicalJson(
                new com.law.todo.definition.codec.LegacyDefinitionAdapter().fromLegacy(source)));
        when(mapper.selectTemplateVersionById(9L)).thenReturn(source);templateDraftLedger("release-rollback");
        when(mapper.selectTemplateForUpdate(1L)).thenReturn(Map.of("template_id",1L));
        when(mapper.selectNextTemplateVersionNo(1L)).thenReturn(5);
        when(mapper.insertTemplateVersion(anyMap())).thenAnswer(invocation->{Map<String,Object> row=invocation.getArgument(0);row.put("versionId",15L);return 1;});

        assertEquals(15L,service().rollbackReleaseDraft(9L,new ReleaseDraftCommand("release-rollback"),actor));

        ArgumentCaptor<Map<String,Object>> inserted=ArgumentCaptor.forClass(Map.class);verify(mapper).insertTemplateVersion(inserted.capture());
        assertEquals(5,inserted.getValue().get("versionNo"));assertEquals(9L,inserted.getValue().get("rollbackSourceVersionId"));
        org.mockito.InOrder order=org.mockito.Mockito.inOrder(mapper);order.verify(mapper).selectTemplateForUpdate(1L);
        order.verify(mapper).selectNextTemplateVersionNo(1L);order.verify(mapper).insertTemplateVersion(anyMap());
    }

    @Test void releaseCopyAllocatesServerVersionAndAReplayReturnsTheSameDraft()
    {
        Map<String,Object> source=draft(null,null);source.put("status","RETIRED");source.put("definition_json",new TodoDefinitionCodec().canonicalJson(
                new com.law.todo.definition.codec.LegacyDefinitionAdapter().fromLegacy(source)));
        when(mapper.selectTemplateVersionById(9L)).thenReturn(source);templateDraftLedger("release-copy");
        when(mapper.selectTemplateForUpdate(1L)).thenReturn(Map.of("template_id",1L));when(mapper.selectNextTemplateVersionNo(1L)).thenReturn(6);
        when(mapper.insertTemplateVersion(anyMap())).thenAnswer(invocation->{Map<String,Object> row=invocation.getArgument(0);row.put("versionId",16L);return 1;});

        assertEquals(16L,service().copyReleaseDraft(9L,new ReleaseDraftCommand("release-copy"),actor));
        assertEquals(16L,service().copyReleaseDraft(9L,new ReleaseDraftCommand("release-copy"),actor));

        verify(mapper,org.mockito.Mockito.times(1)).selectTemplateForUpdate(1L);
        verify(mapper,org.mockito.Mockito.times(1)).insertTemplateVersion(anyMap());
    }

    private String rollbackFingerprint(long source,long template,int version,Actor actor)
    {
        java.util.Map<String,Object> value=new java.util.TreeMap<>();
        value.put("actionType","ROLLBACK_DRAFT");value.put("actorDeptId",actor.deptId());value.put("actorId",actor.userId());
        value.put("actorName",actor.userName());value.put("sourceVersionId",source);value.put("targetTemplateId",template);value.put("targetVersionNo",version);
        return TodoDefinitionSimulationService.sha256(com.alibaba.fastjson2.JSON.toJSONString(value));
    }

    private AtomicReference<Map<String,Object>> templateDraftLedger(String actionId)
    {
        AtomicReference<Map<String,Object>> action=new AtomicReference<>();
        when(mapper.insertDefinitionActionClaim(anyMap())).thenAnswer(invocation->{
            if(action.get()==null){action.set(new HashMap<>(invocation.getArgument(0)));return 1;}return 0;
        });
        when(mapper.selectDefinitionActionForUpdate(actionId)).thenAnswer(invocation->{
            Map<String,Object> claimed=action.get();if(claimed==null)return null;
            Map<String,Object> locked=new HashMap<>();locked.put("action_type",claimed.get("actionType"));locked.put("request_fingerprint",claimed.get("requestFingerprint"));
            locked.put("operator_id",claimed.get("operatorId"));locked.put("operator_name",claimed.get("operatorName"));locked.put("action_status",claimed.getOrDefault("actionStatus","CLAIMED"));locked.put("entity_id",claimed.get("entityId"));locked.put("source_entity_id",claimed.get("sourceEntityId"));return locked;
        });
        when(mapper.completeDefinitionAction(org.mockito.ArgumentMatchers.eq(actionId),anyString(),org.mockito.ArgumentMatchers.anyLong())).thenAnswer(invocation->{
            action.get().put("actionStatus","APPLIED");action.get().put("entityId",invocation.getArgument(2));return 1;
        });return action;
    }
    private String canonical(String templateCode)
    {return new TodoDefinitionCodec().canonicalJson(new TodoDefinitionDocument(1,templateCode,
            new TodoDefinitionDocument.EventRule("LEAD_CREATED",1,Map.of()),new TodoDefinitionDocument.OwnerRule(Map.of()),
            new TodoDefinitionDocument.DodRule(Map.of()),new TodoDefinitionDocument.SlaRule(Map.of()),new TodoDefinitionDocument.UiSchema(Map.of()),
            new TodoDefinitionDocument.RoutingGraph(Map.of()),List.of(),List.of(),List.of()));}
    private String definitionWithSla(String sla)
    {return "{\"schemaVersion\":1,\"templateCode\":\"TD-001\","+
            "\"event\":{\"eventType\":\"LEAD_FIRST_CONTACT_UNREACHABLE\",\"payloadVersion\":1,\"condition\":{}},"+
            "\"owner\":{\"config\":{}},\"dod\":{\"config\":{}},\"sla\":{\"config\":{\"calendarCode\":\"DEFAULT\","+
            sla+"}},\"ui\":{\"config\":{}},\"routing\":{\"config\":{}},\"autoActions\":[],"+
            "\"decisionRefs\":[],\"acceptanceRefs\":[]}";}
    private TodoDefinitionService service(){return new TodoDefinitionService(mapper,compiler());}
    private TodoDefinitionCompiler compiler(){return new TodoDefinitionCompiler(new TodoDefinitionCodec(),new TodoEventCatalogService(mapper),new TodoDecisionService(mapper));}
    private void registeredEvent(){when(mapper.selectEventCatalog("LEAD_CREATED",1)).thenReturn(Map.of("event_type","LEAD_CREATED","payload_version",1,"payload_schema_json","{\"type\":\"object\"}","status","ACTIVE"));}
    private void stubPublishedLeadRouteTargets()
    {
        when(mapper.selectTemplateVersionById(1L)).thenReturn(Map.of("version_id",1L,"status","PUBLISHED"));
        when(mapper.selectTemplateVersionById(2L)).thenReturn(Map.of("version_id",2L,"status","PUBLISHED"));
    }

    private Map<String,Object> blockedPrdDraft(Long versionId,String code)
    {
        try(InputStream input=getClass().getResourceAsStream("/todo-definitions/v0.2/"+code+".json"))
        {
            if(input==null)throw new IllegalStateException("Missing PRD definition resource: "+code);
            JSONObject envelope=JSON.parseObject(new String(input.readAllBytes(),StandardCharsets.UTF_8));
            TodoDefinitionDocument definition=new TodoDefinitionCodec().read(
                    envelope.getJSONObject("definition").toJSONString());
            Map<String,Object> row=new HashMap<>();row.put("version_id",versionId);row.put("template_id",versionId);
            row.put("version_no",1);row.put("status","DRAFT");row.put("template_code",code);
            row.put("event_type",definition.event().eventType());row.put("payload_version",1);
            row.put("owner_rule_json",JSON.toJSONString(definition.owner().config()));
            row.put("dod_rule_json",JSON.toJSONString(definition.dod().config()));
            row.put("sla_rule_json",JSON.toJSONString(definition.sla().config()));
            row.put("next_rule_json",JSON.toJSONString(definition.routing().config()));
            row.put("ui_schema_json",JSON.toJSONString(definition.ui().config()));
            row.put("definition_json",new TodoDefinitionCodec().canonicalJson(definition));
            row.put("prd_foundation_state","BLOCKED");
            row.put("prd_production_state","BLOCKED");
            row.put("prd_blockers_json",envelope.getJSONArray("blockers").toJSONString());
            return row;
        }
        catch(java.io.IOException failure){throw new IllegalStateException(failure);}
    }
    private UpdateDraftCommand update(Long id){return new UpdateDraftCommand("edit-1",id,"\"OWNER\"","{}",null,null,"{}");}
    private Map<String,Object> draft(String sla,String next){Map<String,Object> value=new HashMap<>();value.put("version_id",9L);value.put("template_id",1L);value.put("version_no",2);value.put("status","DRAFT");value.put("template_code","TD-001");value.put("event_type","LEAD_CREATED");value.put("payload_version",1);value.put("owner_rule_json","\"OWNER\"");value.put("dod_rule_json","{}");value.put("sla_rule_json",sla);value.put("next_rule_json",next);value.put("ui_schema_json","{}");return value;}
}
