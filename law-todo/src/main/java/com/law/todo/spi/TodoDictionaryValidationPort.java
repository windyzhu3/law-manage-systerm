package com.law.todo.spi;

/** Resolves whether an exact dictionary type/value pair is currently enabled. */
public interface TodoDictionaryValidationPort
{
    boolean isEnabled(String dictType,String dictValue);
}
