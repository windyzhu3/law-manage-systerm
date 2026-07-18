package com.law.todo.spi;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Single registry shared by catalog, definition compilation and execution. */
@Component
public class TodoAutoActionCapabilityRegistry
{
    private final Map<String,TodoAutoActionCapability> capabilities;

    public TodoAutoActionCapabilityRegistry(List<TodoAutoActionCapability> capabilities)
    {
        Map<String,TodoAutoActionCapability> values=new LinkedHashMap<>();
        for(TodoAutoActionCapability capability:capabilities==null?List.<TodoAutoActionCapability>of():capabilities)
        {
            Objects.requireNonNull(capability,"capability");String type=Objects.requireNonNull(capability.actionType(),"capability actionType");
            if(type.isBlank()||values.putIfAbsent(type,capability)!=null)throw new IllegalStateException("Duplicate auto-action capability: "+type);
            TodoAutoActionCapability.Descriptor descriptor=Objects.requireNonNull(capability.descriptor(),"capability descriptor");
            if(!type.equals(descriptor.actionType()))throw new IllegalStateException("Capability descriptor action type mismatch: "+type);
        }
        this.capabilities=Map.copyOf(values);
    }

    public TodoAutoActionCapability capability(String actionType){return capabilities.get(actionType);}
    public Collection<TodoAutoActionCapability> capabilities(){return capabilities.values();}
    public List<String> types(){return capabilities.keySet().stream().sorted().toList();}
}
