package com.law.todo.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.law.todo.application.TodoSimulationEffectResolver.EffectKind;
import com.law.todo.application.TodoSimulationEffectResolver.SimulationEffect;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoConfigurationCommands.JourneySimulationCommand;
import com.law.todo.application.command.TodoConfigurationCommands.ScenarioSimulationCommand;
import com.law.todo.application.command.TodoDefinitionCommands.VirtualTaskCompletionSample;
import com.law.todo.application.view.TodoConfigurationJourneyView;
import com.law.todo.application.view.TodoJourneySimulationResult;
import com.law.todo.application.view.TodoSimulationScenarioViews.BatchScenarioResult;
import com.law.todo.application.view.TodoSimulationScenarioViews.ScenarioSimulationResult;
import com.law.todo.application.view.TodoSimulationScenarioViews.SimulationEvidenceSummary;
import com.law.todo.application.view.TodoSimulationScenarioViews.SimulationScenario;
import com.law.todo.definition.codec.TodoDefinitionCodec;
import com.law.todo.definition.model.TodoDefinitionDocument;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoConfigurationMapper;
import com.law.todo.spi.TodoCompletionHandler;
import com.law.todo.spi.TodoCompletionHandler.SimulationResult;

@Service
public class TodoSimulationScenarioService
{
    private static final Set<String> TERMINAL_OR_NONBLOCKING_ROUTE_STATUSES=Set.of(
            "ENDED","VISITED","ADVANCED","ALREADY_ADVANCED","VIRTUAL_COMPLETED");

    private final TodoSimulationScenarioCatalog catalog;
    private final TodoConfigurationJourneyService journeys;
    private final TodoJourneySimulationService simulations;
    private final TodoSimulationEvidenceService evidence;
    private final TodoConfigurationMapper mapper;
    private final List<TodoCompletionHandler> handlers;
    private final TodoDefinitionCodec codec=new TodoDefinitionCodec();
    private final TodoSimulationEffectResolver effects=new TodoSimulationEffectResolver();

    public TodoSimulationScenarioService(TodoSimulationScenarioCatalog catalog,
            TodoConfigurationJourneyService journeys,TodoJourneySimulationService simulations,
            TodoSimulationEvidenceService evidence,TodoConfigurationMapper mapper,
            List<TodoCompletionHandler> handlers)
    {
        this.catalog=catalog;this.journeys=journeys;this.simulations=simulations;
        this.evidence=evidence;this.mapper=mapper;
        this.handlers=handlers==null?List.of():List.copyOf(handlers);
    }

    @Transactional(readOnly=true)
    public List<SimulationScenario> scenarios(long templateId,Actor actor)
    {
        TodoConfigurationJourneyView journey=journeys.load(templateId,actor);
        return catalog.scenarios(journey.template().templateCode(),journey.template().businessType());
    }

    @Transactional
    public ScenarioSimulationResult simulate(long templateId,String scenarioCode,
            ScenarioSimulationCommand command,Actor actor)
    {
        TodoConfigurationJourneyView journey=journeys.load(templateId,actor);
        requireExactVersion(journey,command);
        List<SimulationScenario> governed=catalog.scenarios(journey.template().templateCode(),
                journey.template().businessType());
        SimulationScenario scenario=governed.stream().filter(item->item.scenarioCode().equals(scenarioCode))
                .findFirst().orElseThrow(()->new TodoException("TODO_SIMULATION_SCENARIO_NOT_FOUND",
                        "TODO_SIMULATION_SCENARIO_NOT_FOUND: Scenario not found"));
        return simulateWithJourney(templateId,scenario,command,actor,journey);
    }

    @Transactional
    public BatchScenarioResult simulateRequired(long templateId,ScenarioSimulationCommand command,Actor actor)
    {
        TodoConfigurationJourneyView journey=journeys.load(templateId,actor);
        requireExactVersion(journey,command);
        List<SimulationScenario> governed=catalog.scenarios(journey.template().templateCode(),
                journey.template().businessType());
        List<ScenarioSimulationResult> results=new ArrayList<>();
        for(SimulationScenario scenario:governed)
            if(scenario.requiredForPublish())
                results.add(simulateWithJourney(templateId,scenario,command,actor,journey));
        var gate=evidence.gate(templateId,command.versionId(),command.definitionHash(),governed);
        return new BatchScenarioResult(results,gate.publicationReady(),gate.blockingScenarioCodes());
    }

