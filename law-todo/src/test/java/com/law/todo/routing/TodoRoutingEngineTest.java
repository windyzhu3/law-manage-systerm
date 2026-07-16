package com.law.todo.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.law.todo.definition.model.TodoDefinitionDocument.RoutingGraph;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.routing.TodoRoutingEngine.RouteContext;
import com.law.todo.routing.TodoRoutingEngine.RouteStatus;

@ExtendWith(MockitoExtension.class)
class TodoRoutingEngineTest
{
    @Mock TodoMapper mapper;

    @Test void allJoinWaitsForEveryConfiguredForkToken()
    {
        RoutingGraph graph = graph(
                List.of(node("join", "JOIN", Map.of("joinMode", "ALL", "branches", List.of("legal", "finance"))),
                        node("next", "TASK", Map.of("templateVersionId", 22L)), node("end", "END", Map.of())),
                List.of(edge("join-next", "join", "next", null, 0), edge("next-end", "next", "end", null, 0)));
        when(mapper.selectRouteJoinForUpdate(1L, "join", 0)).thenReturn(Map.of("status", "WAITING"));
        when(mapper.selectRouteTokenArrivalsForUpdate(1L, "join", 0))
                .thenReturn(List.of("legal"), List.of("legal", "finance"));
        when(mapper.advanceRouteJoinConditionally(1L, "join", 0)).thenReturn(1);
        TodoRoutingEngine engine = new TodoRoutingEngine(mapper);

        assertEquals(RouteStatus.WAITING, engine.advance(context(graph, token("legal", 0), Map.of())).status());
        assertEquals(RouteStatus.ADVANCED, engine.advance(context(graph, token("finance", 0), Map.of())).status());

        InOrder writes = inOrder(mapper);
        writes.verify(mapper).insertRouteJoinIfAbsent(anyMap());
        writes.verify(mapper).selectRouteJoinForUpdate(1L, "join", 0);
        writes.verify(mapper).insertRouteTokenIfAbsent(anyMap());
        writes.verify(mapper).insertRouteJoinIfAbsent(anyMap());
        writes.verify(mapper).selectRouteJoinForUpdate(1L, "join", 0);
        writes.verify(mapper).insertRouteTokenIfAbsent(anyMap());
    }

    @Test void anyJoinAdvancesExactlyOnce()
    {
        RoutingGraph graph = graph(
                List.of(node("join", "JOIN", Map.of("joinMode", "ANY", "branches", List.of("a", "b"))),
                        node("next", "TASK", Map.of("templateVersionId", 22L)), node("end", "END", Map.of())),
                List.of(edge("join-next", "join", "next", null, 0), edge("next-end", "next", "end", null, 0)));
        when(mapper.selectRouteJoinForUpdate(1L, "join", 0))
                .thenReturn(Map.of("status", "WAITING"), Map.of("status", "ADVANCED"));
        when(mapper.selectRouteTokenArrivalsForUpdate(1L, "join", 0)).thenReturn(List.of("a"));
        when(mapper.advanceRouteJoinConditionally(1L, "join", 0)).thenReturn(1);
        TodoRoutingEngine engine = new TodoRoutingEngine(mapper);

        assertEquals(RouteStatus.ADVANCED, engine.advance(context(graph, token("a", 0), Map.of())).status());
        assertEquals(RouteStatus.WAITING, engine.advance(context(graph, token("b", 0), Map.of())).status());

        verify(mapper).advanceRouteJoinConditionally(1L, "join", 0);
    }

    @Test void decisionChoosesFirstMatchingEdgeByOrder()
    {
        RoutingGraph graph = graph(
                List.of(node("decision", "DECISION", Map.of()),
                        node("low", "TASK", Map.of("templateVersionId", 10L)),
                        node("high", "TASK", Map.of("templateVersionId", 20L)), node("end", "END", Map.of())),
                List.of(
                        conditionalEdge("exact", "decision", "low", 10, eq("amount", 100)),
                        conditionalEdge("high", "decision", "high", 20, gte("amount", 100)),
                        defaultEdge("fallback", "decision", "low", 0),
                        edge("low-end", "low", "end", null, 0), edge("high-end", "high", "end", null, 0)));

        var result = new TodoRoutingEngine(mapper).advance(context(graph,
                new RouteToken(1L, "decision", null, 0, RouteTokenStatus.ACTIVE), Map.of("amount", 100)));

        assertEquals(RouteStatus.ADVANCED, result.status());
        assertEquals("high", result.tasks().get(0).nodeKey());
    }

