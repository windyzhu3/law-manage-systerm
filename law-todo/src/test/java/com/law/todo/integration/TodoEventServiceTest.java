package com.law.todo.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoInteractions;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import com.law.todo.application.TodoAssignmentResolver;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.mapper.TodoConfigurationMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Autowired;
import com.law.todo.definition.catalog.TodoEventCatalogService;
import com.law.todo.expression.ConditionValidator;
import com.law.todo.expression.ConditionEvaluator;
import com.law.todo.spi.TodoOrganizationPort;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness=Strictness.LENIENT)
class TodoEventServiceTest
{
    @Mock TodoMapper mapper;
    @Mock TodoConfigurationMapper configurationMapper;
    @BeforeEach void activeCatalog(){when(mapper.selectEventCatalog("LEAD_ASSIGNED",1)).thenReturn(Map.of("status","ACTIVE","payload_schema_json","{\"type\":\"object\",\"properties\":{\"ownerId\":{\"type\":\"integer\"}}}"));}

    @Test void eventCarriesAnExplicitPayloadVersion()
    {
        assertEquals(2,new TodoEvent("evt","LEAD_ASSIGNED","LEAD",7L,"L-7",Map.of(),2).payloadVersion());
        assertEquals(1,new TodoEvent("evt","LEAD_ASSIGNED","LEAD",7L,"L-7",Map.of()).payloadVersion());
    }

    @Test void springConstructorExplicitlyInjectsConditionValidationDependencies() throws Exception
    {
        assertTrue(TodoEventService.class.getConstructor(TodoMapper.class,
                TodoAssignmentResolver.class,ConditionEvaluator.class,
                ConditionValidator.class,TodoEventCatalogService.class)
                .isAnnotationPresent(Autowired.class));
    }

    @Test void rejectsInvalidPayloadBeforeSelectingTriggerRules()
    {
        when(mapper.selectEventCatalog("LEAD_ASSIGNED", 1)).thenReturn(Map.of("status", "ACTIVE",
                "payload_schema_json", "{\"type\":\"object\",\"required\":[\"ownerId\"],\"properties\":{\"ownerId\":{\"type\":\"integer\"}}}"));
        TodoEvent invalid = new TodoEvent("evt-invalid", "LEAD_ASSIGNED", "LEAD", 7L, "L-7", Map.of());

        TodoException error = assertThrows(TodoException.class,
                () -> new TodoEventService(mapper, new TodoAssignmentResolver()).handle(invalid));

        assertEquals("TODO_EVENT_PAYLOAD_INVALID", error.getBusinessCode());
        verify(mapper, never()).selectTriggerRules(any(), any());
    }

    @Test void duplicateEventReturnsExistingTodo()
    {
        TodoInstance existing=new TodoInstance();existing.setTodoId(4L);
        when(mapper.selectTriggerRules("LEAD_ASSIGNED","LEAD")).thenReturn(List.of(rule()));
        when(mapper.selectByTriggerKey("evt-1:22:7")).thenReturn(existing);
        TodoInstance result=new TodoEventService(mapper,new TodoAssignmentResolver()).handle(event()).get(0);
        assertSame(existing,result);verify(mapper,never()).insertInstance(any());
    }

    @Test void concurrentDuplicateCreationReturnsCommittedWinner()
    {
        TodoInstance winner=new TodoInstance();winner.setTodoId(5L);
        when(mapper.selectTriggerRules("LEAD_ASSIGNED","LEAD")).thenReturn(List.of(rule()));
        when(mapper.selectByTriggerKey("evt-1:22:7")).thenReturn(null,winner);
        doThrow(new DuplicateKeyException("duplicate")).when(mapper).insertInstance(any());

        TodoInstance result=new TodoEventService(mapper,new TodoAssignmentResolver()).handle(event()).get(0);

        assertSame(winner,result);verify(mapper,never()).insertRelation(anyMap());
    }

