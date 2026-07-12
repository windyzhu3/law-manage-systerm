package com.law.todo.application;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.spi.TodoBusinessValidator;

@Service
public class TodoDodService
{
    private final List<TodoBusinessValidator> validators;
    public TodoDodService(List<TodoBusinessValidator> validators){this.validators=validators;}
    public void validate(TodoInstance todo,List<String> fields,List<String> attachmentTypes,Map<String,Object> payload,List<String> attachments)
    {
        Map<String,Object> values=payload==null?Map.of():payload;List<String> files=attachments==null?List.of():attachments;
        for(String field:fields)if(!values.containsKey(field)||values.get(field)==null||String.valueOf(values.get(field)).isBlank())throw new TodoException("TODO_DOD_FIELD_MISSING","缺少完成字段："+field);
        for(String type:attachmentTypes)if(!files.contains(type))throw new TodoException("TODO_DOD_ATTACHMENT_MISSING","缺少完成材料："+type);
        for(TodoBusinessValidator validator:validators)if(validator.supports(todo.getBusinessType()))validator.validate(todo,values);
    }
    public void validateBusiness(TodoInstance todo,Map<String,Object> payload)
    {
        Map<String,Object> values=payload==null?Map.of():payload;
        for(TodoBusinessValidator validator:validators)if(validator.supports(todo.getBusinessType()))validator.validate(todo,values);
    }
}
