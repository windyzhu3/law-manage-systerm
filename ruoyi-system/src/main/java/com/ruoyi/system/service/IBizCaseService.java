package com.ruoyi.system.service;

import java.util.List;
import java.util.Map;
import com.ruoyi.system.domain.BizContract;

public interface IBizCaseService
{
    List<Map<String, Object>> selectCaseList(Map<String, Object> params);

    Map<String, Object> selectCaseById(Long caseId);

    Map<String, Object> selectDashboard();

    List<Map<String, Object>> selectLawyerLoads(Map<String, Object> params);

    List<Map<String, Object>> selectLawyerSpecialtyStats(Map<String, Object> params);

    List<Map<String, Object>> selectLawyerProfiles(Map<String, Object> params);

    Map<String, Object> selectLawyerProfileByUserId(Long userId);

    int saveLawyerProfile(Map<String, Object> profile);

    int updateLawyerProfileStatus(Map<String, Object> profile);

    int createCaseFromContract(BizContract contract);

    int assignCase(Map<String, Object> assignment);

    int batchAssignCases(Map<String, Object> assignment);

    List<Map<String, Object>> selectAssignments(Map<String, Object> params);

    int requestTransfer(Map<String, Object> transfer);

    int approveTransfer(Map<String, Object> approval);

    List<Map<String, Object>> selectTransfers(Map<String, Object> params);

    List<Map<String, Object>> selectConfirms(Map<String, Object> params);

    int handleConfirm(Map<String, Object> confirm);

    List<Map<String, Object>> selectStatusLogs(Map<String, Object> params);
}
