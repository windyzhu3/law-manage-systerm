package com.ruoyi.system.service.impl;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.BizContract;
import com.ruoyi.system.domain.BizCustomer;
import com.ruoyi.system.mapper.BizContractMapper;
import com.ruoyi.system.mapper.BizCustomerMapper;
import com.ruoyi.system.service.IBizCaseService;
import com.ruoyi.system.service.IBizContractService;
import com.ruoyi.system.service.ISysDictTypeService;
import com.ruoyi.system.service.contract.ContractTemplateService;
import com.ruoyi.system.service.contract.ContractNumberService;
import com.ruoyi.system.service.contract.ContractQueryService;
import com.ruoyi.system.service.contract.ContractAttachmentService;
import com.ruoyi.system.service.contract.ContractLifecycleService;
import com.ruoyi.system.service.contract.ContractInvoiceService;
import com.ruoyi.system.service.contract.ContractPaymentService;
import com.ruoyi.system.service.contract.ContractFeePlanService;
import com.ruoyi.system.service.contract.ContractCommandService;
import com.law.business.shared.status.ContractAuditStatus;
import com.law.business.shared.status.ContractSignStatus;
import com.law.business.shared.status.ContractStatus;
import com.law.business.security.ContractPermissions;

@Service
public class BizContractServiceImpl implements IBizContractService
{
    private static final String CUSTOMER_NORMAL = "0";

    private static final String CUSTOMER_MODULE_PERMISSIONS =
            "customer:list,customer:query,customer:add,customer:edit,customer:remove,customer:import,customer:export,"
                    + "customer:contact:list,customer:contact:add,customer:contact:edit,customer:contact:remove,"
                    + "customer:followup:list,customer:followup:add,customer:followup:remove,"
                    + "customer:tag:list,customer:tag:add,customer:tag:edit,customer:tag:remove,customer:tag:assign,"
                    + "customer:merge:list,customer:merge:merge";
    private static final String CONTRACT_MODULE_PERMISSIONS = ContractPermissions.DATA_SCOPE;

    private static final String AUDIT_PENDING = ContractAuditStatus.PENDING.code();
    private static final String AUDIT_REVIEWING = ContractAuditStatus.REVIEWING.code();
    private static final String AUDIT_PASSED = ContractAuditStatus.PASSED.code();
    private static final String AUDIT_REJECTED = ContractAuditStatus.REJECTED.code();
    private static final String AUDIT_BACK = ContractAuditStatus.BACK.code();

    private static final String CONTRACT_DRAFT = ContractStatus.DRAFT.code();
    private static final String CONTRACT_PERFORMING = ContractStatus.PERFORMING.code();
    private static final String CONTRACT_ARCHIVED = ContractStatus.ARCHIVED.code();
    private static final String CONTRACT_VOID = ContractStatus.VOID.code();
    private static final String CONTRACT_TERMINATED = ContractStatus.TERMINATED.code();

    private static final String SIGN_UNSIGNED = ContractSignStatus.UNSIGNED.code();
    private static final String SIGN_SIGNED = ContractSignStatus.SIGNED.code();
    private static final String SIGN_PARTIAL = ContractSignStatus.PARTIAL.code();


    @Autowired
    private BizContractMapper contractMapper;

    @Autowired
    private BizCustomerMapper customerMapper;

    @Autowired
    private IBizCaseService caseService;

    @Autowired
    private ISysDictTypeService dictTypeService;


    @Autowired
    private ContractTemplateService templateService;

    @Autowired
    private ContractNumberService numberService;

    @Autowired
    private ContractQueryService queryService;

    @Autowired
    private ContractAttachmentService attachmentService;

    @Autowired
    private ContractLifecycleService lifecycleService;

    @Autowired
    private ContractInvoiceService invoiceService;

    @Autowired
    private ContractPaymentService paymentService;

    @Autowired
    private ContractFeePlanService feePlanService;

    @Autowired
    private ContractCommandService commandService;

    @Override
    public List<BizContract> selectContractList(BizContract contract)
    {
        return queryService.contracts(contract);
    }

    @Override
    public BizContract selectContractById(Long contractId)
    {
        BizContract contract = contractMapper.selectContractById(contractId);
        if (contract == null || "2".equals(contract.getDelFlag()))
        {
            throw new ServiceException("合同不存在或已删除");
        }
        assertContractAccess(contractId);
        return contract;
    }

    @Override
    @Transactional
    public int insertContract(BizContract contract)
    {
        return commandService.create(contract);
    }

    @Override
    public int updateContract(BizContract contract)
    {
        return commandService.update(contract);
    }

