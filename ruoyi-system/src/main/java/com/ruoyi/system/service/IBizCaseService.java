package com.ruoyi.system.service;

import java.util.List;
import java.util.Map;
import com.law.business.lawcase.dto.CaseAssignmentCommand;
import com.law.business.lawcase.dto.CaseBatchAssignmentCommand;
import com.law.business.lawcase.dto.CaseConfirmCommand;
import com.law.business.lawcase.dto.CaseTransferApprovalCommand;
import com.law.business.lawcase.dto.CaseTransferCommand;
import com.law.business.lawcase.dto.LawyerProfileSaveCommand;
import com.law.business.lawcase.dto.LawyerProfileStatusCommand;

public interface IBizCaseService
{
    List<Map<String, Object>> selectCaseList(Map<String, Object> params);

    Map<String, Object> selectCaseById(Long caseId);

    Map<String, Object> selectDashboard();

    List<Map<String, Object>> selectLawyerLoads(Map<String, Object> params);

    List<Map<String, Object>> selectLawyerSpecialtyStats(Map<String, Object> params);

    List<Map<String, Object>> selectLawyerProfiles(Map<String, Object> params);

    Map<String, Object> selectLawyerProfileByUserId(Long userId);

    int saveLawyerProfile(LawyerProfileSaveCommand profile);

    int updateLawyerProfileStatus(LawyerProfileStatusCommand profile);

    int createCaseFromContract(Long contractId);

    int assignCase(CaseAssignmentCommand assignment);

    int batchAssignCases(CaseBatchAssignmentCommand assignment);

    List<Map<String, Object>> selectAssignments(Map<String, Object> params);

    int requestTransfer(CaseTransferCommand transfer);

    int approveTransfer(CaseTransferApprovalCommand approval);

    List<Map<String, Object>> selectTransfers(Map<String, Object> params);

    List<Map<String, Object>> selectConfirms(Map<String, Object> params);

    int handleConfirm(CaseConfirmCommand confirm);

    List<Map<String, Object>> selectStatusLogs(Map<String, Object> params);
}
