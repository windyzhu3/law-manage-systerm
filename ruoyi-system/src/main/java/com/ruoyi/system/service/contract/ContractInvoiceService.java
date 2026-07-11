package com.ruoyi.system.service.contract;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.business.event.BusinessEventCommand;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.event.BusinessEventType;
import com.law.business.shared.status.FeeInvoiceStatus;
import com.law.business.shared.status.FeePaymentStatus;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.uuid.IdUtils;
import com.ruoyi.system.mapper.BizContractMapper;
import com.ruoyi.system.service.ISysDictTypeService;

/** Owns invoice transitions for contract fee plans. */
@Service
public class ContractInvoiceService
{
    private static final String CONTRACT_PERFORMING = "3";
    private static final String RECEIVE_CONFIRMED = FeePaymentStatus.CONFIRMED.code();
    private static final String INVOICE_NONE = FeeInvoiceStatus.NONE.code();
    private static final String INVOICE_DONE = FeeInvoiceStatus.INVOICED.code();
    private static final String INVOICE_PARTIAL = FeeInvoiceStatus.PARTIAL.code();

    @Autowired private BizContractMapper mapper;
    @Autowired private ContractQueryService queryService;
    @Autowired private ISysDictTypeService dictionaries;
    @Autowired private BusinessEventPublisher publisher;

    @Transactional
    public int invoice(Long planId, String target, String remark, String invoiceType)
    {
        Map<String, Object> plan = plan(planId);
        if (!CONTRACT_PERFORMING.equals(text(plan.get("contractStatus")))) throw error("STATE_CONFLICT", "只有履约中的合同可以确认收款或开票");
        String type = clean(invoiceType);
        if (type != null) requireDict("law_finance_invoice_type", type, "发票类型不合法");
        if (!RECEIVE_CONFIRMED.equals(text(plan.get("confirm_status")))) throw error("STATE_CONFLICT", "只有已确认收款的计划可以开票");
        requireDict("law_contract_invoice_status", target, "开票状态不合法");
        if (!INVOICE_DONE.equals(target) && !INVOICE_PARTIAL.equals(target)) throw error("VALIDATION_FAILED", "开票状态不合法");
        String current = text(plan.get("invoice_status"));
        validateTransition(current, target);

        Map<String, Object> update = new HashMap<>();
        update.put("planId", planId); update.put("invoiceStatus", target);
        update.put("expectedConfirmStatus", plan.get("confirm_status"));
        update.put("expectedInvoiceStatus", current); update.put("expectedContractStatus", plan.get("contractStatus"));
        update.put("updateBy", SecurityUtils.getUsername());
        String cleanRemark = clean(remark);
        if (cleanRemark != null) update.put("remark", cleanRemark);
        if (type != null) update.put("invoiceType", type);
        int rows = mapper.updateFeePlanStatus(update);
        if (rows <= 0) throw error("CONCURRENT_MODIFICATION", "收费计划状态已变化，请刷新后重试");
        String content = INVOICE_PARTIAL.equals(target) ? "部分开票: 第 " + plan.get("period_no") + " 期"
                : (INVOICE_PARTIAL.equals(current) ? "补齐开票: 第 " + plan.get("period_no") + " 期" : "已开票: 第 " + plan.get("period_no") + " 期");
        if (type != null) content += "，发票类型 " + type;
        if (cleanRemark != null) content += "，备注：" + cleanRemark;
        requireDict("law_contract_status_action", "fee_invoice", "合同状态动作不合法");
        if (mapper.insertStatusLog(contractId(plan), current, target, "fee_invoice", content, SecurityUtils.getUsername()) <= 0)
            throw error("CONCURRENT_MODIFICATION", "合同状态日志创建失败");
        Map<String, Object> payload = new HashMap<>(); payload.put("planId", planId); payload.put("invoiceStatus", target); if (type != null) payload.put("invoiceType", type);
        Long contractId = contractId(plan);
        publisher.publish(new BusinessEventCommand(BusinessEventType.INVOICE_HANDLED, "CONTRACT", contractId,
                text(plan.get("contract_no")), "INVOICE_HANDLED:" + contractId + ":" + IdUtils.fastUUID(), payload));
        return rows;
    }

    private void validateTransition(String current, String target)
    {
        FeeInvoiceStatus source;
        FeeInvoiceStatus destination;
        try { source = FeeInvoiceStatus.fromCode(current); destination = FeeInvoiceStatus.fromCode(target); }
        catch (IllegalArgumentException e) { throw error("STATE_CONFLICT", "当前开票状态不允许继续开票"); }
        if (!source.canTransitionTo(destination))
        {
            if (source == FeeInvoiceStatus.INVOICED) throw error("STATE_CONFLICT", "已开票的计划不能重复开票");
            if (source == FeeInvoiceStatus.PARTIAL) throw error("STATE_CONFLICT", "部分开票的计划只能补齐为已开票");
            throw error("STATE_CONFLICT", "当前开票状态不允许继续开票");
        }
    }

    private Map<String, Object> plan(Long planId)
    {
        Map<String, Object> plan = mapper.selectFeePlanById(planId);
        if (plan == null) throw error("DATA_NOT_FOUND", "收费计划不存在");
        queryService.contract(contractId(plan));
        return plan;
    }
    private Long contractId(Map<String,Object> plan){return Long.valueOf(String.valueOf(plan.get("contract_id")));}
    private void requireDict(String type,String value,String message){List<SysDictData> xs=dictionaries.selectDictDataByType(type);if(xs!=null)for(SysDictData x:xs)if(value!=null&&value.equals(x.getDictValue()))return;throw error("VALIDATION_FAILED",xs==null||xs.isEmpty()?"字典未初始化："+type:message);}
    private String clean(String v){return StringUtils.isEmpty(v)?null:v.trim();}
    private String text(Object v){return v==null?null:String.valueOf(v);}
    private ServiceException error(String code,String message){return new ServiceException(message,code);}
}