    @Test void createsCandidateWhenRuleTargetsRole()
    {
        when(mapper.selectTriggerRules("LEAD_ASSIGNED","LEAD")).thenReturn(List.of(rule()));
        new TodoEventService(mapper,new TodoAssignmentResolver()).handle(event());
        verify(mapper).insertCandidate(anyMap());
        verify(mapper).insertRelation(anyMap());
        assertEquals("ROLE",new TodoAssignmentResolver().resolve("ROLE:5",Map.of()).candidateType());
    }

    @Test void resolvesOwnersDepartmentForSupervisorVisibility()
    {
        Map<String,Object> ownerRule=new java.util.HashMap<>(rule());ownerRule.put("owner_rule_json","PAYLOAD:ownerId");
        when(mapper.selectTriggerRules("LEAD_ASSIGNED","LEAD")).thenReturn(List.of(ownerRule));
        when(mapper.selectUserDeptId(8L)).thenReturn(3L);

        TodoInstance created=new TodoEventService(mapper,new TodoAssignmentResolver()).handle(event()).get(0);

        assertEquals(3L,created.getOwnerDeptId());
    }

    @Test void skipsRuleWhenPayloadDoesNotMatchCondition()
    {
        Map<String,Object> conditional=new java.util.HashMap<>(rule());
        conditional.put("condition_json","{\"source\":\"ONLINE\",\"priority\":2}");
        when(mapper.selectTriggerRules("LEAD_ASSIGNED","LEAD")).thenReturn(List.of(conditional));

        List<TodoInstance> result=new TodoEventService(mapper,new TodoAssignmentResolver()).handle(event());

        assertEquals(0,result.size());
        verify(mapper,never()).insertInstance(any());
    }

    @Test void legacyFlatConditionUsesActiveCatalog()
    {
        Map<String,Object> conditional=new java.util.HashMap<>(rule());
        conditional.put("condition_json","{\"source\":\"ONLINE\",\"priority\":2}");
        when(mapper.selectTriggerRules("LEAD_ASSIGNED","LEAD")).thenReturn(List.of(conditional));
        when(mapper.selectEventCatalog("LEAD_ASSIGNED",1)).thenReturn(Map.of("status","ACTIVE","payload_schema_json","{\"type\":\"object\",\"properties\":{\"source\":{\"type\":\"string\"},\"priority\":{\"type\":\"number\"}}}"));
        TodoEvent matching=new TodoEvent("evt-2","LEAD_ASSIGNED","LEAD",8L,"L-8",Map.of("source","ONLINE","priority",2));

        List<TodoInstance> result=new TodoEventService(mapper,new TodoAssignmentResolver()).handle(matching);

        assertEquals(1,result.size());
        verify(mapper,times(1)).insertInstance(any());
    }

    @Test void canonicalTypedOwnerRuleResolvesCandidatesInsteadOfUsingStaleScalarProjection()
    {
        Map<String,Object> typed=new java.util.HashMap<>(rule());
        typed.put("owner_rule_json","ROLE:999");
        typed.put("definition_json","""
                {"schemaVersion":1,"templateCode":"T","event":{"eventType":"LEAD_ASSIGNED","payloadVersion":1,"condition":{}},
                 "owner":{"config":{"type":"ROLE","roleKey":"legal_reviewer"}},"dod":{"config":{}},"sla":{"config":{}},"ui":{"config":{}},"routing":{"config":{}},"autoActions":[],"decisionRefs":[],"acceptanceRefs":[]}
                """);
        when(mapper.selectTriggerRules("LEAD_ASSIGNED","LEAD")).thenReturn(List.of(typed));
        when(mapper.selectRoleIdByKey("legal_reviewer")).thenReturn(5L);
        TodoOrganizationPort organization=new TodoOrganizationPort(){
            public List<Long> usersForRole(long roleId){return roleId==5?List.of(41L,42L):List.of();}
            public List<Long> usersForDepartment(long id){return List.of();} public List<Long> usersForPost(long id){return List.of();}
            public Optional<Long> businessOwner(String type,Long id){return Optional.empty();} public Optional<Long> supervisor(long id,int levels){return Optional.empty();}
            public Optional<Long> roundRobin(String key,List<Long> candidates){return Optional.empty();} public boolean isAvailable(long id,LocalDateTime at){return true;}
            public Optional<Long> delegateFor(long id,LocalDateTime at){return Optional.empty();} public List<Long> assignmentLevel(int level,String type,Long id){return List.of();}
        };

        new TodoEventService(mapper,new TodoAssignmentResolver(organization)).handle(event());

        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String,Object>> candidate=ArgumentCaptor.forClass(Map.class);
        verify(mapper).insertCandidate(candidate.capture());
        assertEquals("USER",candidate.getValue().get("candidateType"));
        assertEquals(41L,candidate.getValue().get("candidateValue"));
    }

