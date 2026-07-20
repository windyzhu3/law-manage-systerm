package com.law.todo.spi;

import org.springframework.stereotype.Component;

import com.law.todo.mapper.TodoConfigurationMapper;

@Component
public class TodoDictionaryValidationMapperAdapter implements TodoDictionaryValidationPort
{
    private final TodoConfigurationMapper mapper;
    public TodoDictionaryValidationMapperAdapter(TodoConfigurationMapper mapper){this.mapper=mapper;}

    @Override public boolean isEnabled(String dictType,String dictValue)
    {return dictType!=null&&dictValue!=null&&mapper.countEnabledDictionaryValue(dictType,dictValue)>0;}
}
