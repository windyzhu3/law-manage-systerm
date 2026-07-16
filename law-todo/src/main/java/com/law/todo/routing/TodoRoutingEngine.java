package com.law.todo.routing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson2.JSON;
import com.law.todo.definition.model.TodoDefinitionDocument.RoutingGraph;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.expression.ConditionEvaluator;
import com.law.todo.expression.ConditionValidator;
import com.law.todo.mapper.TodoMapper;

/** Executes immutable routing graphs and persists concurrency-sensitive join state. */
@Component
public class TodoRoutingEngine
{
    private static final Comparator<Edge> EDGE_ORDER = Comparator.comparingInt(Edge::priority).reversed().thenComparing(Edge::key);
    private final TodoMapper mapper;
    private final ConditionValidator conditionValidator;
    private final ConditionEvaluator conditionEvaluator;

    public TodoRoutingEngine(TodoMapper mapper)
    {
        this(mapper, new ConditionValidator(), new ConditionEvaluator());
    }

    public TodoRoutingEngine(TodoMapper mapper, ConditionValidator conditionValidator, ConditionEvaluator conditionEvaluator)
    {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.conditionValidator = Objects.requireNonNull(conditionValidator, "conditionValidator");
        this.conditionEvaluator = Objects.requireNonNull(conditionEvaluator, "conditionEvaluator");
    }

    @Transactional
    public RoutingResult advance(RouteContext context)
    {
        Objects.requireNonNull(context, "context");
        List<com.law.todo.definition.compiler.DefinitionValidationReport.ValidationIssue> issues =
                new RoutingGraphValidator(conditionValidator).validate(context.graph());
        if (!issues.isEmpty()) throw new TodoException(issues.get(0).code(), issues.get(0).message());
        Graph graph = Graph.read(context.graph());
        RouteToken token = context.token();
        if (token == null)
        {
            Long root = root(context.todo());
            token = new RouteToken(root, graph.start, null, 0, RouteTokenStatus.ACTIVE);
        }
        List<NextTask> tasks = new ArrayList<>();
        Outcome outcome = visit(graph, context, token, tasks, 0);
        RouteStatus status = !tasks.isEmpty() ? RouteStatus.ADVANCED
                : outcome == Outcome.WAITING ? RouteStatus.WAITING : RouteStatus.ENDED;
        return new RoutingResult(status, tasks);
    }

    private Outcome visit(Graph graph, RouteContext context, RouteToken token,
            List<NextTask> tasks, int depth)
    {
        if (depth > graph.nodes.size() * 4 + 8)
            throw new TodoException("TODO_ROUTE_EXECUTION_LIMIT", "Routing traversal exceeded its safe bound");
        Node node = graph.nodes.get(token.nodeKey());
        if (node == null) throw new TodoException("TODO_ROUTE_NODE_NOT_FOUND", "Route node does not exist: " + token.nodeKey());
        return switch (node.type)
        {
            case "TASK" -> visitTask(graph, context, token, node, tasks, depth);
            case "DECISION" -> visitDecision(graph, context, token, node, tasks, depth);
            case "FORK" -> visitFork(graph, context, token, node, tasks, depth);
            case "JOIN" -> visitJoin(graph, context, token, node, tasks, depth);
            case "LOOP" -> visitLoop(graph, context, token, node, tasks, depth);
            case "END" -> Outcome.ENDED;
            default -> throw new TodoException("TODO_ROUTE_NODE_TYPE_INVALID", "Unsupported route node type: " + node.type);
        };
    }

    private Outcome visitTask(Graph graph, RouteContext context, RouteToken token, Node node,
            List<NextTask> tasks, int depth)
    {
        if (isCompletedCurrentTask(context.todo(), token.nodeKey()))
            return followSingle(graph, context, token, node, tasks, depth);
        tasks.add(new NextTask(node.key, longValue(node.config.get("templateVersionId")), token));
        return Outcome.ADVANCED;
    }

    private Outcome visitDecision(Graph graph, RouteContext context, RouteToken token, Node node,
            List<NextTask> tasks, int depth)
    {
        Edge fallback = null;
        for (Edge edge : graph.outgoing(node.key))
        {
            if (edge.isDefault) fallback = edge;
            else if (matches(edge.condition, context.payload()))
                return visit(graph, context, move(token, edge.to, token.branchKey(), token.occurrence()), tasks, depth + 1);
        }
        if (fallback == null) throw new TodoException("TODO_ROUTE_DECISION_NO_MATCH", "DECISION has no matching or default edge");
        return visit(graph, context, move(token, fallback.to, token.branchKey(), token.occurrence()), tasks, depth + 1);
    }

