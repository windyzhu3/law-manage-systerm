package com.law.todo.mapper;

import java.util.List;
import java.util.Map;

public interface TodoFoundationResourceMapper
{
    List<Map<String,Object>> selectResourceReadiness(String gateCode);
}
