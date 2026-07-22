package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.law.todo.spi.TodoOrganizationPort;

class TodoAssignmentResolverSpringWiringTest
{
    @Test
    void springInjectsTheRealOrganizationPortInsteadOfTheLegacyEmptyPort()
    {
        Constructor<?> noArgs=Arrays.stream(TodoAssignmentResolver.class.getConstructors())
            .filter(value->value.getParameterCount()==0).findFirst().orElseThrow();
        Constructor<?> organization=Arrays.stream(TodoAssignmentResolver.class.getConstructors())
            .filter(value->Arrays.equals(value.getParameterTypes(),new Class<?>[]{TodoOrganizationPort.class}))
            .findFirst().orElseThrow();

        assertFalse(noArgs.isAnnotationPresent(Autowired.class));
        assertTrue(organization.isAnnotationPresent(Autowired.class));
    }
}
