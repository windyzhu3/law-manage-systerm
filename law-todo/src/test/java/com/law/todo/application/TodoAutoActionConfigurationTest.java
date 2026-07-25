package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.law.todo.definition.model.TodoDefinitionDocument.AutoActionRule;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.spi.TodoAutoActionCapability;
import com.law.todo.notification.TodoNotificationPort;
import com.law.todo.notification.TodoNotificationPort.NotificationCommand;
import com.law.todo.spi.TodoSupervisorPort;
import org.mockito.ArgumentCaptor;

class TodoAutoActionConfigurationTest
{
    @Test void completeDefaultPassesConfiguredFieldsToFullDod()
    {
        TodoCommandService commands=mock(TodoCommandService.class);
        TodoAutoActionCapability capability=new TodoAutoActionConfiguration().completeDefaultCapability(commands);
        TodoInstance todo=new TodoInstance();todo.setTodoId(1L);
        AutoActionRule rule=new AutoActionRule(Map.of(
                "ruleKey","default-invalid",
                "actionType","COMPLETE_DEFAULT",
                "capability","COMPLETE_DEFAULT",
                "fields",Map.of(
                        "reviewResult","TRUE_INVALID",
                        "reviewOpinion","System default confirmation")));

        capability.execute(todo,rule,TodoAutoActionService.SERVICE_ACTOR);

        verify(commands).autoComplete(eq(1L),argThat(command->
                "TRUE_INVALID".equals(command.payload().get("reviewResult"))
                &&"System default confirmation".equals(command.payload().get("reviewOpinion"))),
                eq(TodoAutoActionService.SERVICE_ACTOR));
    }

    @Test void registeredCapabilitiesDelegateToCommandBoundary()
    {
        TodoCommandService commands=mock(TodoCommandService.class);TodoNotificationPort notifications=mock(TodoNotificationPort.class);TodoSupervisorPort supervisors=mock(TodoSupervisorPort.class);TodoAutoActionConfiguration config=new TodoAutoActionConfiguration();
        List<TodoAutoActionCapability> values=List.of(config.completeDefaultCapability(commands),config.returnDefaultCapability(commands),config.escalationCapability(commands,notifications,supervisors),config.transferCapability(commands),config.returnPoolCapability(commands));
        assertEquals(List.of("COMPLETE_DEFAULT","ESCALATE","RETURN_DEFAULT","RETURN_POOL","TRANSFER"),values.stream().map(TodoAutoActionCapability::actionType).sorted().toList());
        TodoInstance todo=new TodoInstance();todo.setTodoId(4L);
        for(TodoAutoActionCapability value:values){Map<String,Object> rule=new java.util.HashMap<>();rule.put("ruleKey",value.actionType());rule.put("actionType",value.actionType());rule.put("capability",value.actionType());if("TRANSFER".equals(value.actionType()))rule.put("targetOwnerId",9L);value.execute(todo,new AutoActionRule(rule),TodoAutoActionService.SERVICE_ACTOR);}
        verify(commands).autoComplete(eq(4L),any(),eq(TodoAutoActionService.SERVICE_ACTOR));verify(commands).autoReturn(eq(4L),any(),eq(TodoAutoActionService.SERVICE_ACTOR));verify(commands).autoEscalate(eq(4L),any(),eq(TodoAutoActionService.SERVICE_ACTOR));verify(commands).autoTransfer(eq(4L),any(),eq(TodoAutoActionService.SERVICE_ACTOR));verify(commands).autoReturnPool(eq(4L),any(),eq(TodoAutoActionService.SERVICE_ACTOR));
    }

    @Test void escalationNotificationCarriesActualConfiguredTriggerAndSource()
    {
        TodoCommandService commands=mock(TodoCommandService.class);TodoNotificationPort notifications=mock(TodoNotificationPort.class);TodoSupervisorPort supervisors=mock(TodoSupervisorPort.class);org.mockito.Mockito.when(supervisors.supervisors(7L,null)).thenReturn(List.of(8L));TodoAutoActionCapability escalation=new TodoAutoActionConfiguration().escalationCapability(commands,notifications,supervisors);TodoInstance todo=new TodoInstance();todo.setTodoId(4L);todo.setOwnerId(7L);todo.setTitle("Review");
        for(String trigger:List.of("DUE","SLA_80","SLA_100","SLA_150"))escalation.execute(todo,new AutoActionRule(Map.of("ruleKey","escalate-"+trigger,"actionType","ESCALATE","capability","ESCALATE","triggerAt",trigger)),TodoAutoActionService.SERVICE_ACTOR);
        ArgumentCaptor<NotificationCommand> captor=ArgumentCaptor.forClass(NotificationCommand.class);verify(notifications,times(4)).send(captor.capture());
        assertEquals(List.of("AUTO_ESCALATE_DUE","AUTO_ESCALATE_SLA_80","AUTO_ESCALATE_SLA_100","AUTO_ESCALATE_SLA_150"),captor.getAllValues().stream().map(NotificationCommand::type).toList());
        assertEquals(4,captor.getAllValues().stream().map(NotificationCommand::idempotencyKey).distinct().count());
        org.junit.jupiter.api.Assertions.assertTrue(captor.getAllValues().stream().allMatch(value->value.idempotencyKey().startsWith("AUTO_ESCALATE:")&&value.idempotencyKey().length()<=128&&value.recipientUserId().equals(8L)));
    }

