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
import com.law.business.shared.status.ContractAuditStatus;
import com.law.business.shared.status.ContractSignStatus;
import com.law.business.shared.status.ContractStatus;
import com.law.business.event.BusinessEventCommand;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.event.BusinessEventType;
import com.ruoyi.common.utils.uuid.IdUtils;
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

    private static final String RECEIVE_PENDING = "0";
    private static final String RECEIVE_CONFIRMED = "1";
    private static final String RECEIVE_REJECTED = "2";

    private static final String INVOICE_NONE = "0";
    private static final String INVOICE_DONE = "1";
    private static final String INVOICE_PARTIAL = "2";

    @Autowired
    private BizContractMapper contractMapper;

    @Autowired
    private BizCustomerMapper customerMapper;

    @Autowired
    private IBizCaseService caseService;

    @Autowired
    private ISysDictTypeService dictTypeService;

    @Autowired
    private BusinessEventPublisher eventPublisher;

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
        normalizeNewContract(contract);
        validateNewContract(contract);
        validateCustomer(contract);
        contract.setContractNo(numberService.nextNumber());
        contract.setCreateBy(SecurityUtils.getUsername());
        if (contract.getOwnerId() == null)
        {
            contract.setOwnerId(SecurityUtils.getUserId());
            contract.setDeptId(SecurityUtils.getDeptId());
        }
        int rows = contractMapper.insertContract(contract);
        assertRowsChanged(rows, "Contract was not created");
        insertStatusLog(contract.getContractId(), null, contract.getContractStatus(), "create", "创建合同");
        return rows;
    }

    @Override
    public int updateContract(BizContract contract)
    {
        BizContract existed = selectContractById(contract.getContractId());
        assertEditable(existed);
        normalizeContractUpdate(contract);
        validateNewContract(contract);
        validateCustomer(contract);
        contract.setContractNo(null);
        contract.setAuditStatus(null);
        contract.setContractStatus(null);
        contract.setSignStatus(null);
        contract.setUpdateBy(SecurityUtils.getUsername());
        int rows = contractMapper.updateContract(contract);
        assertRowsChanged(rows, "Contract was changed, please refresh and try again");
        return rows;
    }

    @Override
    public int deleteContractByIds(Long[] contractIds)
    {
        for (Long contractId : contractIds)
        {
            BizContract contract = selectContractById(contractId);
            if (AUDIT_REVIEWING.equals(contract.getAuditStatus()) || CONTRACT_PERFORMING.equals(contract.getContractStatus()) || CONTRACT_ARCHIVED.equals(contract.getContractStatus()))
            {
                throw new ServiceException("当前合同状态不允许删除");
            }
        }
        int rows = contractMapper.deleteContractByIds(contractIds, SecurityUtils.getUsername());
        assertRowsChanged(rows, "Contract was changed, please refresh and try again");
        return rows;
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
        Long contractId = toLong(plan.get("contractId"));
        assertContractAccess(contractId);
        requireFeeEditableContract(contractId);
        validateFeePlan(plan);
        plan.put("receivedAmount", BigDecimal.ZERO);
        plan.put("confirmStatus", RECEIVE_PENDING);
        plan.put("invoiceStatus", INVOICE_NONE);
        plan.put("createBy", SecurityUtils.getUsername());
        int rows = contractMapper.insertFeePlan(plan);
        assertRowsChanged(rows, "Fee plan was not created");
        insertStatusLog(contractId, null, RECEIVE_PENDING, "fee_create", feeContent("新增收费计划", plan));
        return rows;
    }
    @Override
    @Transactional
    public int updateFeePlan(Map<String, Object> plan) {
        Map<String, Object> existed = feePlanInScope(toLong(plan.get("planId")));
        requireFeeEditableContractStatus(existed.get("contractStatus"));
        if (RECEIVE_CONFIRMED.equals(String.valueOf(existed.get("confirm_status")))) {
            throw new ServiceException("Confirmed fee plans cannot be edited by normal update");
        }
        if (!INVOICE_NONE.equals(String.valueOf(existed.get("invoice_status")))) {
            throw new ServiceException("Invoiced fee plans cannot be edited by normal update");
        }
        plan.put("contractId", existed.get("contract_id"));
        plan.remove("receivedAmount");
        plan.remove("received_amount");
        plan.remove("confirmStatus");
        plan.remove("confirm_status");
        plan.remove("invoiceStatus");
        plan.remove("invoice_status");
        validateFeePlan(plan);
        plan.put("expectedConfirmStatus", existed.get("confirm_status"));
        plan.put("expectedInvoiceStatus", existed.get("invoice_status"));
        plan.put("expectedContractStatus", existed.get("contractStatus"));
        plan.put("updateBy", SecurityUtils.getUsername());
        int rows = contractMapper.updateFeePlan(plan);
        assertRowsChanged(rows, "Fee plan was changed, please refresh and try again");
        if (RECEIVE_REJECTED.equals(String.valueOf(existed.get("confirm_status")))) {
            Map<String, Object> statusUpdate = new HashMap<>();
            statusUpdate.put("planId", plan.get("planId"));
            statusUpdate.put("confirmStatus", RECEIVE_PENDING);
            statusUpdate.put("expectedConfirmStatus", existed.get("confirm_status"));
            statusUpdate.put("expectedInvoiceStatus", existed.get("invoice_status"));
            statusUpdate.put("expectedContractStatus", existed.get("contractStatus"));
            statusUpdate.put("updateBy", SecurityUtils.getUsername());
            assertStateChanged(contractMapper.updateFeePlanStatus(statusUpdate));
        }
        insertStatusLog(Long.valueOf(String.valueOf(existed.get("contract_id"))), null, RECEIVE_PENDING, "fee_update", feeContent("调整收费计划", plan));
        return rows;
    }
    @Override
    @Transactional
    public int deleteFeePlan(Long planId) {
        Map<String, Object> existed = feePlanInScope(planId);
        requireFeeEditableContractStatus(existed.get("contractStatus"));
        if (RECEIVE_CONFIRMED.equals(String.valueOf(existed.get("confirm_status"))) || !INVOICE_NONE.equals(String.valueOf(existed.get("invoice_status")))) {
            throw new ServiceException("Fee plans with received or invoiced records cannot be deleted");
        }
        int rows = contractMapper.deleteFeePlan(planId, existed.get("confirm_status"), existed.get("invoice_status"), existed.get("contractStatus"));
        assertRowsChanged(rows, "Fee plan was changed, please refresh and try again");
        insertStatusLog(Long.valueOf(String.valueOf(existed.get("contract_id"))), RECEIVE_PENDING, null, "fee_delete"ۿ-�G����ƭy�ect(planId, reason);
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

    private void validateFeePlan(Map<String, Object> plan)
    {
        Integer periodNo = parseInteger(requiredText(plan, "periodNo", "请输入期数"), "期数必须为数字");
        if (periodNo <= 0)
        {
            throw new ServiceException("期数必须大于0");
        }
        BigDecimal receivableAmount = parseDecimal(requiredText(plan, "receivableAmount", "请输入应收金额"), "应收金额必须为数字");
        if (receivableAmount.compareTo(BigDecimal.ZERO) <= 0)
        {
            throw new ServiceException("应收金额必须大于0");
        }
        requiredText(plan, "planReceiveDate", "请选择计划收款日");
    }

    private String requiredText(Map<String, Object> source, String key, String message)
    {
        Object value = source == null ? null : source.get(key);
        if (value == null || StringUtils.isEmpty(String.valueOf(value)) || "null".equalsIgnoreCase(String.valueOf(value)))
        {
            throw new ServiceException(message);
        }
        return String.valueOf(value);
    }

    private String feeContent(String action, Map<String, Object> plan)
    {
        return action + ": 第 " + plan.get("periodNo") + " 期，应收 " + plan.get("receivableAmount");
    }

    private String appendRemark(String content, String remark)
    {
        return StringUtils.isEmpty(remark) ? content : content + "；处理备注：" + remark;
    }

    private Integer parseInteger(String value, String message)
    {
        try
        {
            return Integer.valueOf(value);
        }
        catch (NumberFormatException e)
        {
            throw new ServiceException(message);
        }
    }

    private BigDecimal parseDecimal(String value, String message)
    {
        try
        {
            return new BigDecimal(value);
        }
        catch (NumberFormatException e)
        {
            throw new ServiceException(message);
        }
    }

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

    private Long toLong(Object value)
    {
        if (value == null || StringUtils.isEmpty(String.valueOf(value)))
        {
            throw new ServiceException("请选择合同");
        }
        return Long.valueOf(String.valueOf(value));
    }

    private Map<String, Object> feePlanInScope(Long planId)
    {
        Map<String, Object> plan = contractMapper.selectFeePlanById(planId);
        if (plan == null)
        {
            throw new ServiceException("收费计划不存在");
        }
        assertContractAccess(Long.valueOf(String.valueOf(plan.get("contract_id"))));
        return plan;
    }

    private void requireFeeEditableContract(Long contractId)
    {
        BizContract contract = selectContractById(contractId);
        requireFeeEditableContractStatus(contract.getContractStatus());
    }

    private void requireFeeEditableContractStatus(Object contractStatus)
    {
        String status = String.valueOf(contractStatus);
        if (CONTRACT_ARCHIVED.equals(status) || CONTRACT_VOID.equals(status) || CONTRACT_TERMINATED.equals(status))
        {
            throw new ServiceException("归档、作废或终止的合同不允许维护收费计划");
        }
    }

    private void requireFeeCollectableContractStatus(Object contractStatus)
    {
        if (!CONTRACT_PERFORMING.equals(String.valueOf(contractStatus)))
        {
            throw new ServiceException("只有履约中的合同可以确认收款或开票");
        }
    }

    private BigDecimal parseAmount(String value, Object defaultValue)
    {
        Object source = StringUtils.isEmpty(value) || "null".equalsIgnoreCase(value) ? defaultValue : value;
        if (source == null || StringUtils.isEmpty(String.valueOf(source)))
        {
            throw new ServiceException("请输入实收金额");
        }
        return parseDecimal(String.valueOf(source), "实收金额必须为数字");
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

    private void publish(BusinessEventType type, BizContract contract, Map<String, Object> payload)
    {
        eventPublisher.publish(new BusinessEventCommand(type, "CONTRACT", contract.getContractId(),
                contract.getContractNo(), type.name() + ":" + contract.getContractId() + ":" + IdUtils.fastUUID(), payload));
    }

    private void publishForPlan(BusinessEventType type, Map<String, Object> plan, Map<String, Object> payload)
    {
        Long contractId = Long.valueOf(String.valueOf(plan.get("contract_id")));
        String contractNo = plan.get("contract_no") == null ? null : String.valueOf(plan.get("contract_no"));
        eventPublisher.publish(new BusinessEventCommand(type, "CONTRACT", contractId, contractNo,
                type.name() + ":" + contractId + ":" + IdUtils.fastUUID(), payload));
    }

    private Map<String, Object> eventPayload(Object... values)
    {
        Map<String, Object> payload = new HashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2)
        {
            if (values[i + 1] != null) payload.put(String.valueOf(values[i]), values[i + 1]);
        }
        return payload;
    }
}