    @Override
    public int deleteContractByIds(Long[] contractIds)
    {
        return commandService.delete(contractIds);
    }

    @Override
    @Transactional
    public String importContract(List<BizContract> contractList, Boolean updateSupport, String operName)
    {
        if (StringUtils.isNull(contractList) || contractList.isEmpty())
        {
            throw new ServiceException("导入合同数据不能为空");
        }
        int successNum = 0;
        for (BizContract contract : contractList)
        {
            if (StringUtils.isEmpty(contract.getContractName()))
            {
                continue;
            }
            resolveImportCustomer(contract);
            List<BizContract> duplicates = contractMapper.selectContractsByExactNameInScope(contract.getContractName(), SecurityUtils.getUserId(), SecurityUtils.getDeptId(), !SecurityUtils.isAdmin(), CONTRACT_MODULE_PERMISSIONS);
            if (!duplicates.isEmpty())
            {
                if (!Boolean.TRUE.equals(updateSupport))
                {
                    throw new ServiceException("合同已存在，勾选更新后可覆盖导入：" + contract.getContractName());
                }
                if (duplicates.size() > 1)
                {
                    throw new ServiceException("合同名称重复，请在页面手动编辑：" + contract.getContractName());
                }
                contract.setContractId(duplicates.get(0).getContractId());
                updateContract(contract);
            }
            else
            {
                insertContract(contract);
            }
            successNum++;
        }
        return "导入成功，共 " + successNum + " 条";
    }

    @Override
    @Transactional
    public int submitContract(Long contractId)
    {
        return lifecycleService.submit(contractId);
    }

    @Override
    @Transactional
    public int approveContract(Long contractId, String action, String opinion)
    {
        return lifecycleService.approve(contractId, action, opinion);
    }

    @Override
    @Transactional
    public int signContract(Long contractId, String signStatus)
    {
        return lifecycleService.sign(contractId, signStatus);
    }

    @Override
    @Transactional
    public int archiveContract(Long contractId, String reason)
    {
        return lifecycleService.archive(contractId, reason);
    }

    @Override
    @Transactional
    public int voidContract(Long contractId, String reason)
    {
        return lifecycleService.voidContract(contractId, reason);
    }

    @Override
    @Transactional
    public int terminateContract(Long contractId, String reason)
    {
        return lifecycleService.terminate(contractId, reason);
    }

    @Override
    public Map<String, Object> selectDashboard()
    {
        return queryService.dashboard();
    }

    @Override public List<Map<String, Object>> selectRules() { return numberService.selectRules(); }
    @Override
    @Transactional
    public int updateRule(Map<String, Object> rule) {
        return numberService.updateRule(rule);
    }
    @Override public List<Map<String, Object>> selectTemplates(Map<String, Object> params) { return templateService.select(params); }
    @Override public int insertTemplate(Map<String, Object> template) { return templateService.create(template); }
    @Override public int updateTemplate(Map<String, Object> template) { return templateService.update(template); }
    @Override public int deleteTemplate(Long templateId) { return templateService.delete(templateId); }
    @Override public List<Map<String, Object>> selectApprovals(Map<String, Object> params) { return queryService.approvals(params); }
    @Override public List<Map<String, Object>> selectFeePlans(Map<String, Object> params) { return queryService.feePlans(params); }
    @Override
    @Transactional
    public int insertFeePlan(Map<String, Object> plan) {
        return feePlanService.create(plan);
    }

    @Override
    @Transactional
    public int updateFeePlan(Map<String, Object> plan) {
        return feePlanService.update(plan);
    }

    @Override
    @Transactional
    public int deleteFeePlan(Long planId) {
        return feePlanService.delete(planId);
    }

    @Override
    @Transactional
    public int confirmFeePlan(Long planId, String receivedAmount) {
        return paymentService.confirm(planId, receivedAmount, null, null);
    }

    @Override
    @Transactional
    public int confirmFeePlan(Long planId, String receivedAmount, String remark) {
        return paymentService.confirm(planId, receivedAmount, remark, null);
    }

    @Override
    @Transactional
    public int confirmFeePlan(Long planId, String receivedAmount, String remark, String paymentMethod) {
        return paymentService.confirm(planId, receivedAmount, remark, paymentMethod);
    }

    @Override
    @Transactional
    public int rejectFeePlan(Long planId, String reason) {
        return paymentService.reject(planId, reason);
    }

    @Override
    @Transactional
    public int invoiceFeePlan(Long planId, String invoiceStatus) {
        return invoiceService.invoice(planId, invoiceStatus, null, null);
    }

