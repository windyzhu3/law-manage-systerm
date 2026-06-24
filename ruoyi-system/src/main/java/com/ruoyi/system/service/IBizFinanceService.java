package com.ruoyi.system.service;

import java.util.List;
import java.util.Map;

public interface IBizFinanceService
{
    Map<String, Object> selectDashboard();

    List<Map<String, Object>> selectReceivableList(Map<String, Object> params);

    List<Map<String, Object>> selectPaymentList(Map<String, Object> params);

    List<Map<String, Object>> selectInvoiceList(Map<String, Object> params);

    List<Map<String, Object>> selectExpenseList(Map<String, Object> params);

    Map<String, Object> selectCaseFinance(Long caseId);

    Map<String, Object> selectReports(Map<String, Object> params);

    int confirmPayment(Map<String, Object> body);

    int rejectPayment(Map<String, Object> body);

    int handleInvoice(Map<String, Object> body);

    int updateExpense(Map<String, Object> expense);
}
