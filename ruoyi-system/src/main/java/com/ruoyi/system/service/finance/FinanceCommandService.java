package com.ruoyi.system.service.finance;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.business.contract.dto.FeeConfirmCommand;
import com.law.business.contract.dto.FeeInvoiceCommand;
import com.law.business.contract.dto.FeeRejectCommand;
import com.law.business.finance.dto.ExpenseUpdateCommand;
import com.law.business.finance.dto.InvoiceHandleCommand;
import com.law.business.finance.dto.PaymentConfirmCommand;
import com.law.business.finance.dto.PaymentRejectCommand;
import com.law.business.shared.status.CaseStatus;
import com.law.business.security.FinancePermissions;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.mapper.BizFinanceMapper;
import com.ruoyi.system.service.IBizContractService;
import com.ruoyi.system.service.ISysDictTypeService;

/**
 * 财务写操作服务。查询和报表仍由 BizFinanceServiceImpl 负责，避免读写职责继续膨胀。
 */
@Service
public class FinanceCommandService
{
    @Autowired
    private BizFinanceMapper financeMapper;

    @Autowired
    private IBizContractService contractService;

    @Autowired
    private ISysDictTypeService dictTypeService;

    public int confirmPayment(PaymentConfirmCommand command)
    {
        FeeConfirmCommand contractCommand = new FeeConfirmCommand();
        contractCommand.setPlanId(command.getPlanId());
        contractCommand.setReceivedAmount(command.getReceivedAmount());
        contractCommand.setRemark(command.getReason());
        contractCommand.setPaymentMethod(command.getPaymentMethod());
        return contractService.confirmFeePlan(contractCommand);
    }

    public int rejectPayment(PaymentRejectCommand command)
    {
        FeeRejectCommand contractCommand = new FeeRejectCommand();
        contractCommand.setPlanId(command.getPlanId());
        contractCommand.setReason(command.getReason());
        return contractService.rejectFeePlan(contractCommand);
    }

    public int handleInvoice(InvoiceHandleCommand command)
    {
        FeeInvoiceCommand contractCommand = new FeeInvoiceCommand();
        contractCommand.setPlanId(command.getPlanId());
        contractCommand.setInvoiceStatus(command.getInvoiceStatus());
        contractCommand.setRemark(command.getReason());
        contractCommand.setInvoiceType(command.getInvoiceType());
        return contractService.invoiceFeePlan(contractCommand);
    }

    @Transactional
    public int updateExpense(ExpenseUpdateCommand command)
    {
        Map<String, Object> expense = command.toPersistenceMap();
        Map<String, Object> params = scopeParams();
        params.put("expenseId", command.getExpenseId());
        Map<String, Object> existed = financeMapper.selectFinanceExpenseById(params);
        if (existed == null)
        {
            throw new ServiceException("费用不存在或无权处理");
        }
        String caseStatusCode = text(existed.get("caseStatus"));
        CaseStatus caseStatus;
        try
        {
            caseStatus = CaseStatus.fromCode(caseStatusCode);
        }
        catch (IllegalArgumentException e)
        {
            throw new ServiceException("案件状态不合法");
        }
        if (caseStatus.isFinalStatus())
        {
            throw new ServiceException("归档或终止案件不允许处理费用");
        }
        assertDictValue("law_case_expense_type", command.getExpenseType(), "费用类型不合法");
        assertDictValue("law_case_pay_status", command.getPayStatus(), "付款状态不合法");
        assertDictValue("law_case_reimburse_status", command.getReimburseStatus(), "报销状态不合法");
        assertDictValue("law_case_voucher_status", command.getVoucherStatus(), "凭证状态不合法");
        expense.put("updateBy", SecurityUtils.getUsername());
        int rows = financeMapper.updateFinanceExpense(expense);
        if (rows <= 0)
        {
            throw new ServiceException("费用处理失败");
        }
        insertCaseStatusLog(number(existed.get("caseId")), caseStatusCode, "expense_edit",
                "财务处理案件费用：" + command.getAmount());
        return rows;
    }

    private Map<String, Object> scopeParams()
    {
        Map<String, Object> params = new HashMap<>();
        LoginUser loginUser = SecurityUtils.getLoginUser();
        params.put("currentUserId", SecurityUtils.getUserId());
        params.put("currentDeptId", SecurityUtils.getDeptId());
        params.put("dataScope", loginUser != null && !SecurityUtils.isAdmin());
        params.put("permissions", FinancePermissions.DATA_SCOPE);
        return params;
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

    private void assertDictValue(String dictType, String value, String message)
    {
        List<SysDictData> options = dictTypeService.selectDictDataByType(dictType);
        if (!containsDictValue(options, value))
        {
            dictTypeService.resetDictCache();
            options = dictTypeService.selectDictDataByType(dictType);
        }
        if (!containsDictValue(options, value))
        {
            throw new ServiceException(message);
        }
    }

    private boolean containsDictValue(List<SysDictData> options, String value)
    {
        if (options == null) return false;
        for (SysDictData option : options)
        {
            if (option != null && "0".equals(option.getStatus()) && value.equals(option.getDictValue())) return true;
        }
        return false;
    }

    private Long number(Object value)
    {
        if (value instanceof Number) return ((Number) value).longValue();
        try
        {
            return Long.valueOf(String.valueOf(value));
        }
        catch (RuntimeException e)
        {
            throw new ServiceException("请选择案件");
        }
    }

    private String text(Object value) { return value == null ? null : String.valueOf(value); }
}