    @Test void canonicalOwnerWithoutEligibleOwnerOrCandidateFailsBeforePersistence()
    {
        Map<String,Object> typed=new java.util.HashMap<>(rule());
        typed.put("definition_json","""
                {"schemaVersion":1,"templateCode":"T","event":{"eventType":"LEAD_ASSIGNED","payloadVersion":1,"condition":{}},
                 "owner":{"config":{"type":"ROLE","operand":5}},"dod":{"config":{}},"sla":{"config":{"calendarCode":"DEFAULT","minutes":30}},
                 "ui":{"config":{}},"routing":{"config":{}},"autoActions":[],"decisionRefs":[],"acceptanceRefs":[]}
                """);
        when(mapper.selectTriggerRules("LEAD_ASSIGNED","LEAD")).thenReturn(List.of(typed));

        TodoException error=assertThrows(TodoException.class,
                ()->new TodoEventService(mapper,new TodoAssignmentResolver(TodoOrganizationPort.legacyCompatible())).handle(event()));

        assertEquals("TODO_OWNER_UNRESOLVED",error.getBusinessCode());
        verify(mapper,never()).insertInstance(any());
        verify(mapper,never()).insertRelation(anyMap());
        verify(mapper,never()).insertSlaRecord(anyMap());
    }

    @Test void malformedCanonicalOwnerDefinitionNeverFallsBackToStaleLegacyProjection()
    {
        Map<String,Object> corrupted=new java.util.HashMap<>(rule());
        corrupted.put("definition_json","{not-json");
        corrupted.put("owner_rule_json","PAYLOAD:ownerId");
        when(mapper.selectTriggerRules("LEAD_ASSIGNED","LEAD")).thenReturn(List.of(corrupted));

        TodoException error=org.junit.jupiter.api.Assertions.assertThrows(TodoException.class,
                ()->new TodoEventService(mapper,new TodoAssignmentResolver()).handle(event()));

        assertEquals("TODO_OWNER_RULE_RESOLUTION_FAILED",error.getBusinessCode());
        verify(mapper,never()).insertInstance(any());
    }

    @Test void malformedCompiledOwnerDefinitionNeverFallsBackToCanonicalOrLegacy()
    {
        Map<String,Object> corrupted=new java.util.HashMap<>(rule());
        corrupted.put("compiled_json","{not-json");
        corrupted.put("definition_json","""
                {"schemaVersion":1,"templateCode":"T","event":{"eventType":"LEAD_ASSIGNED","payloadVersion":1,"condition":{}},
                "owner":{"config":{"type":"PAYLOAD","operand":"ownerId"}},"dod":{"config":{}},"sla":{"config":{}},"ui":{"config":{}},"routing":{"config":{}},"autoActions":[],"decisionRefs":[],"acceptanceRefs":[]}
                """);
        corrupted.put("owner_rule_json","PAYLOAD:ownerId");
        when(mapper.selectTriggerRules("LEAD_ASSIGNED","LEAD")).thenReturn(List.of(corrupted));

        TodoException error=org.junit.jupiter.api.Assertions.assertThrows(TodoException.class,
                ()->new TodoEventService(mapper,new TodoAssignmentResolver()).handle(event()));

        assertEquals("TODO_OWNER_RULE_RESOLUTION_FAILED",error.getBusinessCode());
        verify(mapper,never()).insertInstance(any());
    }