    @Test void escalationUsesDepartmentFallbackAndDeduplicatesGovernedSupervisors()
    {
        TodoCommandService commands=mock(TodoCommandService.class);TodoNotificationPort notifications=mock(TodoNotificationPort.class);TodoSupervisorPort supervisors=mock(TodoSupervisorPort.class);org.mockito.Mockito.when(supervisors.supervisors(null,3L)).thenReturn(List.of(9L,9L,10L));
        TodoInstance todo=new TodoInstance();todo.setTodoId(5L);todo.setOwnerDeptId(3L);todo.setTitle("Review");
        new TodoAutoActionConfiguration().escalationCapability(commands,notifications,supervisors).execute(todo,new AutoActionRule(Map.of("ruleKey","due","actionType","ESCALATE","capability","ESCALATE","triggerAt","DUE")),TodoAutoActionService.SERVICE_ACTOR);
        ArgumentCaptor<NotificationCommand> captor=ArgumentCaptor.forClass(NotificationCommand.class);verify(notifications,times(2)).send(captor.capture());assertEquals(List.of(9L,10L),captor.getAllValues().stream().map(NotificationCommand::recipientUserId).toList());
    }

    @Test void escalationWithoutGovernedSupervisorCompletesWithoutOwnerNotification()
    {
        TodoCommandService commands=mock(TodoCommandService.class);TodoNotificationPort notifications=mock(TodoNotificationPort.class);TodoSupervisorPort supervisors=mock(TodoSupervisorPort.class);org.mockito.Mockito.when(supervisors.supervisors(7L,3L)).thenReturn(List.of());
        TodoInstance todo=new TodoInstance();todo.setTodoId(6L);todo.setOwnerId(7L);todo.setOwnerDeptId(3L);todo.setTitle("Review");
        new TodoAutoActionConfiguration().escalationCapability(commands,notifications,supervisors).execute(todo,new AutoActionRule(Map.of("ruleKey","due","actionType","ESCALATE","capability","ESCALATE","triggerAt","DUE")),TodoAutoActionService.SERVICE_ACTOR);
        verify(commands).autoEscalate(eq(6L),any(),eq(TodoAutoActionService.SERVICE_ACTOR));verify(notifications,org.mockito.Mockito.never()).send(any());
    }

    @Test void escalationSourceIsStableAndBoundedForMaximumIdentityLengths()
    {
        TodoCommandService commands=mock(TodoCommandService.class);TodoNotificationPort notifications=mock(TodoNotificationPort.class);TodoSupervisorPort supervisors=mock(TodoSupervisorPort.class);org.mockito.Mockito.when(supervisors.supervisors(7L,3L)).thenReturn(List.of(8L));TodoInstance todo=new TodoInstance();todo.setTodoId(Long.MAX_VALUE);todo.setOwnerId(7L);todo.setOwnerDeptId(3L);todo.setTitle("Review");AutoActionRule rule=new AutoActionRule(Map.of("ruleKey","x".repeat(96),"actionType","ESCALATE","capability","ESCALATE","triggerAt","SLA_150"));TodoAutoActionCapability escalation=new TodoAutoActionConfiguration().escalationCapability(commands,notifications,supervisors);
        escalation.execute(todo,rule,TodoAutoActionService.SERVICE_ACTOR);escalation.execute(todo,rule,TodoAutoActionService.SERVICE_ACTOR);
        ArgumentCaptor<NotificationCommand> captor=ArgumentCaptor.forClass(NotificationCommand.class);verify(notifications,times(2)).send(captor.capture());assertEquals(captor.getAllValues().get(0).idempotencyKey(),captor.getAllValues().get(1).idempotencyKey());org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().idempotencyKey().length()<=128);
    }
}
