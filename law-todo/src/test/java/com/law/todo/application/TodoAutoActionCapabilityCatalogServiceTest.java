package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.jupiter.api.Test;
import com.law.todo.notification.TodoNotificationPort;
import com.law.todo.spi.TodoAutoActionCapability;
import com.law.todo.spi.TodoSupervisorPort;

class TodoAutoActionCapabilityCatalogServiceTest
{
    @Test void listsEveryInjectedCapabilityInStableActionTypeOrder()
    {
        TodoAutoActionConfiguration configuration=new TodoAutoActionConfiguration();
        TodoCommandService commands=mock(TodoCommandService.class);
        TodoNotificationPort notifications=mock(TodoNotificationPort.class);
        TodoSupervisorPort supervisors=mock(TodoSupervisorPort.class);
        List<TodoAutoActionCapability> injectedCapabilities=List.of(
                configuration.transferCapability(commands),
                configuration.returnPoolCapability(commands),
                configuration.completeDefaultCapability(commands),
                configuration.escalationCapability(commands,notifications,supervisors),
                configuration.returnDefaultCapability(commands));

        var catalog=new TodoAutoActionCapabilityCatalogService(injectedCapabilities).list();

        assertEquals(List.of("COMPLETE_DEFAULT","ESCALATE","RETURN_DEFAULT","RETURN_POOL","TRANSFER"),
                catalog.stream().map(TodoAutoActionCapabilityCatalogService.CapabilityView::actionType).toList());
        assertEquals(injectedCapabilities.stream().map(TodoAutoActionCapability::actionType).sorted().toList(),
                catalog.stream().map(TodoAutoActionCapabilityCatalogService.CapabilityView::actionType).toList());

        TodoAutoActionCapabilityCatalogService.CapabilityView transfer=catalog.get(4);
        assertEquals(List.of("DUE","SLA_80","SLA_100","SLA_150"),transfer.triggerAt());
        assertEquals(List.of("maxAttempts","retryDelayMinutes","claimTimeoutMinutes"),
                transfer.retryFields().stream().map(TodoAutoActionCapabilityCatalogService.FieldView::name).toList());
        assertEquals(List.of(3,5,15),transfer.retryFields().stream().map(TodoAutoActionCapabilityCatalogService.FieldView::defaultValue).toList());
        assertTrue(transfer.retryFields().stream().allMatch(field->!field.required()&&field.min()==1));
        assertTrue(transfer.requiredFields().stream().anyMatch(field->field.name().equals("targetOwnerId")&&field.required()));
    }
}
