package com.ruoyi.system.service.impl;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.system.domain.BizContract;
import com.ruoyi.system.service.IBizCaseService;
import com.ruoyi.system.service.casecenter.CaseQueryService;
import com.ruoyi.system.service.casecenter.CaseCreationService;
import com.ruoyi.system.service.casecenter.CaseAssignmentService;
import com.ruoyi.system.service.casecenter.CaseTransferService;
import com.ruoyi.system.service.casecenter.CaseConfirmationService;
import com.ruoyi.system.service.casecenter.LawyerProfileService;

@Service
public class BizCaseServiceImpl implements IBizCaseService
{
    @Autowired
    private CaseQueryService queryService;

    @Autowired
    private LawyerProfileService lawyerProfileService;

    @Autowired
    private CaseCreationService caseCreationService;

    @Autowired
    private CaseAssignmentService caseAssignmentService;

    @Autowired
    private CaseTransferService caseTransferService;

    @Autowired
    private CaseConfirmationService caseConfirmationService;

    @Override
    public List<Map<String, Object>> selectCaseList(Map<String, Object> params)
    {
        return queryService.cases(params);
    }

    @Override
    public Map<String, Object> selectCaseById(Long caseId)
    {
        return queryService.caseDetail(caseId);
    }

    @Override
    public Map<String, Object> selectDashboard()
    {
        return queryService.dashboard();
    }

    @Override
    public List<Map<String, Object>> selectLawyerLoads(Map<String, Object> params)
    {
        return queryService.lawyerLoads(params);
    }

    @Override
    public List<Map<String, Object>> selectLawyerSpecialtyStats(Map<String, Object> params)
    {
        return queryService.lawyerSpecialties(params);
    }

    @Override
    public List<Map<String, Object>> selectLawyerProfiles(Map<String, Object> params)
    {
        return queryService.lawyerProfiles(params);
    }

    @Override
    public Map<String, Object> selectLawyerProfileByUserId(Long userId)
    {
        return queryService.lawyerProfile(userId);
    }

    @Override
    @Transactional
    public int saveLawyerProfile(Map<String, Object> profile)
    {
        return lawyerProfileService.save(profile);
    }

    @Override
    @Transactional
    public int updateLawyerProfileStatus(Map<String, Object> profile)
    {
        return lawyerProfileService.updateStatus(profile);
    }

    @Override
    @Transactional
    public int createCaseFromContract(BizContract contract)
    {
        return caseCreationService.createFromContract(contract);
    }

    @Override
    @Transactional
    public int assignCase(Map<String, Object> assignment)
    {
        return caseAssignmentService.assign(assignment);
    }

    @Override
    @Transactional
    public int batchAssignCases(Map<String, Object> assignment)
    {
        return caseAssignmentService.batchAssign(assignment);
    }

    @Override
    public List<Map<String, Object>> selectAssignments(Map<String, Object> params)
    {
        return queryService.assignments(params);
    }

    @Override
    @Transactional
    public int requestTransfer(Map<String, Object> transfer)
    {
        return caseTransferService.request(transfer);
    }

    @Override
    @Transactional
    public int approveTransfer(Map<String, Object> approval)
    {
        return caseTransferService.approve(approval);
    }

    @Override
    public List<Map<String, Object>> selectTransfers(Map<String, Object> params)
    {
        return queryService.transfers(params);
    }

    @Override
    public List<Map<String, Object>> selectConfirms(Map<String, Object> params)
    {
        return queryService.confirms(params);
    }

    @Override
    @Transactional
    public int handleConfirm(Map<String, Object> confirm)
    {
        return caseConfirmationService.handle(confirm);
    }

    @Override
    public List<Map<String, Object>> selectStatusLogs(Map<String, Object> params)
    {
        return queryService.statusLogs(params);
    }

}
