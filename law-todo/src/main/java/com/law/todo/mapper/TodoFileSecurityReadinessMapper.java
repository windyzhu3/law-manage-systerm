package com.law.todo.mapper;

import java.util.List;
import java.util.Map;

public interface TodoFileSecurityReadinessMapper
{
    List<Map<String,Object>> selectSecurityReadiness(String gateCode);
}
