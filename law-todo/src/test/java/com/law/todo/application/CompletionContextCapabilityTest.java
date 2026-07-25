package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.law.todo.domain.model.TodoInstance;

class CompletionContextCapabilityTest
{
    @Test
    void external_code_cannot_construct_a_controlled_automatic_context()
            throws Exception
    {
        assertTrue(Arrays.stream(CompletionContext.class.getDeclaredConstructors())
                .allMatch(value->Modifier.isPrivate(value.getModifiers())));
        assertTrue(Modifier.isPublic(CompletionContext.class.getDeclaredMethod("human",
                TodoInstance.class,Map.class,Long.class,String.class).getModifiers()));
        assertFalse(Modifier.isPublic(CompletionContext.class.getDeclaredMethod(
                "controlledAutomatic",TodoInstance.class,Map.class,Long.class,String.class)
                .getModifiers()));
    }
}
