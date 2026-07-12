package com.ruoyi.system.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.BizContract;

public interface BizContractMapper
{
    List<BizContract> selectContractList(BizContract contract);
    BizContract selectContractById(Long contractId);
    List<BizContract> selectContractsByExactNameInScope(@Param("contractName") String contractName, @Param("currentUserId") Long currentUserId, @Param("currentDeptId") Long currentDeptId, @Param("dataScope") Boolean dataScope, @Param("permissions") String permissions);
    int insertContract(BizContract contract);
    int updateContract(BizContract contract);
    int deleteContractByIds(@Param("contractIds") Long[] contractIds, @Param("updateBy") String updateBy);
    int countContractInDataScope(@Param("contractId") Long contractId, @Param("currentUserId") Long currentUserId, @Param("currentDeptId") Long currentDeptId, @Param("permissions") String permissions);
    List<Map<String, Object>> selectDashboardCards(@Param("currentUserId") Long currentUserId, @Param("currentDeptId") Long currentDeptId, @Param("dataScope") Boolean dataScope, @Param("permissions") String permissions, @Param("params") Map<String, Object> params);
    List<Map<String, Object>> selectCaseStats(@Param("currentUserId") Long currentUserId, @Param("currentDeptId") Long currentDeptId, @Param("dataScope") Boolean dataScope, @Param("permissions") String permissions, @Param("params") Map<String, Object> params);
    Map<String, Object> selectActiveNoRule();
    int updateNoRule(@Param("ruleId") Long ruleId, @Param("currentSerial") Integer currentSerial, @Param("updateBy") String updateBy);
    int disableOtherNoRules(@Param("ruleId") Long ruleId, @Param("updateBy") String updateBy);
    int countOtherEnabledNoRules(Long ruleId);
    List<Map<String, Object>> selectRules();
    int updateRule(Map<String, Object> rule);
    List<Map<String, Object>> selectTemplates(Map<String, Object> params);
    int insertTemplate(Map<String, Object> template);
    int updateTemplate(Map<String, Object> template);
    Map<String, Object> selectTemplateById(Long templateId);
    int deleteTemplate(Long templateId);
    List<Map<String, Object>> selectApprovals(Map<String, Object> params);
    int insertApproval(Map<String, Object> approval);
    int updateAuditStatus(@Param("contractId") Long contractId, @Param("auditStatus") String auditStatus, @Param("contractStatus") String contractStatus, @Param("expectedAuditStatus") String expectedAuditStatus, @Param("expectedContractStatus") String expectedContractStatus, @Param("updateBy") String updateBy);
    int updateLifecycleStatus(@Param("contractId") Long contractId, @Param("signStatus") String signStatus, @Param("contractStatus") String contractStatus, @Param("expectedAuditStatus") String expectedAuditStatus, @Param("expectedContractStatus") String expectedContractStatus, @Param("updateBy") String updateBy);
    int updateSignMetadata(@Param("contractId") Long contractId,@Param("signMethod") String signMethod,@Param("signDate") java.time.LocalDate signDate,@Param("updateBy") String updateBy);
    List<Map<String, Object>> selectFeePlans(Map<String, Object> params);
    Map<String, Object> selectFeePlanById(Long planId);
    Long selectFeePlanContractId(Long planId);
    int insertFeePlan(Map<String, Object> plan);
    int updateFeePlan(Map<String, Object> plan);
    int updateFeePlanStatus(Map<String, Object> plan);
    int deleteFeePlan(@Param("planId") Long planId, @Param("expectedConfirmStatus") Object expectedConfirmStatus, @Param("expectedInvoiceStatus") Object expectedInvoiceStatus, @Param("expectedContractStatus") Object expectedContractStatus);
    List<Map<String, Object>> selectAttachments(Map<String, Object> params);
    Long selectAttachmentContractId(Long attachmentId);
    int insertAttachment(Map<String, Object> attachment);
    int deleteAttachment(@Param("attachmentId") Long attachmentId, @Param("contractId") Long contractId);
    List<Map<String, Object>> selectStatusLogs(Map<String, Object> params);
    int insertStatusLog(@Param("contractId") Long contractId, @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus, @Param("actionType") String actionType, @Param("content") String content, @Param("createBy") String createBy);
}
