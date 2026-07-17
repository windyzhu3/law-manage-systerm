package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.law.todo.mapper.TodoMapper;
import com.law.todo.spi.TodoBusinessValidator;
import com.law.todo.spi.TodoCompletionHandler;

@ExtendWith(MockitoExtension.class)
class TodoDefinitionCatalogServiceTest
{
    @Mock TodoMapper mapper;

    @Test void catalogs_are_stable_read_only_projections()
    {
        when(mapper.selectEventCatalogs()).thenReturn(List.of(Map.of("event_type","B"),Map.of("event_type","A")));
        when(mapper.selectDecisions()).thenReturn(List.of(Map.of("decision_code","Q-001")));
        TodoCompletionHandler handler=org.mockito.Mockito.mock(TodoCompletionHandler.class);
        when(handler.catalogCode()).thenReturn("HANDLER_B");when(handler.simulationDescription()).thenReturn("not executed");
        TodoBusinessValidator validator=org.mockito.Mockito.mock(TodoBusinessValidator.class);when(validator.catalogCode()).thenReturn("VALIDATOR_A");when(validator.catalogDescription()).thenReturn("server validation");
        TodoDefinitionCatalogService service=new TodoDefinitionCatalogService(mapper,List.of(handler),List.of(validator));

        assertEquals(List.of("A","B"),service.events().stream().map(row->String.valueOf(row.get("event_type"))).toList());assertEquals(1,service.decisions().size());
        assertEquals("HANDLER_B",service.handlers().get(0).code());assertFalse(service.handlers().get(0).simulatable());
        assertEquals("VALIDATOR_A",service.validators().get(0).code());
        verify(mapper).selectEventCatalogs();verify(mapper).selectDecisions();
    }
}
