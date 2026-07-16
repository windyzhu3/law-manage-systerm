package com.law.todo.routing;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.law.todo.definition.compiler.DefinitionValidationReport.ValidationIssue;
import com.law.todo.definition.model.TodoDefinitionDocument.RoutingGraph;
import com.law.todo.expression.ConditionValidator;

/** Validates the canonical, data-only routing graph envelope. */
@Component
public final class RoutingGraphValidator
{
    private static final Set<String> TYPES = Set.of("TASK", "DECISION", "FORK", "JOIN", "LOOP", "END");
    private final ConditionValidator conditions;

    public RoutingGraphValidator() { this(new ConditionValidator()); }
    public RoutingGraphValidator(ConditionValidator conditions) { this.conditions = conditions; }

    public List<ValidationIssue> validate(RoutingGraph graph)
    {
        List<ValidationIssue> issues = new ArrayList<>();
        if (graph == null) return List.of(issue("TODO_ROUTE_GRAPH_REQUIRED", "routing", "Routing graph is required"));
        Map<String, Object> config = graph.config();
        String start = text(config.get("start"));
        List<Map<String, Object>> nodes = objects(config.get("nodes"), "routing.nodes", issues);
        List<Map<String, Object>> edges = objects(config.get("edges"), "routing.edges", issues);
        Map<String, Map<String, Object>> byKey = new LinkedHashMap<>();
        for (int i = 0; i < nodes.size(); i++) validateNode(nodes.get(i), i, byKey, issues);
        if (blank(start) || !byKey.containsKey(start))
            issues.add(issue("TODO_ROUTE_START_NOT_FOUND", "routing.start", "Start node must reference an existing node"));

        Set<String> edgeKeys = new HashSet<>();
        Map<String, List<Map<String, Object>>> outgoing = new HashMap<>();
        for (int i = 0; i < edges.size(); i++)
            validateEdge(edges.get(i), i, byKey, edgeKeys, outgoing, issues);
        validateNodeEdges(byKey, outgoing, issues);
        validateReachability(start, byKey, outgoing, issues);
        validateCycles(byKey, outgoing, issues);
        validateForkReconvergence(byKey, outgoing, issues);
        validateJoinBranchFlow(start, byKey, outgoing, issues);
        return List.copyOf(issues);
    }

    private void validateNode(Map<String, Object> node, int index,
            Map<String, Map<String, Object>> byKey, List<ValidationIssue> issues)
    {
        String path = "routing.nodes[" + index + "]";
        String key = text(node.get("key"));String type = text(node.get("type"));
        if (blank(key)) issues.add(issue("TODO_ROUTE_NODE_KEY_REQUIRED", path + ".key", "Node key is required"));
        else if (byKey.putIfAbsent(key, node) != null)
            issues.add(issue("TODO_ROUTE_NODE_KEY_DUPLICATE", path + ".key", "Node key must be unique"));
        if (type == null || !TYPES.contains(type)) issues.add(issue("TODO_ROUTE_NODE_TYPE_INVALID", path + ".type", "Unsupported route node type"));
        if ("TASK".equals(type) && positive(node.get("templateVersionId")) == null)
            issues.add(issue("TODO_ROUTE_TASK_TEMPLATE_REQUIRED", path + ".templateVersionId", "TASK requires a positive templateVersionId"));
        if ("JOIN".equals(type)) validateJoin(node, path, issues);
        if ("LOOP".equals(type)) validateLoop(node, path, issues);
    }

    private void validateJoin(Map<String, Object> node, String path, List<ValidationIssue> issues)
    {
        String mode = text(node.get("joinMode"));
        if (!"ANY".equals(mode) && !"ALL".equals(mode))
            issues.add(issue("TODO_ROUTE_JOIN_MODE_INVALID", path + ".joinMode", "JOIN mode must be ANY or ALL"));
        List<String> branches = strings(node.get("branches"));
        if (branches.isEmpty() || branches.stream().anyMatch(RoutingGraphValidator::blank))
            issues.add(issue("TODO_ROUTE_JOIN_BRANCHES_REQUIRED", path + ".branches", "JOIN requires nonblank branches"));
        else if (new HashSet<>(branches).size() != branches.size())
            issues.add(issue("TODO_ROUTE_JOIN_BRANCH_DUPLICATE", path + ".branches", "JOIN branches must be unique"));
    }

