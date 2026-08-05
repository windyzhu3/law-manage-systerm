package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoOperationCommands.*;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.spi.TodoCompletionHandler;
import com.law.todo.spi.TodoCompletionHandler.CompletionResult;

class TodoExceptionOperationServiceTest
{
    private final TodoMapper mapper=mock(TodoMapper.class);
    private final TodoDodService dod=mock(TodoDodService.class);
    private final Actor admin=new Actor(1L,"admin",103L);

    @Test void forceCompleteRunsBusinessValidatorsAndWritesImmutableLog()
    {
        TodoInstance todo=todo("SUBMITTED",7L);when(mapper.selectById(9L)).thenReturn(todo);
        when(mapper.insertExceptionLogIfAbsent(anyMap())).thenReturn(1);when(mapper.forceTerminalConditionally(9L,"SUBMITTED","COMPLETED","admin")).thenReturn(1);
        TodoExceptionOperationService service=service();service.forceComplete(9L,new ForceCommand("act-1","经负责人审批",Map.of()),admin);
        verify(dod).validateBusiness(eq(todo),eq(Map.of()));verify(mapper).insertExceptionLogIfAbsent(argThat(row->"FORCE_COMPLETE".equals(row.get("operationType"))));
    }

    @Test void forceCompletePreparesBusinessLockBeforeExceptionOrTodoMutation()
    {
        TodoInstance todo=todo("SUBMITTED",7L);todo.setTemplateCode("TD-004");
        TodoCompletionHandler handler=mock(TodoCompletionHandler.class);
        when(mapper.selectById(9L)).thenReturn(todo);
        when(mapper.insertExceptionLogIfAbsent(anyMap())).thenReturn(1);
        when(mapper.forceTerminalConditionally(9L,"SUBMITTED","COMPLETED","admin")).thenReturn(1);
        when(handler.supports(todo)).thenReturn(true);
        when(handler.handle(any())).thenReturn(CompletionResult.completeTodo(Map.of()));

        new TodoExceptionOperationService(mapper,dod,List.of(handler)).forceComplete(9L,
                new ForceCommand("act-lock","approved",Map.of()),admin);

        var order=inOrder(handler,mapper);
        order.verify(handler).prepare(any());
        order.verify(mapper).insertExceptionLogIfAbsent(anyMap());
        order.verify(mapper).forceTerminalConditionally(9L,"SUBMITTED","COMPLETED","admin");
        order.verify(handler).handle(any());
    }

    @Test void rejectsTerminalAndDuplicateActionConflict()
    {
        when(mapper.selectById(9L)).thenReturn(todo("COMPLETED",7L));
        assertEquals("TODO_TERMINAL",assertThrows(TodoException.class,()->service().forceCancel(9L,new ForceCommand("a","原因",Map.of()),admin)).getBusinessCode());
        when(mapper.selectExceptionLogByActionId("dup")).thenReturn(Map.of("todo_id",8L));
        assertEquals("TODO_ACTION_ID_CONFLICT",assertThrows(TodoException.class,()->service().forceCancel(9L,new ForceCommand("dup","原因",Map.of()),admin)).getBusinessCode());
    }

    @Test void batchTransferUsesTargetDepartmentAndRollsBackOnConflict()
    {
        when(mapper.selectById(anyLong())).thenReturn(todo("IN_PROGRESS",7L));when(mapper.selectUserDeptId(22L)).thenReturn(5L);
        when(mapper.insertExceptionLogIfAbsent(anyMap())).thenReturn(1);when(mapper.transferOwnerConditionally(anyLong(),eq("IN_PROGRESS"),eq(22L),eq(5L),eq("admin"))).thenReturn(1,0);
        BatchTransferCommand command=new BatchTransferCommand("batch-1",List.of(1L,2L),22L,"人员调整");
        assertEquals("TODO_CONCURRENT_MODIFICATION",assertThrows(TodoException.class,()->service().batchTransfer(command,admin)).getBusinessCode());
    }

    @Test void waiverKeepsOriginalDueDateAndExtendsCurrentDueDate()
    {
        LocalDateTime original=LocalDateTime.of(2026,7,13,9,0),replacement=original.plusDays(2);
        when(mapper.selectById(9L)).thenReturn(todo("IN_PROGRESS",7L));when(mapper.selectSlaRecord(9L)).thenReturn(Map.of("due_at",original));
        when(mapper.insertSlaWaiverIfAbsent(anyMap())).thenReturn(1);when(mapper.extendSlaConditionally(eq(9L),eq(original),eq(replacement))).thenReturn(1);
        service().waiveSla(9L,new SlaWaiverCommand("waive-1",replacement,"法院临时调整"),admin);
        verify(mapper).insertSlaWaiverIfAbsent(argThat(row->original.equals(row.get("originalDueAt"))&&replacement.equals(row.get("newDueAt"))));
    }

    @Test void regenerateCreatesTraceableNewTodo()
    {
        TodoInstance source=todo("COMPLETED",7L),created=todo("CREATED",7L);created.setTodoId(10L);when(mapper.selectById(9L)).thenReturn(source);when(mapper.selectById(10L)).thenReturn(created);
        when(mapper.insertRegeneratedTodo(anyMap())).thenAnswer(invocation->{Map<String,Object> row=invocation.getArgument(0);row.put("newTodoId",10L);return 1;});when(mapper.insertExceptionLogIfAbsent(anyMap())).thenReturn(1);
        assertEquals(10L,service().regenerate(9L,new ForceCommand("regen-1","原任务异常关闭",Map.of()),admin).getTodoId());
        verify(mapper).insertExceptionLogIfAbsent(argThat((Map<String,Object> row)->"REGENERATE".equals(row.get("operationType"))));
    }

    private TodoExceptionOperationService service(){return new TodoExceptionOperationService(mapper,dod,List.of());}
    private TodoInstance todo(String status,Long owner){TodoInstance value=new TodoInstance();value.setTodoId(9L);value.setStatus(status);value.setOwnerId(owner);value.setBusinessType("CASE");value.setBusinessId(3L);return value;}
}