    private Outcome visitFork(Graph graph, RouteContext context, RouteToken token, Node node,
            List<NextTask> tasks, int depth)
    {
        boolean matched = false;Outcome outcome = Outcome.ENDED;
        for (Edge edge : graph.outgoing(node.key))
        {
            if (edge.condition != null && !matches(edge.condition, context.payload())) continue;
            matched = true;
            Outcome branch = visit(graph, context, move(token, edge.to, edge.branchKey, token.occurrence()), tasks, depth + 1);
            if (branch == Outcome.WAITING) outcome = Outcome.WAITING;
            else if (branch == Outcome.ADVANCED) outcome = Outcome.ADVANCED;
        }
        if (!matched) throw new TodoException("TODO_ROUTE_FORK_NO_MATCH", "FORK produced no matching branch");
        return outcome;
    }

    private Outcome visitJoin(Graph graph, RouteContext context, RouteToken token, Node node,
            List<NextTask> tasks, int depth)
    {
        List<String> required = stringList(node.config.get("branches"));
        if (token.branchKey() == null || !required.contains(token.branchKey()))
            throw new TodoException("TODO_ROUTE_JOIN_BRANCH_UNKNOWN", "JOIN received an unknown branch token");
        Map<String, Object> state = new HashMap<>();
        state.put("rootTodoId", token.rootTodoId());state.put("nodeKey", node.key);state.put("occurrence", token.occurrence());
        state.put("joinMode", text(node.config.get("joinMode")));state.put("requiredBranches", JSON.toJSONString(required));
        mapper.insertRouteJoinIfAbsent(state);
        Map<String, Object> locked = mapper.selectRouteJoinForUpdate(token.rootTodoId(), node.key, token.occurrence());
        Map<String, Object> arrival = new HashMap<>();
        arrival.put("rootTodoId", token.rootTodoId());arrival.put("nodeKey", node.key);
        arrival.put("branchKey", token.branchKey());arrival.put("occurrence", token.occurrence());
        arrival.put("definitionHash", context.definitionHash());arrival.put("status", RouteTokenStatus.ARRIVED.name());
        arrival.put("routeToken", JSON.toJSONString(token));
        mapper.insertRouteTokenIfAbsent(arrival);
        if (locked != null && "ADVANCED".equals(text(value(locked, "status", "join_status")))) return Outcome.WAITING;

        Set<String> arrivals = new HashSet<>(mapper.selectRouteTokenArrivalsForUpdate(token.rootTodoId(), node.key, token.occurrence()));
        boolean ready = "ANY".equals(text(node.config.get("joinMode"))) ? !arrivals.isEmpty() : arrivals.containsAll(required);
        if (!ready || mapper.advanceRouteJoinConditionally(token.rootTodoId(), node.key, token.occurrence()) <= 0)
            return Outcome.WAITING;
        return followSingle(graph, context,
                new RouteToken(token.rootTodoId(), node.key, null, token.occurrence(), RouteTokenStatus.CONSUMED),
                node, tasks, depth);
    }

    private Outcome visitLoop(Graph graph, RouteContext context, RouteToken token, Node node,
            List<NextTask> tasks, int depth)
    {
        boolean ended = node.config.containsKey("endCondition") && matches(map(node.config.get("endCondition")), context.payload());
        Integer max = integer(node.config.get("maxOccurrences"));
        boolean maxReached = max != null && token.occurrence() >= max;
        String branch = ended || maxReached ? "EXIT" : "BODY";
        Edge edge = graph.outgoing(node.key).stream().filter(candidate -> branch.equals(candidate.branchKey)).findFirst()
                .orElseThrow(() -> new TodoException("TODO_ROUTE_LOOP_EDGES_INVALID", "LOOP edge is missing: " + branch));
        int occurrence = "BODY".equals(branch) ? token.occurrence() + 1 : token.occurrence();
        return visit(graph, context, move(token, edge.to, token.branchKey(), occurrence), tasks, depth + 1);
    }