    @Test void nestedStableOwnerReferencesFailClosedWhenRoleIsMissing()
    {
        Map<String,Object> typed=new java.util.HashMap<>(rule());
        typed.put("definition_json","""
                {"schemaVersion":1,"templateCode":"T","event":{"eventType":"LEAD_ASSIGNED","payloadVersion":1,"condition":{}},
                 "owner":{"config":{"type":"PAYLOAD","operand":"ownerId","candidates":[{"type":"ROLE","roleKey":"retired_reviewer"}]}},
                 "dod":{"config":{}},"sla":{"config":{}},"ui":{"config":{}},"routing":{"config":{}},"autoActions":[],"decisionRefs":[],"acceptanceRefs":[]}
                """);
        typed.put("owner_rule_json","PAYLOAD:ownerId");
        when(mapper.selectTriggerRules("LEAD_ASSIGNED","LEAD")).thenReturn(List.of(typed));
        when(mapper.selectRoleIdByKey("retired_reviewer")).thenReturn(null);

        TodoException error=org.junit.jupiter.api.Assertions.assertThrows(TodoException.class,
                ()->new TodoEventService(mapper,new TodoAssignmentResolver()).handle(event()));

        assertEquals("TODO_OWNER_ROLE_KEY_NOT_FOUND",error.getBusinessCode());
        verify(mapper,never()).insertInstance(any());
    }

    @Test void createsTodoWhenNestedConditionTreeMatches()
    {
        Map<String,Object> conditional=new java.util.HashMap<>(rule());
        conditional.put("condition_json","""
                {"$expression":{"version":1,"root":
                    {"type":"AND","conditions":[
                      {"field":"amount","operator":"GTE","value":100},
                      {"type":"OR","conditions":[
                        {"field":"type","operator":"EQ","value":"A"},
                        {"field":"type","operator":"EQ","value":"B"}
                      ]}
                    ]}
                  }
                }
                """);
        when(mapper.selectTriggerRules("LEAD_ASSIGNED","LEAD")).thenReturn(List.of(conditional));
        when(mapper.selectEventCatalog("LEAD_ASSIGNED",1)).thenReturn(catalog());
        TodoEvent matching=new TodoEvent("evt-3","LEAD_ASSIGNED","LEAD",9L,"L-9",Map.of("amount",100,"type","B"));

        List<TodoInstance> result=new TodoEventService(mapper,new TodoAssignmentResolver()).handle(matching);

        assertEquals(1,result.size());
        verify(mapper).insertInstance(any());
    }

    @Test void canonicalConditionWithUndeclaredFieldFailsClosed()
    {
        Map<String,Object> conditional=new java.util.HashMap<>(rule());
        conditional.put("condition_json","{\"$expression\":{\"version\":1,\"root\":{\"field\":\"class.classLoader\",\"operator\":\"EQ\",\"value\":\"x\"}}}");
        when(mapper.selectTriggerRules("LEAD_ASSIGNED","LEAD")).thenReturn(List.of(conditional));
        when(mapper.selectEventCatalog("LEAD_ASSIGNED",1)).thenReturn(catalog());

        List<TodoInstance> result=new TodoEventService(mapper,new TodoAssignmentResolver()).handle(event());

        assertEquals(0,result.size());
        verify(mapper,never()).insertInstance(any());
    }

    @Test void malformedCanonicalEnvelopeFailsClosed()
    {
        Map<String,Object> conditional=new java.util.HashMap<>(rule());
        conditional.put("condition_json","{\"$expression\":{\"version\":2,\"root\":{\"field\":\"ownerId\",\"operator\":\"EQ\",\"value\":8}}}");
        when(mapper.selectTriggerRules("LEAD_ASSIGNED","LEAD")).thenReturn(List.of(conditional));

        List<TodoInstance> result=new TodoEventService(mapper,new TodoAssignmentResolver()).handle(event());

        assertEquals(0,result.size());
        verify(mapper,never()).insertInstance(any());
    }

