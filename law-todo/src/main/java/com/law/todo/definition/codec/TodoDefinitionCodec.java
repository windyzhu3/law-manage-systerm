package com.law.todo.definition.codec;

import java.util.Objects;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.law.todo.definition.model.TodoDefinitionDocument;

/** Serializes definition documents into a repeatable representation suitable for hashing. */
public final class TodoDefinitionCodec
{
    public TodoDefinitionDocument read(String json)
    {
        Objects.requireNonNull(json, "json");
        return JSON.parseObject(json, TodoDefinitionDocument.class);
    }

    public String canonicalJson(TodoDefinitionDocument value)
    {
        Objects.requireNonNull(value, "value");
        return JSON.toJSONString(value, JSONWriter.Feature.SortMapEntriesByKeys);
    }
}
