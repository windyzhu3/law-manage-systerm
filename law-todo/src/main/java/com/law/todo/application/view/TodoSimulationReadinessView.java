package com.law.todo.application.view;

import java.util.List;

import com.law.todo.application.TodoSimulationEvidenceService.SimulationGateBlocker;
import com.law.todo.application.view.TodoConfigurationJourneyView.JourneyIssue;

public record TodoSimulationReadinessView(
        long templateId,
        long versionId,
        String definitionHash,
        int requiredScenarioCount,
        int passedScenarioCount,
        List<SimulationGateBlocker> blockingScenarios,
        boolean fullSimulationPassed,
        boolean publicationReady,
        List<JourneyIssue> issues)
{
    public TodoSimulationReadinessView
    {
        blockingScenarios=blockingScenarios==null?List.of():List.copyOf(blockingScenarios);
        issues=issues==null?List.of():List.copyOf(issues);
    }
}
