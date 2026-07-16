package com.law.todo.spi;

import java.util.List;

import com.law.todo.definition.validation.TodoFormValidator.Material;

/** File-center boundary used by Todo without trusting raw URLs or client material types. */
@FunctionalInterface
public interface TodoMaterialLookup
{
    List<Material> resolve(List<Long> fileObjectIds);
}
