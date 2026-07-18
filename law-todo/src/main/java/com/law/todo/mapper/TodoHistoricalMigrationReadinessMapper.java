package com.law.todo.mapper;

import java.util.List;
import java.util.Map;

public interface TodoHistoricalMigrationReadinessMapper
{
    List<Map<String,Object>> selectMigrationReadiness(String gateCode);
}
