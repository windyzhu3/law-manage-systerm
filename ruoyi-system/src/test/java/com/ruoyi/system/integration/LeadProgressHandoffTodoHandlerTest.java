package com.ruoyi.system.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import com.law.todo.domain.model.TodoInstance;
import com.ruoyi.system.service.event.LeadProgressHandoffTodoHandler;

class LeadProgressHandoffTodoHandlerTest
{
    @Test
    void exposes_the_approved_stage_three_boundary_handler()
    {
        TodoInstance todo=new TodoInstance();todo.setTemplateCode("TD-004");
        LeadProgressHandoffTodoHandler handler=new LeadProgressHandoffTodoHandler();

        assertTrue(handler.supports(todo));
        assertEquals("TD-004_COMPLETE",handler.catalogCode());
        handler.complete(todo,Map.of("progressType","PROGRESS"),8L,"alice");
    }
}