    private void validateLoop(Map<String, Object> node, String path, List<ValidationIssue> issues)
    {
        Object max = node.get("maxOccurrences");Object end = node.get("endCondition");
        if (max == null && end == null)
            issues.add(issue("TODO_ROUTE_LOOP_UNBOUNDED", path, "LOOP requires maxOccurrences or endCondition"));
        if (max != null && positive(max) == null)
            issues.add(issue("TODO_ROUTE_LOOP_MAX_INVALID", path + ".maxOccurrences", "maxOccurrences must be positive"));
        if (end != null) validateCondition(end, path + ".endCondition", issues);
    }

    private void validateEdge(Map<String, Object> edge, int index,
            Map<String, Map<String, Object>> nodes, Set<String> edgeKeys,
            Map<String, List<Map<String, Object>>> outgoing, List<ValidationIssue> issues)
    {
        String path = "routing.edges[" + index + "]";
        String key = text(edge.get("key"));String from = text(edge.get("from"));String to = text(edge.get("to"));
        if (blank(key)) issues.add(issue("TODO_ROUTE_EDGE_KEY_REQUIRED", path + ".key", "Edge key is required"));
        else if (!edgeKeys.add(key)) issues.add(issue("TODO_ROUTE_EDGE_KEY_DUPLICATE", path + ".key", "Edge key must be unique"));
        if (!nodes.containsKey(from) || !nodes.containsKey(to))
            issues.add(issue("TODO_ROUTE_EDGE_NODE_NOT_FOUND", path, "Edge endpoints must reference existing nodes"));
        if (nodes.containsKey(from)) outgoing.computeIfAbsent(from, ignored -> new ArrayList<>()).add(edge);
        if (edge.containsKey("condition")) validateCondition(edge.get("condition"), path + ".condition", issues);
        if (edge.containsKey("priority") && integer(edge.get("priority")) == null)
            issues.add(issue("TODO_ROUTE_EDGE_PRIORITY_INVALID", path + ".priority", "Edge priority must be an integer"));
    }

    private void validateNodeEdges(Map<String, Map<String, Object>> nodes,
            Map<String, List<Map<String, Object>>> outgoing, List<ValidationIssue> issues)
    {
        nodes.forEach((key, node) -> {
            String type = text(node.get("type"));List<Map<String, Object>> edges = outgoing.getOrDefault(key, List.of());
            if ("END".equals(type) && !edges.isEmpty())
                issues.add(issue("TODO_ROUTE_END_HAS_OUTGOING", "routing.nodes." + key, "END cannot have outgoing edges"));
            if (!"END".equals(type) && edges.isEmpty())
                issues.add(issue("TODO_ROUTE_NODE_DEAD_END", "routing.nodes." + key, "Non-END node cannot be a dead end"));
            if ("DECISION".equals(type))
            {
                long defaults = edges.stream().filter(edge -> bool(edge.get("default"))).count();
                if (defaults != 1) issues.add(issue("TODO_ROUTE_DECISION_DEFAULT_INVALID", "routing.nodes." + key, "DECISION requires exactly one default edge"));
                if (edges.stream().anyMatch(edge -> !bool(edge.get("default")) && !edge.containsKey("condition")))
                    issues.add(issue("TODO_ROUTE_DECISION_CONDITION_REQUIRED", "routing.nodes." + key, "Non-default DECISION edges require conditions"));
            }
            if ("FORK".equals(type))
            {
                List<String> branches = edges.stream().map(edge -> text(edge.get("branchKey"))).toList();
                if (branches.stream().anyMatch(RoutingGraphValidator::blank) || new HashSet<>(branches).size() != branches.size())
                    issues.add(issue("TODO_ROUTE_FORK_BRANCH_INVALID", "routing.nodes." + key, "FORK edges require unique branchKey values"));
            }
            if ("LOOP".equals(type))
            {
                Set<String> branches = new HashSet<>(edges.stream().map(edge -> text(edge.get("branchKey"))).toList());
                if (edges.size() != 2 || !branches.equals(Set.of("BODY", "EXIT")))
                    issues.add(issue("TODO_ROUTE_LOOP_EDGES_INVALID", "routing.nodes." + key, "LOOP requires exactly BODY and EXIT edges"));
            }
        });
    }