    @Test void forkProducesStableBranchTokens()
    {
        RoutingGraph graph = graph(
                List.of(node("fork", "FORK", Map.of()),
                        node("legalTask", "TASK", Map.of("templateVersionId", 10L)),
                        node("financeTask", "TASK", Map.of("templateVersionId", 20L)), node("legalEnd", "END", Map.of()),node("financeEnd", "END", Map.of())),
                List.of(
                        edge("finance", "fork", "financeTask", "finance", 10),
                        edge("legal", "fork", "legalTask", "legal", 20),
                        edge("legal-end", "legalTask", "legalEnd", null, 0), edge("finance-end", "financeTask", "financeEnd", null, 0)));

        var result = new TodoRoutingEngine(mapper).advance(context(graph,
                new RouteToken(1L, "fork", null, 2, RouteTokenStatus.ACTIVE), Map.of()));

        assertEquals(List.of("legalTask", "financeTask"), result.tasks().stream().map(task -> task.nodeKey()).toList());
        assertEquals(List.of("legal", "finance"), result.tasks().stream().map(task -> task.token().branchKey()).toList());
        assertTrue(result.tasks().stream().allMatch(task -> task.token().occurrence() == 2));
    }

    @Test void loopIncrementsOccurrenceAndUsesExitConditionDeterministically()
    {
        RoutingGraph graph = graph(
                List.of(node("loop", "LOOP", Map.of("maxOccurrences", 3, "endCondition", eq("approved", true))),
                        node("body", "TASK", Map.of("templateVersionId", 10L)),
                        node("done", "END", Map.of())),
                List.of(edge("exit", "loop", "done", "EXIT", 10), edge("body", "loop", "body", "BODY", 20),
                        edge("repeat", "body", "loop", null, 0)));
        TodoRoutingEngine engine = new TodoRoutingEngine(mapper);

        var body = engine.advance(context(graph,
                new RouteToken(1L, "loop", null, 1, RouteTokenStatus.ACTIVE), Map.of("approved", false)));
        var done = engine.advance(context(graph,
                new RouteToken(1L, "loop", null, 2, RouteTokenStatus.ACTIVE), Map.of("approved", true)));

        assertEquals(2, body.tasks().get(0).token().occurrence());
        assertEquals(RouteStatus.ENDED, done.status());
    }

    @Test void unboundedLoopIsRejected()
    {
        RoutingGraph graph = graph(
                List.of(node("loop", "LOOP", Map.of()), node("end", "END", Map.of())),
                List.of(edge("body", "loop", "end", "BODY", 0)));

        assertEquals("TODO_ROUTE_LOOP_UNBOUNDED", new RoutingGraphValidator().validate(graph).get(0).code());
    }

    @Test void malformedJoinAndDanglingEdgeAreRejected()
    {
        RoutingGraph graph = graph(
                List.of(node("join", "JOIN", Map.of("joinMode", "ALL", "branches", List.of()))),
                List.of(edge("missing-join", "missing", "join", "a", 0)));

        List<String> codes = new RoutingGraphValidator().validate(graph).stream().map(issue -> issue.code()).toList();

        assertTrue(codes.contains("TODO_ROUTE_JOIN_BRANCHES_REQUIRED"));
        assertTrue(codes.contains("TODO_ROUTE_EDGE_NODE_NOT_FOUND"));
    }

    @Test void routeContextPreservesPresentNullPayloadValues()
    {
        Map<String,Object> payload=new java.util.LinkedHashMap<>();payload.put("decision",null);
        RouteContext context=context(graph(List.of(node("end","END",Map.of())),List.of()),
                new RouteToken(1L,"end",null,0,RouteTokenStatus.ACTIVE),payload);

        assertTrue(context.payload().containsKey("decision"));
    }

