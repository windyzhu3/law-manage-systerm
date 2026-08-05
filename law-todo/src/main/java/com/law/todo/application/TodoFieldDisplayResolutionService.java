package com.law.todo.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.law.todo.application.TodoConfigurationResourceCatalogService.FieldResource;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.domain.TodoException;
import com.law.todo.spi.TodoFieldReferenceDirectory;
import com.law.todo.spi.TodoFieldReferenceDirectory.DisplayReference;
import com.law.todo.spi.TodoFieldReferenceDirectory.ReferencePage;

/** Resolves semantic values in batches and never exposes unresolved raw identifiers as labels. */
@Service
public class TodoFieldDisplayResolutionService
{
    private static final Set<String> REFERENCE_SEMANTICS=Set.of(
            "USER_ID","DEPT_ID","POST_ID","ROLE_KEY","DICT","BUSINESS_REF");
    private final List<TodoFieldReferenceDirectory> directories;

    public TodoFieldDisplayResolutionService(List<TodoFieldReferenceDirectory> directories)
    {this.directories=directories==null?List.of():List.copyOf(directories);}

    public Map<String,DisplayReference> resolve(List<FieldValue> values,Actor actor)
    {
        Map<ResolutionKey,List<FieldValue>> grouped=new LinkedHashMap<>();
        for(FieldValue value:values==null?List.<FieldValue>of():values)
        {
            FieldResource field=value.field();
            if(value.rawValue()==null||!REFERENCE_SEMANTICS.contains(field.semanticType()))continue;
            grouped.computeIfAbsent(new ResolutionKey(field.semanticType(),field.optionSource(),field.dictType()),
                    ignored->new ArrayList<>()).add(value);
        }
        Map<String,DisplayReference> result=new LinkedHashMap<>();
        grouped.forEach((key,fields)->
        {
            TodoFieldReferenceDirectory directory=directory(key.semanticType(),key.optionSource());
            Collection<Object> rawValues=fields.stream().map(FieldValue::rawValue)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            Map<Object,DisplayReference> resolved=directory.resolve(key.semanticType(),key.optionSource(),
                    key.dictType(),rawValues,actor);
            for(FieldValue field:fields)
            {
                DisplayReference display=resolved==null?null:resolved.get(field.rawValue());
                if(display==null)display=invalid(field.rawValue(),"原配置对象已失效");
                result.put(field.field().code(),display);
            }
        });
        return Map.copyOf(result);
    }

    public ReferencePage options(String semanticType,String optionSource,String dictType,String keyword,
            int offset,int limit,Actor actor)
    {
        return directory(semanticType,optionSource).options(semanticType,optionSource,dictType,keyword,
                Math.max(0,offset),Math.max(1,Math.min(100,limit)),actor);
    }

    private TodoFieldReferenceDirectory directory(String semanticType,String optionSource)
    {
        List<TodoFieldReferenceDirectory> supported=directories.stream()
                .filter(value->value.supports(semanticType,optionSource)).toList();
        if(supported.size()!=1)throw new TodoException("TODO_FIELD_REFERENCE_DIRECTORY_UNAVAILABLE",
                supported.isEmpty()?"名称解析服务不可用":"名称解析服务配置冲突");
        return supported.get(0);
    }

    private DisplayReference invalid(Object rawValue,String reason)
    {return new DisplayReference(rawValue,reason,Map.of(),false,false,reason);}

    public record FieldValue(FieldResource field,Object rawValue) { }
    private record ResolutionKey(String semanticType,String optionSource,String dictType) { }
}
