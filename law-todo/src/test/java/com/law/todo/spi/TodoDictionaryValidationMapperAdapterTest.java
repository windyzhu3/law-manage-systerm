package com.law.todo.spi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.law.todo.mapper.TodoConfigurationMapper;

@ExtendWith(MockitoExtension.class)
class TodoDictionaryValidationMapperAdapterTest
{
    @Mock TodoConfigurationMapper mapper;

    @Test void returnsTrueOnlyWhenMapperFindsAnEnabledExactDictionaryValue()
    {
        when(mapper.countEnabledDictionaryValue("law_todo_sla_type","RESPONSE")).thenReturn(1);

        assertTrue(new TodoDictionaryValidationMapperAdapter(mapper).isEnabled("law_todo_sla_type","RESPONSE"));
        verify(mapper).countEnabledDictionaryValue("law_todo_sla_type","RESPONSE");
    }

    @Test void returnsFalseForUnknownOrDisabledDictionaryValues()
    {
        when(mapper.countEnabledDictionaryValue("law_todo_sla_type","RESPONSE")).thenReturn(0);

        assertFalse(new TodoDictionaryValidationMapperAdapter(mapper).isEnabled("law_todo_sla_type","RESPONSE"));
    }
}
