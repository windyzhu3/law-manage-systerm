package com.law.todo.application.view;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.law.todo.application.TodoSimulationEffectResolver.EffectKind;
import com.law.todo.application.TodoSimulationEffectResolver.SimulationEffect;

public final class TodoSimulationScenarioViews
{
    private TodoSimulationScenarioViews() { }

    public record SimulationScenario(
            long resourceItemId,String scenarioCode,String templateCode,
            String scenarioName,int scenarioVersion,
            Map<String,Object> completionPayload,List<String> editableFields,
            List<String> requiredMaterials,String completionNodeKey,
            int occurrence,SimulationEffect expectedEffect,String expectedNextTemplateCode,
            String expectedErrorCode,
            boolean requiredForPublish,String status,int sortOrder)
    {
        public SimulationScenario
        {
            completionPayload=completionPayload==null?Map.of():
                    Collections.unmodifiableMap(new LinkedHashMap<>(completionPayload));
            editableFields=editableFields==null?List.of():List.copyOf(editableFields);
            requiredMaterials=requiredMaterials==null?List.of():List.copyOf(requiredMaterials);
            if(expectedEffect==null&&expectedNextTemplateCode!=null&&!expectedNextTemplateCode.isBlank())
                expectedEffect=new SimulationEffect(EffectKind.NEXT_TEMPLATE,expectedNextTemplateCode,null);
            if(expectedEffect!=null)
                expectedNextTemplateCode=expectedEffect.kind()==EffectKind.NEXT_TEMPLATE
                        ?expectedEffect.targetTemplateCode():null;
        }

        public SimulationScenario(long resourceItemId,String scenarioCode,String templateCode,
                String scenarioName,int scenarioVersion,Map<String,Object> completionPayload,
                List<String> editableFields,List<String> requiredMaterials,String completionNodeKey,
                int occurrence,String expectedNextTemplateCode,boolean requiredForPublish,
                String status,int sortOrder)
        {
            this(resourceItemId,scenarioCode,templateCode,scenarioName,scenarioVersion,
                    completionPayload,editableFields,requiredMaterials,completionNodeKey,occurrence,
                    expectedNextTemplateCode==null?null:
                            new SimulationEffect(EffectKind.NEXT_TEMPLATE,expectedNextTemplateCode,null),
                    expectedNextTemplateCode,null,requiredForPublish,status,sortOrder);
        }
    }

    public record SimulationEvidenceSummary(String scenarioCode,int scenarioVersion,String definitionHash,
            String resultStatus,String inputHash,java.time.LocalDateTime executedTime,
            java.time.LocalDateTime expireTime) { }

    public record ScenarioSimulationResult(String scenarioCode,String scenarioName,
            String expectedNextTemplateCode,String actualNextTemplateCode,
            SimulationEffect expectedEffect,SimulationEffect actualEffect,boolean passed,String message,
            TodoJourneySimulationResult simulation,SimulationEvidenceSummary evidence) { }

    public record BatchScenarioResult(List<ScenarioSimulationResult> results,boolean publicationReady,
            List<String> blockingScenarioCodes)
    {
        public BatchScenarioResult
        {
            results=results==null?List.of():List.copyOf(results);
            blockingScenarioCodes=blockingScenarioCodes==null?List.of():List.copyOf(blockingScenarioCodes);
        }
    }
}
