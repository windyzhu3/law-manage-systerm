package com.ruoyi.system.service.contract;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.business.event.BusinessEventCommand;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.event.BusinessEventType;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.uuid.IdUtils;
import com.ruoyi.system.mapper.BizContractMapper;
import com.ruoyi.system.service.ISysDictTypeService;

/** Owns fee-plan payment confirmation and rejection. */
@Service
public class ContractPaymentService
{
    private static final String PERFORMING="3", PENDING="0", CONFIRMED="1", REJECTED="2";
    @Autowired private BizContractMapper mapper;
    @Autowired private ContractQueryService queryService;
    @Autowired private ISysDictTypeService dictionaries;
    @Autowired private BusinessEventPublisher publisher;

    @Transactional
    public int confirm(Long planId,String receivedAmount,String remark,String paymentMethod)
    {
        Map<String,Object> plan=plan(planId); requireCollectable(plan);
        String method=clean(paymentMethod); if(method!=null)requireDict("law_finance_payment_method",method,"付款方式不合法");
        String current=text(plan.get("confirm_status"));
        BigDecimal receivable=amount(text(plan.get("receivable_amount")),plan.get("receivable_amount"));
        BigDecimal received=amount(text(plan.get("received_amount")),BigDecimal.ZERO);
        BigDecimal remaining=receivable.subtract(received);
        if(!PENDING.equals(current)&&!CONFIRMED.equals(current))throw error("STATE_CONFLICT","当前收费计划状态不允许确认收款");
        if(CONFIRMED.equals(current)&&remaining.signum()<=0)throw error("STATE_CONFLICT","收费计划已收齐，不能重复确认收款");
        BigDecimal delta=amount(receivedAmount,remaining.signum()>0?remaining:receivable);
        if(delta.signum()<=0)throw error("VALIDATION_FAILED","实收金额必须大于0");
        if(delta.compareTo(remaining.signum()>0?remaining:receivable)>0)throw error("VALIDATION_FAILED","本次回款不能超过待收金额");
        BigDecimal total=received.add(delta); String cleanRemark=clean(remark);
        Map<String,Object> update=baseUpdate(planId,plan,CONFIRMED);
        update.put("receivedAmount",total); if(cleanRemark!=null)update.put("remark",cleanRemark); if(method!=null)update.put("paymentMethod",method);
        int rows=mapper.updateFeePlanStatus(update); changed(rows);
        String action=total.compareTo(receivable)>=0?(CONFIRMED.equals(current)?"补齐收款":"确认收款"):"部分收款";
        String content=action+": 第 "+plan.get("period_no")+" 期，本次实收 "+delta+"，累计实收 "+total;
        if(method!=null)content+="，付款方式 "+method;if(cleanRemark!=null)content+="，备注："+cleanRemark;
        log(plan,current,CONFIRMED,"fee_confirm",content);
        publish(BusinessEventType.PAYMENT_CONFIRMED,plan,data("planId",planId,"receivedAmount",delta,"totalReceivedAmount",total));
        return rows;
    }

    @Transactional
    public int reject(Long planId,String reason)
    {
        String clean=clean(reason);if(clean==null)throw error("VALIDATION_FAILED","驳回原因必填");
        Map<String,Object> plan=plan(planId);requireCollectable(plan);
        if(!PENDING.equals(text(plan.get("confirm_status"))))throw error("STATE_CONFLICT","只有待确认的收费计划可以驳回");
        Map<String,Object> update=baseUpdate(planId,plan,REJECTED);update.put("remark",clean);
        int rows=mapper.updateFeePlanStatus(update);changed(rows);
        log(plan,PENDING,REJECTED,"fee_reject",clean);
        publish(BusinessEventType.PAYMENT_REJECTED,plan,data("planId",planId,"reason",clean));return rows;
    }

    private Map<String,Object> plan(Long id){Map<String,Object> p=mapper.selectFeePlanById(id);if(p==null)throw error("DATA_NOT_FOUND","收费计划不存在");queryService.contract(contractId(p));return p;}
    private void requireCollectable(Map<String,Object> p){if(!PERFORMING.equals(text(p.get("contractStatus"))))throw error("STATE_CONFLICT","只有履约中的合同可以确认收款或开票");}
    private Map<String,Object> baseUpdate(Long id,Map<String,Object> p,String status){Map<String,Object> m=new HashMap<>();m.put("planId",id);m.put("confirmStatus",status);m.put("expectedConfirmStatus",p.get("confirm_status"));m.put("expectedInvoiceStatus",p.get("invoice_status"));m.put("expectedContractStatus",p.get("contractStatus"));m.put("updateBy",SecurityUtils.getUsername());return m;}
    private void log(Map<String,Object> p,String from,String to,String action,String content){requireDict("law_contract_status_action",action,"合同状态动作不合法");if(mapper.insertStatusLog(contractId(p),from,to,action,content,SecurityUtils.getUsername())<=0)throw error("CONCURRENT_MODIFICATION","合同状态日志创建失败");}
    private void publish(BusinessEventType t,Map<String,Object> p,Map<String,Object> d){Long id=contractId(p);publisher.publish(new BusinessEventCommand(t,"CONTRACT",id,text(p.get("contract_no")),t.name()+":"+id+":"+IdUtils.fastUUID(),d));}
    private Map<String,Object> data(Object...v){Map<String,Object> m=new HashMap<>();for(int i=0;i+1<v.length;i+=2)if(v[i+1]!=null)m.put(String.valueOf(v[i]),v[i+1]);return m;}
    private void requireDict(String type,String value,String message){List<SysDictData> xs=dictionaries.selectDictDataByType(type);if(xs!=null)for(SysDictData x:xs)if(value.equals(x.getDictValue()))return;throw error("VALIDATION_FAILED",xs==null||xs.isEmpty()?"字典未初始化："+type:message);}
    private BigDecimal amount(String value,Object fallback){Object source=StringUtils.isEmpty(value)||"null".equalsIgnoreCase(value)?fallback:value;try{return new BigDecimal(String.valueOf(source));}catch(Exception e){throw error("VALIDATION_FAILED","实收金额必须为数字");}}
    private Long contractId(Map<String,Object> p){return Long.valueOf(String.valueOf(p.get("contract_id")));}
    private String clean(String v){return StringUtils.isEmpty(v)?null:v.trim();} private String text(Object v){return v==null?null:String.valueOf(v);}
    private void changed(int rows){if(rows<=0)throw error("CONCURRENT_MODIFICATION","收费计划状态已变化，请刷新后重试");}
    private ServiceException error(String code,String message){return new ServiceException(message,code);}
}
