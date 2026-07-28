package com.law.todo.spi;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.law.todo.application.command.TodoActionCommands.Actor;

/** Actor-scoped boundary for resolving stable IDs/codes into safe business labels. */
public interface TodoFieldReferenceDirectory
{
    boolean supports(String semanticType,String optionSource);

    Map<Object,DisplayReference> resolve(String semanticType,String optionSource,String dictType,
            Collection<?> rawValues,Actor actor);

    ReferencePage options(String semanticType,String optionSource,String dictType,String keyword,
            int offset,int limit,Actor actor);

    record DisplayReference(Object rawValue,String displayValue,Map<String,Object> meta,
            boolean selectable,boolean restricted,String invalidReason)
    {
        public DisplayReference
        {
            meta=meta==null?Map.of():Map.copyOf(meta);
        }
    }

    record ReferencePage(List<DisplayReference> rows,long total)
    {
        public ReferencePage { rows=rows==null?List.of():List.copyOf(rows); }
    }
}
