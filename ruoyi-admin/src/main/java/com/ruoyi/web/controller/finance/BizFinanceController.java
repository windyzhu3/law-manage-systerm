package com.ruoyi.web.controller.finance;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.system.service.IBizFinanceService;

@RestController
@RequestMapping("/finance")
public class BizFinanceController extends BaseController
{
    @Autowired
    private IBizFinanceService financeService;

    @PreAuthorize("@ss.hasAnyPermi('finance:overview,finance:receivable:list,finance:payment:list,finance:invoice:list,finance:expense:list,finance:report:list')")
    @GetMapping("/dashboard")
    public AjaxResult dashboard()
    {
        return success(financeService.selectDashboard());
    }

    @PreAuthorize("@ss.hasPermi('finance:receivable:list')")
    @GetMapping("/receivable/list")
    public TableDataInfo receivableList(@RequestParam Map<String, Object> params)
    {
        startPage();
        return getDataTable(financeService.selectReceivableList(params));
    }

    @PreAuthorize("@ss.hasPermi('finance:payment:list')")
    @GetMapping("/payment/list")
    public TableDataInfo paymentList(@RequestParam Map<String, Object> params)
    {
        startPage();
        return getDataTable(financeService.selectPaymentList(params));
    }

    @PreAuthorize("@ss.hasPermi('finance:invoice:list')")
    @GetMapping("/invoice/list")
    public TableDataInfo invoiceList(@RequestParam Map<String, Object> params)
    {
        startPage();
        return getDataTable(financeService.selectInvoiceList(params));
    }

    @PreAuthorize("@ss.hasPermi('finance:expense:list')")
    @GetMapping("/expense/list")
    public TableDataInfo expenseList(@RequestParam Map<String, Object> params)
    {
        startPage();
        return getDataTable(financeService.selectExpenseList(params));
    }

    @PreAuthorize("@ss.hasAnyPermi('finance:receivable:list,finance:payment:list,finance:invoice:list,finance:expense:list,finance:report:list')")
    @GetMapping("/case/{caseId}")
    public AjaxResult caseFinance(@PathVariable Long caseId)
    {
        return success(financeService.selectCaseFinance(caseId));
    }

    @PreAuthorize("@ss.hasPermi('finance:report:list')")
    @GetMapping("/report")
    public AjaxResult report(@RequestParam Map<String, Object> params)
    {
        return success(financeService.selectReports(params));
    }

    @PreAuthorize("@ss.hasPermi('finance:payment:confirm')")
    @Log(title = "finance-payment-confirm", businessType = BusinessType.UPDATE)
    @PostMapping("/payment/confirm")
    public AjaxResult confirmPayment(@RequestBody Map<String, Object> body)
    {
        return toAjax(financeService.confirmPayment(body));
    }

    @PreAuthorize("@ss.hasPermi('finance:payment:reject')")
    @Log(title = "finance-payment-reject", businessType = BusinessType.UPDATE)
    @PostMapping("/payment/reject")
    public AjaxResult rejectPayment(@RequestBody Map<String, Object> body)
    {
        return toAjax(financeService.rejectPayment(body));
    }

    @PreAuthorize("@ss.hasPermi('finance:invoice:handle')")
    @Log(title = "finance-invoice", businessType = BusinessType.UPDATE)
    @PostMapping("/invoice/handle")
    public AjaxResult handleInvoice(@RequestBody Map<String, Object> body)
    {
        return toAjax(financeService.handleInvoice(body));
    }

    @PreAuthorize("@ss.hasPermi('finance:expense:edit')")
    @Log(title = "finance-expense", businessType = BusinessType.UPDATE)
    @PutMapping("/expense")
    public AjaxResult updateExpense(@RequestBody Map<String, Object> body)
    {
        return toAjax(financeService.updateExpense(body));
    }
}
