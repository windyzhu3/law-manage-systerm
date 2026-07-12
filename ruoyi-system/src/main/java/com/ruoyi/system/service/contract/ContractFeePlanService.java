package com.ruoyi.system.service.contract;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.business.shared.status.ContractStatus;
import com.law.business.shared.status.FeeInvoiceStatus;
import com.law.business.shared.status.FeePaymentStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.BizContract;
import com.ruoyi.system.mapper.BizContractMapper;

/** Owns creation, editing and deletion of contract fee plans. */
@Service
public class ContractFeePlanService
{
    private static final String PENDING=FeePaymentStatus.PENDING.code(), CONFIRMED=FeePaymentStatus.CONFIRMED.code(), REJECTED=FeePaymentStatus.REJECTED.code(), NOT_INVOICED=FeeInvoiceStatus.NONE.code();
    @Autowired private BizContractMapper mapper;
    @Autowired private ContractQueryService queryService;

    @Transactional
    public int create(Map<String,Object> plan)
    {
        Long contractId=id(plan.get("contractId"),"请选择合同");
        BizContract contract=queryService.contract(contractId); requireEditable(contract.getContractStatus()); validate(plan);
        plan.put("receivedAmount",BigDecimal.ZERO);plan.put("confirmStatus",PENDING);plan.put("invoiceStatus",NOT_INVOICED);plan.put("createBy",SecurityUtils.getUsername());
        int rows=mapper.insertFeePlan(plan);changed(rows,"收费计划创建失败");log(contractId,null,PENDING,"fee_create",content("新增收费计划",plan));return rows;
    }

    @Transactional
    public int update(Map<String,Object> plan)
    {
        Map<String,Object> old=plan(id(plan.get("planId"),"请选择收费计划"));requireEditable(text(old.get("contractStatus")));
        if(CONFIRMED.equals(text(old.get("confirm_status"))))throw error("STATE_CONFLICT","已确认收费计划不能通过普通修改编辑");
        if(!NOT_INVOICED.equals(text(old.get("invoice_status"))))throw error("STATE_CONFLICT","已开票收费计划不能通过普通修改编辑");
        plan.put("contractId",old.get("contract_id"));plan.remove("receivedAmount");plan.remove("received_amount");plan.remove("confirmStatus");plan.remove("confirm_status");plan.remove("invoiceStatus");plan.remove("invoice_status");validate(plan);
        plan.put("expectedConfirmStatus",old.get("confirm_status"));plan.put("expectedInvoiceStatus",old.get("invoice_status"));plan.put("expectedContractStatus",old.get("contractStatus"));plan.put("updateBy",SecurityUtils.getUsername());
        int rows=mapper.updateFeePlan(plan);changed(rows,"收费计划状态已变化，请刷新后重试");
        if(REJECTED.equals(text(old.get("confirm_status")))){Map<String,Object> reset=new HashMap<>();reset.put("planId",plan.get("planId"));reset.put("confirmStatus",PENDING);reset.put("expectedConfirmStatus",REJECTED);reset.put("expectedInvoiceStatus",old.get("invoice_status"));reset.put("expectedContractStatus",old.get("contractStatus"));reset.put("updateBy",SecurityUtils.getUsername());changed(mapper.updateFeePlanStatus(reset),"收费计划状态已变化，请刷新后重试");}
        log(contractId(old),null,PENDING,"fee_update",content("调整收费计划",plan));return rows;
    }

    @Transactional
    public int delete(Long planId)
    {
        Map<String,Object> old=plan(planId);requireEditable(text(old.get("contractStatus")));
        if(CONFIRMED.equals(text(old.get("confirm_status")))||!NOT_INVOICED.equals(text(old.get("invoice_status"))))throw error("STATE_CONFLICT","已有回款或开票记录的收费计划不能删除");
        int rows=mapper.deleteFeePlan(planId,old.get("confirm_status"),old.get("invoice_status"),old.get("contractStatus"));changed(rows,"收费计划状态已变化，请刷新后重试");
        log(contractId(old),PENDING,null,"fee_delete","删除收费计划: 第 "+old.get("period_no")+" 期");return rows;
    }

    private Map<String,Object> plan(Long id){Map<String,Object> p=mapper.selectFeePlanById(id);if(p==null)throw error("DATA_NOT_FOUND","收费计划不存在");queryService.contract(contractId(p));return p;}
    private void requireEditable(String status){if(ContractStatus.ARCHIVED.code().equals(status)||ContractStatus.VOID.code().equals(status)||ContractStatus.TERMINATED.code().equals(status))throw error("STATE_CONFLICT","归档、作废或终止的合同不允许维护收费计划");}
    private void validate(Map<String,Object> p){int period=integer(required(p,"periodNo","请输入期数"),"期数必须为数字");if(period<=0)throw error("VALIDATION_FAILED","期数必须大于0");BigDecimal amount=decimal(required(p,"receivableAmount","请输入应收金额"),"应收金额必须为数字");if(amount.signum()<=0)throw error("VALIDATION_FAILED","应收金额必须大于0");required(p,"planReceiveDate","请选择计划收款日");}
    private void log(Long id,String from,String to,String action,String content){if(mapper.insertStatusLog(id,from,to,action,content,SecurityUtils.getUsername())<=0)throw error("CONCURRENT_MODIFICATION","合同状态日志创建失败");}
    private String content(String action,Map<String,Object> p){return action+": 第 "+p.get("periodNo")+" 期，应收 "+p.get("receivableAmount");}
    private String required(Map<String,Object> p,String key,String msg){Object v=p==null?null:p.get(key);if(v==null||StringUtils.isEmpty(String.valueOf(v))||"null".equalsIgnoreCase(String.valueOf(v)))throw error("VALIDATION_FAILED",msg);return String.valueOf(v);}
    private int integer(String v,String msg){try{return Integer.parseInt(v);}catch(NumberFormatException e){throw error("VALIDATION_FAILED",msg);}}
    private BigDecimal decimal(String v,String msg){try{return new BigDecimal(v);}catch(NumberFormatException e){throw error("VALIDATION_FAILED",msg);}}
    private Long id(Object v,String msg){try{if(v==null)throw new NumberFormatException();return Long.valueOf(String.valueOf(v));}catch(NumberFormatException e){throw error("VALIDATION_FAILED",msg);}}
    private Long contractId(Map<String,Object> p){return Long.valueOf(String.valueOf(p.get("contract_id")));}private String text(Object v){return v==null?null:String.valueOf(v);}
    private void changed(int rows,String msg){if(rows<=0)throw error("CONCURRENT_MODIFICATION",msg);}private ServiceException error(String code,String msg){return new ServiceException(msg,code);}
}
