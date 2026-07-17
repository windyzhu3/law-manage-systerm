package com.law.todo.application;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.law.todo.assignment.CompositeOwnerResolver;
import com.law.todo.assignment.OwnerResolutionContext;
import com.law.todo.assignment.OwnerResolutionResult;
import com.law.todo.definition.model.TodoDefinitionDocument.OwnerRule;
import com.law.todo.spi.TodoOrganizationPort;

@Service
public class TodoAssignmentResolver
{
    private final CompositeOwnerResolver composite;

    public record Assignment(Long ownerId, String candidateType, Long candidateValue) { }

    /** Preserves direct construction used by existing event and routing callers. */
    public TodoAssignmentResolver()
    {
        this(TodoOrganizationPort.legacyCompatible());
    }

    public TodoAssignmentResolver(TodoOrganizationPort organization)
    {
        this(new CompositeOwnerResolver(organization));
    }

    public TodoAssignmentResolver(CompositeOwnerResolver composite)
    {
        this.composite = composite;
    }

    public OwnerResolutionResult resolve(OwnerRule rule, OwnerResolutionContext context)
    {
        return composite.resolve(rule, context);
    }

    public OwnerResolutionResult resolveForSimulation(OwnerRule rule,OwnerResolutionContext context)
    {
        return composite.resolveForSimulation(rule,context);
    }

    public Assignment resolve(String rule, Map<String, Object> payload)
    {
        String value = rule == null ? "" : rule.trim();
        if (value.startsWith("\"") && value.endsWith("\""))
            value = value.substring(1, value.length() - 1);
        if (value.equals("OWNER"))
        {
            Long id = longValue(payload.get("ownerId"));
            return new Assignment(id, id == null ? null : "USER", id);
        }
        if (value.startsWith("PAYLOAD:"))
        {
            Long id = longValue(payload.get(value.substring(8)));
            return new Assignment(id, id == null ? null : "USER", id);
        }
        String[] parts = value.split(":", 2);
        if (parts.length == 2 && ListTypes.supports(parts[0]))
            return new Assignment("USER".equals(parts[0]) ? Long.valueOf(parts[1]) : null,
                    parts[0], Long.valueOf(parts[1]));
        return new Assignment(null, null, null);
    }

    private Long longValue(Object value)
    {
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }

    private static final class ListTypes
    {
        private static boolean supports(String value)
        {
            return "USER".equals(value) || "ROLE".equals(value)
                    || "DEPT".equals(value) || "POST".equals(value);
        }
    }
}
