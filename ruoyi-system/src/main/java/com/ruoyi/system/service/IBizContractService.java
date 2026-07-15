package com.ruoyi.system.service;

import java.util.List;
import java.util.Map;
import com.law.business.contract.dto.FeeConfirmCommand;
import com.law.business.contract.dto.FeeInvoiceCommand;
import com.law.business.contract.dto.FeeRejectCommand;
import com.ruoyi.system.domain.BizContract;

public interface IBizContractService
{
    List<BizContract> selectContractList(BizContract contract);
    BizContract selectContractById(Long contractId);
    int insertContract(BizContract contract);
    int updateContract(BizContract contract);
    int deleteContractByIds(Long[] contractIds);
    String importContract(List<BizContract> contractList, Boolean updateSupport, String operName);
    int submitContract(Long contractId);
    int approveContract(Long contractId, String action, String opinion);
    int signContract(Long contractId, String signStatus);
    int archiveContract(Long contractId, String reason);
    int voidContract(Long contractId, String reason);
    int terminateContract(Long contractId, String reason);
    Map<String, Object> selectDashboard();
    List<Map<String, Object>> selectRules();
    int updateRule(Map<String, Object> rule);
    List<Map<String, Object>> selectTemplates(Map<String, Object> params);
    int insertTemplate(Map<String, Object> template);
    int updateTemplate(Map<String, Object> template);
    int deleteTemplate(Long templateId);
    List<Map<String, Object>> selectApprovals(Map<String, Object> params);
    List<Map<String, Object>> selectFeePlans(Map<String, Object> params);
    int insertFeePlan(Map<String, Object> plan);
    int updateFeePlan(Map<String, Object> plan);
    int deleteFeePlan(Long planId);
    int confirmFeePlan(FeeConfirmCommand command);
    int confirmFeePlan(Long planId, String receivedAmount);
    int confirmFeePlan(Long planId, String receivedAmount, String remark);
    int confirmFeePlan(Long planId, String receivedAmount, String remark, String paymentMethod);
    int rejectFeePlan(FeeRejectCommand command);
    int rejectFeePlan(Long planId, String reason);
    int invoiceFeePlan(FeeInvoiceCommand command);
    int invoiceFeePlan(Long planId, String invoiceStatus);
    int invoiceFeePlan(Long planId, String invoiceStatus, String remark);
    int invoiceFeePlan(Long planId, String invoiceStatus, String remark, String invoiceType);
    List<Map<String, Object>> selectAttachments(Map<String, Object> params);
    int insertAttachment(Map<String, Object> attachment);
    int deleteAttachment(Long attachmentId);
    List<Map<String, Object>> selectStatusLogs(Map<String, Object> params);
}
