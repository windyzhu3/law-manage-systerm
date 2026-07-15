package com.ruoyi.system.service.impl;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.law.business.contract.dto.ContractApprovalCommand;
import com.law.business.contract.dto.ContractAttachmentCreateCommand;
import com.law.business.contract.dto.ContractCreateCommand;
import com.law.business.contract.dto.ContractFeePlanCreateCommand;
import com.law.business.contract.dto.ContractFeePlanUpdateCommand;
import com.law.business.contract.dto.ContractNumberRuleUpdateCommand;
import com.law.business.contract.dto.ContractReasonCommand;
import com.law.business.contract.dto.ContractSignCommand;
import com.law.business.contract.dto.ContractTemplateCreateCommand;
import com.law.business.contract.dto.ContractTemplateUpdateCommand;
import com.law.business.contract.dto.ContractUpdateCommand;
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

    @Override public int insertContract(ContractCreateCommand command) { return commandService.create(command); }
    @Override public int updateContract(ContractUpdateCommand command) { return commandService.update(command); }
    @Override public int deleteContractByIds(Long[] contractIds) { return commandService.delete(contractIds); }

    @Override public String importContract(ContractImportCommand command)
    {
        return importService.importContracts(command);
    }

    @Override public int submitContract(Long contractId) { return lifecycleService.submit(contractId); }
    @Override public int approveContract(ContractApprovalCommand command)
    {
        return lifecycleService.approve(command.getContractId(), command.getAction(), command.getOpinion());
    }
    @Override public int signContract(ContractSignCommand command)
    {
        return lifecycleService.sign(command.getContractId(), command.getSignStatus());
    }
    @Override public int archiveContract(ContractReasonCommand command)
    {
        return lifecycleService.archive(command.getContractId(), command.getReason());
    }
    @Override public int voidContract(ContractReasonCommand command)
    {
        return lifecycleService.voidContract(command.getContractId(), command.getReason());
    }
    @Override public int terminateContract(ContractReasonCommand command)
    {
        return lifecycleService.terminate(command.getContractId(), command.getReason());
    }

    @Override public Map<String, Object> selectDashboard() { return queryService.dashboard(); }
    @Override public List<Map<String, Object>> selectRules() { return numberService.selectRules(); }
    @Override public int updateRule(ContractNumberRuleUpdateCommand command)
    {
        return numberService.updateRule(command);
    }
    @Override public List<Map<String, Object>> selectTemplates(Map<String, Object> params)
    {
        return templateService.select(params);
    }
    @Override public int insertTemplate(ContractTemplateCreateCommand command)
    {
        return templateService.create(command);
    }
    @Override public int updateTemplate(ContractTemplateUpdateCommand command)
    {
        return templateService.update(command);
    }
    @Override public int deleteTemplate(Long templateId) { return templateService.delete(templateId); }
    @Override public List<Map<String, Object>> selectApprovals(Map<String, Object> params)
    {
        return queryService.approvals(params);
    }
    @Override public List<Map<String, Object>> selectFeePlans(Map<String, Object> params)
    {
        return queryService.feePlans(params);
    }
    @Override public int insertFeePlan(ContractFeePlanCreateCommand command)
    {
        return feePlanService.create(command);
    }
    @Override public int updateFeePlan(ContractFeePlanUpdateCommand command)
    {
        return feePlanService.update(command);
    }
    @Override public int deleteFeePlan(Long planId) { return feePlanService.delete(planId); }
    @Override public int confirmFeePlan(FeeConfirmCommand command) { return paymentService.confirm(command); }
    @Override public int rejectFeePlan(FeeRejectCommand command) { return paymentService.reject(command); }
    @Override public int invoiceFeePlan(FeeInvoiceCommand command) { return invoiceService.invoice(command); }
    @Override public List<Map<String, Object>> selectAttachments(Map<String, Object> params)
    {
        return queryService.attachments(params);
    }
    @Override public int insertAttachment(ContractAttachmentCreateCommand command)
    {
        return attachmentService.create(command);
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