    private ScenarioSimulationResult simulateWithJourney(long templateId,SimulationScenario scenario,
            ScenarioSimulationCommand command,Actor actor,TodoConfigurationJourneyView journey)
    {
        ScenarioExecution execution;
        try
        {
            TodoDefinitionDocument definition=codec.read(journeys.canonicalDefinition(templateId,actor));
            Map<String,Object> completion=resolve(scenario.completionPayload(),command);
            SimulationResult businessOutcome=simulateBusinessOutcome(journey,completion,command);
            completion.putAll(businessOutcome.routingPayload());
            VirtualTaskCompletionSample sample=new VirtualTaskCompletionSample(completionNodeKey(definition,
                    scenario.completionNodeKey()),engineOccurrence(scenario.occurrence()),completion,
                    command.effectiveAt());
            TodoJourneySimulationResult simulation=simulations.simulate(new JourneySimulationCommand(
                    templateId,command.versionId(),definition.event().eventType(),
                    definition.event().payloadVersion(),command.businessType(),command.businessId(),
                    command.manualOverrides(),command.effectiveAt(),List.of(sample),command.definitionHash()),actor);
            String generated=actualNext(simulation,businessOutcome);
            boolean hashMatches=scenarioSimulationSucceeded(simulation,command.definitionHash());
            SimulationEffect actual=effects.actual(generated,journey.resources().businessOutcomeSet(),
                    completion,normalGraphEnded(simulation,hashMatches));
            boolean passed=effects.matches(scenario.expectedEffect(),actual,hashMatches,
                    scenario.expectedErrorCode());
            execution=new ScenarioExecution(actual,passed,
                    passed?"场景验证通过":"实际效果与预期不一致",simulation,
                    simulation.trace().stream().map(trace->trace.code()+":"+trace.status()).toList());
        }
        catch(RuntimeException failure)
        {
            SimulationEffect actual=effects.validationFailure(failure);
            boolean passed=effects.matches(scenario.expectedEffect(),actual,true,scenario.expectedErrorCode());
            execution=new ScenarioExecution(actual,passed,
                    passed?"场景按预期校验失败":failure.getMessage(),null,
                    List.of("VALIDATION:"+(actual.actionCode()==null?"UNKNOWN":actual.actionCode())));
        }
        SimulationEvidenceSummary stored=evidence.record(templateId,scenario,command,
                execution.actual(),execution.passed(),execution.traceCodes(),actor);
        return result(scenario,execution.actual(),execution.passed(),execution.message(),
                execution.simulation(),stored);
    }

    private record ScenarioExecution(SimulationEffect actual,boolean passed,String message,
            TodoJourneySimulationResult simulation,List<String> traceCodes) { }

    private ScenarioSimulationResult result(SimulationScenario scenario,SimulationEffect actual,
            boolean passed,String message,TodoJourneySimulationResult simulation,
            SimulationEvidenceSummary stored)
    {
        String actualNext=actual!=null&&actual.kind()==EffectKind.NEXT_TEMPLATE
                ?actual.targetTemplateCode():null;
        return new ScenarioSimulationResult(scenario.scenarioCode(),scenario.scenarioName(),
                scenario.expectedNextTemplateCode(),actualNext,scenario.expectedEffect(),actual,
                passed,message,simulation,stored);
    }

    private void requireExactVersion(TodoConfigurationJourneyView journey,ScenarioSimulationCommand command)
    {
        if(journey.template().versionId()!=command.versionId()
                ||!command.definitionHash().equals(journey.template().definitionHash()))
            throw new TodoException("TODO_TEMPLATE_PREFLIGHT_STALE",
                    "Definition changed after preflight; rerun preflight and scenarios");
    }

    private String actualNext(TodoJourneySimulationResult result,SimulationResult businessOutcome)
    {
        String routed=result.engine().routes().stream()
                .filter(route->route.templateVersionId()!=null
                        &&("MATCHED".equals(route.status())||"SELECTED".equals(route.status())
                                ||"PENDING_COMPLETION".equals(route.status())))
                .map(route->mapper.selectTemplateCodeByVersionId(route.templateVersionId()))
                .filter(code->code!=null&&!code.isBlank()).findFirst().orElse(null);
        return routed!=null?routed:businessOutcome.producedTemplateCodes().stream()
                .filter(code->code!=null&&!code.isBlank()).findFirst().orElse(null);
    }

    private boolean scenarioSimulationSucceeded(TodoJourneySimulationResult result,String definitionHash)
    {
        return result!=null&&result.engine()!=null
                &&definitionHash.equals(result.engine().definitionHash())
                &&result.engine().trigger()!=null
                &&"MATCHED".equals(result.engine().trigger().status())
                &&result.engine().issues().stream().noneMatch(issue->"ERROR".equals(issue.severity()));
    }

