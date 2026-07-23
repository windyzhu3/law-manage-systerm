package com.law.todo.application;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.law.todo.application.view.TodoConfigurationJourneyView.JourneyIssue;
import com.law.todo.application.view.TodoConfigurationJourneyView.JourneyStep;
import com.law.todo.application.view.TodoConfigurationViews.TemplateConfigurationDetail;
import com.law.todo.definition.model.TodoDefinitionDocument;

/** Baseline journey shape; authoritative journey health is supplied in the next phase. */
@Component
public class TodoConfigurationJourneyEvaluator
{
    public Evaluation evaluate(TemplateConfigurationDetail detail,TodoDefinitionDocument definition)
    {
        return new Evaluation(List.of(
                step("EVENT","Event","IN_PROGRESS"),
                step("TRIGGER","Trigger","NOT_STARTED"),
                step("OWNER","Owner","NOT_STARTED"),
                step("DOD","Definition of done","NOT_STARTED"),
                step("SLA","Service level agreement","NOT_STARTED"),
                step("ROUTING","Routing","NOT_STARTED"),
                step("SIMULATION_PUBLISH","Simulation and publish","NOT_STARTED")),List.of());
    }

    private JourneyStep step(String code,String title,String state)
    {return new JourneyStep(code,title,state,0,Map.of());}

    public record Evaluation(List<JourneyStep> steps,List<JourneyIssue> issues) { }
}
