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
import com.law.todo.application.command.TodoDefinitionCommands.VirtualTaskCompletionSample;
import com.law.todo.definition.codec.TodoDefinitionCodec;
import com.law.todo.definition.catalog.TodoDecisionService;
import com.law.todo.definition.catalog.TodoEventCatalogService;
import com.law.todo.definition.compiler.TodoDefinitionCompiler;
import com.law.todo.definition.model.TodoDefinitionDocument;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.spi.TodoOrganizationPort;
import com.law.todo.spi.TodoCompletionHandler;
import com.law.todo.spi.TodoAutoActionCapability;
import com.law.todo.spi.TodoAutoActionCapabilityRegistry;

@ExtendWith(MockitoExtension.class)
class TodoDefinitionSimulationServiceTest
{
    @Mock TodoMapper mapper;

    @BeforeEach void registerEventCatalog()
    {
        org.mockito.Mockito.lenient().when(mapper.selectEventCatalog("LEAD_CREATED",1)).thenReturn(Map.of(
                "event_type","LEAD_CREATED","payload_version",1,"business_object_type","LEAD",
                "payload_schema_json","{\"type\":\"object\",\"properties\":{\"stage\":{\"type\":\"string\"}}}","status","ACTIVE"));
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
        assertEquals("PLANNED",result.sla().status());
        assertEquals("TASK",result.routes().get(0).nodeType());
        assertFalse(result.definitionHash().isBlank());
        verify(mapper,never()).insertInstance(org.mockito.ArgumentMatchers.any());
        verify(mapper,never()).insertStationNotification(org.mockito.ArgumentMatchers.anyMap());
        verify(mapper,never()).insertActionIfAbsent(org.mockito.ArgumentMatchers.anyMap());
    }

    @Test void expectedEventTypeIsCheckedAgainstTheSameCompiledSnapshotAsSimulation()
    {
        String compiled=new TodoDefinitionCodec().canonicalJson(definition(Map.of("type","USER","value",7L)));
        Map<String,Object> row=version(compiled);row.put("status","BLOCKED");when(mapper.selectTemplateVersionById(9L)).thenReturn(row);

        com.law.todo.domain.TodoException error=org.junit.jupiter.api.Assertions.assertThrows(com.law.todo.domain.TodoException.class,
                ()->new TodoDefinitionSimulationService(mapper,new TodoAssignmentResolver()).simulate(9L,
                        new SimulateDefinitionCommand(Map.of("stage","READY"),"LEAD",3L,LocalDateTime.of(2026,7,17,9,0)),"CONTRACT_CREATED"));

        assertEquals("TODO_SIMULATION_EVENT_TYPE_MISMATCH",error.getBusinessCode());
        verify(mapper,never()).insertInstance(org.mockito.ArgumentMatchers.any());
    }

    @Test void expectedPayloadVersionMustMatchTheDefinitionAndActiveCatalog()
    {
        String compiled=new TodoDefinitionCodec().canonicalJson(definition(Map.of("type","USER","value",7L)));
        when(mapper.selectTemplateVersionById(9L)).thenReturn(version(compiled));

        com.law.todo.domain.TodoException error=org.junit.jupiter.api.Assertions.assertThrows(com.law.todo.domain.TodoException.class,
                ()->new TodoDefinitionSimulationService(mapper,new TodoAssignmentResolver()).simulate(9L,
                        new SimulateDefinitionCommand(Map.of("stage","READY"),"LEAD",3L,LocalDateTime.of(2026,7,17,9,0)),
                        "LEAD_CREATED",2,"LEAD"));

        assertEquals("TODO_SIMULATION_PAYLOAD_VERSION_MISMATCH",error.getBusinessCode());
    }

