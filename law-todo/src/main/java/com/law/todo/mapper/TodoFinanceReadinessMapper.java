package com.law.todo.mapper;

import java.util.List;
import java.util.Map;

public interface TodoFinanceReadinessMapper
{
    List<Map<String, Object>> selectFinanceReadiness(String gateCode);
}
