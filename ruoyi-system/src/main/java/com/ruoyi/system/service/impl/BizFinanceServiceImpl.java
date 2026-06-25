package com.ruoyi.system.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.mapper.BizFinanceMapper;
import com.ruoyi.system.service.IBizContractService;
import com.ruoyi.system.service.IBizFinanceService;
import com.ruoyi.system.service.IBizMatterService;

@Service
public class BizFinanceServiceImpl implements IBizFinanceService
{
    private static final String FINANCE_PERMISSIONS =
        "finance:overview,finance:receivable:list,finance:payment:list,finance:invoice:list,finance:expense:list,finance:report:list,contract:fee:list,matter:expense:list";

    @Autowired
    private BizFinanceMapper financeMapper;

    @Autowired
    private IBizContractService contractService;

    @Autowired
    private IBizMatterService matterService;

    @Override
    public Map<String, Object> selectDashboard()
    {
        Map<String, Object> params = scopeParams(new HashMap<>());
        Map<String, Object> data = new HashMap<>();
        data.put("cards", financeMapper.selectDashboardCards(params));
        data.put("trend", financeMapper.selectReceiveTrend(params));
        data.put("aging", financeMapper.selectAgingStats(params));
        data.put("links", financeMapper.selectLinkStats(params));
        data.put("reminders", financeMapper.selectReminders(params));
        data.put("leadFunnel", financeMapper.selectLeadFunnel(params));
        data.put("leadSourceConversion", financeMapper.selectLeadSourceConversion(params));
        data.put("pendingPayments", financeMapper.selectPaymentList(withLimit(params, 5, "0")));
        data.put("dueReceivables", financeMapper.selectReceivableList(withLimit(params, 5, null)));
        return data;
    }

    @Override
    public List<Map<String, Object>> selectReceivableList(Map<String, Object> params)
    {
        return financeMapper.selectReceivableList(scopeParams(params));
    }

    @Override
    public List<Map<String, Object>> selectPaymentList(Map<String, Object> params)
    {
        return financeMapper.selectPaymentList(scopeParams(params));
    }

    @Override
    public List<Map<String, Object>> selectInvoiceList(Map<String, Object> params)
    {
        return financeMapper.selectInvoiceList(scopeParams(params));
    }

    @Override
    public List<Map<String, Object>> selectExpenseList(Map<String, Object> params)
    {
        return financeMapper.selectExpenseList(scopeParams(params));
    }

    @Override
    public Map<String, Object> selectCaseFinance(Long caseId)
    {
        if (caseId == null)
        {
            throw new ServiceException("请选择案件");
        }
        Map<String, Object> params = scopeParams(new HashMap<>());
        params.put("caseId", caseId);
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> summary = financeMapper.selectCaseFinanceSummary(params);
        if (summary == null)
        {
            throw new ServiceException("案件不存在或无权查看");
        }
        data.put("summary", summary);
        data.put("feePlans", financeMapper.selectCaseFinanceFeePlans(params));
        data.put("expenses", financeMapper.selectCaseFinanceExpenses(params));
        return data;
    }

    @Override
    public Map<String, Object> selectReports(Map<String, Object> params)
    {
        Map<String, Object> scoped = scopeParams(params);
        Map<String, Object> data = new HashMap<>();
        data.put("cards", financeMapper.selectReportCards(scoped));
        data.put("trend", financeMapper.selectReceiveTrend(scoped));
        data.put("aging", financeMapper.selectAgingStats(scoped));
        data.put("lawyerRevenue", financeMapper.selectLawyerRevenue(scoped));
        data.put("caseCost", financeMapper.selectCaseCost(scoped));
        data.put("salesCollection", financeMapper.selectSalesCollection(scoped));
        data.put("leadFunnel", financeMapper.selectLeadFunnel(scoped));
        data.put("leadSourceConversion", financeMapper.selectLeadSourceConversion(scoped));
        return data;
    }

    @Override
    public int confirmPayment(Map<String, Object> body)
    {
        Long planId = requiredLong(body, "planId", "请选择收费计划");
        String amount = requiredText(body, "receivedAmount", "请输入本次回款金额");
        return contractService.confirmFeePlan(planId, amount, optionalText(body, "reason"));
    }

    @Override
    public int rejectPayment(Map<String, Object> body)
    {
        Long planId = requiredLong(body, "planId", "请选择收费计划");
        String reason = requiredText(body, "reason", "请填写驳回原因");
        return contractService.rejectFeePlan(planId, reason);
    }

    @Override
    public int handleInvoice(Map<String, Object> body)
    {
        Long planId = requiredLong(body, "planId", "请选择收费计划");
        String invoiceStatus = requiredText(body, "invoiceStatus", "请选择开票状态");
        return contractService.invoiceFeePlan(planId, invoiceStatus, optionalText(body, "reason"));
    }

    @Override
    public int updateExpense(Map<String, Object> expense)
    {
        return matterService.updateExpense(expense);
    }

    private Map<String, Object> scopeParams(Map<String, Object> params)
    {
        Map<String, Object> target = new HashMap<>();
        if (params != null)
        {
            target.putAll(params);
        }
        LoginUser loginUser = SecurityUtils.getLoginUser();
        target.put("currentUserId", SecurityUtils.getUserId());
        target.put("currentDeptId", SecurityUtils.getDeptId());
        target.put("dataScope", loginUser != null && !SecurityUtils.isAdmin());
        target.put("permissions", FINANCE_PERMISSIONS);
        return target;
    }

    private Map<String, Object> withLimit(Map<String, Object> params, int pageSize, String confirmStatus)
    {
        Map<String, Object> target = new HashMap<>(params);
        target.put("dashboardLimit", pageSize);
        if (confirmStatus != null)
        {
            target.put("confirmStatus", confirmStatus);
        }
        return target;
    }

    private Long requiredLong(Map<String, Object> source, String key, String message)
    {
        String text = requiredText(source, key, message);
        try
        {
            return Long.valueOf(text);
        }
        catch (NumberFormatException e)
        {
            throw new ServiceException(message);
        }
    }

    private String requiredText(Map<String, Object> source, String key, String message)
    {
        Object value = source == null ? null : source.get(key);
        String text = value == null ? null : String.valueOf(value).trim();
        if (StringUtils.isEmpty(text) || "null".equalsIgnoreCase(text))
        {
            throw new ServiceException(message);
        }
        return text;
    }

    private String optionalText(Map<String, Object> source, String key)
    {
        Object value = source == null ? null : source.get(key);
        String text = value == null ? null : String.valueOf(value).trim();
        return StringUtils.isEmpty(text) || "null".equalsIgnoreCase(text) ? null : text;
    }
}
