package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.law.todo.application.command.TodoDefinitionCommands.SimulateDefinitionCommand;
import com.law.todo.definition.codec.TodoDefinitionCodec;
import com.law.todo.definition.model.TodoDefinitionDocument;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.spi.TodoOrganizationPort;
import com.law.todo.spi.TodoCompletionHandler;

@ExtendWith(MockitoExtension.class)
class TodoDefinitionSimulationServiceTest
{
    @Mock TodoMapper mapper;

    @BeforeEach void registerEventCatalog()
    {
        org.mockito.Mockito.lenient().when(mapper.selectEventCatalog("LEAD_CREATED",1)).thenReturn(Map.of(
                "event_type","LEAD_CREATED","payload_version",1,"payload_schema_json","{\"type\":\"object\",\"properties\":{\"stage\":{\"type\":\"string\"}}}","status","ACTIVE"));
    }

    @Test void simulation_uses_compiled_snapshot_and_performs_no_writes()
    {
        TodoDefinitionDocument definition=definition(Map.of("type","PAYLOAD","field","ownerId"));
        String compiled=new TodoDefinitionCodec().canonicalJson(definition);
        when(mapper.selectTemplateVersionById(9L)).thenReturn(version(compiled));
        when(mapper.selectCalendarByCode("DEFAULT")).thenReturn(Map.of(
                "work_days","1,2,3,4,5","work_start","09:00:00","work_end","18:00:00",
                "exception_json","{}"));

        var result=new TodoDefinitionSimulationService(mapper,new TodoAssignmentResolver()).simulate(9L,
                new SimulateDefinitionCommand(Map.of("ownerId",7L,"stage","READY"),"LEAD",3L,
                        LocalDateTime.of(2026,7,17,9,0)));

        assertEquals("MATCHED",result.trigger().status());
        assertEquals(7L,result.owner().ownerId());
        assertEquals("TASK",result.routes().get(0).nodeType());
        assertFalse(result.definitionHash().isBlank());
        verify(mapper,never()).insertInstance(org.mockito.ArgumentMatchers.any());
        verify(mapper,never()).insertStationNotification(org.mockito.ArgumentMatchers.anyMap());
        verify(mapper,never()).insertActionIfAbsent(org.mockito.ArgumentMatchers.anyMap());
    }