    @Override
    @Transactional
    public int invoiceFeePlan(Long planId, String invoiceStatus, String remark) {
        return invoiceService.invoice(planId, invoiceStatus, remark, null);
    }

    @Override
    @Transactional
    public int invoiceFeePlan(Long planId, String invoiceStatus, String remark, String invoiceType) {
        return invoiceService.invoice(planId, invoiceStatus, remark, invoiceType);
    }

    @Override public List<Map<String, Object>> selectAttachments(Map<String, Object> params) { return queryService.attachments(params); }
    @Override
    @Transactional
    public int insertAttachment(Map<String, Object> attachment) {
        return attachmentService.create(attachment);
    }
    @Override
    @Transactional
    public int deleteAttachment(Long attachmentId) {
        return attachmentService.delete(attachmentId);
    }
    @Override public List<Map<String, Object>> selectStatusLogs(Map<String, Object> params) { return queryService.statusLogs(params); }

    private void validateCustomer(BizContract contract)
    {
        if (contract.getCustomerId() == null)
        {
            throw new ServiceException("合同必须关联客户");
        }
        BizCustomer customer = customerMapper.selectCustomerById(contract.getCustomerId());
        if (customer == null || "2".equals(customer.getDelFlag()) || !CUSTOMER_NORMAL.equals(customer.getStatus()))
        {
            throw new ServiceException("客户不存在、已停用或已删除");
        }
        if (!SecurityUtils.isAdmin() && customerMapper.countCustomerInDataScope(contract.getCustomerId(), SecurityUtils.getUserId(), SecurityUtils.getDeptId(), CUSTOMER_MODULE_PERMISSIONS) == 0)
        {
            throw new ServiceException("无权为该客户创建或编辑合同");
        }
        contract.setCustomerName(customer.getCustomerName());
    }

    private void resolveImportCustomer(BizContract contract)
    {
        if (contract.getCustomerId() != null)
        {
            return;
        }
        if (StringUtils.isEmpty(contract.getCustomerName()))
        {
            throw new ServiceException("导入合同必须填写客户名称");
        }
        List<BizCustomer> customers = customerMapper.selectCustomersByExactNameInScope(contract.getCustomerName(), SecurityUtils.getUserId(), SecurityUtils.getDeptId(), !SecurityUtils.isAdmin(), CUSTOMER_MODULE_PERMISSIONS);
        if (customers.isEmpty())
        {
            throw new ServiceException("客户不存在或无权访问：" + contract.getCustomerName());
        }
        if (customers.size() > 1)
        {
            throw new ServiceException("客户名称重复，请在页面手动选择客户后新建合同：" + contract.getCustomerName());
        }
        contract.setCustomerId(customers.get(0).getCustomerId());
    }

    private void normalizeNewContract(BizContract contract)
    {
        if (StringUtils.isEmpty(contract.getFeeType()))
        {
            contract.setFeeType(dictValue("law_contract_fee_type", "once"));
        }
        if (StringUtils.isEmpty(contract.getSignMethod()))
        {
            contract.setSignMethod(dictValue("law_contract_sign_method", "online"));
        }
        if (StringUtils.isEmpty(contract.getRiskLevel()))
        {
            contract.setRiskLevel(dictValue("law_contract_risk_level", "1"));
        }
        if (StringUtils.isEmpty(contract.getAuditStatus()))
        {
            contract.setAuditStatus(AUDIT_PENDING);
        }
        if (StringUtils.isEmpty(contract.getContractStatus()))
        {
            contract.setContractStatus(CONTRACT_DRAFT);
        }
        if (contract.getSignAmount() == null)
        {
            contract.setSignAmount(BigDecimal.ZERO);
        }
        contract.setSignStatus(SIGN_UNSIGNED);
        if (!AUDIT_PENDING.equals(contract.getAuditStatus()))
        {
            throw new ServiceException("新建合同必须为待审核状态");
        }
        if (!CONTRACT_DRAFT.equals(contract.getContractStatus()))
        {
            throw new ServiceException("新建合同必须为草稿状态");
        }
    }

    private void normalizeContractUpdate(BizContract contract)
    {
        if (StringUtils.isEmpty(contract.getLawyerName()))
        {
            contract.setLawyerName(null);
        }
        if (StringUtils.isEmpty(contract.getFeeType()))
        {
            contract.setFeeType(null);
        }
        if (StringUtils.isEmpty(contract.getSignMethod()))
        {
            contract.setSignMethod(null);
        }
        if (StringUtils.isEmpty(contract.getRiskLevel()))
        {
            contract.setRiskLevel(null);
        }
    }

