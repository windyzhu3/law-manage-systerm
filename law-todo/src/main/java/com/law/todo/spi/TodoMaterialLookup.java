package com.law.todo.spi;

import java.util.List;

import com.law.todo.definition.validation.TodoFormValidator.Material;
import com.law.todo.application.command.TodoActionCommands.Actor;

/** File-center boundary used by Todo without trusting raw URLs or client material types. */
@FunctionalInterface
public interface TodoMaterialLookup
{
    List<Material> resolve(List<Long> fileObjectIds);
    default List<Material> resolve(String businessType,Long businessId,List<Long> fileObjectIds)
    {
        return resolve(fileObjectIds);
    }
    default List<Material> resolve(String businessType,Long businessId,List<Long> fileObjectIds,Actor actor)
    {
        return resolve(businessType,businessId,fileObjectIds);
    }
}
