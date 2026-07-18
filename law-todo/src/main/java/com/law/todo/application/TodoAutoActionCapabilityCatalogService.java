package com.law.todo.application;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.law.todo.spi.TodoAutoActionCapability;
import com.law.todo.spi.TodoAutoActionCapabilityRegistry;

@Service
public class TodoAutoActionCapabilityCatalogService
{
    private final TodoAutoActionCapabilityRegistry registry;

    /** Retained for focused construction tests; production injects the shared registry. */
    public TodoAutoActionCapabilityCatalogService(List<TodoAutoActionCapability> capabilities)
    {
        this(new TodoAutoActionCapabilityRegistry(capabilities));
    }

    @Autowired
    public TodoAutoActionCapabilityCatalogService(TodoAutoActionCapabilityRegistry registry)
    {
        this.registry=registry;
    }

    public List<CapabilityView> list()
    {
        return registry.capabilities().stream().map(capability->{
            TodoAutoActionCapability.Descriptor descriptor=capability.descriptor();
            return new CapabilityView(descriptor.actionType(),descriptor.actionType(),descriptor.triggerAt(),
                    views(descriptor.retryFields()),views(descriptor.requiredFields()));
        }).sorted(java.util.Comparator.comparing(CapabilityView::actionType)).toList();
    }

    private List<FieldView> views(List<TodoAutoActionCapability.Field> fields)
    {
        return fields.stream().map(field->new FieldView(field.name(),field.type(),field.required(),
                field.min(),field.defaultValue(),field.label())).toList();
    }

    public record CapabilityView(String actionType,String capability,List<String> triggerAt,
            List<FieldView> retryFields,List<FieldView> requiredFields) { }
    public record FieldView(String name,String type,boolean required,Integer min,Integer defaultValue,String label) { }
}
