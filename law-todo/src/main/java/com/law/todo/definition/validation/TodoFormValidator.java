package com.law.todo.definition.validation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.law.todo.definition.model.TodoDefinitionDocument;
import com.law.todo.domain.TodoException;

/** Server-side authority for definition parity and runtime form rules. */
public class TodoFormValidator
{
    public record ValidationIssue(String code, String path, String message) { }
    public record Material(Long fileObjectId, String materialType) { }

    @FunctionalInterface
    public interface MaterialResolver
    {
        List<Material> resolve(List<Long> fileObjectIds);
    }

    public List<ValidationIssue> validateDefinition(TodoDefinitionDocument definition)
    {
        if (definition == null || definition.dod() == null || definition.ui() == null)
            return List.of();
        Set<String> renderable = uiFields(definition.ui().config());
        renderable.addAll(strings(definition.dod().config().get("systemDerivedFields")));
        List<ValidationIssue> issues = new ArrayList<>();
        for (String field : referencedFields(definition.dod().config()))
            if (!renderable.contains(field))
                issues.add(new ValidationIssue("TODO_DOD_FIELD_NOT_RENDERABLE", "dod." + field,
                        "DoD field is neither rendered nor system-derived: " + field));
        return List.copyOf(issues);
    }

    public void validateSubmission(TodoDefinitionDocument definition, String action,
            Map<String,Object> fields)
    {
        validateSubmission(definition, action, fields, List.of(), ids -> List.of());
    }

    public void validateSubmission(TodoDefinitionDocument definition, String action,
            Map<String,Object> fields, List<Long> fileObjectIds, MaterialResolver resolver)
    {
        Map<String,Object> values = fields == null ? Map.of() : fields;
        List<Long> ids = fileObjectIds == null ? List.of() : List.copyOf(fileObjectIds);
        for (Long id : ids)
            if (id == null || id <= 0)
                throw new TodoException("TODO_FILE_OBJECT_ID_INVALID", "fileObjectIds must contain positive IDs");
        if (definition == null || definition.dod() == null)
            return;
        Map<String,Object> dod = definition.dod().config();
        Map<String,Object> rules = effectiveRules(dod, action);
        for (String field : strings(rules.get("requiredFields")))
            requireField(values, field);
        for (Map<String,Object> conditional : objects(rules.get("conditionalRequired")))
            if (matches(object(conditional.get("when")), values))
                requireField(values, text(conditional.get("field")));
        validateMaterials(objects(rules.get("materials")), ids, resolver);
    }

    private void validateMaterials(List<Map<String,Object>> rules, List<Long> ids,
            MaterialResolver resolver)
    {
        if (rules.isEmpty())
            return;
        List<Material> materials = Objects.requireNonNull(resolver, "material resolver").resolve(ids);
        if (materials == null)
            materials = List.of();
        for (Map<String,Object> rule : rules)
        {
            String type = text(rule.get("type"));
            long count = materials.stream().filter(value -> Objects.equals(type, value.materialType())).count();
            int minimum = integer(rule.get("minCount"), 1);
            int maximum = integer(rule.get("maxCount"), Integer.MAX_VALUE);
            if (count < minimum || count > maximum)
                throw new TodoException("TODO_DOD_MATERIAL_COUNT",
                        "Material count outside configured range: " + type);
        }
    }

    private Map<String,Object> effectiveRules(Map<String,Object> dod, String action)
    {
        Map<String,Object> result = new java.util.LinkedHashMap<>();
        if ("COMPLETE".equalsIgnoreCase(action))
            result.putAll(dod);
        Map<String,Object> actions = object(dod.get("actions"));
        Object actionRule = actions.get(action == null ? null : action.toUpperCase());
        result.putAll(object(actionRule));
        return result;
    }

    private void requireField(Map<String,Object> fields, String field)
    {
        if (field == null || !fields.containsKey(field))
            throw new TodoException("TODO_DOD_FIELD_MISSING", "Required field is missing: " + field);
        Object value = fields.get(field);
        if (value == null)
            throw new TodoException("TODO_DOD_FIELD_NULL", "Required field is null: " + field);
        if (value instanceof String text && text.isBlank())
            throw new TodoException("TODO_DOD_FIELD_BLANK", "Required field is blank: " + field);
    }

    private boolean matches(Map<String,Object> when, Map<String,Object> values)
    {
        String field = text(when.get("field"));
        if (field == null)
            return false;
        if (when.containsKey("equals"))
            return Objects.equals(values.get(field), when.get("equals"));
        if (when.containsKey("present"))
            return Boolean.parseBoolean(String.valueOf(when.get("present"))) == values.containsKey(field);
        return false;
    }

    private Set<String> referencedFields(Map<String,Object> dod)
    {
        Set<String> result = new LinkedHashSet<>(strings(dod.get("requiredFields")));
        for (Map<String,Object> rule : objects(dod.get("conditionalRequired")))
        {
            add(result, text(rule.get("field")));
            add(result, text(object(rule.get("when")).get("field")));
        }
        for (Object action : object(dod.get("actions")).values())
        {
            Map<String,Object> rules = object(action);
            result.addAll(strings(rules.get("requiredFields")));
            for (Map<String,Object> conditional : objects(rules.get("conditionalRequired")))
            {
                add(result, text(conditional.get("field")));
                add(result, text(object(conditional.get("when")).get("field")));
            }
        }
        return result;
    }

    private Set<String> uiFields(Map<String,Object> ui)
    {
        Set<String> result = new LinkedHashSet<>();
        for (Object entry : list(ui.get("fields")))
        {
            if (entry instanceof String value)
                add(result, value);
            else if (entry instanceof Map<?,?> value)
            {
                Object key = value.containsKey("key") ? value.get("key")
                        : value.containsKey("name") ? value.get("name") : value.get("field");
                add(result, text(key));
            }
        }
        return result;
    }

    private void add(Set<String> target, String value){if(value != null && !value.isBlank())target.add(value);}
    private String text(Object value){return value == null ? null : String.valueOf(value);}
    private int integer(Object value,int fallback){return value == null ? fallback : Integer.parseInt(String.valueOf(value));}
    private List<?> list(Object value){return value instanceof List<?> list ? list : List.of();}
    private List<String> strings(Object value){List<String> result=new ArrayList<>();for(Object item:list(value))result.add(String.valueOf(item));return result;}
    @SuppressWarnings("unchecked") private Map<String,Object> object(Object value){return value instanceof Map<?,?> map ? (Map<String,Object>)map : Map.of();}
    private List<Map<String,Object>> objects(Object value){List<Map<String,Object>> result=new ArrayList<>();for(Object item:list(value))if(item instanceof Map<?,?>)result.add(object(item));return result;}
}