    @Test void unknown_owner_context_and_handlers_are_explicit_and_deterministic()
    {
        TodoDefinitionDocument definition=definition(Map.of("type","PAYLOAD","field","missingOwner"));
        String compiled=new TodoDefinitionCodec().canonicalJson(definition);
        when(mapper.selectTemplateVersionById(9L)).thenReturn(version(compiled));
        when(mapper.selectCalendarByCode("DEFAULT")).thenReturn(Map.of(
                "work_days","1,2,3,4,5","work_start","09:00:00","work_end","18:00:00",
                "exception_json","{}"));

        var result=new TodoDefinitionSimulationService(mapper,new TodoAssignmentResolver()).simulate(9L,
                new SimulateDefinitionCommand(Map.of("stage","READY"),"LEAD",3L,
                        LocalDateTime.of(2026,7,17,9,0)));

        assertEquals("UNRESOLVED",result.owner().status());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.code().equals("TODO_SIMULATION_OWNER_UNRESOLVED")));
        assertEquals(result.issues().stream().map(i->i.code()+i.path()).toList(),
                result.issues().stream().map(i->i.code()+i.path()).sorted().toList());
    }

    @Test void simulation_round_robin_never_advances_runtime_cursor()
    {
        TodoDefinitionDocument definition=definition(Map.of("type","ROUND_ROBIN","strategyKey","lead",
                "source",Map.of("type","USER","value",11L)));
        String compiled=new TodoDefinitionCodec().canonicalJson(definition);
        when(mapper.selectTemplateVersionById(9L)).thenReturn(version(compiled));
        when(mapper.selectCalendarByCode("DEFAULT")).thenReturn(Map.of(
                "work_days","1,2,3,4,5","work_start","09:00:00","work_end","18:00:00","exception_json","{}"));
        TodoOrganizationPort port=new TodoOrganizationPort()
        {
            public List<Long> usersForRole(long id){return List.of();}public List<Long> usersForDepartment(long id){return List.of();}public List<Long> usersForPost(long id){return List.of();}
            public java.util.Optional<Long> businessOwner(String t,Long id){return java.util.Optional.empty();}public java.util.Optional<Long> supervisor(long id,int levels){return java.util.Optional.empty();}
            public java.util.Optional<Long> roundRobin(String key,List<Long> users){throw new AssertionError("cursor advanced");}
            public boolean isAvailable(long id,LocalDateTime at){return true;}public java.util.Optional<Long> delegateFor(long id,LocalDateTime at){return java.util.Optional.empty();}
            public List<Long> assignmentLevel(int level,String type,Long id){return List.of();}
        };

        var result=new TodoDefinitionSimulationService(mapper,new TodoAssignmentResolver(port)).simulate(9L,
                new SimulateDefinitionCommand(Map.of("stage","READY"),"LEAD",3L,LocalDateTime.of(2026,7,17,9,0)));

        assertEquals(11L,result.owner().ownerId());
        assertTrue(result.owner().trace().stream().anyMatch(line->line.endsWith(":preview")));
    }

    @Test void first_uncompleted_task_stops_before_fork_join_and_downstream()
    {
        TodoDefinitionDocument base=definition(Map.of("type","USER","value",7L));
        var graph=new TodoDefinitionDocument.RoutingGraph(Map.of(
                "start","start",
                "nodes",List.of(
                        Map.of("key","start","type","TASK","templateVersionId",9L),
                        Map.of("key","fork","type","FORK"),
                        Map.of("key","a","type","TASK","templateVersionId",10L),
                        Map.of("key","b","type","TASK","templateVersionId",11L),
                        Map.of("key","join","type","JOIN","joinMode","ALL","branches",List.of("A","B")),
                        Map.of("key","after","type","TASK","templateVersionId",12L),Map.of("key","end","type","END")),
                "edges",List.of(
                        Map.of("key","s-f","from","start","to","fork"),
                        Map.of("key","f-a","from","fork","to","a","branchKey","A"),
                        Map.of("key","f-b","from","fork","to","b","branchKey","B"),
                        Map.of("key","a-j","from","a","to","join"),Map.of("key","b-j","from","b","to","join"),
                        Map.of("key","j-a","from","join","to","after"),Map.of("key","a-e","from","after","to","end"))));
        TodoDefinitionDocument definition=new TodoDefinitionDocument(base.schemaVersion(),base.templateCode(),base.event(),base.owner(),base.dod(),base.sla(),base.ui(),graph,base.autoActions(),base.decisionRefs(),base.acceptanceRefs());
        String compiled=new TodoDefinitionCodec().canonicalJson(definition);when(mapper.selectTemplateVersionById(9L)).thenReturn(version(compiled));
        when(mapper.selectTemplateVersionById(10L)).thenReturn(publishedVersion(10L));when(mapper.selectTemplateVersionById(11L)).thenReturn(publishedVersion(11L));when(mapper.selectTemplateVersionById(12L)).thenReturn(publishedVersion(12L));
        when(mapper.selectCalendarByCode("DEFAULT")).thenReturn(Map.of("work_days","1,2,3,4,5","work_start","09:00:00","work_end","18:00:00","exception_json","{}"));

        var result=new TodoDefinitionSimulationService(mapper,new TodoAssignmentResolver()).simulate(9L,
                new SimulateDefinitionCommand(Map.of("stage","READY"),"LEAD",3L,LocalDateTime.of(2026,7,17,9,0)));

        assertEquals(List.of("start"),result.routes().stream().map(route->route.nodeKey()).toList());
    }

    @Test void fork_tasks_stop_tokens_before_join_and_downstream_without_completion_samples()
    {
        TodoDefinitionDocument base=definition(Map.of("type","USER","value",7L));
        var graph=new TodoDefinitionDocument.RoutingGraph(Map.of(
                "start","root",
                "nodes",List.of(
                        Map.of("key","root","type","TASK","templateVersionId",9L),
                        Map.of("key","fork","type","FORK"),
                        Map.of("key","a","type","TASK","templateVersionId",10L),
                        Map.of("key","b","type","TASK","templateVersionId",11L),
                        Map.of("key","join","type","JOIN","joinMode","ALL","branches",List.of("A","B")),
                        Map.of("key","after","type","TASK","templateVersionId",12L),Map.of("key","end","type","END")),
                "edges",List.of(
                        Map.of("key","r-f","from","root","to","fork"),
                        Map.of("key","f-a","from","fork","to","a","branchKey","A"),
                        Map.of("key","f-b","from","fork","to","b","branchKey","B"),
                        Map.of("key","a-j","from","a","to","join"),Map.of("key","b-j","from","b","to","join"),
                        Map.of("key","j-a","from","join","to","after"),Map.of("key","a-e","from","after","to","end"))));
        TodoDefinitionDocument value=new TodoDefinitionDocument(base.schemaVersion(),base.templateCode(),base.event(),base.owner(),base.dod(),base.sla(),base.ui(),graph,base.autoActions(),base.decisionRefs(),base.acceptanceRefs());
        String compiled=new TodoDefinitionCodec().canonicalJson(value);when(mapper.selectTemplateVersionById(9L)).thenReturn(version(compiled));
        when(mapper.selectTemplateVersionById(10L)).thenReturn(publishedVersion(10L));when(mapper.selectTemplateVersionById(11L)).thenReturn(publishedVersion(11L));when(mapper.selectTemplateVersionById(12L)).thenReturn(publishedVersion(12L));
        when(mapper.selectCalendarByCode("DEFAULT")).thenReturn(Map.of("work_days","1,2,3,4,5","work_start","09:00:00","work_end","18:00:00","exception_json","{}"));

        var result=new TodoDefinitionSimulationService(mapper,new TodoAssignmentResolver()).simulate(9L,
                new SimulateDefinitionCommand(Map.of("stage","READY"),"LEAD",3L,LocalDateTime.of(2026,7,17,9,0)));

        assertEquals(List.of("root"),result.routes().stream().map(route->route.nodeKey()).toList());
        assertFalse(result.routes().stream().anyMatch(route->route.nodeKey().equals("join")||route.nodeKey().equals("after")));
    }

    @Test void invalid_compiled_graph_becomes_sorted_issues_instead_of_throwing()
    {
        TodoDefinitionDocument base=definition(Map.of("type","USER","value",7L));
        TodoDefinitionDocument value=new TodoDefinitionDocument(base.schemaVersion(),base.templateCode(),base.event(),base.owner(),base.dod(),base.sla(),base.ui(),
                new TodoDefinitionDocument.RoutingGraph(Map.of("start","missing","nodes",List.of(),"edges",List.of())),
                base.autoActions(),base.decisionRefs(),base.acceptanceRefs());
        String compiled=new TodoDefinitionCodec().canonicalJson(value);when(mapper.selectTemplateVersionById(9L)).thenReturn(version(compiled));

        var result=new TodoDefinitionSimulationService(mapper,new TodoAssignmentResolver()).simulate(9L,
                new SimulateDefinitionCommand(Map.of("stage","READY"),"LEAD",3L,LocalDateTime.of(2026,7,17,9,0)));

        assertTrue(result.issues().stream().anyMatch(issue->issue.code().startsWith("TODO_ROUTE_")));
        assertTrue(result.routes().isEmpty());
        assertEquals(result.issues().stream().map(i->i.code()+i.path()).sorted().toList(),result.issues().stream().map(i->i.code()+i.path()).toList());
    }

    @Test void missing_definition_sections_become_issues_without_null_dereference()
    {
        String compiled="{\"schemaVersion\":1,\"templateCode\":\"T\",\"event\":{\"eventType\":\"LEAD_CREATED\",\"payloadVersion\":1,\"condition\":{}},\"autoActions\":[],\"decisionRefs\":[],\"acceptanceRefs\":[]}";
        Map<String,Object> row=version(compiled);row.put("definition_hash",TodoDefinitionSimulationService.sha256(new TodoDefinitionCodec().canonicalJson(new TodoDefinitionCodec().read(compiled))));
        when(mapper.selectTemplateVersionById(9L)).thenReturn(row);

        var result=new TodoDefinitionSimulationService(mapper,new TodoAssignmentResolver()).simulate(9L,
                new SimulateDefinitionCommand(Map.of("stage","READY"),"LEAD",3L,LocalDateTime.of(2026,7,17,9,0)));

        assertTrue(result.issues().stream().anyMatch(issue->issue.code().equals("TODO_DEFINITION_SECTION_REQUIRED")));
        assertTrue(result.routes().isEmpty());
    }

    @Test void unsupported_version_status_is_ineligible_without_traversing_definition()
    {
        String compiled=new TodoDefinitionCodec().canonicalJson(definition(Map.of("type","USER","value",7L)));
        Map<String,Object> row=version(compiled);row.put("status","DISABLED");when(mapper.selectTemplateVersionById(9L)).thenReturn(row);

        var result=new TodoDefinitionSimulationService(mapper,new TodoAssignmentResolver()).simulate(9L,
                new SimulateDefinitionCommand(Map.of("stage","READY"),"LEAD",3L,LocalDateTime.of(2026,7,17,9,0)));

        assertTrue(result.issues().stream().anyMatch(issue->issue.code().equals("TODO_SIMULATION_VERSION_STATUS_INELIGIBLE")));
        assertTrue(result.routes().isEmpty());
    }

    @Test void unmatched_trigger_cannot_claim_that_runtime_work_would_be_created()
    {
        TodoDefinitionDocument definition=definition(Map.of("type","USER","value",7L));String compiled=new TodoDefinitionCodec().canonicalJson(definition);
        when(mapper.selectTemplateVersionById(9L)).thenReturn(version(compiled));

        var result=new TodoDefinitionSimulationService(mapper,new TodoAssignmentResolver()).simulate(9L,
                new SimulateDefinitionCommand(Map.of("stage","OTHER"),"LEAD",3L,LocalDateTime.of(2026,7,17,9,0)));

        assertEquals("NOT_MATCHED",result.trigger().status());assertEquals("SKIPPED",result.owner().status());assertTrue(result.routes().isEmpty());
        assertTrue(result.issues().stream().anyMatch(issue->issue.code().equals("TODO_SIMULATION_TRIGGER_NOT_MATCHED")));
    }

    @Test void controlled_actions_show_precondition_and_governed_schedule_without_execution()
    {
        TodoDefinitionDocument base=definition(Map.of("type","USER","value",7L));
        Map<String,Object> condition=Map.of("$expression",Map.of("version",1,"root",Map.of("field","stage","operator","EQ","value","READY")));
        TodoDefinitionDocument definition=new TodoDefinitionDocument(base.schemaVersion(),base.templateCode(),base.event(),base.owner(),base.dod(),base.sla(),base.ui(),base.routing(),
                List.of(new TodoDefinitionDocument.AutoActionRule(Map.of("ruleKey","remind","actionType","ESCALATE","capability","ESCALATE","triggerAt","SLA_80","precondition",condition))),base.decisionRefs(),base.acceptanceRefs());
        String compiled=new TodoDefinitionCodec().canonicalJson(definition);when(mapper.selectTemplateVersionById(9L)).thenReturn(version(compiled));
        when(mapper.selectCalendarByCode("DEFAULT")).thenReturn(Map.of("work_days","1,2,3,4,5","work_start","09:00:00","work_end","18:00:00","exception_json","{}"));

        var result=new TodoDefinitionSimulationService(mapper,new TodoAssignmentResolver()).simulate(9L,
                new SimulateDefinitionCommand(Map.of("stage","READY"),"LEAD",3L,LocalDateTime.of(2026,7,17,9,0)));

        assertEquals("WOULD_SCHEDULE",result.autoActions().get(0).status());assertEquals(result.sla().remind80At(),result.autoActions().get(0).scheduledAt());
    }

    @Test void governed_action_is_unschedulable_when_sla_calendar_cannot_be_resolved()
    {
        TodoDefinitionDocument base=definition(Map.of("type","USER","value",7L));
        TodoDefinitionDocument value=new TodoDefinitionDocument(base.schemaVersion(),base.templateCode(),base.event(),base.owner(),base.dod(),base.sla(),base.ui(),base.routing(),
                List.of(new TodoDefinitionDocument.AutoActionRule(Map.of("ruleKey","remind","actionType","ESCALATE","capability","ESCALATE","triggerAt","SLA_80"))),base.decisionRefs(),base.acceptanceRefs());
        String compiled=new TodoDefinitionCodec().canonicalJson(value);when(mapper.selectTemplateVersionById(9L)).thenReturn(version(compiled));
        when(mapper.selectCalendarByCode("DEFAULT")).thenReturn(null);

        var result=new TodoDefinitionSimulationService(mapper,new TodoAssignmentResolver()).simulate(9L,
                new SimulateDefinitionCommand(Map.of("stage","READY"),"LEAD",3L,LocalDateTime.of(2026,7,17,9,0)));

        assertEquals("UNSCHEDULABLE",result.autoActions().get(0).status());
        assertEquals(null,result.autoActions().get(0).scheduledAt());
        assertTrue(result.issues().stream().anyMatch(issue->issue.code().equals("TODO_SIMULATION_AUTO_ACTION_UNSCHEDULABLE")));
    }

    @Test void registered_completion_handlers_are_described_but_never_invoked()
    {
        TodoDefinitionDocument definition=definition(Map.of("type","USER","value",7L));String compiled=new TodoDefinitionCodec().canonicalJson(definition);
        when(mapper.selectTemplateVersionById(9L)).thenReturn(version(compiled));when(mapper.selectCalendarByCode("DEFAULT")).thenReturn(Map.of("work_days","1,2,3,4,5","work_start","09:00:00","work_end","18:00:00","exception_json","{}"));
        TodoCompletionHandler handler=org.mockito.Mockito.mock(TodoCompletionHandler.class);when(handler.catalogCode()).thenReturn("BUSINESS_HANDLER");when(handler.simulationDescription()).thenReturn("not simulatable");

        var result=new TodoDefinitionSimulationService(mapper,new TodoAssignmentResolver(),List.of(handler)).simulate(9L,
                new SimulateDefinitionCommand(Map.of("stage","READY"),"LEAD",3L,LocalDateTime.of(2026,7,17,9,0)));

        assertEquals("NOT_EXECUTED",result.handlers().get(0).status());
        verify(handler,never()).supports(org.mockito.ArgumentMatchers.any());
        verify(handler,never()).complete(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyMap(),org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyString());
    }

    private Map<String,Object> version(String compiled)
    {
        Map<String,Object> row=new HashMap<>();
        row.put("version_id",9L);row.put("status","PUBLISHED");row.put("compiled_json",compiled);
        row.put("definition_hash",TodoDefinitionSimulationService.sha256(compiled));
        return row;
    }

    private Map<String,Object> publishedVersion(long id){return Map.of("version_id",id,"status","PUBLISHED");}

    private TodoDefinitionDocument definition(Map<String,Object> owner)
    {
        return new TodoDefinitionDocument(1,"TD-001",
                new TodoDefinitionDocument.EventRule("LEAD_CREATED",1,Map.of("stage","READY")),
                new TodoDefinitionDocument.OwnerRule(owner),
                new TodoDefinitionDocument.DodRule(Map.of("requiredFields",List.of("summary"))),
                new TodoDefinitionDocument.SlaRule(Map.of("calendarCode","DEFAULT","minutes",60)),
                new TodoDefinitionDocument.UiSchema(Map.of("fields",List.of(Map.of("key","summary","type","text")))),
                new TodoDefinitionDocument.RoutingGraph(Map.of(
                        "start","task","nodes",List.of(Map.of("key","task","type","TASK","templateVersionId",9L),Map.of("key","end","type","END")),
                        "edges",List.of(Map.of("key","done","from","task","to","end")))),
                List.of(),List.of(),List.of());
    }
}
