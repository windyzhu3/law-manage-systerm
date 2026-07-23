package com.law.todo.application;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.spi.TodoBusinessValidator;
import com.law.todo.spi.TodoMaterialLookup;
import com.law.todo.definition.model.TodoDefinitionDocument;
import com.law.todo.definition.validation.TodoFormValidator;
import org.springframework.beans.factory.annotation.Autowired;
import com.law.todo.application.command.TodoActionCommands.Actor;

@Service
public class TodoDodService
{
    private final List<TodoBusinessValidator> validators;
    private final TodoFormValidator formValidator;
    private final TodoMaterialLookup materialLookup;
    public TodoDodService(List<TodoBusinessValidator> validators){this(validators,List.of());}
    @Autowired public TodoDodService(List<TodoBusinessValidator> validators,List<TodoMaterialLookup> materialLookups)
    {
        this.validators=validators==null?List.of():validators;this.formValidator=new TodoFormValidator();
        this.materialLookup=materialLookups==null||materialLookups.isEmpty()?ids->{throw new TodoException("TODO_MATERIAL_LOOKUP_UNAVAILABLE","Material metadata lookup is unavailable");}:materialLookups.get(0);
    }
    public void validate(TodoInstance todo,TodoDefinitionDocument definition,String action,Map<String,Object> fields,List<Long> fileObjectIds,Actor actor)
    {
        formValidator.validateSubmission(definition,action,fields,fileObjectIds,
            ids->materialLookup.resolve(todo.getBusinessType(),todo.getBusinessId(),ids,actor));
        if("COMPLETE".equalsIgnoreCase(action))validateBusiness(todo,definition,fields);
    }
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
    private void validateBusiness(TodoInstance todo,TodoDefinitionDocument definition,Map<String,Object> payload)
    {
        Map<String,Object> values=payload==null?Map.of():payload;
        Map<String,Object> config=definition==null||definition.dod()==null?Map.of():definition.dod().config();
        if(!config.containsKey("validatorRefs"))
        {
            validateBusiness(todo,values);
            return;
        }
        Set<String> selected=validatorRefs(config.get("validatorRefs"));
        for(TodoBusinessValidator validator:validators)
            if(selected.contains(validator.catalogCode())&&validator.supports(todo.getBusinessType()))
                validator.validate(todo,values);
    }
    private Set<String> validatorRefs(Object value)
    {
        if(!(value instanceof List<?> refs))return Set.of();
        return refs.stream().filter(item->item!=null).map(String::valueOf)
                .filter(item->!item.isBlank()).collect(Collectors.toUnmodifiableSet());
    }
}
