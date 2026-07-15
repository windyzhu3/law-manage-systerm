package com.ruoyi.system.service.event;

import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.spi.TodoBusinessValidator;
import com.ruoyi.system.domain.BizContract;
import com.ruoyi.system.mapper.BizContractMapper;

@Component
public class ContractTodoValidator implements TodoBusinessValidator
{
    private final BizContractMapper mapper;public ContractTodoValidator(BizContractMapper mapper){this.mapper=mapper;}
    @Override public boolean supports(String type){return "CONTRACT".equals(type);}
    @Override public void validate(TodoInstance todo,Map<String,Object> payload)
    {
        BizContract contract=mapper.selectContractById(todo.getBusinessId());if(contract==null)fail("CONTRACT_NOT_FOUND","合同不存在或已删除");
        if("CONTRACT_REVIEW".equals(todo.getTemplateCode())){String action=text(payload.get("action"));if(!Set.of("pass","back","reject").contains(action))fail("CONTRACT_ACTION_INVALID","审核动作不合法");if(!"1".equals(contract.getAuditStatus()))fail("CONTRACT_AUDIT_STATE_STALE","合同已不处于审核中");required(payload,"opinion");}
        if("CONTRACT_SIGN".equals(todo.getTemplateCode())){if(!"2".equals(contract.getAuditStatus()))fail("CONTRACT_SIGN_PRECONDITION_FAILED","合同尚未审核通过");required(payload,"signStatus");required(payload,"signMethod");required(payload,"signDate");required(payload,"signFileUrl");}
        if("PAYMENT_CONFIRM".equals(todo.getTemplateCode())){required(payload,"planId");required(payload,"receivedAmount");required(payload,"voucherUrl");Map<String,Object> plan=mapper.selectFeePlanById(Long.valueOf(text(payload.get("planId"))));if(plan==null||!todo.getBusinessId().equals(Long.valueOf(text(plan.get("contract_id")))))fail("PAYMENT_PLAN_INVALID","收费计划不属于当前合同");}
        if("INVOICE_HANDLE".equals(todo.getTemplateCode())){required(payload,"planId");required(payload,"action");if("invoice".equals(text(payload.get("action")))){required(payload,"invoiceNo");required(payload,"invoiceFileUrl");}}
        if("CASE_CREATE_CHECK".equals(todo.getTemplateCode())){required(payload,"materialsChecked");if(!Boolean.parseBoolean(text(payload.get("materialsChecked"))))fail("CASE_MATERIALS_INCOMPLETE","案件生成材料尚未核验完成");if(!"1".equals(contract.getSignStatus()))fail("CASE_CREATE_PRECONDITION_FAILED","合同尚未完成签署");if(mapper.countConfirmedFeePlans(contract.getContractId())<=0)fail("CASE_CREATE_PRECONDITION_FAILED","合同尚无已确认缴费记录");}
    }
    private void required(Map<String,Object> value,String key){if(value.get(key)==null||text(value.get(key)).isBlank())fail("TODO_DOD_FIELD_MISSING","缺少完成字段："+key);}
    private String text(Object value){return value==null?"":String.valueOf(value);}private void fail(String code,String message){throw new TodoException(code,message);}
}
