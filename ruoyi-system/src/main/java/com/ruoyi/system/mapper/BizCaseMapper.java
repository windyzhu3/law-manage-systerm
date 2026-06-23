package com.ruoyi.system.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

public interface BizCaseMapper
{
    List<Map<String, Object>> selectCaseList(Map<String, Object> params);

    Map<String, Object> selectCaseById(Long caseId);

    Map<String, Object> selectCaseByContractId(Long contractId);

    int insertCase(Map<String, Object> entity);

    int updateCaseAssignment(Map<String, Object> entity);

    int updateCaseStatus(Map<String, Object> entity);

    int updateCaseConfirmResult(Map<String, Object> entity);

    int countCaseInDataScope(@Param("caseId") Long caseId, @Param("currentUserId") Long currentUserId, @Param("currentDeptId") Long currentDeptId, @Param("permissions") String permissions);

    List<Map<String, Object>> selectDashboardCards(Map<String, Object> params);

    List<Map<String, Object>> selectLawyerLoads(Map<String, Object> params);

    List<Map<String, Object>> selectLawyerSpecialtyStats(Map<String, Object> params);

    List<Map<String, Object>> selectLawyerProfiles(Map<String, Object> params);

    Map<String, Object> selectLawyerProfileByUserId(Long userId);

    int insertLawyerProfile(Map<String, Object> entity);

    int updateLawyerProfile(Map<String, Object> entity);

    int updateLawyerProfileStatus(Map<String, Object> entity);

    int insertAssignment(Map<String, Object> entity);

    List<Map<String, Object>> selectAssignments(Map<String, Object> params);

    int insertTransfer(Map<String, Object> entity);

    int updateTransferApproval(Map<String, Object> entity);

    List<Map<String, Object>> selectTransfers(Map<String, Object> params);

    Map<String, Object> selectTransferById(Long transferId);

    int insertConfirm(Map<String, Object> entity);

    int updateConfirm(Map<String, Object> entity);

    Map<String, Object> selectConfirmById(Long confirmId);

    List<Map<String, Object>> selectConfirms(Map<String, Object> params);

    List<Map<String, Object>> selectStatusLogs(Map<String, Object> params);

    int insertStatusLog(Map<String, Object> entity);
}
