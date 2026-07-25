package com.law.todo.definition;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;
import com.law.todo.definition.validation.TodoEventPayloadValidator;
import com.law.todo.domain.TodoException;

class TodoEventPayloadValidatorTest
{
    private final TodoEventPayloadValidator validator = new TodoEventPayloadValidator();

    @Test
    void rejectsMissingRequiredOwner()
    {
        TodoException error = assertThrows(TodoException.class,
                () -> validator.validate(schema(), Map.of("assignmentId", 1L)));

        assertEquals("TODO_EVENT_PAYLOAD_INVALID", error.getBusinessCode());
    }

    @Test
    void acceptsDeclaredIntegerAndDateTimeFields()
    {
        assertDoesNotThrow(() -> validator.validate(schema(),
                Map.of("assignmentId", 1L, "ownerId", 11L, "contactedAt", "2026-07-25T09:30:00")));
    }

    private String schema()
    {
        return """
                {"type":"object","required":["assignmentId","ownerId"],"properties":{
                  "assignmentId":{"type":"integer"},"ownerId":{"type":"integer"},
                  "contactedAt":{"type":"string","format":"date-time"}
                }}
                """;
    }
}
