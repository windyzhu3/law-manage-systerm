package com.law.todo.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import com.law.file.application.FileMaterialQuery;
import com.law.file.domain.FileObject.FileMaterial;
import com.law.todo.definition.validation.TodoFormValidator.Material;
import com.law.todo.domain.TodoException;
import org.junit.jupiter.api.Test;

class FileCenterTodoMaterialLookupTest
{
    @Test void resolves_material_type_only_when_file_is_related_to_the_todo_business()
    {
        FileMaterialQuery files=mock(FileMaterialQuery.class);
        when(files.resolve("CASE",9L,List.of(11L))).thenReturn(List.of(new FileMaterial(11L,"CONTACT_PROOF")));
        var lookup=new FileCenterTodoMaterialLookup(files);
        assertEquals(List.of(new Material(11L,"CONTACT_PROOF")),lookup.resolve("CASE",9L,List.of(11L)));
    }

    @Test void rejects_a_file_id_not_related_to_the_todo_business()
    {
        FileMaterialQuery files=mock(FileMaterialQuery.class);
        when(files.resolve("CASE",9L,List.of(11L))).thenReturn(List.of());
        TodoException error=assertThrows(TodoException.class,
            ()->new FileCenterTodoMaterialLookup(files).resolve("CASE",9L,List.of(11L)));
        assertEquals("TODO_MATERIAL_NOT_RELATED",error.getBusinessCode());
    }
}
