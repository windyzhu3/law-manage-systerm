package com.law.todo.application;

import java.util.List;

import org.springframework.stereotype.Component;

import com.law.todo.application.view.TodoConfigurationJourneyView.EmployeeTodoPreview;
import com.law.todo.application.view.TodoConfigurationViews.TemplateConfigurationDetail;
import com.law.todo.definition.model.TodoDefinitionDocument;

/** Baseline placeholder; employee-facing projection is supplied in the next phase. */
@Component
public class TodoEmployeeTodoPreviewProjector
{
    public EmployeeTodoPreview project(TemplateConfigurationDetail detail,TodoDefinitionDocument definition)
    {return new EmployeeTodoPreview(null,null,List.of(),List.of(),List.of(),null);}
}
