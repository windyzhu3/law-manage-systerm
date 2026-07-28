package com.law.todo.application.view;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import com.law.todo.application.TodoTemplateFieldUsageService.FieldUsage;

/** Business-facing, stage-grouped read model for journey simulation. */
public record TodoJourneyPayloadView(
        BusinessObjectSummary businessObject,
        Map<String,Object> payload,
        List<PayloadField> eventInput,
        List<PayloadField> creationDependencies,
        List<PayloadField> completionFields,
        List<PayloadField> routingFields,
        List<PayloadField> advancedFields,
        int creationCoveragePercent,
        List<JourneyIssue> blockingIssues)
{
    public TodoJourneyPayloadView
    {
        payload=payload==null?Map.of():Map.copyOf(payload);
        eventInput=copy(eventInput);creationDependencies=copy(creationDependencies);
        completionFields=copy(completionFields);routingFields=copy(routingFields);advancedFields=copy(advancedFields);
        blockingIssues=blockingIssues==null?List.of():List.copyOf(blockingIssues);
    }

    /** Compatibility alias for clients migrating from the flat hydration response. */
    public List<PayloadField> fields()
    {
        List<PayloadField> result=new ArrayList<>();
        result.addAll(eventInput);result.addAll(creationDependencies);result.addAll(completionFields);
        result.addAll(routingFields);result.addAll(advancedFields);return List.copyOf(result);
    }
    /** Compatibility alias for clients migrating from the flat hydration response. */
    public int coveragePercent(){return creationCoveragePercent;}
    public List<String> allDefaultPaths()
    {
        LinkedHashSet<String> result=new LinkedHashSet<>();
        eventInput.forEach(field->result.add(field.path()));creationDependencies.forEach(field->result.add(field.path()));
        completionFields.forEach(field->result.add(field.path()));routingFields.forEach(field->result.add(field.path()));
        return List.copyOf(result);
    }
    private static List<PayloadField> copy(List<PayloadField> values)
    {return values==null?List.of():List.copyOf(values);}

    public record BusinessObjectSummary(String businessType,long businessId,String businessNo,String businessName,
            boolean sample) { }
    public record PayloadField(String path,String label,Object rawValue,String displayValue,String semanticType,
            String optionSource,String dictType,String source,boolean required,boolean missing,boolean sensitive,
            boolean editable,Map<String,Object> displayMeta,List<FieldUsage> usages,String issueCode,String issueMessage)
    {
        public PayloadField
        {
            displayMeta=displayMeta==null?Map.of():Map.copyOf(displayMeta);
            usages=usages==null?List.of():List.copyOf(usages);
        }
    }
    public record JourneyIssue(String code,String fieldPath,String message) { }
}
