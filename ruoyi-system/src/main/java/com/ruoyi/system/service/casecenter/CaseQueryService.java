package com.ruoyi.system.service.casecenter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.law.business.security.CasePermissions;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.mapper.BizCaseMapper;

@Service
public class CaseQueryService
{
    private final BizCaseMapper mapper;
    private final CaseAccessPolicy access;

    public CaseQueryService(BizCaseMapper mapper, CaseAccessPolicy access)
    {
        this.mapper = mapper;
        this.access = access;
    }

    public List<Map<String, Object>> cases(Map<String, Object> params) { return mapper.selectCaseList(scope(params)); }

    public Map<String, Object> caseDetail(Long caseId)
    {
        return access.requireReadable(caseId);
    }

    public Map<String, Object> dashboard()
    {
        Map<String, Object> params = scope(null);
        Map<String, Object> result = new HashMap<>();
        result.put("cards", mapper.selectDashboardCards(params));
        result.put("lawyers", mapper.selectLawyerLoads(params));
        result.put("specialties", mapper.selectLawyerSpecialtyStats(params));
        return result;
    }

    public List<Map<String, Object>> lawyerLoads(Map<String, Object> params) { return mapper.selectLawyerLoads(params); }
    public List<Map<String, Object>> lawyerSpecialties(Map<String, Object> params) { return mapper.selectLawyerSpecialtyStats(params); }
    public List<Map<String, Object>> lawyerProfiles(Map<String, Object> params) { return mapper.selectLawyerProfiles(params); }

    public Map<String, Object> lawyerProfile(Long userId)
    {
        Map<String, Object> profile = mapper.selectLawyerProfileByUserId(userId);
        if (profile == null) throw new ServiceException("律师不存在或已停用", "DATA_NOT_FOUND");
        return profile;
    }

    public List<Map<String, Object>> assignments(Map<String, Object> params) { return mapper.selectAssignments(scope(params)); }
    public List<Map<String, Object>> transfers(Map<String, Object> params) { return mapper.selectTransfers(scope(params)); }
    public List<Map<String, Object>> confirms(Map<String, Object> params) { return mapper.selectConfirms(scope(params)); }
    public List<Map<String, Object>> statusLogs(Map<String, Object> params) { return mapper.selectStatusLogs(scope(params)); }

    public void requireAccess(Long caseId)
    {
        access.requireReadable(caseId);
    }

    private Map<String, Object> scope(Map<String, Object> source)
    {
        Map<String, Object> params = new HashMap<>();
        if (source != null) params.putAll(source);
        params.put("currentUserId", SecurityUtils.getUserId());
        params.put("currentDeptId", SecurityUtils.getDeptId());
        params.put("dataScope", !SecurityUtils.isAdmin());
        params.put("permissions", CasePermissions.DATA_SCOPE);
        return params;
    }
}
