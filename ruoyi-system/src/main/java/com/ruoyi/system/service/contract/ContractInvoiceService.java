package com.ruoyi.system.service.contract;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.business.contract.dto.FeeInvoiceCommand;
import com.law.business.event.BusinessEventCommand;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.event.BusinessEventType;
import com.law.business.security.BusinessActor;
import com.law.business.shared.error.BusinessErrorCode;
import com.law.business.shared.status.ContractStatus;
import com.law.business.shared.status.FeeInvoiceStatus;
import com.law.business.shared.status.FeePaymentStatus;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.mapper.BizContractMapper;
import com.ruoyi.system.service.ISysDictTypeService;

@Service
public class ContractInvoiceService
{
    private static final String RECEIVE_CONFIRMED = FeePaymentStatus.CONFIRMED.code();
    private static final String INVOICE_DONE = FeeInvoiceStatus.INVOICED.code();
    private static final String INVOICE_PARTIAL = FeeInvoiceStatus.PARTIAL.code();

    private final BizContractMapper mapper;
    private final ContractAccessPolicy access;
    private final ISysDictTypeService dictionaries;
    private final BusinessEventPublisher events;
    private final ContractActionLogService logs;

    public ContractInvoiceService(BizContractMapper mapper, ContractAccessPolicy access,
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
    public int invoice(FeeInvoiceCommand command)
    {
        return invoice(command.getPlanId(), command.getInvoiceStatus(), command.getRemark(),
                command.getInvoiceType(), null, null, currentActor());
    }

    @Transactional
    public int invoice(Long planId, String target, String remark, String invoiceType)
    {
        return invoice(planId, target, remark, invoiceType, null, null, currentActor());
    }

    @Transactional
    public int invoice(Long planId, String target, String remark, String invoiceType,
            String invoiceNo, String invoiceFileUrl, BusinessActor actor)
    {
        ContractFeePlanContext context = access.requireFeePlanOperable(planId);
        if (!ContractStatus.PERFORMING.code().equals(context.contractStatus()))
            throw error(BusinessErrorCode.STATE_CONFLICT, "只有履约中的合同可以开票");
        if (!RECEIVE_CONFIRMED.equals(context.confirmStatus()))
            throw error(BusinessErrorCode.STATE_CONFLICT, "只有已确认收款的计划可以开票");
        String type = clean(invoiceType);
        if (type != null) dict("law_finance_invoice_type", type, "发票类型不合法");
        dict("law_contract_invoice_status", target, "开票状态不合法");
        if (!INVOICE_DONE.equals(target) && !INVOICE_PARTIAL.equals(target))
            throw error(BusinessErrorCode.VALIDATION_FAILED, "开票状态不合法");
        FeeInvoiceStatus source;
        FeeInvoiceStatus destination;
        try
        {
            source = FeeInvoiceStatus.fromCode(context.invoiceStatus());
            destination = FeeInvoiceStatus.fromCode(target);
        }
        catch (IllegalArgumentException exception)
        {
            throw error(BusinessErrorCode.STATE_CONFLICT, "当前开票状态不允许继续开票");
        }
        if (!source.canTransitionTo(destination))
            throw error(source == FeeInvoiceStatus.INVOICED ? BusinessErrorCode.DUPLICATE_OPERATION
                    : BusinessErrorCode.STATE_CONFLICT, "当前开票状态不允许继续开票");

        Map<String, Object> update = new HashMap<>();
        update.put("planId", planId);
        update.put("invoiceStatus", target);
        update.put("expectedConfirmStatus", context.confirmStatus());
        update.put("expectedInvoiceStatus", context.invoiceStatus());
        update.put("expectedContractStatus", context.contractStatus());
        update.put("updateBy", actor.userName());
        if (type != null) update.put("invoiceType", type);
        if (clean(remark) != null) update.put("remark", clean(remark));
        changed(mapper.updateFeePlanStatus(update), "收费计划状态已变化，请刷新后重试");

        Long attachmentId = null;
        if (!StringUtils.isEmpty(invoiceFileUrl))
        {
            Map<String, Object> file = new HashMap<>();
            file.put("contractId", context.contractId());
            file.put("fileType", "INVOICE");
            file.put("fileName", StringUtils.isEmpty(invoiceNo) ? "invoice-" + planId : invoiceNo);
            file.put("fileUrl", invoiceFileUrl);
            file.put("createBy", actor.userName());
            changed(mapper.insertAttachment(file), "发票附件保存失败");
            if (file.get("attachmentId") == null)
                throw error(BusinessErrorCode.CONCURRENT_MODIFICATION, "发票附件主键回填失败");
            attachmentId = Long.valueOf(String.valueOf(file.get("attachmentId")));
        }
        String content = INVOICE_PARTIAL.equals(target) ? "部分开票" : "已开票";
        dict("law_contract_status_action", "fee_invoice", "合同状态动作不合法");
        Long logId = logs.record(context.contractId(), context.invoiceStatus(), target,
                "fee_invoice", content, actor);
        Map<String, Object> payload = payload("schemaVersion", 1, "operatorId", actor.userId(),
                "planId", planId, "invoiceStatus", target, "invoiceType", type,
                "logId", logId, "attachmentId", attachmentId);
        events.publish(new BusinessEventCommand(BusinessEventType.INVOICE_HANDLED, "CONTRACT",
                context.contractId(), context.contractNo(),
                "INVOICE_HANDLED:" + context.contractId() + ":" + planId + ":" + logId, payload));
        return 1;
    }

    private void dict(String type, String value, String message)
    {
        List<SysDictData> options = dictionaries.selectDictDataByType(type);
        if (options != null) for (SysDictData option : options) if (value != null && value.equals(option.getDictValue())) return;
        throw error(options == null || options.isEmpty() ? BusinessErrorCode.PRECONDITION_FAILED
                : BusinessErrorCode.VALIDATION_FAILED,
                options == null || options.isEmpty() ? "字典未初始化：" + type : message);
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
