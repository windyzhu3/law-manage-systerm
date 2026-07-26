package com.ruoyi.system.service.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;
import com.law.business.lead.dto.LeadCallRecordCommand;
import com.law.todo.domain.model.TodoInstance;

class LeadTodoPayloadMapperTest
{
    @Test
    void td003DerivesAReplaySafeOccurrenceKeyWhenReadOnlyAttemptCountIsAbsent()
    {
        TodoInstance todo = new TodoInstance();
        todo.setTodoId(21L);
        todo.setBusinessId(7L);
        todo.setTemplateCode("TD-003");
        todo.setOccurrenceKey("PLAN-31:T0:1");

        LeadCallRecordCommand call = LeadTodoPayloadMapper.manualCall(todo, Map.of(
                "contactResult", "NEXT_WINDOW",
                "contactedAt", "2026-07-26 14:31:00"));

        assertEquals("TD-003:21:PLAN-31:T0:1:START:2026-07-26T14:31",
                call.getBusinessOccurrenceKey());
    }
}