    private boolean normalGraphEnded(TodoJourneySimulationResult result,boolean simulationSucceeded)
    {
        if(!simulationSucceeded||result==null||result.engine()==null)return false;
        var routes=result.engine().routes();
        if(routes.isEmpty())return false;
        boolean allTerminalOrNonblocking=routes.stream()
                .allMatch(route->terminalOrNonblocking(route.status()));
        return allTerminalOrNonblocking&&routes.stream().anyMatch(route->"END".equals(route.nodeType())
                &&"ENDED".equals(normalizeStatus(route.status())));
    }

    private boolean terminalOrNonblocking(String status)
    {
        String normalized=normalizeStatus(status);
        return normalized!=null&&!normalized.isEmpty()
                &&TERMINAL_OR_NONBLOCKING_ROUTE_STATUSES.contains(normalized);
    }

    private String normalizeStatus(String status)
    {return status==null?null:status.trim().toUpperCase(Locale.ROOT);}

    private SimulationResult simulateBusinessOutcome(TodoConfigurationJourneyView journey,
            Map<String,Object> completion,ScenarioSimulationCommand command)
    {
        TodoInstance todo=new TodoInstance();
        todo.setTemplateId(journey.template().templateId());
        todo.setTemplateVersionId(journey.template().versionId());
        todo.setTemplateCode(journey.template().templateCode());
        todo.setBusinessType(command.businessType());
        todo.setBusinessId(command.businessId());
        return handlers.stream().filter(handler->handler.supportsSimulation()&&handler.supports(todo))
                .findFirst().map(handler->handler.simulate(todo,completion))
                .orElseGet(()->SimulationResult.none(completion));
    }

    private String completionNodeKey(TodoDefinitionDocument definition,String reference)
    {
        Map<String,Object> routing=definition.routing().config();
        Object rawNodes=routing.get("nodes");
        if(!(rawNodes instanceof List<?> nodes)||nodes.isEmpty())return reference;
        List<Map<?,?>> taskNodes=new ArrayList<>();
        for(Object value:nodes)
            if(value instanceof Map<?,?> node&&"TASK".equals(String.valueOf(node.get("type"))))
                taskNodes.add(node);
        String exact=taskNodes.stream().filter(node->reference.equals(String.valueOf(node.get("key"))))
                .map(node->String.valueOf(node.get("key"))).findFirst().orElse(null);
        if(exact!=null)return exact;
        String start=String.valueOf(routing.get("start"));
        String startMatch=taskNodes.stream()
                .filter(node->start.equals(String.valueOf(node.get("key")))
                        &&referencesTemplate(node,reference))
                .map(node->String.valueOf(node.get("key"))).findFirst().orElse(null);
        if(startMatch!=null)return startMatch;
        List<String> matches=taskNodes.stream().filter(node->referencesTemplate(node,reference))
                .map(node->String.valueOf(node.get("key"))).distinct().toList();
        if(matches.size()==1)return matches.get(0);
        throw new TodoException("TODO_SIMULATION_SCENARIO_NODE_UNRESOLVED",
                "TODO_SIMULATION_SCENARIO_NODE_UNRESOLVED: Completion node reference "+reference
                        +" does not resolve to one task node");
    }

    private boolean referencesTemplate(Map<?,?> node,String reference)
    {
        Object configuredCode=node.get("templateCode");
        if(configuredCode!=null&&reference.equals(String.valueOf(configuredCode)))return true;
        Object configuredVersion=node.get("templateVersionId");
        if(configuredVersion==null)return false;
        try
        {
            String resolved=mapper.selectTemplateCodeByVersionId(Long.parseLong(String.valueOf(configuredVersion)));
            return reference.equals(resolved);
        }
        catch(NumberFormatException ignored){return false;}
    }

    /** Governed scenario resources use user-facing one-based occurrences; the route engine is zero-based. */
    private int engineOccurrence(int governedOccurrence)
    {return Math.max(0,governedOccurrence-1);}

    private Map<String,Object> resolve(Map<String,Object> source,ScenarioSimulationCommand command)
    {
        Map<String,Object> result=new LinkedHashMap<>();
        source.forEach((key,value)->result.put(key,resolveValue(value,command)));
        for(Map.Entry<String,Object> override:command.manualOverrides().entrySet())
            if(result.containsKey(override.getKey()))result.put(override.getKey(),override.getValue());
        return result;
    }

    private Object resolveValue(Object value,ScenarioSimulationCommand command)
    {
        if("${SIMULATION_NOW}".equals(value))return command.effectiveAt().toString();
        if(value instanceof Map<?,?> map)
        {
            Map<String,Object> nested=new LinkedHashMap<>();
            map.forEach((key,item)->nested.put(String.valueOf(key),resolveValue(item,command)));
            return nested;
        }
        if(value instanceof List<?> list)return list.stream().map(item->resolveValue(item,command)).toList();
        return value;
    }
}
