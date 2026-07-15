package com.ruoyi.system.service.contract;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.business.contract.dto.ContractFeePlanCreateCommand;
import com.law.business.contract.dto.ContractFeePlanUpdateCommand;
import com.law.business.security.BusinessActor;
import com.law.business.security.BusinessActorProvider;
import com.law.business.shared.error.BusinessErrorCode;
import com.law.business.shared.status.FeeInvoiceStatus;
import com.law.business.shared.status.FeePaymentStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.mapper.BizContractMapper;

@Service
public class ContractFeePlanService
{
    private static final String PENDING = FeePaymentStatus.PENDING.code();
    private static final String CONFIRMED = FeePaymentStatus.CONFIRMED.code();
    private static final String REJECTED = FeePaymentStatus.REJECTED.code();
    private static final String NOT_INVOICED = FeeInvoiceStatus.NONE.code();

    private final BizContractMapper mapper;
    private final ContractAccessPolicy access;
    private final BusinessActorProvider actors;
    private final ContractActionLogService actionLogs;

    public ContractFeePlanService(BizContractMapper mapper, ContractAccessPolicy access,
            BusinessActorProvider actors, ContractActionLogService actionLogs)
    {
        this.mapper = mapper;
        this.access = access;
        this.actors = actors;
        this.actionLogs = actionLogs;
    }

    @Transactional
    public int create(ContractFeePlanCreateCommand command)
    {
        validate(command);
        access.requireOperable(command.getContractId());
        BusinessActor actor = actors.current();
        Map<String, Object> row = editableValues(command);
        row.put("receivedAmount", BigDecimal.ZERO);
        row.put("confirmStatus", PENDING);
        row.put("invoiceStatus", NOT_INVOICED);
        row.put("createBy", actor.userName());
        int rows = mapper.insertFeePlan(row);
        changed(rows, "收费计划创建失败");
        actionLogs.record(command.getContractId(), null, PENDING, "fee_create",
                content("新增收费计划", command), actor);
        return rows;
    }

    @Transactional
    public int update(ContractFeePlanUpdateCommand command)
    {
        if (command == null || command.getPlanId() == null)
            throw error(BusinessErrorCode.VALIDATION_FAILED, "请选择收费计划");
        validate(command);
        ContractFeePlanContext context = access.requireFeePlanOperable(command.getPlanId());
        requireEditable(context);
        BusinessActor actor = actors.current();
        Map<String, Object> row = editableValues(command);
        row.put("planId", command.getPlanId());
        row.put("contractId", context.contractId());
        row.put("expectedConfirmStatus", context.confirmStatus());
        row.put("expectedInvoiceStatus", context.invoiceStatus());
        row.put("expectedContractStatus", context.contractStatus());
        row.put("updateBy", actor.userName());
        int rows = mapper.updateFeePlan(row);
        changed(rows, "收费计划状态已变化，请刷新后重试");
        if (REJECTED.equals(context.confirmStatus()))
        {
            Map<String, Object> reset = new HashMap<>();
            reset.put("planId", command.getPlanId());
            reset.put("confirmStatus", PENDING);
            reset.put("expectedConfirmStatus", REJECTED);
            reset.put("expectedInvoiceStatus", context.invoiceStatus());
            reset.put("expectedContractStatus", context.contractStatus());
            reset.put("updateBy", actor.userName());
            changed(mapper.updateFeePlanStatus(reset), "收费计划状态已变化，请刷新后重试");
        }
        actionLogs.record(context.contractId(), null, PENDING, "fee_update",
                content("调整收费计划", command), actor);
        return rows;
    }

    @Transactional
    public int delete(Long planId)
    {
        ContractFeePlanContext context = access.requireFeePlanOperable(planId);
        requireEditable(context);
        BusinessActor actor = actors.current();
        int rows = mapper.deleteFeePlan(planId, context.confirmStatus(), context.invoiceStatus(),
                context.contractStatus());
        changed(rows, "收费计划状态已变化，请刷新后重试");
        actionLogs.record(context.contractId(), PENDING, null, "fee_delete", "删除收费计划", actor);
        return rows;
    }

    public int create(Map<String, Object> value) { return create(toCreate(value)); }
    public int update(Map<String, Object> value) { return update(toUpdate(value)); }

