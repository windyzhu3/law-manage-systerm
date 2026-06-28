package com.ruoyi.system.mapper;

import java.util.List;
import java.util.Map;

public interface BizFinanceMapper
{
    List<Map<String, Object>> selectDashboardCards(Map<String, Object> params);

    List<Map<String, Object>> selectReceiveTrend(Map<String, Object> params);

    List<Map<String, Object>> selectAgingStats(Map<String, Object> params);

    List<Map<String, Object>> selectLinkStats(Map<String, Object> params);

    List<Map<String, Object>> selectReminders(Map<String, Object> params);

    List<Map<String, Object>> selectFinanceFlow(Map<String, Object> params);

    List<Map<String, Object>> selectInvoiceExpenseTrend(Map<String, Object> params);

    List<Map<String, Object>> selectInvoiceActivities(Map<String, Object> params);

    List<Map<String, Object>> selectFinanceSummaryRows(Map<String, Object> params);

    List<Map<String, Object>> selectLeadFunnel(Map<String, Object> params);

    List<Map<String, Object>> selectLeadSourceConversion(Map<String, Object> params);

    List<Map<String, Object>> selectReceivableList(Map<String, Object> params);

    List<Map<String, Object>> selectPaymentList(Map<String, Object> params);

    List<Map<String, Object>> selectInvoiceList(Map<String, Object> params);

    List<Map<String, Object>> selectExpenseList(Map<String, Object> params);

    Map<String, Object> selectFinanceExpenseById(Map<String, Object> params);

    int updateFinanceExpense(Map<String, Object> expense);

    int insertCaseStatusLog(Map<String, Object> log);

    Map<String, Object> selectCaseFinanceSummary(Map<String, Object> params);

    List<Map<String, Object>> selectCaseFinanceFeePlans(Map<String, Object> params);

    List<Map<String, Object>> selectCaseFinanceExpenses(Map<String, Object> params);

    List<Map<String, Object>> selectReportCards(Map<String, Object> params);

    List<Map<String, Object>> selectLawyerRevenue(Map<String, Object> params);

    List<Map<String, Object>> selectCaseCost(Map<String, Object> params);

    List<Map<String, Object>> selectSalesCollection(Map<String, Object> params);
}