    @Test void loopExitBackEdgeRemainsAnUncontrolledCycle()
    {
        RoutingGraph graph=graph(List.of(
                node("loop","LOOP",Map.of("maxOccurrences",3)),node("body","TASK",Map.of("templateVersionId",10L)),
                node("exitTask","TASK",Map.of("templateVersionId",11L)),node("end","END",Map.of())),List.of(
                edge("body-edge","loop","body","BODY",10),edge("exit-edge","loop","exitTask","EXIT",0),
                edge("body-back","body","loop",null,0),edge("exit-back","exitTask","loop",null,0)));

        assertTrue(new RoutingGraphValidator().validate(graph).stream().anyMatch(issue->"TODO_ROUTE_CYCLE_UNCONTROLLED".equals(issue.code())));
    }

    @Test void decisionAndTaskCycleIsRejected()
    {
        RoutingGraph graph=graph(List.of(node("decision","DECISION",Map.of()),node("task","TASK",Map.of("templateVersionId",10L)),node("end","END",Map.of())),List.of(
                defaultEdge("default","decision","task",0),conditionalEdge("finish","decision","end",10,eq("done",true)),
                edge("back","task","decision",null,0)));

        assertTrue(new RoutingGraphValidator().validate(graph).stream().anyMatch(issue->"TODO_ROUTE_CYCLE_UNCONTROLLED".equals(issue.code())));
    }

    @Test void forkBranchesCannotReconvergeBeforeJoin()
    {
        RoutingGraph graph=graph(List.of(node("fork","FORK",Map.of()),node("a","TASK",Map.of("templateVersionId",10L)),
                node("b","TASK",Map.of("templateVersionId",11L)),node("shared","TASK",Map.of("templateVersionId",12L)),node("end","END",Map.of())),List.of(
                edge("fork-a","fork","a","a",10),edge("fork-b","fork","b","b",0),edge("a-shared","a","shared",null,0),
                edge("b-shared","b","shared",null,0),edge("shared-end","shared","end",null,0)));

        assertTrue(new RoutingGraphValidator().validate(graph).stream().anyMatch(issue->"TODO_ROUTE_FORK_RECONVERGENCE_INVALID".equals(issue.code())));
    }

    @Test void joinMayBeFirstSharedNodeOfForkBranches()
    {
        RoutingGraph graph=graph(List.of(node("fork","FORK",Map.of()),node("a","TASK",Map.of("templateVersionId",10L)),
                node("b","TASK",Map.of("templateVersionId",11L)),node("join","JOIN",Map.of("joinMode","ALL","branches",List.of("a","b"))),
                node("end","END",Map.of())),List.of(edge("fork-a","fork","a","a",10),edge("fork-b","fork","b","b",0),
                edge("a-join","a","join",null,0),edge("b-join","b","join",null,0),edge("join-end","join","end",null,0)));

        assertTrue(new RoutingGraphValidator().validate(graph).stream().noneMatch(issue->"TODO_ROUTE_FORK_RECONVERGENCE_INVALID".equals(issue.code())));
    }

    @Test void joinBranchesMustMatchTokensThatCanActuallyArrive()
    {
        RoutingGraph graph=graph(List.of(node("fork","FORK",Map.of()),node("a","TASK",Map.of("templateVersionId",10L)),
                node("b","TASK",Map.of("templateVersionId",11L)),node("join","JOIN",Map.of("joinMode","ALL","branches",List.of("a","c"))),
                node("end","END",Map.of())),List.of(edge("fork-a","fork","a","a",10),edge("fork-b","fork","b","b",0),
                edge("a-join","a","join",null,0),edge("b-join","b","join",null,0),edge("join-end","join","end",null,0)));

        assertTrue(new RoutingGraphValidator().validate(graph).stream()
                .anyMatch(issue->"TODO_ROUTE_JOIN_BRANCH_FLOW_INVALID".equals(issue.code())));
    }

