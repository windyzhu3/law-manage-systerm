package com.law.todo.integration;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.law.file.domain.FileObject.FileActor;
import com.law.file.domain.FileObject.FileBusinessRelation;
import com.law.file.security.FileAccessDeniedException;
import com.law.file.security.FileAccessPolicy;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.definition.validation.TodoFormValidator.Material;
import com.law.todo.domain.TodoException;
import com.law.todo.spi.TodoMaterialLookup;
import org.springframework.stereotype.Component;

@Component
public class FileCenterTodoMaterialLookup implements TodoMaterialLookup
{
    private final FileAccessPolicy access;
    public FileCenterTodoMaterialLookup(FileAccessPolicy access){this.access=access;}
    @Override public List<Material> resolve(List<Long> ids)
    {throw new TodoException("TODO_MATERIAL_BUSINESS_CONTEXT_REQUIRED","Material lookup requires a business relation");}
    @Override public List<Material> resolve(String businessType,Long businessId,List<Long> ids)
    {
        throw new TodoException("TODO_MATERIAL_ACTOR_REQUIRED","Material lookup requires an authenticated actor");
    }
    @Override public List<Material> resolve(String businessType,Long businessId,List<Long> ids,Actor actor)
    {
        List<Long> requested=ids==null?List.of():List.copyOf(ids);
        FileActor fileActor=new FileActor(actor.userId(),actor.userName(),actor.deptId());
        Set<Material> resolved=new LinkedHashSet<>();
        for(Long id:new LinkedHashSet<>(requested))
        {
            List<FileBusinessRelation> visible;
            try{visible=access.requireCanRead(id,fileActor);}
            catch(FileAccessDeniedException denied){throw notRelated();}
            List<FileBusinessRelation> sameBusiness=visible.stream()
                .filter(relation->relation.businessType().equals(businessType)&&relation.businessId().equals(businessId)).toList();
            if(sameBusiness.isEmpty())throw notRelated();
            sameBusiness.forEach(relation->resolved.add(new Material(id,relation.materialType())));
        }
        return List.copyOf(resolved);
    }
    private static TodoException notRelated()
    {return new TodoException("TODO_MATERIAL_NOT_RELATED","File object is not readable through the todo business relation");}
}