    @Test void canonicalConditionWithoutActiveCatalogFailsClosed()
    {
        Map<String,Object> conditional=new java.util.HashMap<>(rule());
        conditional.put("condition_json","{\"$expression\":{\"version\":1,\"root\":{\"field\":\"ownerId\",\"operator\":\"EQ\",\"value\":8}}}");
        when(mapper.selectTriggerRules("LEAD_ASSIGNED","LEAD")).thenReturn(List.of(conditional));when(mapper.selectEventCatalog("LEAD_ASSIGNED",1)).thenReturn(null);

        assertThrows(TodoException.class,()->new TodoEventService(mapper,new TodoAssignmentResolver()).handle(event()));
        verify(mapper,never()).insertInstance(any());
    }

    @Test void createsSlaRecordFromTemplateRule()
    {
        Map<String,Object> slaRule=new java.util.HashMap<>(rule());slaRule.put("sla_rule_json","{\"calendarCode\":\"DEFAULT\",\"minutes\":60}");
        when(mapper.selectTriggerRules("LEAD_ASSIGNED","LEAD")).thenReturn(List.of(slaRule));
        when(mapper.selectCalendarByCode("DEFAULT")).thenReturn(Map.of("calendar_id",1L,"work_days","1,2,3,4,5","work_start","09:00:00","work_end","18:00:00","exception_json","{}"));
        new TodoEventService(mapper,new TodoAssignmentResolver()).handle(event());
        verify(mapper).insertSlaRecord(anyMap());
    }

    @Test void snapshotsDodRuleOnCreatedTodo()
    {
        Map<String,Object> version=new java.util.HashMap<>(rule());version.put("dod_rule_json","{\"requiredFields\":[\"contactResult\"]}");
        when(mapper.selectTriggerRules("LEAD_ASSIGNED","LEAD")).thenReturn(List.of(version));
        TodoInstance created=new TodoEventService(mapper,new TodoAssignmentResolver()).handle(event()).get(0);
        assertEquals("{\"requiredFields\":[\"contactResult\"]}",created.getDodSnapshotJson());
    }

    @Test void publishedSnapshotDrivesRuntimeAfterLibraryMutationWithoutLibraryReads()
    {
        String slaSnapshot="{\"calendarCode\":\"DEFAULT\",\"minutes\":45,\"softRemindPercent\":80,\"hardRemindPercent\":100,\"escalatePercent\":150,\"pausePolicy\":{\"pause\":true},\"ruleSnapshots\":[{\"ruleCode\":\"SLA-PUBLISHED\",\"minutes\":45}]}";
        String dodSnapshot="{\"requiredFields\":[\"publishedField\"],\"errorMessages\":{\"publishedField\":\"required\"},\"ruleType\":\"TASK\",\"ruleTypes\":[\"TASK\"],\"ruleSnapshots\":[{\"ruleCode\":\"DOD-PUBLISHED\",\"requiredFields\":[\"publishedField\"]}]}";
        Map<String,Object> version=new java.util.HashMap<>(rule());version.put("status","PUBLISHED");version.put("template_code","PUBLISHED");version.put("payload_version",1);
        version.put("sla_rule_json",slaSnapshot);version.put("dod_rule_json",dodSnapshot);
        version.put("definition_json","""
                {"schemaVersion":1,"templateCode":"PUBLISHED","event":{"eventType":"LEAD_ASSIGNED","payloadVersion":1,"condition":{}},
                 "owner":{"config":{"type":"PAYLOAD","operand":"ownerId"}},"dod":{"config":%s},"sla":{"config":%s},"ui":{"config":{}},"routing":{"config":{}},"autoActions":[],"decisionRefs":[],"acceptanceRefs":[]}
                """.formatted(dodSnapshot,slaSnapshot));
        Map<String,Object> changedSlaLibrary=new java.util.HashMap<>(Map.of("sla_rule_id",8L,"duration_value",999,"rule_code","SLA-CHANGED"));
        Map<String,Object> changedDodLibrary=new java.util.HashMap<>(Map.of("dod_rule_id",11L,"required_fields_json","[\"changed\"]","rule_code","DOD-CHANGED"));
        when(configurationMapper.selectSlaRule(8L)).thenReturn(changedSlaLibrary);when(configurationMapper.selectDodRule(11L)).thenReturn(changedDodLibrary);
        changedSlaLibrary.put("duration_value",1440);changedDodLibrary.put("required_fields_json","[\"changed-again\"]");
        when(mapper.selectTriggerRules("LEAD_ASSIGNED","LEAD")).thenReturn(List.of(version));
        when(mapper.selectCalendarByCode("DEFAULT")).thenReturn(Map.of("calendar_id",1L,"work_days","1,2,3,4,5,6,7","work_start","00:00:00","work_end","23:59:00","exception_json","{}"));

        TodoInstance created=new TodoEventService(mapper,new TodoAssignmentResolver()).handle(event()).get(0);

        assertEquals(dodSnapshot,created.getDodSnapshotJson());
        assertEquals(45L,java.time.Duration.between(created.getCreatedAt(),created.getDueAt()).toMinutes());
        verifyNoInteractions(configurationMapper);
    }