    private void validateReachability(String start, Map<String, Map<String, Object>> nodes,
            Map<String, List<Map<String, Object>>> outgoing, List<ValidationIssue> issues)
    {
        if (!nodes.containsKey(start)) return;
        Set<String> reached = new HashSet<>();ArrayDeque<String> queue = new ArrayDeque<>();queue.add(start);
        while (!queue.isEmpty())
        {
            String key = queue.remove();if (!reached.add(key)) continue;
            outgoing.getOrDefault(key, List.of()).stream().map(edge -> text(edge.get("to"))).filter(nodes::containsKey).forEach(queue::add);
        }
        nodes.keySet().stream().filter(key -> !reached.contains(key)).forEach(key ->
                issues.add(issue("TODO_ROUTE_NODE_UNREACHABLE", "routing.nodes." + key, "Node is not reachable from start")));
    }

    private void validateCycles(Map<String, Map<String, Object>> nodes,
            Map<String, List<Map<String, Object>>> outgoing, List<ValidationIssue> issues)
    {
        Map<String, Integer> index = new HashMap<>(), low = new HashMap<>();
        ArrayDeque<String> stack = new ArrayDeque<>();Set<String> onStack = new HashSet<>();int[] next = {0};
        for (String node : nodes.keySet()) if (!index.containsKey(node))
            strong(node, nodes, outgoing, index, low, stack, onStack, next, issues);
    }

    private void strong(String node, Map<String, Map<String, Object>> nodes,
            Map<String, List<Map<String, Object>>> outgoing, Map<String, Integer> index,
            Map<String, Integer> low, ArrayDeque<String> stack, Set<String> onStack,
            int[] next, List<ValidationIssue> issues)
    {
        index.put(node, next[0]);low.put(node, next[0]++);stack.push(node);onStack.add(node);
        for (Map<String, Object> edge : outgoing.getOrDefault(node, List.of()))
        {
            if ("LOOP".equals(text(nodes.get(node).get("type"))) && "BODY".equals(text(edge.get("branchKey")))) continue;
            String target = text(edge.get("to"));if (!nodes.containsKey(target)) continue;
            if (!index.containsKey(target)) { strong(target, nodes, outgoing, index, low, stack, onStack, next, issues);low.put(node, Math.min(low.get(node), low.get(target))); }
            else if (onStack.contains(target)) low.put(node, Math.min(low.get(node), index.get(target)));
        }
        if (!low.get(node).equals(index.get(node))) return;
        List<String> component = new ArrayList<>();String current;
        do { current = stack.pop();onStack.remove(current);component.add(current); } while (!current.equals(node));
        boolean self = outgoing.getOrDefault(node, List.of()).stream()
                .filter(edge -> !("LOOP".equals(text(nodes.get(node).get("type"))) && "BODY".equals(text(edge.get("branchKey")))))
                .anyMatch(edge -> node.equals(text(edge.get("to"))));
        if (component.size() > 1 || self)
            issues.add(issue("TODO_ROUTE_CYCLE_UNCONTROLLED", "routing", "Cycles must be controlled by a LOOP node"));
    }

    private void validateForkReconvergence(Map<String, Map<String, Object>> nodes,
            Map<String, List<Map<String, Object>>> outgoing, List<ValidationIssue> issues)
    {
        nodes.forEach((key,node)->{
            if(!"FORK".equals(text(node.get("type"))))return;
            Set<String> claimed=new HashSet<>();
            for(Map<String,Object> edge:outgoing.getOrDefault(key,List.of()))
            {
                Set<String> branchNodes=new HashSet<>();
                collectUntilJoin(text(edge.get("to")),nodes,outgoing,branchNodes,new HashSet<>());
                Set<String> intersection=new HashSet<>(branchNodes);intersection.retainAll(claimed);
                if(!intersection.isEmpty())
                {
                    issues.add(issue("TODO_ROUTE_FORK_RECONVERGENCE_INVALID","routing.nodes."+key,
                            "FORK branches may reconverge only at an explicit JOIN"));
                    return;
                }
                claimed.addAll(branchNodes);
            }
        });
    }

    private void collectUntilJoin(String key,Map<String,Map<String,Object>> nodes,
            Map<String,List<Map<String,Object>>> outgoing,Set<String> result,Set<String> visited)
    {
        if(!visited.add(key)||!nodes.containsKey(key))return;
        String type=text(nodes.get(key).get("type"));
        if("JOIN".equals(type)||"END".equals(type))return;
        result.add(key);
        for(Map<String,Object> edge:outgoing.getOrDefault(key,List.of()))
            collectUntilJoin(text(edge.get("to")),nodes,outgoing,result,visited);
    }

