package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.law.todo.definition.model.TodoDefinitionDocument.AutoActionRule;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.spi.TodoAutoActionCapability;

class TodoAutoActionConfigurationTest
{
    @Test void allAndOnlyAllowListedCapabilitiesDelegateToCommandBoundary()
    {
        TodoCommandService commands=mock(TodoCommandService.class);TodoAutoActionConfiguration config=new TodoAutoActionConfiguration();
        List<TodoAutoActionCapability> values=List.of(config.completeDefaultCapability(commands),config.returnDefaultCapability(commands),config.escalationCapability(commands),config.transferCapability(commands),config.returnPoolCapability(commands));
        assertEquals(TodoAutoActionCapability.ALLOWED_ACTION_TYPES,new java.util.HashSet<>(values.stream().map(TodoAutoActionCapability::actionType).toList()));
        TodoInstance todo=new TodoInstance();todo.setTodoId(4L);
        for(TodoAutoActionCapability value:values){Map<String,Object> rule=new java.util.HashMap<>();rule.put("ruleKey",value.actionType());rule.put("actionType",value.actionType());rule.put("capability",value.actionType());if("TRANSFER".equals(value.actionType()))rule.put("targetOwnerId",9L);value.execute(todo,new AutoActionRule(rule),TodoAutoActionService.SERVICE_ACTOR);}
        verify(commands).autoComplete(eq(4L),any(),eq(TodoAutoActionService.SERVICE_ACTOR));verify(commands).autoReturn(eq(4L),any(),eq(TodoAutoActionService.SERVICE_ACTOR));verify(commands).autoEscalate(eq(4L),any(),eq(TodoAutoActionService.SERVICE_ACTOR));verify(commands).autoTransfer(eq(4L),any(),eq(TodoAutoActionService.SERVICE_ACTOR));verify(commands).autoReturnPool(eq(4L),any(),eq(TodoAutoActionService.SERVICE_ACTOR));
    }
}
