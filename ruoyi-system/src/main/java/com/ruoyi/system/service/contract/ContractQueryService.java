package com.ruoyi.system.service.contract;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.law.business.security.ContractPermissions;
import com.ruoyi.common.annotation.DataScope;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.domain.BizContract;
import com.ruoyi.system.mapper.BizContractMapper;

@Service
public class ContractQueryService
{
    private final BizContractMapper contractMapper;
    private final ContractAccessPolicy accessPolicy;

    public ContractQueryService(BizContractMapper contractMapper, ContractAccessPolicy accessPolicy)
    {
        this.contractMapper = contractMapper;
        this.accessPolicy = accessPolicy;
    }

    @DataScope(deptAlias = "c", userAlias = "c", userField = "owner_id")
    public List<BizContract> contracts(BizContract contract)
    {
        return contractMapper.selectContractList(contract);
    }

    public BizContract contract(Long contractId)
    {
        return accessPolicy.requireReadable(contractId);
    }

    public Map<String, Object> dashboard()
    {
        Map<String, Object> result = new HashMap<>();
        boolean scoped = !SecurityUtils.isAdmin();
        result.put("cards", contractMapper.selectDashboardCards(SecurityUtils.getUserId(), SecurityUtils.getDeptId(),
                scoped, ContractPermissions.DATA_SCOPE, Collections.emptyMap()));
        result.put("cases", contractMapper.selectCaseStats(SecurityUtils.getUserId(), SecurityUtils.getDeptId(),
                scoped, ContractPermissions.DATA_SCOPE, Collections.emptyMap()));
        return result;
    }

    public List<Map<String, Object>> approvals(Map<String, Object> params)
    {
        return contractMapper.selectApprovals(scope(params));
    }

    public List<Map<String, Object>> feePlans(Map<String, Object> params)
    {
        return contractMapper.selectFeePlans(scope(params));
    }

    public List<Map<String, Object>> attachments(Map<String, Object> params)
    {
        return contractMapper.selectAttachments(scope(params));
    }

    public List<Map<String, Object>> statusLogs(Map<String, Object> params)
    {
        return contractMapper.selectStatusLogs(scope(params));
    }

    private Map<String, Object> scope(Map<String, Object> params)
    {
        Map<String, Object> scoped = new HashMap<>();
        if (params != null) scoped.putAll(params);
        scoped.put("currentUserId", SecurityUtils.getUserId());
        scoped.put("currentDeptId", SecurityUtils.getDeptId());
        scoped.put("dataScope", !SecurityUtils.isAdmin());
        scoped.put("permissions", ContractPermissions.DATA_SCOPE);
        return scoped;
    }
}