    private void validateJoinBranchFlow(String start,Map<String,Map<String,Object>> nodes,
            Map<String,List<Map<String,Object>>> outgoing,List<ValidationIssue> issues)
    {
        if(!nodes.containsKey(start)||"JOIN".equals(text(nodes.get(start).get("type"))))return;
        final String noBranch="\u0000";
        Map<String,Set<JoinArrival>> arrivals=new HashMap<>();
        Map<String,Set<String>> provenancesByNode=new HashMap<>();
        ArrayDeque<BranchFlow> queue=new ArrayDeque<>();
        Set<BranchFlow> visited=new HashSet<>();
        queue.add(new BranchFlow(start,noBranch,"ROOT",null,null,null));
        while(!queue.isEmpty())
        {
            BranchFlow flow=queue.remove();
            if(!visited.add(flow)||!nodes.containsKey(flow.nodeKey()))continue;
            String type=text(nodes.get(flow.nodeKey()).get("type"));
            if("END".equals(type))continue;
            if("JOIN".equals(type))
            {
                arrivals.computeIfAbsent(flow.nodeKey(),ignored->new HashSet<>()).add(new JoinArrival(
                        flow.branchKey(),flow.provenance(),flow.sourceForkKey(),flow.sourceEdgeKey(),flow.sourceParentProvenance()));
                String outputProvenance=completeParallelBlock(flow,nodes.get(flow.nodeKey()),flow.nodeKey(),nodes,outgoing)
                        ?flow.sourceParentProvenance():"JOIN:"+flow.nodeKey();
                for(Map<String,Object> edge:outgoing.getOrDefault(flow.nodeKey(),List.of()))
                    queue.add(new BranchFlow(text(edge.get("to")),noBranch,outputProvenance,null,null,null));
                continue;
            }
            provenancesByNode.computeIfAbsent(flow.nodeKey(),ignored->new HashSet<>()).add(flow.provenance());
            for(Map<String,Object> edge:outgoing.getOrDefault(flow.nodeKey(),List.of()))
            {
                boolean fork="FORK".equals(type);
                String branch=fork?text(edge.get("branchKey")):flow.branchKey();
                String provenance=fork?flow.provenance()+"|"+flow.nodeKey()+":"+text(edge.get("key")):flow.provenance();
                queue.add(new BranchFlow(text(edge.get("to")),blank(branch)?noBranch:branch,provenance,
                        fork?flow.nodeKey():flow.sourceForkKey(),fork?text(edge.get("key")):flow.sourceEdgeKey(),
                        fork?flow.provenance():flow.sourceParentProvenance()));
            }
        }
        provenancesByNode.forEach((key,provenances)->{
            if(provenances.size()>1)
                issues.add(issue("TODO_ROUTE_TOKEN_RECONVERGENCE_INVALID","routing.nodes."+key,
                        "Concurrent route tokens may reconverge only at an explicit JOIN"));
        });
        nodes.forEach((key,node)->{
            if(!"JOIN".equals(text(node.get("type")))||key.equals(start))return;
            Set<String> expected=new HashSet<>(strings(node.get("branches")));
            Set<JoinArrival> reachable=arrivals.getOrDefault(key,Set.of());
            Set<String> actual=new HashSet<>();
            reachable.forEach(arrival->actual.add(arrival.branchKey()));
            boolean duplicated=expected.stream().anyMatch(branch->reachable.stream()
                    .filter(arrival->branch.equals(arrival.branchKey())).map(JoinArrival::provenance).distinct().count()!=1);
            Set<String> sourceForks=new HashSet<>();
            reachable.stream().map(JoinArrival::sourceForkKey).forEach(sourceForks::add);
            if(!actual.equals(expected)||duplicated||sourceForks.size()!=1||sourceForks.contains(null))
                issues.add(issue("TODO_ROUTE_JOIN_BRANCH_FLOW_INVALID","routing.nodes."+key+".branches",
                        "JOIN branches must exactly match the branch tokens that can arrive"));
            if("ALL".equals(text(node.get("joinMode")))&&reachable.stream().anyMatch(arrival->
                    !arrivalGuaranteed(arrival,key,nodes,outgoing)))
                issues.add(issue("TODO_ROUTE_JOIN_ALL_NOT_GUARANTEED","routing.nodes."+key+".branches",
                        "Every ALL JOIN branch must be unconditional and must reach this JOIN on every path"));
        });
    }

