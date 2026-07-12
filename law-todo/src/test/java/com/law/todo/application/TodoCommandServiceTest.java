package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.todo.application.command.TodoActionCommands.ActionCommand;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.domain.TodoAccessPolicy;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;

@ExtendWith(MockitoExtension.class)
class TodoCommandServiceTest
{
    @Mock TodoMapper mapper;
    @Mock TodoAccessPolicy access;
    TodoCommandService service;

    @BeforeEach void setUp(){service=new TodoCommandService(mapper,access);}

    @Test void claimsCreatedTodoAndWritesAudit()
    {
        TodoInstance todo=todo(1L,"CREATED",null);
        when(mapper.selectById(1L)).thenReturn(todo);
        when(access.canClaim(todo,7L,3L)).thenReturn(true);
        when(mapper.updateStatusConditionally(1L,"CREATED","CLAIMED",7L,"alice")).thenReturn(1);
        when(mapper.insertActionIfAbsent(anyMap())).thenReturn(1);
        service.claim(1L,new ActionCommand("a-1",null,Map.of()),new Actor(7L,"alice",3L));
        verify(mapper).insertActionIfAbsent(anyMap());
    }

    @Test void repeatedActionReturnsWithoutChangingState()
    {
        when(mapper.selectActionById("same")).thenReturn(Map.of("todo_id",1L));
        service.claim(1L,new ActionCommand("same",null,Map.of()),new Actor(7L,"alice",3L));
        verify(mapper,never()).updateStatusConditionally(org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString());
    }

    @Test void nonOwnerCannotStartTodo()
    {
        TodoInstance todo=todo(1L,"CLAIMED",8L);
        when(mapper.selectById(1L)).thenReturn(todo);
        TodoException error=assertThrows(TodoException.class,()->service.start(1L,new ActionCommand("a-2",null,Map.of()),new Actor(7L,"alice",3L)));
        assertEquals("TODO_ACCESS_DENIED",error.getBusinessCode());
    }

    @Test void completeRejectsMissingDodField()
    {
        TodoInstance todo=todo(2L,"SUBMITTED",7L);todo.setTemplateVersionId(9L);todo.setBusinessType("LEAD");
        when(mapper.selectById(2L)).thenReturn(todo);when(access.canOperate(todo,7L)).thenReturn(true);
        when(mapper.selectTemplateVersionById(9L)).thenReturn(Map.of("dod_rule_json","{\"requiredFields\":[\"contactResult\"]}"));
        TodoCommandService guarded=new TodoCommandService(mapper,access,new TodoDodService(List.of()),List.of(),null);
        TodoException error=assertThrows(TodoException.class,()->guarded.complete(2L,new ActionCommand("done-1",null,Map.of()),new Actor(7L,"alice",3L)));
        assertEquals("TODO_DOD_FIELD_MISSING",error.getBusinessCode());
        verify(mapper,never()).updateStatusConditionally(2L,"SUBMITTED","COMPLETED",null,"alice");
    }

    private TodoInstance todo(Long id,String status,Long owner){TodoInstance t=new TodoInstance();t.setTodoId(id);t.setStatus(status);t.setOwnerId(owner);return t;}
}
