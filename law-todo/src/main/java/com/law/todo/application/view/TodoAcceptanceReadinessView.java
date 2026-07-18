package com.law.todo.application.view;

import java.util.List;

public record TodoAcceptanceReadinessView(String gateCode, int total, int ready, int sourceUnresolved,
        int runtimeMissing, int runtimeIncomplete, boolean gateReady, int phaseOneTemplateCount,
        int catalogAtCount, int mappingAtCount, int catalogMismatchCount, int scenarioCount,
        int approvedScenarioCount, int goldenScenarioCount, int goldenScenarioReadyCount,
        int scenarioAccountabilityCount, int mappedAtCount, int mappingAccountabilityCount,
        int inReviewAtCount, int approvedAtCount, int mockBusinessEvidenceCount,
        List<TodoAcceptanceRequirementView> requirements)
{
}
