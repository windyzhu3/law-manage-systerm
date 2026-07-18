package com.law.todo.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Param;

public interface TodoAdmissionEvidenceMapper
{
    List<Map<String,Object>> selectEvidence();
    Map<String,Object> selectEvidenceById(Long evidenceId);
    Map<String,Object> selectActiveUser(Long userId);
    List<Map<String,Object>> selectActiveUsers();
    int insertEvidenceActionClaim(Map<String,Object> value);
    Map<String,Object> selectEvidenceActionForUpdate(String actionId);
    int updateEvidenceConditionally(Map<String,Object> value);
    int completeEvidenceAction(@Param("actionId") String actionId,@Param("requestFingerprint") String requestFingerprint,@Param("evidenceId") Long evidenceId);
}
