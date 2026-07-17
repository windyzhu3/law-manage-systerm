package com.law.todo.application.view;

import java.util.List;
import java.util.Map;

import com.law.todo.definition.model.TodoDefinitionDocument.DodRule;
import com.law.todo.definition.model.TodoDefinitionDocument.UiSchema;
import java.time.LocalDateTime;

public record TodoFormView(Long todoId, String action, String businessType, Long businessId,
        UiSchema ui, DodRule dod, Map<String,Object> defaults, List<MaterialState> materials,
        ExtensionPolicyView extensionPolicy, String definitionHash)
{
    public TodoFormView
    {
        defaults = defaults == null ? Map.of() : java.util.Collections.unmodifiableMap(
                new java.util.LinkedHashMap<>(defaults));
        materials = materials == null ? List.of() : List.copyOf(materials);
    }

    public record MaterialState(Long fileObjectId, String materialType, String fileName) { }
    public record ExtensionPolicyView(Long policyVersionId,int maxExtensionCount,int approvedExtensionCount,
        int remainingRequestCount,long maxExtensionValue,String maxExtensionUnit,boolean proofRequired,
        String pendingSlaMode,LocalDateTime currentDueAt) { }
}
