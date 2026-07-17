package com.law.todo.integration;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.law.file.application.FileMaterialQuery;
import com.law.file.domain.FileObject.FileMaterial;
import com.law.todo.definition.validation.TodoFormValidator.Material;
import com.law.todo.domain.TodoException;
import com.law.todo.spi.TodoMaterialLookup;
import org.springframework.stereotype.Component;

@Component
public class FileCenterTodoMaterialLookup implements TodoMaterialLookup
{
    private final FileMaterialQuery files;
    public FileCenterTodoMaterialLookup(FileMaterialQuery files){this.files=files;}
    @Override public List<Material> resolve(List<Long> ids)
    {throw new TodoException("TODO_MATERIAL_BUSINESS_CONTEXT_REQUIRED","Material lookup requires a business relation");}
    @Override public List<Material> resolve(String businessType,Long businessId,List<Long> ids)
    {
        List<Long> requested=ids==null?List.of():List.copyOf(ids);List<FileMaterial> resolved=files.resolve(businessType,businessId,requested);
        Set<Long> found=resolved.stream().map(FileMaterial::fileObjectId).collect(Collectors.toSet());
        if(!found.containsAll(new LinkedHashSet<>(requested)))
            throw new TodoException("TODO_MATERIAL_NOT_RELATED","One or more file objects are not active materials of this business object");
        return resolved.stream().map(item->new Material(item.fileObjectId(),item.materialType())).toList();
    }
}
