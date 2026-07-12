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
import org.mockito.ArgumentCaptor;
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

    @Test void rejectsActionIdReusedForAnotherTodo()
    {
        when(mapper.selectActionById("same")).thenReturn(Map.of("todo_id",99L));
        TodoException error=assertThrows(TodoException.class,()->service.claim(1L,new ActionCommand("same",null,Map.of()),new Actor(7L,"alice",3L)));
        assertEquals("TODO_ACTION_ID_CONFLICT",error.getBusinessCode());
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

    @Test void nonReviewerCannotReturnTodo(){TodoInstance todo=todo(3L,"SUBMITTED",8L);when(mapper.selectById(3L)).thenReturn(todo);when(access.canReview(todo,7L)).thenReturn(false);TodoException e=assertThrows(TodoException.class,()->service.returnTodo(3L,new ActionCommand("back-1",null,Map.of()),new Actor(7L,"alice",3L)));assertEquals("TODO_ACCESS_DENIED",e.getBusinessCode());}
    @Test void terminalTodoCannotTransfer(){TodoInstance todo=todo(4L,"COMPLETED",7L);when(mapper.selectById(4L)).thenReturn(todo);when(access.canOperate(todo,7L)).thenReturn(true);TodoException e=assertThrows(TodoException.class,()->service.transfer(4L,new ActionCommand("move-1",null,Map.of("targetOwnerId",9L)),new Actor(7L,"alice",3L)));assertEquals("TODO_TERMINAL",e.getBusinessCode());}

    @Test void writesValidJsonPayloadToAuditLog()
    {
        TodoInstance todo=todo(5L,"IN_PROGRESS",7L);when(mapper.selectById(5L)).thenReturn(todo);when(access.canOperate(todo,7L)).thenReturn(true);when(mapper.updateStatusConditionally(5L,"IN_PROGRESS","IN_PROGRESS",9L,"alice")).thenReturn(1);when(mapper.insertActionIfAbsent(anyMap())).thenReturn(1);
        service.transfer(5L,new ActionCommand("move-2",null,Map.of("targetOwnerId",9L)),new Actor(7L,"alice",3L));
        ArgumentCaptor<Map<String,Object>> log=ArgumentCaptor.forClass(Map.class);verify(mapper).insertActionIfAbsent(log.capture());assertEquals("{\"targetOwnerId\":9}",log.getValue().get("payloadJson"));
    }

    private TodoInstance todo(Long id,String status,Long owner){TodoInstance t=new TodoInstance();t.setTodoId(id);t.setStatus(status);t.setOwnerId(owner);return t;}
}
