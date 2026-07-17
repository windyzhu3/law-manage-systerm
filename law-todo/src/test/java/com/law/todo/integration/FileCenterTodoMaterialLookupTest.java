package com.law.todo.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import com.law.file.domain.FileObject.FileActor;
import com.law.file.domain.FileObject.FileBusinessRelation;
import com.law.file.security.FileAccessDeniedException;
import com.law.file.security.FileAccessPolicy;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.definition.validation.TodoFormValidator.Material;
import com.law.todo.domain.TodoException;
import org.junit.jupiter.api.Test;

class FileCenterTodoMaterialLookupTest
{
    @Test void resolves_material_type_only_when_file_is_related_to_the_todo_business()
    {
        FileAccessPolicy access=mock(FileAccessPolicy.class);
        when(access.requireCanRead(11L,new FileActor(7L,"alice",3L))).thenReturn(List.of(
            new FileBusinessRelation(1L,11L,"CASE",9L,"CONTACT_PROOF","BUSINESS",0L,0L,7L,3L,true)));
        var lookup=new FileCenterTodoMaterialLookup(access);
        Actor actor=new Actor(7L,"alice",3L);
        assertEquals(List.of(new Material(11L,"CONTACT_PROOF")),lookup.resolve("CASE",9L,List.of(11L),actor));
        verify(access).requireCanRead(11L,new FileActor(7L,"alice",3L));
    }

    @Test void rejects_a_file_id_not_related_to_the_todo_business()
    {
        FileAccessPolicy access=mock(FileAccessPolicy.class);
        TodoException error=assertThrows(TodoException.class,
            ()->new FileCenterTodoMaterialLookup(access).resolve("CASE",9L,List.of(11L),new Actor(7L,"alice",3L)));
        assertEquals("TODO_MATERIAL_NOT_RELATED",error.getBusinessCode());
    }

    @Test void private_or_department_file_that_actor_can_no_longer_read_cannot_satisfy_dod()
    {
        FileAccessPolicy access=mock(FileAccessPolicy.class);
        doThrow(new FileAccessDeniedException("scope lost")).when(access)
            .requireCanRead(11L,new FileActor(7L,"alice",3L));

        TodoException error=assertThrows(TodoException.class,()->new FileCenterTodoMaterialLookup(access)
            .resolve("CASE",9L,List.of(11L),new Actor(7L,"alice",3L)));
        assertEquals("TODO_MATERIAL_NOT_RELATED",error.getBusinessCode());
    }

    @Test void invisible_private_material_type_cannot_satisfy_dod_through_a_visible_general_relation()
    {
        FileAccessPolicy access=mock(FileAccessPolicy.class);
        when(access.requireCanRead(11L,new FileActor(7L,"alice",3L))).thenReturn(List.of(
            new FileBusinessRelation(1L,11L,"CASE",9L,"GENERAL","BUSINESS",0L,0L,8L,3L,true)));

        List<Material> resolved=new FileCenterTodoMaterialLookup(access)
            .resolve("CASE",9L,List.of(11L),new Actor(7L,"alice",3L));

        assertEquals(List.of(new Material(11L,"GENERAL")),resolved);
        assertFalse(resolved.contains(new Material(11L,"SECRET")));
    }
}
