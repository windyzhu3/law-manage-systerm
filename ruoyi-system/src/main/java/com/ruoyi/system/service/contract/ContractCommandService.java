package com.ruoyi.system.service.contract;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.business.shared.status.ContractAuditStatus;
import com.law.business.shared.status.ContractSignStatus;
import com.law.business.shared.status.ContractStatus;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.BizContract;
import com.ruoyi.system.domain.BizCustomer;
import com.ruoyi.system.mapper.BizContractMapper;
import com.ruoyi.system.mapper.BizCustomerMapper;
import com.ruoyi.system.service.ISysDictTypeService;

/** Owns basic contract create, edit and delete commands. */
@Service
public class ContractCommandService
{
    private static final String CUSTOMER_PERMISSIONS = "customer:list,customer:query,customer:add,customer:edit,customer:remove,customer:import,customer:export,customer:contact:list,customer:contact:add,customer:contact:edit,customer:contact:remove,customer:followup:list,customer:followup:add,customer:followup:remove,customer:tag:list,customer:tag:add,customer:tag:edit,customer:tag:remove,customer:tag:assign,customer:merge:list,customer:merge:merge";
    @Autowired private BizContractMapper mapper;
    @Autowired private BizCustomerMapper customers;
    @Autowired private ContractQueryService queryService;
    @Autowired private ContractNumberService numberService;
    @Autowired private ISysDictTypeService dictionaries;

    @Transactional
    public int create(BizContract contract)
    {
        normalizeNew(contract); validate(contract); validateCustomer(contract);
        contract.setContractNo(numberService.nextNumber()); contract.setCreateBy(SecurityUtils.getUsername());
        if(contract.getOwnerId()==null){contract.setOwnerId(SecurityUtils.getUserId());contract.setDeptId(SecurityUtils.getDeptId());}
        int rows=mapper.insertContract(contract);changed(rows,"合同创建失败");
        if(mapper.insertStatusLog(contract.getContractId(),null,contract.getContractStatus(),"create","创建合同",SecurityUtils.getUsername())<=0)throw error("CONCURRENT_MODIFICATION","合同状态日志创建失败");
        return rows;
    }

    @Transactional
    public int update(BizContract contract)
    {
        BizContract existed=queryService.contract(contract.getContractId());requireEditable(existed);normalizeUpdate(contract);validate(contract);validateCustomer(contract);
        contract.setContractNo(null);contract.setAuditStatus(null);contract.setContractStatus(null);contract.setSignStatus(null);contract.setUpdateBy(SecurityUtils.getUsername());
        int rows=mapper.updateContract(contract);changed(rows,"合同状态已变化，请刷新后重试");return rows;
    }

    @Transactional
    public int delete(Long[] ids)
    {
        if(ids==null||ids.length==0)throw error("VALIDATION_FAILED","请选择合同");
        for(Long id:ids){BizContract c=queryService.contract(id);if(ContractAuditStatus.REVIEWING.code().equals(c.getAuditStatus())||ContractStatus.PERFORMING.code().equals(c.getContractStatus())||ContractStatus.ARCHIVED.code().equals(c.getContractStatus()))throw error("STATE_CONFLICT","当前合同状态不允许删除");}
        int rows=mapper.deleteContractByIds(ids,SecurityUtils.getUsername());changed(rows,"合同状态已变化，请刷新后重试");return rows;
    }

    private void normalizeNew(BizContract c)
    {
        if(c==null)throw error("VALIDATION_FAILED","合同不能为空");
        if(StringUtils.isEmpty(c.getFeeType()))c.setFeeType(dictValue("law_contract_fee_type","once"));
        if(StringUtils.isEmpty(c.getSignMethod()))c.setSignMethod(dictValue("law_contract_sign_method","online"));
        if(StringUtils.isEmpty(c.getRiskLevel()))c.setRiskLevel(dictValue("law_contract_risk_level","1"));
        if(StringUtils.isEmpty(c.getAuditStatus()))c.setAuditStatus(ContractAuditStatus.PENDING.code());
        if(StringUtils.isEmpty(c.getContractStatus()))c.setContractStatus(ContractStatus.DRAFT.code());
        if(c.getSignAmount()==null)c.setSignAmount(BigDecimal.ZERO);c.setSignStatus(ContractSignStatus.UNSIGNED.code());
        if(!ContractAuditStatus.PENDING.code().equals(c.getAuditStatus()))throw error("STATE_CONFLICT","新建合同必须为待审核状态");
        if(!ContractStatus.DRAFT.code().equals(c.getContractStatus()))throw error("STATE_CONFLICT","新建合同必须为草稿状态");
    }

