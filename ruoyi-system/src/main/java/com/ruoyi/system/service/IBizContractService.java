package com.ruoyi.system.service;

import java.util.List;
import java.util.Map;
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
import com.ruoyi.system.service.contract.ContractImportCommand;

public interface IBizContractService
{
    List<BizContract> selectContractList(BizContract contract);
    BizContract selectContractById(Long contractId);
    int insertContract(ContractCreateCommand command);
    int updateContract(ContractUpdateCommand command);
    int deleteContractByIds(Long[] contractIds);
    String importContract(ContractImportCommand command);
    int submitContract(Long contractId);
    int approveContract(ContractApprovalCommand command);
    int signContract(ContractSignCommand command);
    int archiveContract(ContractReasonCommand command);
    int voidContract(ContractReasonCommand command);
    int terminateContract(ContractReasonCommand command);
    Map<String, Object> selectDashboard();
    List<Map<String, Object>> selectRules();
    int updateRule(ContractNumberRuleUpdateCommand command);
    List<Map<String, Object>> selectTemplates(Map<String, Object> params);
    int insertTemplate(ContractTemplateCreateCommand command);
    int updateTemplate(ContractTemplateUpdateCommand command);
    int deleteTemplate(Long templateId);
    List<Map<String, Object>> selectApprovals(Map<String, Object> params);
    List<Map<String, Object>> selectFeePlans(Map<String, Object> params);
    int insertFeePlan(ContractFeePlanCreateCommand command);
    int updateFeePlan(ContractFeePlanUpdateCommand command);
    int deleteFeePlan(Long planId);
    int confirmFeePlan(FeeConfirmCommand command);
    int rejectFeePlan(FeeRejectCommand command);
    int invoiceFeePlan(FeeInvoiceCommand command);
    List<Map<String, Object>> selectAttachments(Map<String, Object> params);
    int insertAttachment(ContractAttachmentCreateCommand command);
    int deleteAttachment(Long attachmentId);
    List<Map<String, Object>> selectStatusLogs(Map<String, Object> params);
}