    @Test void nestedForkTokensAreValidatedAtTheirActualJoin()
    {
        RoutingGraph graph=graph(List.of(node("outer","FORK",Map.of()),node("nested","FORK",Map.of()),
                node("x","TASK",Map.of("templateVersionId",10L)),node("y","TASK",Map.of("templateVersionId",11L)),
                node("innerJoin","JOIN",Map.of("joinMode","ALL","branches",List.of("x","y"))),node("end","END",Map.of())),
                List.of(edge("outer-nested","outer","nested","a",10),edge("outer-end","outer","end","b",0),
                        edge("nested-x","nested","x","x",10),edge("nested-y","nested","y","y",0),
                        edge("x-join","x","innerJoin",null,0),edge("y-join","y","innerJoin",null,0),
                        edge("join-end","innerJoin","end",null,0)));

        assertTrue(new RoutingGraphValidator().validate(graph).stream()
                .noneMatch(issue->"TODO_ROUTE_JOIN_BRANCH_FLOW_INVALID".equals(issue.code())));
    }

    @Test void forkBranchesMayShareAnEndNodeWithoutReconvergenceFailure()
    {
        RoutingGraph graph=graph(List.of(node("fork","FORK",Map.of()),node("a","TASK",Map.of("templateVersionId",10L)),
                node("b","TASK",Map.of("templateVersionId",11L)),node("end","END",Map.of())),
                List.of(edge("fork-a","fork","a","a",10),edge("fork-b","fork","b","b",0),
                        edge("a-end","a","end",null,0),edge("b-end","b","end",null,0)));

        assertTrue(new RoutingGraphValidator().validate(graph).stream()
                .noneMatch(issue->"TODO_ROUTE_FORK_RECONVERGENCE_INVALID".equals(issue.code())));
    }

    private RouteContext context(RoutingGraph graph, RouteToken token, Map<String, Object> payload)
    {
        TodoInstance todo = new TodoInstance();
        todo.setTodoId(9L);todo.setRootTodoId(1L);todo.setBusinessType("LEAD");todo.setBusinessId(7L);
        return new RouteContext(graph, "hash", 1, todo, token, payload);
    }

    private RouteToken token(String branch, int occurrence)
    {
        return new RouteToken(1L, "join", branch, occurrence, RouteTokenStatus.ACTIVE);
    }

    private RoutingGraph graph(List<Map<String, Object>> nodes, List<Map<String, Object>> edges)
    {
        return new RoutingGraph(Map.of("start", nodes.get(0).get("key"), "nodes", nodes, "edges", edges));
    }

    private Map<String, Object> node(String key, String type, Map<String, Object> config)
    {
        java.util.Map<String, Object> node = new java.util.LinkedHashMap<>();
        node.put("key", key);node.put("type", type);node.putAll(config);return node;
    }

    private Map<String, Object> edge(String key, String from, String to, String branchKey, int priority)
    {
        java.util.Map<String, Object> edge = new java.util.LinkedHashMap<>();
        edge.put("key", key);edge.put("from", from);edge.put("to", to);edge.put("priority", priority);
        if (branchKey != null) edge.put("branchKey", branchKey);
        return edge;
    }

    private Map<String, Object> conditionalEdge(String key, String from, String to, int priority, Map<String, Object> condition)
    {
        java.util.Map<String, Object> edge = new java.util.LinkedHashMap<>(edge(key, from, to, null, priority));
        edge.put("condition", condition);return edge;
    }

    private Map<String, Object> defaultEdge(String key, String from, String to, int priority)
    {
        java.util.Map<String, Object> edge = new java.util.LinkedHashMap<>(edge(key, from, to, null, priority));
        edge.put("default", true);return edge;
    }

    private Map<String, Object> eq(String field, Object value)
    {
        return Map.of("$expression", Map.of("version", 1, "root", Map.of("field", field, "operator", "EQ", "value", value)));
    }

    private Map<String, Object> gte(String field, Object value)
    {
        return Map.of("$expression", Map.of("version", 1, "root", Map.of("field", field, "operator", "GTE", "value", value)));
    }
}
