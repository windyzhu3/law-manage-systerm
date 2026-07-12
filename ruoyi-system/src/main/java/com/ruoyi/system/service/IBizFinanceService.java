package com.ruoyi.system.service;

import java.util.List;
import java.util.Map;
import com.law.business.finance.dto.ExpenseUpdateCommand;
import com.law.business.finance.dto.InvoiceHandleCommand;
import com.law.business.finance.dto.PaymentConfirmCommand;
import com.law.business.finance.dto.PaymentRejectCommand;

public interface IBizFinanceService
{
    Map<String, Object> selectDashboard();

    List<Map<String, Object>> selectReceivableList(Map<String, Object> params);

    List<Map<String, Object>> selectPaymentList(Map<String, Object> params);

    List<Map<String, Object>> selectInvoiceList(Map<String, Object> params);

    List<Map<String, Object>> selectExpenseList(Map<String, Object> params);

    Map<String, Object> selectCaseFinance(Long caseId);

    Map<String, Object> selectReports(Map<String, Object> params);

    int confirmPayment(PaymentConfirmCommand command);

    int rejectPayment(PaymentRejectCommand command);

    int handleInvoice(InvoiceHandleCommand command);

    int updateExpense(ExpenseUpdateCommand command);
}
