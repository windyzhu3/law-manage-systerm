package com.law.todo.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.todo.application.TodoAssignmentResolver;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Autowired;
import com.law.todo.definition.catalog.TodoEventCatalogService;
import com.law.todo.expression.ConditionValidator;
import com.law.todo.expression.ConditionEvaluator;

@ExtendWith(MockitoExtension.class)
class TodoEventServiceTest
{
    @Mock TodoMapper mapper;

    @Test void springConstructorExplicitlyInjectsConditionValidationDependencies() throws Exception
    {
        assertTrue(TodoEventService.class.getConstructor(TodoMapper.class,
                TodoAssignmentResolver.class,ConditionEvaluator.class,
                ConditionValidator.class,TodoEventCatalogService.class)
                .isAnnotationPresent(Autowired.class));
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

    @Test void createsTodoForTrustedLegacyFlatConditionWithoutCatalog()
    {
        Map<String,Object> conditional=new java.util.HashMap<>(rule());
        conditional.put("condition_json","{\"source\":\"ONLINE\",\"priority\":2}");
        when(mapper.selectTriggerRules("LEAD_ASSIGNED","LEAD")).thenReturn(List.of(conditional));
        TodoEvent matching=new TodoEvent("evt-2","LEAD_ASSIGNED","LEAD",8L,"L-8",Map.of("source","ONLINE","priority",2));

        List<TodoInstance> result=new TodoEventService(mapper,new TodoAssignmentResolver()).handle(matching);

        assertEquals(1,result.size());
        verify(mapper,times(1)).insertInstance(any());
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
        when(mapper.selectTriggerRules("LEAD_ASSIGNED","LEAD")).thenReturn(List.of(conditional));

        List<TodoInstance> result=new TodoEventService(mapper,new TodoAssignmentResolver()).handle(event());

        assertEquals(0,result.size());
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

    @Test void snapshotsGraphPositionOnTriggeredRootTodo()
    {
        Map<String,Object> version=new java.util.HashMap<>(rule());
        version.put("compiled_json","""
                {"schemaVersion":1,"templateCode":"T","event":{"eventType":"LEAD_ASSIGNED","payloadVersion":1,"condition":{}},
                 "routing":{"config":{"start":"review","nodes":[{"key":"review","type":"TASK","templateVersionId":22},{"key":"end","type":"END"}],
                 "edges":[{"key":"done","from":"review","to":"end"}]}},"autoActions":[],"decisionRefs":[],"acceptanceRefs":[]}
                """);
        version.put("definition_hash","abc123");version.put("ui_schema_json","{}");version.put("sla_rule_json","{}");version.put("payload_version",1);
        when(mapper.selectTriggerRules("LEAD_ASSIGNED","LEAD")).thenReturn(List.of(version));
        doAnswer(invocation->{TodoInstance todo=invocation.getArgument(0);todo.setTodoId(41L);return 1;}).when(mapper).insertInstance(any());

        TodoInstance created=new TodoEventService(mapper,new TodoAssignmentResolver()).handle(event()).get(0);

        assertEquals(41L,created.getRootTodoId());assertEquals("review",created.getRouteNodeKey());
        assertEquals("41:review:LEAD:7:0",created.getOccurrenceKey());assertEquals("abc123",created.getDefinitionHash());
        assertTrue(created.getRouteToken().contains("\"rootTodoId\":41"));
        verify(mapper).updateInitialRouteSnapshot(41L,41L,created.getRouteToken(),created.getOccurrenceKey());
    }

    private Map<String,Object> rule(){return Map.of("template_id",3L,"template_version_id",22L,"template_name","首联","owner_rule_json","ROLE:5");}
    private TodoEvent event(){return new TodoEvent("evt-1","LEAD_ASSIGNED","LEAD",7L,"L-7",Map.of("ownerId",8L));}
    private Map<String,Object> catalog(){return Map.of("status","ACTIVE","payload_schema_json","{\"type\":\"object\",\"properties\":{\"amount\":{\"type\":\"number\"},\"type\":{\"type\":\"string\"},\"ownerId\":{\"type\":\"integer\"}}}");}
}
