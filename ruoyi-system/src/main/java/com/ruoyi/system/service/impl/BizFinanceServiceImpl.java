package com.ruoyi.system.service.impl;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.mapper.BizFinanceMapper;
import com.ruoyi.system.service.IBizContractService;
import com.ruoyi.system.service.IBizFinanceService;
import com.ruoyi.system.service.ISysDictTypeService;

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
    private ISysDictTypeService dictTypeService;

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
        String paymentMethod = requiredText(body, "paymentMethod", "请选择付款方式");
        return contractService.confirmFeePlan(planId, amount, optionalText(body, "reason"), paymentMethod);
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
        String invoiceType = requiredText(body, "invoiceType", "请选择发票类型");
        return contractService.invoiceFeePlan(planId, invoiceStatus, optionalText(body, "reason"), invoiceType);
    }

    @Override
    @Transactional
    public int updateExpense(Map<String, Object> expense)
    {
        Long expenseId = requiredLong(expense, "expenseId", "请选择费用");
        Map<String, Object> params = scopeParams(new HashMap<>());
        params.put("expenseId", expenseId);
        Map<String, Object> existed = financeMapper.selectFinanceExpenseById(params);
        if (existed == null)
        {
            throw new ServiceException("费用不存在或无权处理");
        }
        String caseStatus = optionalText(existed, "caseStatus");
        if ("archived".equals(caseStatus) || "terminated".equals(caseStatus))
        {
            throw new ServiceException("归档或终止案件不允许处理费用");
        }
        validateExpense(expense);
        expense.put("updateBy", SecurityUtils.getUsername());
        int rows = financeMapper.updateFinanceExpense(expense);
        if (rows <= 0)
        {
            throw new ServiceException("费用处理失败");
        }
        insertCaseStatusLog(toLong(existed.get("caseId"), "请选择案件"), caseStatus, "expense_edit", "财务处理案件费用：" + expense.get("amount"));
        return rows;
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

    private void validateExpense(Map<String, Object> expense)
    {
        requiredText(expense, "expenseType", "请选择费用类型");
        BigDecimal amount = decimalValue(requiredText(expense, "amount", "请输入费用金额"));
        if (amount.compareTo(BigDecimal.ZERO) <= 0)
        {
            throw new ServiceException("费用金额必须大于 0");
        }
        requiredText(expense, "occurDate", "请选择发生日期");
        requiredText(expense, "payStatus", "请选择付款状态");
        requiredText(expense, "reimburseStatus", "请选择报销状态");
        requiredText(expense, "voucherStatus", "请选择凭证状态");
        assertDictValue("law_case_expense_type", expense.get("expenseType"), "费用类型不合法");
        assertDictValue("law_case_pay_status", expense.get("payStatus"), "付款状态不合法");
        assertDictValue("law_case_reimburse_status", expense.get("reimburseStatus"), "报销状态不合法");
        assertDictValue("law_case_voucher_status", expense.get("voucherStatus"), "凭证状态不合法");
    }

    private BigDecimal decimalValue(String value)
    {
        try
        {
            return new BigDecimal(value);
        }
        catch (NumberFormatException e)
        {
            throw new ServiceException("金额格式不正确");
        }
    }

    private Long toLong(Object value, String message)
    {
        if (value instanceof Number)
        {
            return ((Number) value).longValue();
        }
        try
        {
            return Long.valueOf(String.valueOf(value));
        }
        catch (RuntimeException e)
        {
            throw new ServiceException(message);
        }
    }

    private void insertCaseStatusLog(Long caseId, String caseStatus, String actionType, String content)
    {
        assertDictValue("law_case_status_action", actionType, "案件状态动作不合法");
        Map<String, Object> log = new HashMap<>();
        log.put("caseId", caseId);
        log.put("fromStatus", caseStatus);
        log.put("toStatus", caseStatus);
        log.put("actionType", actionType);
        log.put("content", content);
        log.put("createBy", SecurityUtils.getUsername());
        if (financeMapper.insertCaseStatusLog(log) <= 0)
        {
            throw new ServiceException("案件状态记录创建失败");
        }
    }

    private void assertDictValue(String dictType, Object value, String message)
    {
        String valueText = value == null ? null : String.valueOf(value).trim();
        if (StringUtils.isEmpty(valueText) || "null".equalsIgnoreCase(valueText))
        {
            return;
        }
        List<SysDictData> options = dictTypeService.selectDictDataByType(dictType);
        if (containsDictValue(options, valueText))
        {
            return;
        }
        dictTypeService.resetDictCache();
        options = dictTypeService.selectDictDataByType(dictType);
        if (!containsDictValue(options, valueText))
        {
            throw new ServiceException(message);
        }
    }

    private boolean containsDictValue(List<SysDictData> options, String value)
    {
        if (options == null)
        {
            return false;
        }
        for (SysDictData option : options)
        {
            if (option != null && "0".equals(option.getStatus()) && value.equals(option.getDictValue()))
            {
                return true;
            }
        }
        return false;
    }
}
