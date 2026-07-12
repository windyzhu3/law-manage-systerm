package com.law.todo.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.todo.application.TodoAssignmentResolver;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;

@ExtendWith(MockitoExtension.class)
class TodoEventServiceTest
{
    @Mock TodoMapper mapper;

    @Test void duplicateEventReturnsExistingTodo()
    {
        TodoInstance existing=new TodoInstance();existing.setTodoId(4L);
        when(mapper.selectTriggerRules("LEAD_ASSIGNED","LEAD")).thenReturn(List.of(rule()));
        when(mapper.selectByTriggerKey("evt-1:22:7")).thenReturn(existing);
        TodoInstance result=new TodoEventService(mapper,new TodoAssignmentResolver()).handle(event()).get(0);
        assertSame(existing,result);verify(mapper,never()).insertInstance(any());
    }

    @Test void createsCandidateWhenRuleTargetsRole()
    {
        when(mapper.selectTriggerRules("LEAD_ASSIGNED","LEAD")).thenReturn(List.of(rule()));
        new TodoEventService(mapper,new TodoAssignmentResolver()).handle(event());
        verify(mapper).insertCandidate(anyMap());
        assertEquals("ROLE",new TodoAssignmentResolver().resolve("ROLE:5",Map.of()).candidateType());
    }

    private Map<String,Object> rule(){return Map.of("template_id",3L,"template_version_id",22L,"template_name","首联","owner_rule_json","ROLE:5");}
    private TodoEvent event(){return new TodoEvent("evt-1","LEAD_ASSIGNED","LEAD",7L,"L-7",Map.of("ownerId",8L));}
}
