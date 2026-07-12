package com.ruoyi.system.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.law.business.finance.dto.ExpenseUpdateCommand;
import com.law.business.finance.dto.InvoiceHandleCommand;
import com.law.business.finance.dto.PaymentConfirmCommand;
import com.law.business.finance.dto.PaymentRejectCommand;
import com.law.business.security.FinancePermissions;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.mapper.BizFinanceMapper;
import com.ruoyi.system.service.IBizFinanceService;
import com.ruoyi.system.service.finance.FinanceCommandService;

@Service
public class BizFinanceServiceImpl implements IBizFinanceService
{
    @Autowired
    private BizFinanceMapper financeMapper;

    @Autowired
    private FinanceCommandService commandService;

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
        data.put("flow", financeMapper.selectFinanceFlow(params));
        data.put("receiveTrend", financeMapper.selectReceiveTrend(params));
        data.put("invoiceExpenseTrend", financeMapper.selectInvoiceExpenseTrend(params));
        data.put("invoiceActivities", financeMapper.selectInvoiceActivities(withLimit(params, 5, null)));
        data.put("financeSummaryRows", financeMapper.selectFinanceSummaryRows(params));
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
        Map<String, Object> scoped = reportParams(params);
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

    private Map<String, Object> reportParams(Map<String, Object> params)
    {
        Map<String, Object> scoped = scopeParams(params);
        // 财务报表返回的是聚合图表数据，不应继承列表分页参数，否则 PageHelper 会在固定 limit 后再次追加 LIMIT。
        scoped.remove("pageNum");
        scoped.remove("pageSize");
        scoped.remove("orderByColumn");
        scoped.remove("isAsc");
        scoped.remove("reasonable");
        scoped.remove("pageSizeZero");
        scoped.remove("count");
        scoped.remove("orderBy");
        scoped.remove("params");
        return scoped;
    }

    @Override
    public int confirmPayment(PaymentConfirmCommand command)
    {
        return commandService.confirmPayment(command);
    }

    @Override
    public int rejectPayment(PaymentRejectCommand command)
    {
        return commandService.rejectPayment(command);
    }

    @Override
    public int handleInvoice(InvoiceHandleCommand command)
    {
        return commandService.handleInvoice(command);
    }

    @Override
    public int updateExpense(ExpenseUpdateCommand command)
    {
        return commandService.updateExpense(command);
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
        target.put("permissions", FinancePermissions.DATA_SCOPE);
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

}
