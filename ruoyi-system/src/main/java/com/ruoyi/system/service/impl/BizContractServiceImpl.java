package com.ruoyi.system.service.impl;

import java.time.LocalDate;
import java.time.DateTimeException;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.annotation.DataScope;
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

    @Override
    @DataScope(deptAlias = "c", userAlias = "c", userField = "owner_id")
    public List<BizContract> selectContractList(BizContract contract)
    {
        return contractMapper.selectContractList(contract);
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
        contract.setContractNo(nextContractNo());
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
        BizContract contract = selectContractById(contractId);
        if (!AUDIT_PENDING.equals(contract.getAuditStatus()) && !AUDIT_REJECTED.equals(contract.getAuditStatus()) && !AUDIT_BACK.equals(contract.getAuditStatus()))
        {
            throw new ServiceException("当前审核状态不允许提交");
        }
        if (!CONTRACT_DRAFT.equals(contract.getContractStatus()))
        {
            throw new ServiceException("只有草稿状态合同可以提交审批");
        }
        int rows = contractMapper.updateAuditStatus(contractId, AUDIT_REVIEWING, contract.getContractStatus(), contract.getAuditStatus(), CONTRACT_DRAFT, SecurityUtils.getUsername());
        assertStateChanged(rows);
        insertStatusLog(contractId, contract.getAuditStatus(), AUDIT_REVIEWING, "submit", "提交审批");
        publish(BusinessEventType.CONTRACT_SUBMITTED, contract, eventPayload("auditStatus", AUDIT_REVIEWING));
        return rows;
    }

    @Override
    @Transactional
    public int approveContract(Long contractId, String action, String opinion)
    {
        if (StringUtils.isEmpty(opinion))
        {
            throw new ServiceException("审批意见必填");
        }
        assertDictValue("law_contract_approval_action", action, "审批动作不合法");
        if (!"pass".equals(action) && !"reject".equals(action) && !"back".equals(action))
        {
            throw new ServiceException("审批动作不合法");
        }
        BizContract contract = selectContractById(contractId);
        if (!AUDIT_REVIEWING.equals(contract.getAuditStatus()))
        {
            throw new ServiceException("只有审核中的合同可以审批");
        }

        String auditStatus = "pass".equals(action) ? AUDIT_PASSED : ("back".equals(action) ? AUDIT_BACK : AUDIT_REJECTED);
        String contractStatus = AUDIT_PASSED.equals(auditStatus) && (SIGN_SIGNED.equals(contract.getSignStatus()) || SIGN_PARTIAL.equals(contract.getSignStatus())) ? CONTRACT_PERFORMING : contract.getContractStatus();

        Map<String, Object> approval = new HashMap<>();
        approval.put("contractId", contractId);
        approval.put("approvalAction", action);
        approval.put("approvalOpinion", opinion);
        approval.put("approverId", SecurityUtils.getUserId());
        approval.put("approverName", SecurityUtils.getLoginUser().getUser().getNickName());
        approval.put("createBy", SecurityUtils.getUsername());
        int rows = contractMapper.updateAuditStatus(contractId, auditStatus, contractStatus, AUDIT_REVIEWING, contract.getContractStatus(), SecurityUtils.getUsername());
        assertStateChanged(rows);
        assertRowsChanged(contractMapper.insertApproval(approval), "Approval record was not created");
        insertStatusLog(contractId, contract.getAuditStatus(), auditStatus, "approval", opinion);
        publish(BusinessEventType.CONTRACT_APPROVED, contract,
                eventPayload("action", action, "auditStatus", auditStatus, "opinion", opinion));
        return rows;
    }

    @Override
    @Transactional
    public int signContract(Long contractId, String signStatus)
    {
        BizContract contract = selectContractById(contractId);
        if (!AUDIT_PASSED.equals(contract.getAuditStatus()))
        {
            throw new ServiceException("只有审批通过的合同可以签署");
        }
        if (CONTRACT_ARCHIVED.equals(contract.getContractStatus()) || CONTRACT_VOID.equals(contract.getContractStatus()) || CONTRACT_TERMINATED.equals(contract.getContractStatus()))
        {
            throw new ServiceException("归档、作废或终止的合同不允许签署");
        }
        assertDictValue("law_contract_sign_status", signStatus, "签署状态不合法");
        if (!SIGN_SIGNED.equals(signStatus) && !SIGN_PARTIAL.equals(signStatus))
        {
            throw new ServiceException("签署状态不合法");
        }
        if (SIGN_SIGNED.equals(contract.getSignStatus()))
        {
            throw new ServiceException("合同已签订，不能重复签署");
        }
        String expectedContractStatus = CONTRACT_DRAFT;
        String content = SIGN_PARTIAL.equals(signStatus) ? "合同部分签署" : "合同签署";
        if (SIGN_PARTIAL.equals(contract.getSignStatus()))
        {
            if (!CONTRACT_PERFORMING.equals(contract.getContractStatus()) || !SIGN_SIGNED.equals(signStatus))
            {
                throw new ServiceException("部分签订的合同只能补齐为已签订");
            }
            expectedContractStatus = CONTRACT_PERFORMING;
            content = "合同补齐签署";
        }
        else if (!CONTRACT_DRAFT.equals(contract.getContractStatus()))
        {
            throw new ServiceException("只有草稿或部分签订的履约中合同可以签署");
        }
        String toContractStatus = CONTRACT_PERFORMING;
        int rows = contractMapper.updateLifecycleStatus(contractId, signStatus, toContractStatus, AUDIT_PASSED, expectedContractStatus, SecurityUtils.getUsername());
        assertStateChanged(rows);
        insertStatusLog(contractId, contract.getContractStatus(), toContractStatus, "sign", content);
        if (SIGN_SIGNED.equals(signStatus))
        {
            BizContract signedContract = selectContractById(contractId);
            caseService.createCaseFromContract(signedContract);
        }
        publish(BusinessEventType.CONTRACT_SIGNED, contract, eventPayload("signStatus", signStatus));
        return rows;
    }

    @Override
    @Transactional
    public int archiveContract(Long contractId, String reason)
    {
        String archiveReason = requiredReason(reason, "归档说明必填");
        BizContract contract = selectContractById(contractId);
        if (!CONTRACT_PERFORMING.equals(contract.getContractStatus()))
        {
            throw new ServiceException("只有履约中的合同可以归档");
        }
        int rows = contractMapper.updateLifecycleStatus(contractId, null, CONTRACT_ARCHIVED, contract.getAuditStatus(), CONTRACT_PERFORMING, SecurityUtils.getUsername());
        assertStateChanged(rows);
        insertStatusLog(contractId, contract.getContractStatus(), CONTRACT_ARCHIVED, "archive", archiveReason);
        return rows;
    }

    @Override
    @Transactional
    public int voidContract(Long contractId, String reason)
    {
        String voidReason = requiredReason(reason, "作废原因必填");
        BizContract contract = selectContractById(contractId);
        if (AUDIT_REVIEWING.equals(contract.getAuditStatus()))
        {
            throw new ServiceException("审核中的合同不允许作废");
        }
        if (!CONTRACT_DRAFT.equals(contract.getContractStatus()))
        {
            throw new ServiceException("只有草稿状态合同可以作废");
        }
        int rows = contractMapper.updateLifecycleStatus(contractId, null, CONTRACT_VOID, contract.getAuditStatus(), CONTRACT_DRAFT, SecurityUtils.getUsername());
        assertStateChanged(rows);
        insertStatusLog(contractId, contract.getContractStatus(), CONTRACT_VOID, "void", voidReason);
        return rows;
    }

    @Override
    @Transactional
    public int terminateContract(Long contractId, String reason)
    {
        String terminateReason = requiredReason(reason, "终止原因必填");
        BizContract contract = selectContractById(contractId);
        if (!CONTRACT_PERFORMING.equals(contract.getContractStatus()))
        {
            throw new ServiceException("只有履约中的合同可以终止");
        }
        int rows = contractMapper.updateLifecycleStatus(contractId, null, CONTRACT_TERMINATED, contract.getAuditStatus(), CONTRACT_PERFORMING, SecurityUtils.getUsername());
        assertStateChanged(rows);
        insertStatusLog(contractId, contract.getContractStatus(), CONTRACT_TERMINATED, "terminate", terminateReason);
        return rows;
    }

    @Override
    public Map<String, Object> selectDashboard()
    {
        Map<String, Object> data = new HashMap<>();
        Boolean dataScope = !SecurityUtils.isAdmin();
        data.put("cards", contractMapper.selectDashboardCards(SecurityUtils.getUserId(), SecurityUtils.getDeptId(), dataScope, CONTRACT_MODULE_PERMISSIONS, Collections.emptyMap()));
        data.put("cases", contractMapper.selectCaseStats(SecurityUtils.getUserId(), SecurityUtils.getDeptId(), dataScope, CONTRACT_MODULE_PERMISSIONS, Collections.emptyMap()));
        return data;
    }

    @Override public List<Map<String, Object>> selectRules() { return contractMapper.selectRules(); }
    @Override
    @Transactional
    public int updateRule(Map<String, Object> rule) {
        validateNoRule(rule);
        rule.put("updateBy", SecurityUtils.getUsername());
        int rows = contractMapper.updateRule(rule);
        assertRowsChanged(rows, "编号规则不存在或已变化，请刷新后重试");
        if ("0".equals(String.valueOf(rule.get("status")))) {
            contractMapper.disableOtherNoRules(toLong(rule.get("ruleId")), SecurityUtils.getUsername());
        }
        else if (contractMapper.countOtherEnabledNoRules(toLong(rule.get("ruleId"))) == 0) {
            throw new ServiceException("至少需要保留一个启用的合同编号规则");
        }
        return rows;
    }
    @Override public List<Map<String, Object>> selectTemplates(Map<String, Object> params) { return contractMapper.selectTemplates(params); }
    @Override public int insertTemplate(Map<String, Object> template) { validateTemplate(template); template.put("createBy", SecurityUtils.getUsername()); int rows = contractMapper.insertTemplate(template); assertRowsChanged(rows, "Contract template was not created"); return rows; }
    @Override public int updateTemplate(Map<String, Object> template) { toLong(template.get("templateId")); validateTemplate(template); template.put("updateBy", SecurityUtils.getUsername()); int rows = contractMapper.updateTemplate(template); assertRowsChanged(rows, "Contract template was changed, please refresh and try again"); return rows; }
    @Override public int deleteTemplate(Long templateId) {
        Map<String, Object> template = contractMapper.selectTemplateById(templateId);
        if (template == null)
        {
            throw new ServiceException("合同模板不存在");
        }
        if ("0".equals(String.valueOf(template.get("status"))))
        {
            throw new ServiceException("启用中的合同模板不允许删除，请先停用");
        }
        int rows = contractMapper.deleteTemplate(templateId);
        assertRowsChanged(rows, "Contract template was changed, please refresh and try again");
        return rows;
    }
    @Override public List<Map<String, Object>> selectApprovals(Map<String, Object> params) { applyDataScope(params); return contractMapper.selectApprovals(params); }
    @Override public List<Map<String, Object>> selectFeePlans(Map<String, Object> params) { applyDataScope(params); return contractMapper.selectFeePlans(params); }
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
        insertStatusLog(Long.valueOf(String.valueOf(existed.get("contract_id"))), RECEIVE_PENDING, null, "fee_delete", "删除收费计划: 第 " + existed.get("period_no") + " 期");
        return rows;
    }
    @Override
    @Transactional
    public int confirmFeePlan(Long planId, String receivedAmount) {
        return confirmFeePlan(planId, receivedAmount, null);
    }

    @Override
    @Transactional
    public int confirmFeePlan(Long planId, String receivedAmount, String remark) {
        return confirmFeePlan(planId, receivedAmount, remark, null);
    }

    @Override
    @Transactional
    public int confirmFeePlan(Long planId, String receivedAmount, String remark, String paymentMethod) {
        Map<String, Object> plan = feePlanInScope(planId);
        requireFeeCollectableContractStatus(plan.get("contractStatus"));
        String cleanPaymentMethod = StringUtils.isEmpty(paymentMethod) ? null : paymentMethod.trim();
        if (!StringUtils.isEmpty(cleanPaymentMethod)) {
            assertDictValue("law_finance_payment_method", cleanPaymentMethod, "付款方式不合法");
        }
        String currentConfirmStatus = String.valueOf(plan.get("confirm_status"));
        BigDecimal receivableAmount = parseAmount(String.valueOf(plan.get("receivable_amount")), plan.get("receivable_amount"));
        BigDecimal currentReceivedAmount = parseAmount(String.valueOf(plan.get("received_amount")), BigDecimal.ZERO);
        BigDecimal remainingAmount = receivableAmount.subtract(currentReceivedAmount);
        if (!RECEIVE_PENDING.equals(currentConfirmStatus) && !RECEIVE_CONFIRMED.equals(currentConfirmStatus)) {
            throw new ServiceException("当前收费计划状态不允许确认收款");
        }
        if (RECEIVE_CONFIRMED.equals(currentConfirmStatus) && remainingAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("收费计划已收齐，不能重复确认收款");
        }
        BigDecimal amount = parseAmount(receivedAmount, remainingAmount.compareTo(BigDecimal.ZERO) > 0 ? remainingAmount : plan.get("receivable_amount"));
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("实收金额必须大于0");
        }
        if (amount.compareTo(remainingAmount.compareTo(BigDecimal.ZERO) > 0 ? remainingAmount : receivableAmount) > 0) {
            throw new ServiceException("本次回款不能超过待收金额");
        }
        BigDecimal totalReceivedAmount = currentReceivedAmount.add(amount);
        Map<String, Object> update = new HashMap<>();
        update.put("planId", planId);
        update.put("receivedAmount", totalReceivedAmount);
        update.put("confirmStatus", RECEIVE_CONFIRMED);
        update.put("expectedConfirmStatus", currentConfirmStatus);
        update.put("expectedInvoiceStatus", plan.get("invoice_status"));
        update.put("expectedContractStatus", plan.get("contractStatus"));
        update.put("updateBy", SecurityUtils.getUsername());
        String cleanRemark = StringUtils.isEmpty(remark) ? null : remark.trim();
        if (!StringUtils.isEmpty(cleanRemark)) {
            update.put("remark", cleanRemark);
        }
        if (!StringUtils.isEmpty(cleanPaymentMethod)) {
            update.put("paymentMethod", cleanPaymentMethod);
        }
        int rows = contractMapper.updateFeePlanStatus(update);
        assertStateChanged(rows);
        String actionText = totalReceivedAmount.compareTo(receivableAmount) >= 0
            ? (RECEIVE_CONFIRMED.equals(currentConfirmStatus) ? "补齐收款" : "确认收款")
            : "部分收款";
        String content = actionText + ": 第 " + plan.get("period_no") + " 期，本次实收 " + amount + "，累计实收 " + totalReceivedAmount;
        if (!StringUtils.isEmpty(cleanPaymentMethod)) {
            content += "，付款方式 " + cleanPaymentMethod;
        }
        insertStatusLog(Long.valueOf(String.valueOf(plan.get("contract_id"))), currentConfirmStatus, RECEIVE_CONFIRMED, "fee_confirm", appendRemark(content, cleanRemark));
        publishForPlan(BusinessEventType.PAYMENT_CONFIRMED, plan,
                eventPayload("planId", planId, "receivedAmount", amount, "totalReceivedAmount", totalReceivedAmount));
        return rows;
    }
    @Override
    @Transactional
    public int rejectFeePlan(Long planId, String reason) {
        String rejectReason = requiredReason(reason, "驳回原因必填");
        Map<String, Object> plan = feePlanInScope(planId);
        requireFeeCollectableContractStatus(plan.get("contractStatus"));
        if (!RECEIVE_PENDING.equals(String.valueOf(plan.get("confirm_status")))) {
            throw new ServiceException("只有待确认的收费计划可以驳回");
        }
        Map<String, Object> update = new HashMap<>();
        update.put("planId", planId);
        update.put("confirmStatus", RECEIVE_REJECTED);
        update.put("remark", rejectReason);
        update.put("expectedConfirmStatus", plan.get("confirm_status"));
        update.put("expectedInvoiceStatus", plan.get("invoice_status"));
        update.put("expectedContractStatus", plan.get("contractStatus"));
        update.put("updateBy", SecurityUtils.getUsername());
        int rows = contractMapper.updateFeePlanStatus(update);
        assertStateChanged(rows);
        insertStatusLog(Long.valueOf(String.valueOf(plan.get("contract_id"))), String.valueOf(plan.get("confirm_status")), RECEIVE_REJECTED, "fee_reject", rejectReason);
        publishForPlan(BusinessEventType.PAYMENT_REJECTED, plan,
                eventPayload("planId", planId, "reason", rejectReason));
        return rows;
    }
    @Override
    @Transactional
    public int invoiceFeePlan(Long planId, String invoiceStatus) {
        return invoiceFeePlan(planId, invoiceStatus, null);
    }

    @Override
    @Transactional
    public int invoiceFeePlan(Long planId, String invoiceStatus, String remark) {
        return invoiceFeePlan(planId, invoiceStatus, remark, null);
    }

    @Override
    @Transactional
    public int invoiceFeePlan(Long planId, String invoiceStatus, String remark, String invoiceType) {
        Map<String, Object> plan = feePlanInScope(planId);
        requireFeeCollectableContractStatus(plan.get("contractStatus"));
        String cleanInvoiceType = StringUtils.isEmpty(invoiceType) ? null : invoiceType.trim();
        if (!StringUtils.isEmpty(cleanInvoiceType)) {
            assertDictValue("law_finance_invoice_type", cleanInvoiceType, "发票类型不合法");
        }
        if (!RECEIVE_CONFIRMED.equals(String.valueOf(plan.get("confirm_status")))) {
            throw new ServiceException("只有已确认收款的计划可以开票");
        }
        assertDictValue("law_contract_invoice_status", invoiceStatus, "开票状态不合法");
        if (!INVOICE_DONE.equals(invoiceStatus) && !INVOICE_PARTIAL.equals(invoiceStatus)) {
            throw new ServiceException("开票状态不合法");
        }
        String currentInvoiceStatus = String.valueOf(plan.get("invoice_status"));
        if (INVOICE_DONE.equals(currentInvoiceStatus)) {
            throw new ServiceException("已开票的计划不能重复开票");
        }
        if (INVOICE_PARTIAL.equals(currentInvoiceStatus) && !INVOICE_DONE.equals(invoiceStatus)) {
            throw new ServiceException("部分开票的计划只能补齐为已开票");
        }
        if (INVOICE_NONE.equals(currentInvoiceStatus) && !INVOICE_DONE.equals(invoiceStatus) && !INVOICE_PARTIAL.equals(invoiceStatus)) {
            throw new ServiceException("开票状态不合法");
        }
        if (!INVOICE_NONE.equals(currentInvoiceStatus) && !INVOICE_PARTIAL.equals(currentInvoiceStatus)) {
            throw new ServiceException("当前开票状态不允许继续开票");
        }
        Map<String, Object> update = new HashMap<>();
        update.put("planId", planId);
        update.put("invoiceStatus", invoiceStatus);
        update.put("expectedConfirmStatus", plan.get("confirm_status"));
        update.put("expectedInvoiceStatus", plan.get("invoice_status"));
        update.put("expectedContractStatus", plan.get("contractStatus"));
        update.put("updateBy", SecurityUtils.getUsername());
        String cleanRemark = StringUtils.isEmpty(remark) ? null : remark.trim();
        if (!StringUtils.isEmpty(cleanRemark)) {
            update.put("remark", cleanRemark);
        }
        if (!StringUtils.isEmpty(cleanInvoiceType)) {
            update.put("invoiceType", cleanInvoiceType);
        }
        int rows = contractMapper.updateFeePlanStatus(update);
        assertStateChanged(rows);
        String content = INVOICE_PARTIAL.equals(invoiceStatus) ? "部分开票: 第 " + plan.get("period_no") + " 期" : (INVOICE_PARTIAL.equals(currentInvoiceStatus) ? "补齐开票: 第 " + plan.get("period_no") + " 期" : "已开票: 第 " + plan.get("period_no") + " 期");
        if (!StringUtils.isEmpty(cleanInvoiceType)) {
            content += "，发票类型 " + cleanInvoiceType;
        }
        insertStatusLog(Long.valueOf(String.valueOf(plan.get("contract_id"))), currentInvoiceStatus, invoiceStatus, "fee_invoice", appendRemark(content, cleanRemark));
        publishForPlan(BusinessEventType.INVOICE_HANDLED, plan,
                eventPayload("planId", planId, "invoiceStatus", invoiceStatus, "invoiceType", cleanInvoiceType));
        return rows;
    }
    @Override public List<Map<String, Object>> selectAttachments(Map<String, Object> params) { applyDataScope(params); return contractMapper.selectAttachments(params); }
    @Override
    @Transactional
    public int insertAttachment(Map<String, Object> attachment) {
        Long contractId = toLong(attachment.get("contractId"));
        assertContractAccess(contractId);
        requireAttachmentEditableContract(contractId);
        validateAttachment(attachment);
        attachment.put("createBy", SecurityUtils.getUsername());
        int rows = contractMapper.insertAttachment(attachment);
        assertRowsChanged(rows, "Attachment was not created");
        insertStatusLog(contractId, null, null, "attachment_add", "新增附件: " + requiredText(attachment, "fileName", "请填写附件名称"));
        return rows;
    }
    @Override
    @Transactional
    public int deleteAttachment(Long attachmentId) {
        Long contractId = contractMapper.selectAttachmentContractId(attachmentId);
        if (contractId == null) {
            throw new ServiceException("Attachment does not exist");
        }
        assertContractAccess(contractId);
        requireAttachmentEditableContract(contractId);
        int rows = contractMapper.deleteAttachment(attachmentId, contractId);
        assertRowsChanged(rows, "Attachment was changed, please refresh and try again");
        insertStatusLog(contractId, null, null, "attachment_delete", "删除附件");
        return rows;
    }
    @Override public List<Map<String, Object>> selectStatusLogs(Map<String, Object> params) { applyDataScope(params); return contractMapper.selectStatusLogs(params); }

    private void validateTemplate(Map<String, Object> template)
    {
        requiredText(template, "templateName", "请输入模板名称");
        requiredText(template, "caseType", "请选择案件类型");
        requiredText(template, "fileUrl", "请上传模板文件");
        assertDictValue("law_contract_case_type", template.get("caseType"), "模板案件类型不合法");
        if (StringUtils.isEmpty(String.valueOf(template.get("status"))) || "null".equalsIgnoreCase(String.valueOf(template.get("status"))))
        {
            template.put("status", "0");
        }
        assertDictValue("sys_normal_disable", template.get("status"), "模板状态不合法");
        if (StringUtils.isEmpty(String.valueOf(template.get("versionNo"))) || "null".equalsIgnoreCase(String.valueOf(template.get("versionNo"))))
        {
            template.put("versionNo", "v1");
        }
    }

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

    private void validateAttachment(Map<String, Object> attachment)
    {
        requiredText(attachment, "fileName", "请填写附件名称");
        requiredText(attachment, "fileUrl", "请上传附件");
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

    private String nextContractNo()
    {
        Map<String, Object> rule = contractMapper.selectActiveNoRule();
        if (rule == null)
        {
            throw new ServiceException("No enabled contract number rule configured");
        }
        Long ruleId = parseLong(String.valueOf(rule.get("ruleId")), "Number rule does not exist");
        String prefix = String.valueOf(rule.get("prefix"));
        String datePattern = String.valueOf(rule.get("datePattern"));
        Integer length = parseInteger(String.valueOf(rule.get("serialLength")), "Number rule serial length must be a number");
        Integer serial = parseInteger(String.valueOf(rule.get("currentSerial")), "Number rule current serial must be a number") + 1;
        assertRowsChanged(contractMapper.updateNoRule(ruleId, serial, SecurityUtils.getUsername()), "Contract number rule was changed, please refresh and try again");
        String date = formatDatePattern(datePattern);
        return prefix + date + String.format("%0" + length + "d", serial);
    }

    private void validateNoRule(Map<String, Object> rule)
    {
        if (rule == null || rule.get("ruleId") == null)
        {
            throw new ServiceException("Number rule does not exist");
        }
        String prefix = String.valueOf(rule.get("prefix"));
        String datePattern = String.valueOf(rule.get("datePattern"));
        Integer length = parseInteger(String.valueOf(rule.get("serialLength")), "Number rule serial length must be a number");
        if (StringUtils.isEmpty(prefix))
        {
            throw new ServiceException("Number rule prefix is required");
        }
        if (StringUtils.isEmpty(datePattern))
        {
            throw new ServiceException("Number rule date pattern is required");
        }
        if (length < 3 || length > 12)
        {
            throw new ServiceException("Number rule serial length must be between 3 and 12");
        }
        requiredText(rule, "status", "请选择编号规则状态");
        assertDictValue("sys_normal_disable", rule.get("status"), "编号规则状态不合法");
        formatDatePattern(datePattern);
    }

    private Long parseLong(String value, String message)
    {
        try
        {
            return Long.valueOf(value);
        }
        catch (NumberFormatException e)
        {
            throw new ServiceException(message);
        }
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

    private String formatDatePattern(String datePattern)
    {
        try
        {
            return LocalDate.now().format(DateTimeFormatter.ofPattern(datePattern));
        }
        catch (IllegalArgumentException | DateTimeException e)
        {
            throw new ServiceException("Number rule date pattern is invalid");
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

    private void applyDataScope(Map<String, Object> params)
    {
        params.put("currentUserId", SecurityUtils.getUserId());
        params.put("currentDeptId", SecurityUtils.getDeptId());
        params.put("dataScope", !SecurityUtils.isAdmin());
        params.put("permissions", CONTRACT_MODULE_PERMISSIONS);
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

    private void requireAttachmentEditableContract(Long contractId)
    {
        BizContract contract = selectContractById(contractId);
        String status = contract.getContractStatus();
        if (CONTRACT_ARCHIVED.equals(status) || CONTRACT_VOID.equals(status) || CONTRACT_TERMINATED.equals(status))
        {
            throw new ServiceException("归档、作废或终止的合同不允许维护附件");
        }
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
