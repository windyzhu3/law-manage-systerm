package com.ruoyi.system.service.impl;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.BizContract;
import com.ruoyi.system.domain.BizCustomer;
import com.ruoyi.system.mapper.BizContractMapper;
import com.ruoyi.system.mapper.BizCustomerMapper;
import com.ruoyi.system.service.IBizContractService;
import com.ruoyi.system.service.contract.ContractTemplateService;
import com.ruoyi.system.service.contract.ContractNumberService;
import com.ruoyi.system.service.contract.ContractQueryService;
import com.ruoyi.system.service.contract.ContractAttachmentService;
import com.ruoyi.system.service.contract.ContractLifecycleService;
import com.ruoyi.system.service.contract.ContractInvoiceService;
import com.ruoyi.system.service.contract.ContractPaymentService;
import com.ruoyi.system.service.contract.ContractFeePlanService;
import com.ruoyi.system.service.contract.ContractCommandService;
import com.law.business.security.ContractPermissions;

@Service
public class BizContractServiceImpl implements IBizContractService
{
    private static final String CUSTOMER_MODULE_PERMISSIONS =
            "customer:list,customer:query,customer:add,customer:edit,customer:remove,customer:import,customer:export,"
                    + "customer:contact:list,customer:contact:add,customer:contact:edit,customer:contact:remove,"
                    + "customer:followup:list,customer:followup:add,customer:followup:remove,"
                    + "customer:tag:list,customer:tag:add,customer:tag:edit,customer:tag:remove,customer:tag:assign,"
                    + "customer:merge:list,customer:merge:merge";
    private static final String CONTRACT_MODULE_PERMISSIONS = ContractPermissions.DATA_SCOPE;

    @Autowired
    private BizContractMapper contractMapper;

    @Autowired
    private BizCustomerMapper customerMapper;

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