    private void normalizeUpdate(BizContract c){if(StringUtils.isEmpty(c.getLawyerName()))c.setLawyerName(null);if(StringUtils.isEmpty(c.getFeeType()))c.setFeeType(null);if(StringUtils.isEmpty(c.getSignMethod()))c.setSignMethod(null);if(StringUtils.isEmpty(c.getRiskLevel()))c.setRiskLevel(null);}
    private void validate(BizContract c){if(c==null)throw error("VALIDATION_FAILED","合同不能为空");if(StringUtils.isEmpty(c.getContractName()))throw error("VALIDATION_FAILED","合同名称不能为空");if(StringUtils.isEmpty(c.getCaseType()))throw error("VALIDATION_FAILED","案件类型不能为空");dict("law_contract_case_type",c.getCaseType(),"案件类型不合法");dict("law_contract_fee_type",c.getFeeType(),"收费方式不合法");dict("law_contract_sign_method",c.getSignMethod(),"签订方式不合法");dict("law_contract_risk_level",c.getRiskLevel(),"风险等级不合法");if(c.getSignAmount()==null||c.getSignAmount().signum()<=0)throw error("VALIDATION_FAILED","签约金额必须大于0");}
    private void validateCustomer(BizContract c){if(c.getCustomerId()==null)throw error("VALIDATION_FAILED","合同必须关联客户");BizCustomer customer=customers.selectCustomerById(c.getCustomerId());if(customer==null||"2".equals(customer.getDelFlag())||!"0".equals(customer.getStatus()))throw error("PRECONDITION_FAILED","客户不存在、已停用或已删除");if(!SecurityUtils.isAdmin()&&customers.countCustomerInDataScope(c.getCustomerId(),SecurityUtils.getUserId(),SecurityUtils.getDeptId(),CUSTOMER_PERMISSIONS)==0)throw error("ACCESS_DENIED","无权为该客户创建或编辑合同");c.setCustomerName(customer.getCustomerName());}
    private void requireEditable(BizContract c){if(ContractAuditStatus.REVIEWING.code().equals(c.getAuditStatus()))throw error("STATE_CONFLICT","审核中的合同不允许编辑");if(ContractAuditStatus.PASSED.code().equals(c.getAuditStatus()))throw error("STATE_CONFLICT","审核通过的合同不允许直接编辑");if(ContractStatus.ARCHIVED.code().equals(c.getContractStatus())||ContractStatus.VOID.code().equals(c.getContractStatus())||ContractStatus.TERMINATED.code().equals(c.getContractStatus()))throw error("STATE_CONFLICT","归档、作废或终止的合同不允许编辑");}
    private String dictValue(String type,String preferred){List<SysDictData> xs=dictionaries.selectDictDataByType(type);if(xs!=null){for(SysDictData x:xs)if(preferred.equals(x.getDictValue()))return x.getDictValue();for(SysDictData x:xs)if(x.getDefault())return x.getDictValue();}return preferred;}
    private void dict(String type,Object value,String message){String v=value==null?null:String.valueOf(value).trim();if(StringUtils.isEmpty(v))return;List<SysDictData> xs=dictionaries.selectDictDataByType(type);if(xs==null||xs.isEmpty())throw error("PRECONDITION_FAILED","字典未初始化："+type);for(SysDictData x:xs)if(v.equals(x.getDictValue()))return;throw error("VALIDATION_FAILED",message);}
    private void changed(int rows,String message){if(rows<=0)throw error("CONCURRENT_MODIFICATION",message);}private ServiceException error(String code,String message){return new ServiceException(message,code);}
}
