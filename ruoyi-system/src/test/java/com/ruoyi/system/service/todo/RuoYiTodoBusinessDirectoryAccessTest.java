package com.ruoyi.system.service.todo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.law.todo.application.command.TodoActionCommands.Actor;
import com.ruoyi.system.mapper.TodoBusinessDirectoryMapper;

@ExtendWith(MockitoExtension.class)
class RuoYiTodoBusinessDirectoryAccessTest
{
    @Mock TodoBusinessDirectoryMapper mapper;

    @Test void allFiveTypesCarryActorAndExactPermissionScopeIntoPagedRowsAndCount()
    {
        Actor actor=new Actor(7L,"operator",2L);
        RuoYiTodoBusinessDirectoryAccess service=new RuoYiTodoBusinessDirectoryAccess(mapper);
        when(mapper.selectVisibleBusinessObjects(anyMap())).thenReturn(List.of());
        when(mapper.countVisibleBusinessObjects(anyMap())).thenReturn(0L);

        for(String type:List.of("LEAD","CUSTOMER","CONTRACT","CASE","MATTER"))service.search(type,"needle",20,10,actor);

        ArgumentCaptor<Map<String,Object>> queries=ArgumentCaptor.forClass(Map.class);
        verify(mapper,org.mockito.Mockito.times(5)).selectVisibleBusinessObjects(queries.capture());
        assertEquals(java.util.Arrays.asList("lead:query,lead:mine:query","customer:list,customer:query","contract:list,contract:query","case:list,case:query",
                "matter:list,matter:query,matter:mine:list,matter:mine:query"),queries.getAllValues().stream().map(q->q.get("permissions")).toList());
        for(Map<String,Object> query:queries.getAllValues())
        {assertEquals(7L,query.get("currentUserId"));assertEquals(2L,query.get("currentDeptId"));assertEquals(true,query.get("dataScope"));}
    }

    @Test void administratorStillUsesTheSameDirectoryButBypassesDataScopePredicate()
    {
        when(mapper.selectVisibleBusinessObjects(anyMap())).thenReturn(List.of());
        new RuoYiTodoBusinessDirectoryAccess(mapper).search("LEAD",null,0,20,new Actor(1L,"admin",1L));
        ArgumentCaptor<Map<String,Object>> query=ArgumentCaptor.forClass(Map.class);
        verify(mapper).selectVisibleBusinessObjects(query.capture());
        assertEquals(false,query.getValue().get("dataScope"));
    }

    @Test void distinguishesNoDataNoMatchAndNoPermission()
    {
        Actor actor=new Actor(7L,"operator",2L);RuoYiTodoBusinessDirectoryAccess service=new RuoYiTodoBusinessDirectoryAccess(mapper);
        when(mapper.selectVisibleBusinessObjects(anyMap())).thenReturn(List.of());
        when(mapper.countVisibleBusinessObjects(anyMap())).thenReturn(0L);
        when(mapper.countUnscopedBusinessObjects(anyMap())).thenReturn(4L);

        assertEquals("NO_PERMISSION",service.search("CASE",null,0,20,actor).emptyReason());

        when(mapper.countUnscopedBusinessObjects(anyMap())).thenReturn(0L,4L);
        assertEquals("NO_MATCH",service.search("CASE","missing",0,20,actor).emptyReason());

        when(mapper.countUnscopedBusinessObjects(anyMap())).thenReturn(0L,0L);
        assertEquals("NO_DATA",service.search("CASE",null,0,20,actor).emptyReason());
    }
}
