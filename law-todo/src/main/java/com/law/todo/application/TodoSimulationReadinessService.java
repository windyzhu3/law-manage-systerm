package com.law.todo.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.law.todo.application.TodoSimulationEvidenceService.PublicationGate;
import com.law.todo.application.TodoSimulationEvidenceService.SimulationGateBlocker;
import com.law.todo.application.view.TodoConfigurationJourneyView.JourneyIssue;
import com.law.todo.application.view.TodoSimulationReadinessView;
import com.law.todo.application.view.TodoSimulationScenarioViews.SimulationScenario;

@Service
public class TodoSimulationReadinessService
{
    public static final String FULL_SIMULATION="FULL_SIMULATION";
    public static final int FULL_SIMULATION_VERSION=1;

    private final TodoSimulationScenarioCatalog scenarios;
    private final TodoSimulationEvidenceService evidence;

    public TodoSimulationReadinessService(TodoSimulationScenarioCatalog scenarios,
            TodoSimulationEvidenceService evidence)
    {
        this.scenarios=scenarios;
        this.evidence=evidence;
    }

    @Transactional(readOnly=true)
    public TodoSimulationReadinessView readiness(long templateId,long versionId,
            String definitionHash,String templateCode,String businessType)
    {
        List<SimulationScenario> catalog=scenarios.scenarios(templateCode,businessType);
        List<SimulationScenario> required=catalog.stream()
                .filter(SimulationScenario::requiredForPublish)
                .filter(scenario->"ACTIVE".equals(scenario.status()))
                .toList();
        PublicationGate scenarioGate=evidence.gate(
                templateId,versionId,definitionHash,required);
        boolean fullReady=evidence.hasPassingEvidence(templateId,versionId,
                definitionHash,FULL_SIMULATION,FULL_SIMULATION_VERSION);

        List<JourneyIssue> issues=new ArrayList<>();
        if(!scenarioGate.publicationReady())
        {
            Map<String,String> names=new LinkedHashMap<>();
            required.forEach(scenario->names.put(scenario.scenarioCode(),scenario.scenarioName()));
            String scenarioNames=scenarioGate.blockers().stream()
                    .map(SimulationGateBlocker::scenarioCode)
                    .map(code->names.getOrDefault(code,code))
                    .collect(Collectors.joining("、"));
            issues.add(new JourneyIssue(
                    "TODO_REQUIRED_SIMULATION_SCENARIOS_INCOMPLETE",
                    "BLOCKER",
                    "SIMULATION_PUBLISH",
                    "simulation.scenarios",
                    "还有必测场景未通过："+scenarioNames,
                    "验证未通过场景"));
        }
        if(!fullReady)
        {
            issues.add(new JourneyIssue(
                    "TODO_FULL_SIMULATION_REQUIRED",
                    "BLOCKER",
                    "SIMULATION_PUBLISH",
                    "simulation.full",
                    "完整试运行尚未通过",
                    "运行完整试运行"));
        }

        int passed=Math.max(0,required.size()-scenarioGate.blockers().size());
        boolean publicationReady=scenarioGate.publicationReady()&&fullReady;
        return new TodoSimulationReadinessView(templateId,versionId,definitionHash,
                required.size(),passed,scenarioGate.blockers(),fullReady,
                publicationReady,issues);
    }
}
