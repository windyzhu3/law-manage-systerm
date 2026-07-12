package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.spi.TodoBusinessValidator;

@ExtendWith(MockitoExtension.class)
class TodoTemplateAndRoutingTest
{
    @Mock TodoMapper mapper;

    @Test void dodRejectsMissingRequiredField()
    {
        TodoDodService service=new TodoDodService(List.of());
        TodoException error=assertThrows(TodoException.class,()->service.validate(todo(),List.of("contactResult"),List.of(),Map.of(),List.of()));
        assertEquals("TODO_DOD_FIELD_MISSING",error.getBusinessCode());
    }

    @Test void dodInvokesBusinessValidator()
    {
        TodoBusinessValidator validator=(todo,payload)->{throw new TodoException("LEAD_INVALID","线索状态不允许首联");};
        TodoDodService service=new TodoDodService(List.of(validator));
        assertThrows(TodoException.class,()->service.validate(todo(),List.of(),List.of(),Map.of(),List.of()));
    }

    @Test void routingReturnsExistingNextTodo()
    {
        TodoInstance existing=todo();existing.setTodoId(9L);
        when(mapper.selectByNextKey("1:22")).thenReturn(existing);
        TodoInstance result=new TodoRoutingService(mapper).createNext(todo(),22L,"Next","LEAD",7L);
        assertSame(existing,result);verify(mapper,never()).insertInstance(any());
    }

    private TodoInstance todo(){TodoInstance t=new TodoInstance();t.setTodoId(1L);t.setBusinessType("LEAD");t.setBusinessId(7L);t.setRootTodoId(1L);return t;}
}