    @Test void expectedBusinessTypeMustMatchTheTargetTemplateAndEventCatalog()
    {
        String compiled=new TodoDefinitionCodec().canonicalJson(definition(Map.of("type","USER","value",7L)));
        when(mapper.selectTemplateVersionById(9L)).thenReturn(version(compiled));

        com.law.todo.domain.TodoException error=org.junit.jupiter.api.Assertions.assertThrows(com.law.todo.domain.TodoException.class,
                ()->new TodoDefinitionSimulationService(mapper,new TodoAssignmentResolver()).simulate(9L,
                        new SimulateDefinitionCommand(Map.of("stage","READY"),"CONTRACT",3L,LocalDateTime.of(2026,7,17,9,0)),
                        "LEAD_CREATED",1,"CONTRACT"));

        assertEquals("TODO_SIMULATION_BUSINESS_TYPE_MISMATCH",error.getBusinessCode());
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

    @Test void window_scheduled_sla_is_simulatable_without_a_fake_scalar_duration()
    {
        TodoDefinitionDocument base=definition(Map.of("type","USER","value",7L));
        TodoDefinitionDocument scheduled=new TodoDefinitionDocument(base.schemaVersion(),base.templateCode(),
                base.event(),base.owner(),base.dod(),new TodoDefinitionDocument.SlaRule(Map.of(
                        "calendarCode","DEFAULT","schedule",Map.of("windows",List.of(
                                Map.of("windowCode","T0","dayOffset",0,"startOffsetMinutes",0,
                                        "durationMinutes",120,"maxAttempts",3),
                                Map.of("windowCode","T1_AM","dayOffset",1,"startTime","09:00:00",
                                        "endTime","11:00:00","maxAttempts",1))))),
                base.ui(),base.routing(),base.autoActions(),base.decisionRefs(),base.acceptanceRefs());
        when(mapper.selectTemplateVersionById(9L)).thenReturn(version(new TodoDefinitionCodec().canonicalJson(scheduled)));
        when(mapper.selectCalendarByCode("DEFAULT")).thenReturn(calendar());

        var result=new TodoDefinitionSimulationService(mapper,new TodoAssignmentResolver()).simulate(9L,
                new SimulateDefinitionCommand(Map.of("stage","READY"),"LEAD",3L,
                        LocalDateTime.of(2026,7,17,9,0)));

        assertEquals("WINDOW_SCHEDULED",result.sla().status());
        assertFalse(result.issues().stream().anyMatch(issue->
                "TODO_SIMULATION_SLA_DURATION_UNKNOWN".equals(issue.code())));
    }

    @Test void structurally_invalid_schedule_windows_fail_simulation()
    {
        TodoDefinitionDocument base=definition(Map.of("type","USER","value",7L));
        TodoDefinitionDocument malformed=new TodoDefinitionDocument(base.schemaVersion(),base.templateCode(),
                base.event(),base.owner(),base.dod(),new TodoDefinitionDocument.SlaRule(Map.of(
                        "calendarCode","DEFAULT","schedule",Map.of("windows",List.of(Map.of())))),
                base.ui(),base.routing(),base.autoActions(),base.decisionRefs(),base.acceptanceRefs());
        when(mapper.selectTemplateVersionById(9L)).thenReturn(version(new TodoDefinitionCodec().canonicalJson(malformed)));
        when(mapper.selectCalendarByCode("DEFAULT")).thenReturn(calendar());

        var result=new TodoDefinitionSimulationService(mapper,new TodoAssignmentResolver()).simulate(9L,
                new SimulateDefinitionCommand(Map.of("stage","READY"),"LEAD",3L,
                        LocalDateTime.of(2026,7,17,9,0)));

        assertEquals("UNKNOWN",result.sla().status());
        assertTrue(result.issues().stream().anyMatch(issue->
                "TODO_SIMULATION_SCHEDULE_WINDOWS_INVALID".equals(issue.code())));
    }

    @Test void every_present_invalid_or_mixed_schedule_fails_simulation_instead_of_using_scalar_fallback()
    {
        TodoDefinitionDocument base=definition(Map.of("type","USER","value",7L));
        List<Map<String,Object>> invalid=List.of(
                Map.of("calendarCode","DEFAULT","minutes",60,"schedule",Map.of(
                        "windows",List.of(Map.of()))),
                Map.of("calendarCode","DEFAULT","schedule",Map.of("windows","T0")),
                Map.of("calendarCode","DEFAULT","schedule",Map.of("windows",List.of())),
                Map.of("calendarCode","DEFAULT","schedule",List.of("T0")));
        when(mapper.selectCalendarByCode("DEFAULT")).thenReturn(calendar());
        for(Map<String,Object> sla:invalid)
        {
            TodoDefinitionDocument configured=new TodoDefinitionDocument(base.schemaVersion(),base.templateCode(),
                    base.event(),base.owner(),base.dod(),new TodoDefinitionDocument.SlaRule(sla),
                    base.ui(),base.routing(),base.autoActions(),base.decisionRefs(),base.acceptanceRefs());
            when(mapper.selectTemplateVersionById(9L)).thenReturn(
                    version(new TodoDefinitionCodec().canonicalJson(configured)));

            var result=new TodoDefinitionSimulationService(mapper,new TodoAssignmentResolver()).simulate(9L,
                    new SimulateDefinitionCommand(Map.of("stage","READY"),"LEAD",3L,
                            LocalDateTime.of(2026,7,17,9,0)));

            assertEquals("UNKNOWN",result.sla().status());
            assertTrue(result.issues().stream().anyMatch(issue->
                    "TODO_SIMULATION_SCHEDULE_WINDOWS_INVALID".equals(issue.code())));
        }
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
        assertEquals("PENDING_COMPLETION",result.routes().get(0).status());
        assertTrue(result.routes().get(0).trace().contains("task:would-create"));
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

    @Test void virtual_task_completion_merges_payload_and_time_before_decision_routing()
    {
        TodoDefinitionDocument base=definition(Map.of("type","USER","value",7L));
        Map<String,Object> ready=Map.of("$expression",Map.of("version",1,"root",Map.of("field","route","operator","EQ","value","NEXT")));
        var graph=new TodoDefinitionDocument.RoutingGraph(Map.of(
                "start","start","nodes",List.of(
                        Map.of("key","start","type","TASK","templateVersionId",9L),Map.of("key","decision","type","DECISION"),
                        Map.of("key","next","type","TASK","templateVersionId",10L),Map.of("key","end","type","END")),
                "edges",List.of(Map.of("key","s-d","from","start","to","decision"),
                        Map.of("key","d-n","from","decision","to","next","condition",ready,"priority",10),
                        Map.of("key","d-e","from","decision","to","end","default",true),Map.of("key","n-e","from","next","to","end"))));
        TodoDefinitionDocument value=withRouting(base,graph);String compiled=new TodoDefinitionCodec().canonicalJson(value);
        when(mapper.selectTemplateVersionById(9L)).thenReturn(version(compiled));when(mapper.selectTemplateVersionById(10L)).thenReturn(publishedVersion(10L));
        when(mapper.selectCalendarByCode("DEFAULT")).thenReturn(calendar());
        LocalDateTime completed=LocalDateTime.of(2026,7,17,10,30);

        var result=new TodoDefinitionSimulationService(mapper,new TodoAssignmentResolver()).simulate(9L,
                new SimulateDefinitionCommand(Map.of("stage","READY"),"LEAD",3L,LocalDateTime.of(2026,7,17,9,0),
                        List.of(new VirtualTaskCompletionSample("start",0,Map.of("route","NEXT"),completed))));

        assertEquals(List.of("start","decision","next"),result.routes().stream().map(route->route.nodeKey()).toList());
        assertEquals("VIRTUAL_COMPLETED",result.routes().get(0).status());
        assertTrue(result.routes().get(0).trace().contains("task:completed-at:"+completed));
        assertEquals("PENDING_COMPLETION",result.routes().get(2).status());
    }

    @Test void virtual_task_completions_advance_fork_all_join_without_inventing_uncompleted_work()
    {
        TodoDefinitionDocument base=definition(Map.of("type","USER","value",7L));
        var graph=new TodoDefinitionDocument.RoutingGraph(Map.of(
                "start","root","nodes",List.of(
                        Map.of("key","root","type","TASK","templateVersionId",9L),Map.of("key","fork","type","FORK"),
                        Map.of("key","a","type","TASK","templateVersionId",10L),Map.of("key","b","type","TASK","templateVersionId",11L),
                        Map.of("key","join","type","JOIN","joinMode","ALL","branches",List.of("A","B")),
                        Map.of("key","after","type","TASK","templateVersionId",12L),Map.of("key","end","type","END")),
                "edges",List.of(Map.of("key","r-f","from","root","to","fork"),
                        Map.of("key","f-a","from","fork","to","a","branchKey","A"),Map.of("key","f-b","from","fork","to","b","branchKey","B"),
                        Map.of("key","a-j","from","a","to","join"),Map.of("key","b-j","from","b","to","join"),
                        Map.of("key","j-a","from","join","to","after"),Map.of("key","a-e","from","after","to","end"))));
        TodoDefinitionDocument value=withRouting(base,graph);String compiled=new TodoDefinitionCodec().canonicalJson(value);
        when(mapper.selectTemplateVersionById(9L)).thenReturn(version(compiled));when(mapper.selectTemplateVersionById(10L)).thenReturn(publishedVersion(10L));
        when(mapper.selectTemplateVersionById(11L)).thenReturn(publishedVersion(11L));when(mapper.selectTemplateVersionById(12L)).thenReturn(publishedVersion(12L));
        when(mapper.selectCalendarByCode("DEFAULT")).thenReturn(calendar());LocalDateTime at=LocalDateTime.of(2026,7,17,10,0);

        var result=new TodoDefinitionSimulationService(mapper,new TodoAssignmentResolver()).simulate(9L,
                new SimulateDefinitionCommand(Map.of("stage","READY"),"LEAD",3L,LocalDateTime.of(2026,7,17,9,0),List.of(
                        sample("root",at),sample("a",at.plusMinutes(10)),sample("b",at.plusMinutes(20)))));

        assertEquals(3,result.routes().stream().filter(route->"VIRTUAL_COMPLETED".equals(route.status())).count());
        assertTrue(result.routes().stream().anyMatch(route->route.nodeKey().equals("join")&&route.status().equals("ADVANCED")));
        assertEquals("PENDING_COMPLETION",result.routes().stream().filter(route->route.nodeKey().equals("after")).findFirst().orElseThrow().status());
    }

    @Test void fork_branches_evaluate_from_the_same_prefork_payload_and_merge_only_at_join()
    {
        TodoDefinitionDocument base=definition(Map.of("type","USER","value",7L));
        Map<String,Object> initial=Map.of("$expression",Map.of("version",1,"root",Map.of("field","flag","operator","EQ","value","INITIAL")));
        var graph=new TodoDefinitionDocument.RoutingGraph(Map.of(
                "start","root","nodes",List.of(Map.of("key","root","type","TASK","templateVersionId",9L),Map.of("key","fork","type","FORK"),
                        Map.of("key","a","type","TASK","templateVersionId",10L),Map.of("key","b-decision","type","DECISION"),
                        Map.of("key","b","type","TASK","templateVersionId",11L),Map.of("key","b-other","type","TASK","templateVersionId",13L),
                        Map.of("key","join","type","JOIN","joinMode","ALL","branches",List.of("A","B")),Map.of("key","end","type","END")),
                "edges",List.of(Map.of("key","r-f","from","root","to","fork"),Map.of("key","f-a","from","fork","to","a","branchKey","A"),
                        Map.of("key","f-bd","from","fork","to","b-decision","branchKey","B"),Map.of("key","bd-b","from","b-decision","to","b","condition",initial),
                        Map.of("key","bd-o","from","b-decision","to","b-other","default",true),Map.of("key","a-j","from","a","to","join"),
                        Map.of("key","b-j","from","b","to","join"),Map.of("key","o-j","from","b-other","to","join"),Map.of("key","j-e","from","join","to","end"))));
        TodoDefinitionDocument value=withRouting(base,graph);String compiled=new TodoDefinitionCodec().canonicalJson(value);
        when(mapper.selectTemplateVersionById(9L)).thenReturn(version(compiled));when(mapper.selectTemplateVersionById(10L)).thenReturn(publishedVersion(10L));
        when(mapper.selectTemplateVersionById(11L)).thenReturn(publishedVersion(11L));when(mapper.selectTemplateVersionById(13L)).thenReturn(publishedVersion(13L));
        when(mapper.selectCalendarByCode("DEFAULT")).thenReturn(calendar());
        LocalDateTime at=LocalDateTime.of(2026,7,17,10,0);

        var result=new TodoDefinitionSimulationService(mapper,new TodoAssignmentResolver()).simulate(9L,
                new SimulateDefinitionCommand(Map.of("stage","READY","flag","INITIAL"),"LEAD",3L,LocalDateTime.of(2026,7,17,9,0),List.of(
                        sample("root",at),new VirtualTaskCompletionSample("a",0,Map.of("flag","MUTATED_BY_A"),at.plusMinutes(10)),sample("b",at.plusMinutes(20)))));

        assertTrue(result.routes().stream().anyMatch(route->route.nodeKey().equals("b")&&route.status().equals("VIRTUAL_COMPLETED")),
                ()->"routes="+result.routes()+", issues="+result.issues());
        assertTrue(result.routes().stream().anyMatch(route->route.nodeKey().equals("join")&&route.status().equals("ADVANCED")
                && at.plusMinutes(20).equals(route.effectiveAt())));
    }

    @Test void loop_requires_an_explicit_completion_sample_for_each_task_occurrence()
    {
        TodoDefinitionDocument base=definition(Map.of("type","USER","value",7L));
        var graph=new TodoDefinitionDocument.RoutingGraph(Map.of(
                "start","root","nodes",List.of(Map.of("key","root","type","TASK","templateVersionId",9L),
                        Map.of("key","loop","type","LOOP","maxOccurrences",2),Map.of("key","body","type","TASK","templateVersionId",10L),Map.of("key","end","type","END")),
                "edges",List.of(Map.of("key","r-l","from","root","to","loop"),Map.of("key","l-b","from","loop","to","body","branchKey","BODY"),
                        Map.of("key","b-l","from","body","to","loop"),Map.of("key","l-e","from","loop","to","end","branchKey","EXIT"))));
        TodoDefinitionDocument value=withRouting(base,graph);String compiled=new TodoDefinitionCodec().canonicalJson(value);
        when(mapper.selectTemplateVersionById(9L)).thenReturn(version(compiled));when(mapper.selectTemplateVersionById(10L)).thenReturn(publishedVersion(10L));
        when(mapper.selectCalendarByCode("DEFAULT")).thenReturn(calendar());LocalDateTime at=LocalDateTime.of(2026,7,17,10,0);

        var result=new TodoDefinitionSimulationService(mapper,new TodoAssignmentResolver()).simulate(9L,
                new SimulateDefinitionCommand(Map.of("stage","READY"),"LEAD",3L,LocalDateTime.of(2026,7,17,9,0),List.of(
                        sample("root",at),sample("body",1,at.plusMinutes(10)))));

        assertEquals(List.of(1,2),result.routes().stream().filter(route->route.nodeKey().equals("body")).map(route->route.occurrence()).toList());
        assertEquals(List.of("VIRTUAL_COMPLETED","PENDING_COMPLETION"),result.routes().stream()
                .filter(route->route.nodeKey().equals("body")).map(route->route.status()).toList());
        assertFalse(result.routes().stream().anyMatch(route->route.nodeKey().equals("end")));

        var completed=new TodoDefinitionSimulationService(mapper,new TodoAssignmentResolver()).simulate(9L,
                new SimulateDefinitionCommand(Map.of("stage","READY"),"LEAD",3L,LocalDateTime.of(2026,7,17,9,0),List.of(
                        sample("root",at),sample("body",1,at.plusMinutes(10)),sample("body",2,at.plusMinutes(20)))));
        assertTrue(completed.routes().stream().anyMatch(route->route.nodeKey().equals("end")&&route.status().equals("ENDED")));
    }

    @Test void downstream_completion_cannot_precede_the_current_route_time()
    {
        TodoDefinitionDocument base=definition(Map.of("type","USER","value",7L));
        var graph=new TodoDefinitionDocument.RoutingGraph(Map.of("start","first","nodes",List.of(
                Map.of("key","first","type","TASK","templateVersionId",9L),Map.of("key","second","type","TASK","templateVersionId",10L),Map.of("key","end","type","END")),
                "edges",List.of(Map.of("key","f-s","from","first","to","second"),Map.of("key","s-e","from","second","to","end"))));
        TodoDefinitionDocument value=withRouting(base,graph);String compiled=new TodoDefinitionCodec().canonicalJson(value);
        when(mapper.selectTemplateVersionById(9L)).thenReturn(version(compiled));when(mapper.selectTemplateVersionById(10L)).thenReturn(publishedVersion(10L));
        when(mapper.selectCalendarByCode("DEFAULT")).thenReturn(calendar());LocalDateTime at=LocalDateTime.of(2026,7,17,10,0);

        var result=new TodoDefinitionSimulationService(mapper,new TodoAssignmentResolver()).simulate(9L,
                new SimulateDefinitionCommand(Map.of("stage","READY"),"LEAD",3L,LocalDateTime.of(2026,7,17,9,0),List.of(
                        sample("first",0,at.plusMinutes(20)),sample("second",0,at.plusMinutes(10)))));

        assertEquals("INVALID_COMPLETION_TIME",result.routes().stream().filter(route->route.nodeKey().equals("second")).findFirst().orElseThrow().status());
        assertTrue(result.issues().stream().anyMatch(issue->issue.code().equals("TODO_SIMULATION_TASK_SAMPLE_CAUSAL_TIME_INVALID")));
        assertFalse(result.routes().stream().anyMatch(route->route.nodeKey().equals("end")));
    }

    @Test void any_join_advances_from_the_earliest_actual_branch_arrival_after_decisions()
    {
        TodoDefinitionDocument base=definition(Map.of("type","USER","value",7L));
        var graph=new TodoDefinitionDocument.RoutingGraph(Map.of("start","root","nodes",List.of(
                Map.of("key","root","type","TASK","templateVersionId",9L),Map.of("key","fork","type","FORK"),
                Map.of("key","a-decision","type","DECISION"),Map.of("key","b-decision","type","DECISION"),
                Map.of("key","a","type","TASK","templateVersionId",10L),Map.of("key","b","type","TASK","templateVersionId",11L),
                Map.of("key","join","type","JOIN","joinMode","ANY","branches",List.of("A","B")),Map.of("key","end","type","END")),
                "edges",List.of(Map.of("key","r-f","from","root","to","fork"),
                        Map.of("key","f-a","from","fork","to","a-decision","branchKey","A"),Map.of("key","f-b","from","fork","to","b-decision","branchKey","B"),
                        Map.of("key","ad-a","from","a-decision","to","a","default",true),Map.of("key","bd-b","from","b-decision","to","b","default",true),
                        Map.of("key","a-j","from","a","to","join"),Map.of("key","b-j","from","b","to","join"),Map.of("key","j-e","from","join","to","end"))));
        TodoDefinitionDocument value=withRouting(base,graph);String compiled=new TodoDefinitionCodec().canonicalJson(value);
        when(mapper.selectTemplateVersionById(9L)).thenReturn(version(compiled));when(mapper.selectTemplateVersionById(10L)).thenReturn(publishedVersion(10L));
        when(mapper.selectTemplateVersionById(11L)).thenReturn(publishedVersion(11L));when(mapper.selectCalendarByCode("DEFAULT")).thenReturn(calendar());
        LocalDateTime at=LocalDateTime.of(2026,7,17,10,0);

        var result=new TodoDefinitionSimulationService(mapper,new TodoAssignmentResolver()).simulate(9L,
                new SimulateDefinitionCommand(Map.of("stage","READY"),"LEAD",3L,LocalDateTime.of(2026,7,17,9,0),List.of(
                        sample("root",at),sample("a",0,at.plusMinutes(30)),sample("b",0,at.plusMinutes(10)))));

        var advanced=result.routes().stream().filter(route->route.nodeKey().equals("join")&&route.status().equals("ADVANCED")).findFirst().orElseThrow();
        assertEquals("B",advanced.branchKey());
        assertEquals(at.plusMinutes(10),advanced.effectiveAt());
    }

    @Test void duplicate_unknown_and_non_task_completion_samples_are_stable_issues_and_are_not_applied()
    {
        TodoDefinitionDocument base=definition(Map.of("type","USER","value",7L));
        var graph=new TodoDefinitionDocument.RoutingGraph(Map.of("start","task","nodes",List.of(
                Map.of("key","task","type","TASK","templateVersionId",9L),Map.of("key","decision","type","DECISION"),Map.of("key","end","type","END")),
                "edges",List.of(Map.of("key","t-d","from","task","to","decision"),Map.of("key","d-e","from","decision","to","end","default",true))));
        TodoDefinitionDocument value=withRouting(base,graph);String compiled=new TodoDefinitionCodec().canonicalJson(value);
        when(mapper.selectTemplateVersionById(9L)).thenReturn(version(compiled));when(mapper.selectCalendarByCode("DEFAULT")).thenReturn(calendar());
        LocalDateTime at=LocalDateTime.of(2026,7,17,10,0);

        var result=new TodoDefinitionSimulationService(mapper,new TodoAssignmentResolver()).simulate(9L,
                new SimulateDefinitionCommand(Map.of("stage","READY"),"LEAD",3L,LocalDateTime.of(2026,7,17,9,0),List.of(
                        sample("task",at),sample("task",at.plusMinutes(1)),sample("decision",at),sample("missing",at))));

        assertEquals("PENDING_COMPLETION",result.routes().get(0).status());
        assertEquals(List.of("TODO_SIMULATION_TASK_SAMPLE_DUPLICATE","TODO_SIMULATION_TASK_SAMPLE_NODE_NOT_TASK","TODO_SIMULATION_TASK_SAMPLE_NODE_UNKNOWN"),
                result.issues().stream().map(issue->issue.code()).filter(code->code.startsWith("TODO_SIMULATION_TASK_SAMPLE_")).toList());
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

    @Test void blocked_definition_retains_unresolved_decision_issue_but_simulates_its_compiled_snapshot()
    {
        TodoDefinitionDocument base=definition(Map.of("type","USER","value",7L));
        TodoDefinitionDocument blocked=new TodoDefinitionDocument(base.schemaVersion(),base.templateCode(),base.event(),
                base.owner(),base.dod(),base.sla(),base.ui(),base.routing(),base.autoActions(),List.of("Q-001"),base.acceptanceRefs());
        String compiled=new TodoDefinitionCodec().canonicalJson(blocked);Map<String,Object> row=version(compiled);row.put("status","BLOCKED");
        when(mapper.selectTemplateVersionById(9L)).thenReturn(row);
        when(mapper.selectDecisionByCode("Q-001")).thenReturn(Map.of("decision_code","Q-001","status","OPEN","blocking","Y"));
        when(mapper.selectCalendarByCode("DEFAULT")).thenReturn(Map.of(
                "work_days","1,2,3,4,5","work_start","09:00:00","work_end","18:00:00","exception_json","{}"));

        var result=new TodoDefinitionSimulationService(mapper,new TodoAssignmentResolver()).simulate(9L,
                new SimulateDefinitionCommand(Map.of("stage","READY"),"LEAD",3L,LocalDateTime.of(2026,7,17,9,0)));

        assertTrue(result.issues().stream().anyMatch(issue->issue.code().equals("TODO_DECISION_UNRESOLVED")));
        assertEquals(List.of("task"),result.routes().stream().map(route->route.nodeKey()).toList());
        assertFalse(result.issues().stream().anyMatch(issue->issue.code().equals("TODO_SIMULATION_VERSION_STATUS_INELIGIBLE")));
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

        var result=new TodoDefinitionSimulationService(mapper,new TodoAssignmentResolver(),List.of(),compilerWithEscalation()).simulate(9L,
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

        var result=new TodoDefinitionSimulationService(mapper,new TodoAssignmentResolver(),List.of(),compilerWithEscalation()).simulate(9L,
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
        row.put("business_type","LEAD");
        row.put("definition_hash",TodoDefinitionSimulationService.sha256(compiled));
        return row;
    }

    private Map<String,Object> publishedVersion(long id){return Map.of("version_id",id,"status","PUBLISHED");}
    private Map<String,Object> calendar(){return Map.of("work_days","1,2,3,4,5","work_start","09:00:00","work_end","18:00:00","exception_json","{}");}
    private VirtualTaskCompletionSample sample(String nodeKey,LocalDateTime completedAt){return sample(nodeKey,0,completedAt);}
    private VirtualTaskCompletionSample sample(String nodeKey,int occurrence,LocalDateTime completedAt){return new VirtualTaskCompletionSample(nodeKey,occurrence,Map.of(),completedAt);}
    private TodoDefinitionDocument withRouting(TodoDefinitionDocument base,TodoDefinitionDocument.RoutingGraph graph)
    {return new TodoDefinitionDocument(base.schemaVersion(),base.templateCode(),base.event(),base.owner(),base.dod(),base.sla(),base.ui(),graph,base.autoActions(),base.decisionRefs(),base.acceptanceRefs());}

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
    private TodoDefinitionCompiler compilerWithEscalation()
    {
        TodoAutoActionCapability escalation=new TodoAutoActionCapability(){public String actionType(){return "ESCALATE";}public AutoActionResult execute(com.law.todo.domain.model.TodoInstance todo,TodoDefinitionDocument.AutoActionRule rule,com.law.todo.application.command.TodoActionCommands.Actor actor){return AutoActionResult.success();}};
        return new TodoDefinitionCompiler(new TodoDefinitionCodec(),new TodoEventCatalogService(mapper),new TodoDecisionService(mapper),new com.law.todo.expression.ConditionValidator(),new TodoAutoActionCapabilityRegistry(List.of(escalation)));
    }
}