    private void requireEditable(ContractFeePlanContext context)
    {
        if (CONFIRMED.equals(context.confirmStatus()))
            throw error(BusinessErrorCode.STATE_CONFLICT, "已确认收费计划不能编辑或删除");
        if (!NOT_INVOICED.equals(context.invoiceStatus()))
            throw error(BusinessErrorCode.STATE_CONFLICT, "已有开票记录的收费计划不能编辑或删除");
    }

    private void validate(ContractFeePlanCreateCommand command)
    {
        if (command == null || command.getContractId() == null)
            throw error(BusinessErrorCode.VALIDATION_FAILED, "请选择合同");
        if (command.getPeriodNo() == null || command.getPeriodNo() <= 0)
            throw error(BusinessErrorCode.VALIDATION_FAILED, "期数必须大于0");
        if (command.getReceivableAmount() == null || command.getReceivableAmount().signum() <= 0)
            throw error(BusinessErrorCode.VALIDATION_FAILED, "应收金额必须大于0");
        if (command.getPlanReceiveDate() == null)
            throw error(BusinessErrorCode.VALIDATION_FAILED, "请选择计划收款日");
    }

    private Map<String, Object> editableValues(ContractFeePlanCreateCommand command)
    {
        Map<String, Object> row = new HashMap<>();
        row.put("contractId", command.getContractId());
        row.put("periodNo", command.getPeriodNo());
        row.put("receivableAmount", command.getReceivableAmount());
        row.put("planReceiveDate", command.getPlanReceiveDate());
        row.put("financeUserId", command.getFinanceUserId());
        row.put("remark", clean(command.getRemark()));
        return row;
    }

    private ContractFeePlanCreateCommand toCreate(Map<String, Object> value)
    {
        if (value == null) return null;
        ContractFeePlanCreateCommand command = new ContractFeePlanCreateCommand();
        copy(value, command);
        return command;
    }

    private ContractFeePlanUpdateCommand toUpdate(Map<String, Object> value)
    {
        if (value == null) return null;
        ContractFeePlanUpdateCommand command = new ContractFeePlanUpdateCommand();
        command.setPlanId(longValue(value.get("planId")));
        copy(value, command);
        return command;
    }

    private void copy(Map<String, Object> value, ContractFeePlanCreateCommand command)
    {
        command.setContractId(longValue(value.get("contractId")));
        command.setPeriodNo(integer(value.get("periodNo")));
        command.setReceivableAmount(decimal(value.get("receivableAmount")));
        command.setPlanReceiveDate(date(value.get("planReceiveDate")));
        command.setFinanceUserId(longValue(value.get("financeUserId")));
        command.setRemark(text(value.get("remark")));
    }

    private String content(String action, ContractFeePlanCreateCommand command)
    {
        return action + ": 第" + command.getPeriodNo() + "期，应收 " + command.getReceivableAmount();
    }
    private Long longValue(Object value)
    {
        if (value == null || StringUtils.isEmpty(String.valueOf(value))) return null;
        try { return Long.valueOf(String.valueOf(value)); }
        catch (NumberFormatException exception) { throw error(BusinessErrorCode.VALIDATION_FAILED, "编号必须为数字"); }
    }
    private Integer integer(Object value)
    {
        if (value == null) return null;
        try { return Integer.valueOf(String.valueOf(value)); }
        catch (NumberFormatException exception) { throw error(BusinessErrorCode.VALIDATION_FAILED, "期数必须为数字"); }
    }
    private BigDecimal decimal(Object value)
    {
        if (value == null) return null;
        try { return new BigDecimal(String.valueOf(value)); }
        catch (NumberFormatException exception) { throw error(BusinessErrorCode.VALIDATION_FAILED, "金额必须为数字"); }
    }
    private LocalDate date(Object value)
    {
        if (value == null || StringUtils.isEmpty(String.valueOf(value))) return null;
        if (value instanceof LocalDate date) return date;
        try { return LocalDate.parse(String.valueOf(value)); }
        catch (RuntimeException exception) { throw error(BusinessErrorCode.VALIDATION_FAILED, "计划收款日不合法"); }
    }
    private String text(Object value) { return value == null ? null : String.valueOf(value); }
    private String clean(String value) { return StringUtils.isEmpty(value) ? null : value.trim(); }
    private void changed(int rows, String message)
    {
        if (rows <= 0) throw error(BusinessErrorCode.CONCURRENT_MODIFICATION, message);
    }
    private ServiceException error(BusinessErrorCode code, String message)
    {
        return new ServiceException(message, code.name());
    }
}
