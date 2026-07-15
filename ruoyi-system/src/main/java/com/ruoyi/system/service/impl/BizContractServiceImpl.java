package com.ruoyi.system.service.impl;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.law.business.contract.dto.FeeConfirmCommand;
import com.law.business.contract.dto.FeeInvoiceCommand;
import com.law.business.contract.dto.FeeRejectCommand;
import com.ruoyi.system.domain.BizContract;
import com.ruoyi.system.service.IBizContractService;
import com.ruoyi.system.service.contract.ContractAttachmentService;
import com.ruoyi.system.service.contract.ContractCommandService;
import com.ruoyi.system.service.contract.ContractFeePlanService;
import com.ruoyi.system.service.contract.ContractImportCommand;
import com.ruoyi.system.service.contract.ContractImportService;
import com.ruoyi.system.service.contract.ContractInvoiceService;
import com.ruoyi.system.service.contract.ContractLifecycleService;
import com.ruoyi.system.service.contract.ContractNumberService;
import com.ruoyi.system.service.contract.ContractPaymentService;
import com.ruoyi.system.service.contract.ContractQueryService;
import com.ruoyi.system.service.contract.ContractTemplateService;

@Service
public class BizContractServiceImpl implements IBizContractService
{
    @Autowired private ContractTemplateService templateService;
    @Autowired private ContractNumberService numberService;
    @Autowired private ContractQueryService queryService;
    @Autowired private ContractAttachmentService attachmentService;
    @Autowired private ContractLifecycleService lifecycleService;
    @Autowired private ContractInvoiceService invoiceService;
    @Autowired private ContractPaymentService paymentService;
    @Autowired private ContractFeePlanService feePlanService;
    @Autowired private ContractCommandService commandService;
    @Autowired private ContractImportService importService;

    @Override public List<BizContract> selectContractList(BizContract contract)
    {
        return queryService.contracts(contract);
    }

    @Override public BizContract selectContractById(Long contractId)
    {
        return queryService.contract(contractId);
    }

    @Override public int insertContract(BizContract contract) { return commandService.create(contract); }
    @Override public int updateContract(BizContract contract) { return commandService.update(contract); }
    @Override public int deleteContractByIds(Long[] contractIds) { return commandService.delete(contractIds); }

    @Override
    public String importContract(List<BizContract> contractList, Boolean updateSupport, String operName)
    {
        return importService.importContracts(new ContractImportCommand(
                contractList, Boolean.TRUE.equals(updateSupport), operName));
    }

    @Override public int submitContract(Long contractId) { return lifecycleService.submit(contractId); }
    @Override public int approveContract(Long contractId, String action, String opinion)
    {
        return lifecycleService.approve(contractId, action, opinion);
    }
    @Override public int signContract(Long contractId, String signStatus)
    {
        return lifecycleService.sign(contractId, signStatus);
    }
    @Override public int archiveContract(Long contractId, String reason)
    {
        return lifecycleService.archive(contractId, reason);
    }
    @Override public int voidContract(Long contractId, String reason)
    {
        return lifecycleService.voidContract(contractId, reason);
    }
    @Override public int terminateContract(Long contractId, String reason)
    {
        return lifecycleService.terminate(contractId, reason);
    }

    @Override public Map<String, Object> selectDashboard() { return queryService.dashboard(); }
    @Override public List<Map<String, Object>> selectRules() { return numberService.selectRules(); }
    @Override public int updateRule(Map<String, Object> rule) { return numberService.updateRule(rule); }
    @Override public List<Map<String, Object>> selectTemplates(Map<String, Object> params)
    {
        return templateService.select(params);
    }
    @Override public int insertTemplate(Map<String, Object> template) { return templateService.create(template); }
    @Override public int updateTemplate(Map<String, Object> template) { return templateService.update(template); }
    @Override public int deleteTemplate(Long templateId) { return templateService.delete(templateId); }
    @Override public List<Map<String, Object>> selectApprovals(Map<String, Object> params)
    {
        return queryService.approvals(params);
    }
    @Override public List<Map<String, Object>> selectFeePlans(Map<String, Object> params)
    {
        return queryService.feePlans(params);
    }
    @Override public int insertFeePlan(Map<String, Object> plan) { return feePlanService.create(plan); }
    @Override public int updateFeePlan(Map<String, Object> plan) { return feePlanService.update(plan); }
    @Override public int deleteFeePlan(Long planId) { return feePlanService.delete(planId); }
    @Override public int confirmFeePlan(FeeConfirmCommand command) { return paymentService.confirm(command); }
    @Override public int confirmFeePlan(Long planId, String receivedAmount)
    {
        return paymentService.confirm(planId, receivedAmount, null, null);
    }
    @Override public int confirmFeePlan(Long planId, String receivedAmount, String remark)
    {
        return paymentService.confirm(planId, receivedAmount, remark, null);
    }
    @Override public int confirmFeePlan(Long planId, String receivedAmount, String remark, String paymentMethod)
    {
        return paymentService.confirm(planId, receivedAmount, remark, paymentMethod);
    }
    @Override public int rejectFeePlan(FeeRejectCommand command) { return paymentService.reject(command); }
    @Override public int rejectFeePlan(Long planId, String reason) { return paymentService.reject(planId, reason); }
    @Override public int invoiceFeePlan(FeeInvoiceCommand command) { return invoiceService.invoice(command); }
    @Override public int invoiceFeePlan(Long planId, String invoiceStatus)
    {
        return invoiceService.invoice(planId, invoiceStatus, null, null);
    }
    @Override public int invoiceFeePlan(Long planId, String invoiceStatus, String remark)
    {
        return invoiceService.invoice(planId, invoiceStatus, remark, null);
    }
    @Override public int invoiceFeePlan(Long planId, String invoiceStatus, String remark, String invoiceType)
    {
        return invoiceService.invoice(planId, invoiceStatus, remark, invoiceType);
    }
    @Override public List<Map<String, Object>> selectAttachments(Map<String, Object> params)
    {
        return queryService.attachments(params);
    }
    @Override public int insertAttachment(Map<String, Object> attachment)
    {
        return attachmentService.create(attachment);
    }
    @Override public int deleteAttachment(Long attachmentId)
    {
        return attachmentService.delete(attachmentId);
    }
    @Override public List<Map<String, Object>> selectStatusLogs(Map<String, Object> params)
    {
        return queryService.statusLogs(params);
    }
}