    private Outcome followSingle(Graph graph, RouteContext context, RouteToken token, Node node,
            List<NextTask> tasks, int depth)
    {
        Edge edge = graph.outgoing(node.key).get(0);
        return visit(graph, context, move(token, edge.to, token.branchKey(), token.occurrence()), tasks, depth + 1);
    }

    private boolean matches(Map<String, Object> condition, Map<String, Object> payload)
    {
        return condition != null && conditionEvaluator.evaluate(conditionValidator.decodeCanonical(condition), payload);
    }

    private static RouteToken move(RouteToken token, String nodeKey, String branchKey, int occurrence)
    { return new RouteToken(token.rootTodoId(), nodeKey, branchKey, occurrence, RouteTokenStatus.ACTIVE); }
    private static boolean isCompletedCurrentTask(TodoInstance todo, String nodeKey)
    { return todo != null && "COMPLETED".equals(todo.getStatus()) && nodeKey.equals(todo.getRouteNodeKey()); }
    private static Long root(TodoInstance todo)
    { return todo.getRootTodoId() == null ? todo.getTodoId() : todo.getRootTodoId(); }
    private static Object value(Map<String, Object> map, String... keys) { for (String key : keys) if (map.containsKey(key)) return map.get(key);return null; }
    private static Long longValue(Object value) { return Long.valueOf(String.valueOf(value)); }
    private static Integer integer(Object value) { if (value == null) return null;return Integer.valueOf(String.valueOf(value)); }
    private static String text(Object value) { return value == null ? null : String.valueOf(value); }
    @SuppressWarnings("unchecked") private static Map<String, Object> map(Object value) { return (Map<String, Object>) value; }
    private static List<String> stringList(Object value) { if (!(value instanceof List<?> list)) return List.of();return list.stream().map(String::valueOf).toList(); }

    private enum Outcome { ADVANCED, WAITING, ENDED }
    public enum RouteStatus { ADVANCED, WAITING, ENDED }
    public record RouteContext(RoutingGraph graph, String definitionHash, int payloadSchemaVersion,
            TodoInstance todo, RouteToken token, Map<String, Object> payload)
    {
        public RouteContext { payload = payload == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(payload)); }
    }
    public record NextTask(String nodeKey, Long templateVersionId, RouteToken token) {}
    public record RoutingResult(RouteStatus status, List<NextTask> tasks)
    { public RoutingResult { tasks = tasks == null ? List.of() : List.copyOf(tasks); } }

    private record Node(String key, String type, Map<String, Object> config) {}
    private record Edge(String key, String from, String to, int priority, String branchKey,
            Map<String, Object> condition, boolean isDefault) {}
    private static final class Graph
    {
        private final String start;private final Map<String, Node> nodes;private final Map<String, List<Edge>> edges;
        private Graph(String start, Map<String, Node> nodes, Map<String, List<Edge>> edges)
        { this.start = start;this.nodes = nodes;this.edges = edges; }
        private List<Edge> outgoing(String key) { return edges.getOrDefault(key, List.of()); }
        private static Graph read(RoutingGraph graph)
        {
            Map<String, Node> nodes = new LinkedHashMap<>();
            for (Map<String, Object> value : objectList(graph.config().get("nodes")))
            { String key = text(value.get("key"));nodes.put(key, new Node(key, text(value.get("type")), value)); }
            Map<String, List<Edge>> edges = new HashMap<>();
            for (Map<String, Object> value : objectList(graph.config().get("edges")))
            {
                Edge edge = new Edge(text(value.get("key")), text(value.get("from")), text(value.get("to")),
                        value.get("priority") == null ? 0 : integer(value.get("priority")), text(value.get("branchKey")),
                        value.get("condition") == null ? null : map(value.get("condition")), Boolean.TRUE.equals(value.get("default")));
                edges.computeIfAbsent(edge.from, ignored -> new ArrayList<>()).add(edge);
            }
            edges.values().forEach(list -> list.sort(EDGE_ORDER));
            return new Graph(text(graph.config().get("start")), nodes, edges);
        }
        private static List<Map<String, Object>> objectList(Object value)
        { if (!(value instanceof List<?> list)) return List.of();List<Map<String, Object>> result = new ArrayList<>();for (Object entry : list) result.add(map(entry));return result; }
    }
}