    @Test void snapshotsGraphPositionOnTriggeredRootTodo()
    {
        Map<String,Object> version=new java.util.HashMap<>(rule());
        version.put("compiled_json","""
                {"schemaVersion":1,"templateCode":"T","event":{"eventType":"LEAD_ASSIGNED","payloadVersion":1,"condition":{}},
                 "owner":{"config":{"type":"PAYLOAD","operand":"ownerId"}},
                 "routing":{"config":{"start":"review","nodes":[{"key":"review","type":"TASK","templateVersionId":22},{"key":"end","type":"END"}],
                 "edges":[{"key":"done","from":"review","to":"end"}]}},"autoActions":[],"decisionRefs":[],"acceptanceRefs":[]}
                """);
        version.put("definition_hash","abc123");version.put("ui_schema_json","{}");version.put("sla_rule_json","{}");version.put("payload_version",1);
        when(mapper.selectTriggerRules("LEAD_ASSIGNED","LEAD")).thenReturn(List.of(version));
        doAnswer(invocation->{TodoInstance todo=invocation.getArgument(0);todo.setTodoId(41L);return 1;}).when(mapper).insertInstance(any());

        TodoInstance created=new TodoEventService(mapper,new TodoAssignmentResolver()).handle(event()).get(0);

        assertEquals(41L,created.getRootTodoId());assertEquals("review",created.getRouteNodeKey());
        assertEquals(22L,created.getRouteDefinitionVersionId());
        assertEquals("41:review:LEAD:7:0",created.getOccurrenceKey());assertEquals("abc123",created.getDefinitionHash());
        assertTrue(created.getRouteToken().contains("\"rootTodoId\":41"));
        verify(mapper).updateInitialRouteSnapshot(41L,41L,created.getRouteToken(),created.getOccurrenceKey());
    }

    private Map<String,Object> rule(){return Map.of("template_id",3L,"template_version_id",22L,"template_name","首联","owner_rule_json","ROLE:5");}
    private TodoEvent event(){return new TodoEvent("evt-1","LEAD_ASSIGNED","LEAD",7L,"L-7",Map.of("ownerId",8L));}
    private Map<String,Object> catalog(){return Map.of("status","ACTIVE","payload_schema_json","{\"type\":\"object\",\"properties\":{\"amount\":{\"type\":\"number\"},\"type\":{\"type\":\"string\"},\"ownerId\":{\"type\":\"integer\"}}}");}
}
