package com.law.todo.mapper;

import java.util.List;
import java.util.Map;

public interface TodoAcceptanceReadinessMapper
{
    List<Map<String, Object>> selectAcceptanceReadiness(String gateCode);
}
