package com.ruoyi.system.service.contract;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.business.contract.dto.FeeConfirmCommand;
import com.law.business.contract.dto.FeeRejectCommand;
import com.law.business.event.BusinessEventCommand;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.event.BusinessEventType;
import com.law.business.security.BusinessActor;
import com.law.business.shared.error.BusinessErrorCode;
import com.law.business.shared.status.ContractStatus;
import com.law.business.shared.status.FeePaymentStatus;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.mapper.BizContractMapper;
import com.ruoyi.system.service.ISysDictTypeService;

@Service
public class ContractPaymentService
{
    private static final String PENDING = FeePaymentStatus.PENDING.code();
    private static final String CONFIRMED = FeePaymentStatus.CONFIRMED.code();
    private static final String REJECTED = FeePaymentStatus.REJECTED.code();

    private final BizContractMapper mapper;
    private final ContractAccessPolicy access;
    private final ISysDictTypeService dictionaries;
    private final BusinessEventPublisher events;
    private final ContractActionLogService logs;

    public ContractPaymentService(BizContractMapper mapper, ContractAccessPolicy access,
            ISysDictTypeService dictionaries, BusinessEventPublisher events,
            ContractActionLogService logs)
    {
        this.mapper = mapper;
        this.access = access;
        this.dictionaries = dictionaries;
        this.events = events;
        this.logs = logs;
    }

    @Transactional
    public int confirm(FeeConfirmCommand command)
    {
        return confirm(command.getPlanId(), command.getReceivedAmount(), command.getRemark(),
                command.getPaymentMethod(), command.getVoucherUrl(), currentActor());
    }

    @Transactional
    public int confirm(Long planId, String receivedAmount, String remark, String paymentMethod)
    {
        return confirm(planId, receivedAmount, remark, paymentMethod, null, currentActor());
    }

    @Transactional
    public int confirm(Long planId, String receivedAmount, String remark, String paymentMethod,
            String voucherUrl, BusinessActor actor)
    {
        ContractFeePlanContext context = access.requireFeePlanOperable(planId);
        requirePerforming(context);
        String method = clean(paymentMethod);
        if (method != null) dict("law_finance_payment_method", method, "付款方式不合法");
        BigDecimal receivable = context.receivableAmount();
        BigDecimal received = context.receivedAmount() == null ? BigDecimal.ZERO : context.receivedAmount();
        BigDecimal remaining = receivable.subtract(received);
        if (CONFIRMED.equals(context.confirmStatus()) && remaining.signum() <= 0)
            throw error(BusinessErrorCode.DUPLICATE_OPERATION, "收费计划已收齐，不能重复确认收款");
        if (!PENDING.equals(context.confirmStatus()) && !CONFIRMED.equals(context.confirmStatus()))
            throw error(BusinessErrorCode.STATE_CONFLICT, "当前收费计划状态不允许确认收款");
        BigDecimal delta = amount(receivedAmount, remaining);
        if (delta.signum() <= 0)
            throw error(BusinessErrorCode.VALIDATION_FAILED, "实收金额必须大于0");
        if (delta.compareTo(remaining) > 0)
            throw error(BusinessErrorCode.VALIDATION_FAILED, "本次回款不能超过待收金额");
        BigDecimal total = received.add(delta);
        Map<String, Object> update = new HashMap<>();
        update.put("planId", planId);
        update.put("confirmStatus", CONFIRMED);
        update.put("receivedAmount", total);
        update.put("expectedConfirmStatus", context.confirmStatus());
        update.put("expectedInvoiceStatus", context.invoiceStatus());
        update.put("expectedContractStatus", context.contractStatus());
        update.put("updateBy", actor.userName());
        if (method != null) update.put("paymentMethod", method);
        if (clean(remark) != null) update.put("remark", clean(remark));
        changed(mapper.updateFeePlanStatus(update), "收费计划状态已变化，请刷新后重试");

        Long attachmentId = null;
        if (!StringUtils.isEmpty(voucherUrl))
        {
            Map<String, Object> file = new HashMap<>();
            file.put("contractId", context.contractId());
            file.put("fileType", "PAYMENT_PROOF");
            file.put("fileName", "payment-" + planId);
            file.put("fileUrl", voucherUrl);
            file.put("createBy", actor.userName());
            changed(mapper.insertAttachment(file), "付款凭证保存失败");
            if (file.get("attachmentId") == null)
                throw error(BusinessErrorCode.CONCURRENT_MODIFICATION, "付款凭证主键回填失败");
            attachmentId = Long.valueOf(String.valueOf(file.get("attachmentId")));
        }
        String content = (total.compareTo(receivable) >= 0 ? "确认收款" : "部分收款")
                + "，本次实收 " + delta + "，累计实收 " + total;
        dict("law_contract_status_action", "fee_confirm", "合同状态动作不合法");
        Long logId = logs.record(context.contractId(), context.confirmStatus(), CONFIRMED,
                "fee_confirm", content, actor);
        Map<String, Object> payload = payload("schemaVersion", 1, "operatorId", actor.userId(),
                "planId", planId, "receivedAmount", delta, "totalReceivedAmount", total,
                "logId", logId, "attachmentId", attachmentId);
        events.publish(new BusinessEventCommand(BusinessEventType.PAYMENT_CONFIRMED, "CONTRACT",
                context.contractId(), context.contractNo(),
                "PAYMENT_CONFIRMED:" + context.contractId() + ":" + planId + ":" + logId, payload));
        return 1;
    }

