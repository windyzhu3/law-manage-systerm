package com.law.todo.definition.catalog;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.law.todo.mapper.TodoMapper;

@Service
public class TodoDecisionService
{
    private final TodoMapper mapper;

    public TodoDecisionService(TodoMapper mapper)
    {
        this.mapper = mapper;
    }

    public List<String> unresolvedBlockingDecisions(List<String> decisionCodes)
    {
        if (decisionCodes == null || decisionCodes.isEmpty())
            return List.of();
        List<String> unresolved = new ArrayList<>();
        for (String code : new LinkedHashSet<>(decisionCodes))
        {
            Map<String, Object> decision = code == null || code.isBlank()
                    ? null : mapper.selectDecisionByCode(code);
            if (decision == null || decision.isEmpty() || isBlockingAndOpen(decision))
                unresolved.add(code);
        }
        return List.copyOf(unresolved);
    }

    public boolean isBlockingUnresolved(String decisionCode)
    {
        return !unresolvedBlockingDecisions(List.of(decisionCode)).isEmpty();
    }

    private static boolean isBlockingAndOpen(Map<String, Object> decision)
    {
        Object blockingValue = value(decision, "blocking", "blocking");
        boolean blocking = blockingValue instanceof Boolean bool ? bool
                : "Y".equalsIgnoreCase(String.valueOf(blockingValue))
                        || "1".equals(String.valueOf(blockingValue))
                        || "TRUE".equalsIgnoreCase(String.valueOf(blockingValue));
        String status = String.valueOf(value(decision, "status", "status"));
        return blocking && !"RESOLVED".equalsIgnoreCase(status) && !"CLOSED".equalsIgnoreCase(status);
    }

    private static Object value(Map<String, Object> row, String snakeCase, String camelCase)
    {
        return row.containsKey(snakeCase) ? row.get(snakeCase) : row.get(camelCase);
    }
}