    private boolean arrivalGuaranteed(JoinArrival arrival,String joinKey,Map<String,Map<String,Object>> nodes,
            Map<String,List<Map<String,Object>>> outgoing)
    {
        if(arrival.sourceForkKey()==null||arrival.sourceEdgeKey()==null)return false;
        Map<String,Object> source=outgoing.getOrDefault(arrival.sourceForkKey(),List.of()).stream()
                .filter(edge->arrival.sourceEdgeKey().equals(text(edge.get("key")))).findFirst().orElse(null);
        return source!=null&&!source.containsKey("condition")&&allPathsReachJoin(text(source.get("to")),joinKey,
                nodes,outgoing,new HashSet<>(),new HashMap<>());
    }

    private boolean completeParallelBlock(BranchFlow flow,Map<String,Object> join,String joinKey,
            Map<String,Map<String,Object>> nodes,Map<String,List<Map<String,Object>>> outgoing)
    {
        if(flow.sourceForkKey()==null||flow.sourceParentProvenance()==null)return false;
        List<Map<String,Object>> sourceEdges=outgoing.getOrDefault(flow.sourceForkKey(),List.of());
        Set<String> produced=new HashSet<>();
        sourceEdges.stream().map(edge->text(edge.get("branchKey"))).filter(branch->!blank(branch)).forEach(produced::add);
        Set<String> expected=new HashSet<>(strings(join.get("branches")));
        return !sourceEdges.isEmpty()&&produced.equals(expected)&&sourceEdges.stream().allMatch(edge->
                allPathsReachJoin(text(edge.get("to")),joinKey,nodes,outgoing,new HashSet<>(),new HashMap<>()));
    }

    private boolean allPathsReachJoin(String current,String joinKey,Map<String,Map<String,Object>> nodes,
            Map<String,List<Map<String,Object>>> outgoing,Set<String> visiting,Map<String,Boolean> memo)
    {
        if(joinKey.equals(current))return true;
        if(memo.containsKey(current))return memo.get(current);
        Map<String,Object> node=nodes.get(current);
        if(node==null||"END".equals(text(node.get("type")))||"JOIN".equals(text(node.get("type")))
                ||"FORK".equals(text(node.get("type"))))return false;
        if(!visiting.add(current))return true;
        List<Map<String,Object>> edges=outgoing.getOrDefault(current,List.of());
        boolean result=!edges.isEmpty()&&edges.stream().allMatch(edge->allPathsReachJoin(text(edge.get("to")),joinKey,
                nodes,outgoing,visiting,memo));
        visiting.remove(current);memo.put(current,result);return result;
    }

    private record BranchFlow(String nodeKey,String branchKey,String provenance,String sourceForkKey,String sourceEdgeKey,
            String sourceParentProvenance) { }
    private record JoinArrival(String branchKey,String provenance,String sourceForkKey,String sourceEdgeKey,
            String sourceParentProvenance) { }

    @SuppressWarnings("unchecked")
    private void validateCondition(Object value, String path, List<ValidationIssue> issues)
    {
        try
        {
            if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("Condition must be an object");
            conditions.decodeCanonical((Map<String, ?>) map);
        }
        catch (IllegalArgumentException invalid)
        { issues.add(issue("TODO_ROUTE_CONDITION_INVALID", path, invalid.getMessage())); }
    }

    private static List<Map<String, Object>> objects(Object value, String path, List<ValidationIssue> issues)
    {
        if (!(value instanceof List<?> list)) { issues.add(issue("TODO_ROUTE_ARRAY_REQUIRED", path, "Expected an array"));return List.of(); }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list)
        {
            if (!(item instanceof Map<?, ?> map)) { issues.add(issue("TODO_ROUTE_OBJECT_REQUIRED", path, "Array items must be objects"));continue; }
            Map<String, Object> copy = new LinkedHashMap<>();map.forEach((key, entry) -> { if (key instanceof String text) copy.put(text, entry); });result.add(copy);
        }
        return result;
    }

    private static List<String> strings(Object value) { if (!(value instanceof List<?> list)) return List.of();return list.stream().map(RoutingGraphValidator::text).toList(); }
    private static Long positive(Object value) { try { long parsed = Long.parseLong(String.valueOf(value));return parsed > 0 ? parsed : null; } catch (RuntimeException invalid) { return null; } }
    private static Integer integer(Object value) { try { return Integer.valueOf(String.valueOf(value)); } catch (RuntimeException invalid) { return null; } }
    private static boolean bool(Object value) { return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value)); }
    private static String text(Object value) { return value == null ? null : String.valueOf(value); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static ValidationIssue issue(String code, String path, String message) { return new ValidationIssue(code, path, message); }
}