    @Transactional
    public int reject(FeeRejectCommand command)
    {
        return reject(command.getPlanId(), command.getReason(), currentActor());
    }

    @Transactional
    public int reject(Long planId, String reason)
    {
        return reject(planId, reason, currentActor());
    }

    @Transactional
    public int reject(Long planId, String reason, BusinessActor actor)
    {
        String value = clean(reason);
        if (value == null) throw error(BusinessErrorCode.VALIDATION_FAILED, "驳回原因必填");
        ContractFeePlanContext context = access.requireFeePlanOperable(planId);
        requirePerforming(context);
        if (!PENDING.equals(context.confirmStatus()))
            throw error(BusinessErrorCode.STATE_CONFLICT, "只有待确认的收费计划可以驳回");
        Map<String, Object> update = new HashMap<>();
        update.put("planId", planId);
        update.put("confirmStatus", REJECTED);
        update.put("remark", value);
        update.put("expectedConfirmStatus", context.confirmStatus());
        update.put("expectedInvoiceStatus", context.invoiceStatus());
        update.put("expectedContractStatus", context.contractStatus());
        update.put("updateBy", actor.userName());
        changed(mapper.updateFeePlanStatus(update), "收费计划状态已变化，请刷新后重试");
        dict("law_contract_status_action", "fee_reject", "合同状态动作不合法");
        Long logId = logs.record(context.contractId(), PENDING, REJECTED, "fee_reject", value, actor);
        events.publish(new BusinessEventCommand(BusinessEventType.PAYMENT_REJECTED, "CONTRACT",
                context.contractId(), context.contractNo(),
                "PAYMENT_REJECTED:" + context.contractId() + ":" + planId + ":" + logId,
                payload("schemaVersion", 1, "operatorId", actor.userId(), "planId", planId,
                        "reason", value, "logId", logId)));
        return 1;
    }

    private void requirePerforming(ContractFeePlanContext context)
    {
        if (!ContractStatus.PERFORMING.code().equals(context.contractStatus()))
            throw error(BusinessErrorCode.STATE_CONFLICT, "只有履约中的合同可以确认收款");
    }
    private void dict(String type, String value, String message)
    {
        List<SysDictData> options = dictionaries.selectDictDataByType(type);
        if (options != null) for (SysDictData option : options) if (value.equals(option.getDictValue())) return;
        throw error(options == null || options.isEmpty() ? BusinessErrorCode.PRECONDITION_FAILED
                : BusinessErrorCode.VALIDATION_FAILED,
                options == null || options.isEmpty() ? "字典未初始化：" + type : message);
    }
    private BigDecimal amount(String value, BigDecimal fallback)
    {
        Object source = StringUtils.isEmpty(value) ? fallback : value;
        try { return new BigDecimal(String.valueOf(source)); }
        catch (RuntimeException exception) { throw error(BusinessErrorCode.VALIDATION_FAILED, "实收金额必须为数字"); }
    }
    private Map<String, Object> payload(Object... values)
    {
        Map<String, Object> result = new HashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2)
            if (values[index + 1] != null) result.put(String.valueOf(values[index]), values[index + 1]);
        return result;
    }
    private String clean(String value) { return StringUtils.isEmpty(value) ? null : value.trim(); }
    private void changed(int rows, String message)
    {
        if (rows <= 0) throw error(BusinessErrorCode.CONCURRENT_MODIFICATION, message);
    }
    private ServiceException error(BusinessErrorCode code, String message)
    {
        return new ServiceException(message, code.name());
    }
    private BusinessActor currentActor()
    {
        try
        {
            return new BusinessActor(SecurityUtils.getUserId(), SecurityUtils.getUsername(),
                    SecurityUtils.getLoginUser().getUser().getNickName(), SecurityUtils.getDeptId(),
                    SecurityUtils.isAdmin());
        }
        catch (RuntimeException absent)
        {
            return new BusinessActor(0L, "system", "system", null, false);
        }
    }
}
