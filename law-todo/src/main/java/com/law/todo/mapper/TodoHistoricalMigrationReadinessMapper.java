package com.law.todo.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.cursor.Cursor;

public interface TodoHistoricalMigrationReadinessMapper
{
    List<Map<String,Object>> selectMigrationReadiness(String gateCode);
    Map<String,Object> selectHistoricalMigrationPreflightCounts();
    Cursor<Map<String,Object>> streamHistoricalCaseCandidates();
    List<Map<String,Object>> selectHistoricalCaseGroups();
}
