package com.law.todo.application.view;

import java.util.List;
import java.util.Map;

import com.law.todo.definition.model.TodoDefinitionDocument.DodRule;
import com.law.todo.definition.model.TodoDefinitionDocument.UiSchema;

public record TodoFormView(Long todoId, String action, UiSchema ui, DodRule dod,
        Map<String,Object> defaults, List<MaterialState> materials, String definitionHash)
{
    public TodoFormView
    {
        defaults = defaults == null ? Map.of() : java.util.Collections.unmodifiableMap(
                new java.util.LinkedHashMap<>(defaults));
        materials = materials == null ? List.of() : List.copyOf(materials);
    }

    public record MaterialState(Long fileObjectId, String materialType, String fileName) { }
}
