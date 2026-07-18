package com.law.todo.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Param;

public interface TodoAcceptanceEvidenceMapper
{
    List<Map<String, Object>> selectScenarios();
    Map<String, Object> selectScenarioById(Long scenarioId);
    Map<String, Object> selectScenarioByCode(String scenarioCode);
    List<Map<String, Object>> selectMappings(@Param("templateCode") String templateCode,
            @Param("dimensionCode") String dimensionCode, @Param("status") String status);
    Map<String, Object> selectMappingById(Long mappingId);
    List<Map<String, Object>> selectActiveUsers();
    Map<String, Object> selectActiveUser(Long userId);
    int insertScenario(Map<String, Object> value);
    int updateScenarioConditionally(Map<String, Object> value);
    int updateMappingConditionally(Map<String, Object> value);
    int insertActionClaim(Map<String, Object> value);
    Map<String, Object> selectActionForUpdate(String actionId);
    int completeAction(@Param("actionId") String actionId,
            @Param("requestFingerprint") String requestFingerprint,
            @Param("resultEntityId") Long resultEntityId);
}