    private void validateNewContract(BizContract contract)
    {
        if (contract == null)
        {
            throw new ServiceException("合同不能为空");
        }
        if (StringUtils.isEmpty(contract.getContractName()))
        {
            throw new ServiceException("合同名称不能为空");
        }
        if (StringUtils.isEmpty(contract.getCaseType()))
        {
            throw new ServiceException("案件类型不能为空");
        }
        assertDictValue("law_contract_case_type", contract.getCaseType(), "案件类型不合法");
        assertDictValue("law_contract_fee_type", contract.getFeeType(), "收费方式不合法");
        assertDictValue("law_contract_sign_method", contract.getSignMethod(), "签订方式不合法");
        assertDictValue("law_contract_risk_level", contract.getRiskLevel(), "风险等级不合法");
        if (contract.getSignAmount() == null || contract.getSignAmount().compareTo(BigDecimal.ZERO) <= 0)
        {
            throw new ServiceException("签约金额必须大于0");
        }
    }

    private String dictValue(String dictType, String preferredValue)
    {
        List<SysDictData> options = dictTypeService.selectDictDataByType(dictType);
        if (options != null)
        {
            for (SysDictData item : options)
            {
                if (preferredValue.equals(item.getDictValue()))
                {
                    return item.getDictValue();
                }
            }
            for (SysDictData item : options)
            {
                if (item.getDefault())
                {
                    return item.getDictValue();
                }
            }
        }
        return preferredValue;
    }

    private void assertDictValue(String dictType, Object value, String message)
    {
        String valueText = value == null ? null : String.valueOf(value).trim();
        if (StringUtils.isEmpty(valueText))
        {
            return;
        }
        List<SysDictData> options = dictTypeService.selectDictDataByType(dictType);
        if (options == null || options.isEmpty())
        {
            throw new ServiceException("字典未初始化：" + dictType);
        }
        for (SysDictData item : options)
        {
            if (valueText.equals(item.getDictValue()))
            {
                return;
            }
        }
        throw new ServiceException(message);
    }

    private void assertStateChanged(int rows)
    {
        assertRowsChanged(rows, "合同状态已变化，请刷新后重试");
    }

    private void assertRowsChanged(int rows, String message)
    {
        if (rows <= 0)
        {
            throw new ServiceException(message);
        }
    }

    private void insertStatusLog(Long contractId, String fromStatus, String toStatus, String actionType, String content)
    {
        assertDictValue("law_contract_status_action", actionType, "合同状态动作不合法");
        assertRowsChanged(contractMapper.insertStatusLog(contractId, fromStatus, toStatus, actionType, content, SecurityUtils.getUsername()), "Contract status log was not created");
    }

    private void assertEditable(BizContract contract)
    {
        if (AUDIT_REVIEWING.equals(contract.getAuditStatus()))
        {
            throw new ServiceException("审核中的合同不允许编辑");
        }
        if (AUDIT_PASSED.equals(contract.getAuditStatus()))
        {
            throw new ServiceException("审核通过的合同不允许直接编辑");
        }
        if (CONTRACT_ARCHIVED.equals(contract.getContractStatus()) || CONTRACT_VOID.equals(contract.getContractStatus()) || CONTRACT_TERMINATED.equals(contract.getContractStatus()))
        {
            throw new ServiceException("归档、作废或终止的合同不允许编辑");
        }
    }

    private String safeReason(String reason, String defaultValue)
    {
        return StringUtils.isEmpty(reason) || "null".equalsIgnoreCase(reason) ? defaultValue : reason;
    }

    private String requiredReason(String reason, String message)
    {
        if (StringUtils.isEmpty(reason) || "null".equalsIgnoreCase(reason.trim()))
        {
            throw new ServiceException(message);
        }
        return reason.trim();
    }

    private void applyDataScope(BizContract contract)
    {
        contract.setCurrentUserId(SecurityUtils.getUserId());
        contract.setCurrentDeptId(SecurityUtils.getDeptId());
        contract.setDataScope(!SecurityUtils.isAdmin());
    }

    private void assertContractAccess(Long contractId)
    {
        if (contractId == null)
        {
            throw new ServiceException("合同不存在或已删除");
        }
        if (SecurityUtils.isAdmin())
        {
            return;
        }
        if (contractMapper.countContractInDataScope(contractId, SecurityUtils.getUserId(), SecurityUtils.getDeptId(), CONTRACT_MODULE_PERMISSIONS) == 0)
        {
            throw new ServiceException("无权访问该合同");
        }
    }

}
