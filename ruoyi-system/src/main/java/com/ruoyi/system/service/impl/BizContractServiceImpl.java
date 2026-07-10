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
            throw new ServiceException("鍚堝悓涓嶅瓨鍦ㄦ垨宸插垹闄?);
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
        insertStatusLog(contract.getContractId(), null, contract.getContractStatus(), "create", "鍒涘缓鍚堝悓");
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
                throw new ServiceException("褰撳墠鍚堝悓鐘舵€佷笉鍏佽鍒犻櫎");
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
            throw new ServiceException("瀵煎叆鍚堝悓鏁版嵁涓嶈兘涓虹┖");
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
                    throw new ServiceException("鍚堝悓宸插瓨鍦紝鍕鹃€夋洿鏂板悗鍙鐩栧鍏ワ細" + contract.getContractName());
                }
                if (duplicates.size() > 1)
                {
                    throw new ServiceException("鍚堝悓鍚嶇О閲嶅锛岃鍦ㄩ〉闈㈡墜鍔ㄧ紪杈戯細" + contract.getContractName());
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
        return "瀵煎叆鎴愬姛锛屽叡 " + successNum + " 鏉?;
    }

    @Override
    @Transactional
    public int submitContract(Long contractId)
    {
        BizContract contract = selectContractById(contractId);
        if (!AUDIT_PENDING.equals(contract.getAuditStatus()) && !AUDIT_REJECTED.equals(contract.getAuditStatus()) && !AUDIT_BACK.equals(contract.getAuditStatus()))
        {
            throw new ServiceException("褰撳墠瀹℃牳鐘舵€佷笉鍏佽鎻愪氦");
        }
        if (!CONTRACT_DRAFT.equals(contract.getContractStatus()))
        {
            throw new ServiceException("鍙湁鑽夌鐘舵€佸悎鍚屽彲浠ユ彁浜ゅ鎵?);
        }
        int rows = contractMapper.updateAuditStatus(contractId, AUDIT_REVIEWING, contract.getContractStatus(), contract.getAuditStatus(), CONTRACT_DRAFT, SecurityUtils.getUsername());
        assertStateChanged(rows);
        insertStatusLog(contractId, contract.getAuditStatus(), AUDIT_REVIEWING, "submit", "鎻愪氦瀹℃壒");
        publish(BusinessEventType.CONTRACT_SUBMITTED, contract, eventPayload("auditStatus", AUDIT_REVIEWING));
        return rows;
    }

    @Override
    @Transactional
    public int approveContract(Long contractId, String action, String opinion)
    {
        if (StringUtils.isEmpty(opinion))
        {
            throw new ServiceException("瀹℃壒鎰忚蹇呭～");
        }
        assertDictValue("law_contract_approval_action", action, "瀹℃壒鍔ㄤ綔涓嶅悎娉?);
        if (!"pass".equals(action) && !"reject".equals(action) && !"back".equals(action))
        {
            throw new ServiceException("瀹℃壒鍔ㄤ綔涓嶅悎娉?);
        }
        BizContract contract = selectContractById(contractId);
        if (!AUDIT_REVIEWING.equals(contract.getAuditStatus()))
        {
            throw new ServiceException("鍙湁瀹℃牳涓殑鍚堝悓鍙互瀹℃壒");
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
            throw new ServiceException("鍙湁瀹℃壒閫氳繃鐨勫悎鍚屽彲浠ョ缃?);
        }
        if (CONTRACT_ARCHIVED.equals(contract.getContractStatus()) || CONTRACT_VOID.equals(contract.getContractStatus()) || CONTRACT_TERMINATED.equals(contract.getContractStatus()))
        {
            throw new ServiceException("褰掓。銆佷綔搴熸垨缁堟鐨勫悎鍚屼笉鍏佽绛剧讲");
        }
        assertDictValue("law_contract_sign_status", signStatus, "绛剧讲鐘舵€佷笉鍚堟硶");
        if (!SIGN_SIGNED.equals(signStatus) && !SIGN_PARTIAL.equals(signStatus))
        {
            throw new ServiceException("绛剧讲鐘舵€佷笉鍚堟硶");
        }
        if (SIGN_SIGNED.equals(contract.getSignStatus()))
        {
            throw new ServiceException("鍚堝悓宸茬璁紝涓嶈兘閲嶅绛剧讲");
        }
        String expectedContractStatus = CONTRACT_DRAFT;
        String content = SIGN_PARTIAL.equals(signStatus) ? "鍚堝悓閮ㄥ垎绛剧讲" : "鍚堝悓绛剧讲";
        if (SIGN_PARTIAL.equals(contract.getSignStatus()))
        {
            if (!CONTRACT_PERFORMING.equals(contract.getContractStatus()) || !SIGN_SIGNED.equals(signStatus))
            {
                throw new ServiceException("閮ㄥ垎绛捐鐨勫悎鍚屽彧鑳借ˉ榻愪负宸茬璁?);
            }
            expectedContractStatus = CONTRACT_PERFORMING;
            content = "鍚堝悓琛ラ綈绛剧讲";
        }
        else if (!CONTRACT_DRAFT.equals(contract.getContractStatus()))
        {
            throw new ServiceException("鍙湁鑽夌鎴栭儴鍒嗙璁㈢殑灞ョ害涓悎鍚屽彲浠ョ缃?);
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
        String archiveReason = requiredReason(reason, "褰掓。璇存槑蹇呭～");
        BizContract contract = selectContractById(contractId);
        if (!CONTRACT_PERFORMING.equals(contract.getContractStatus()))
        {
            throw new ServiceException("鍙湁灞ョ害涓殑鍚堝悓鍙互褰掓。");
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
        String voidReason = requiredReason(reason, "浣滃簾鍘熷洜蹇呭～");
        BizContract contract = selectContractById(contractId);
        if (AUDIT_REVIEWING.equals(contract.getAuditStatus()))
        {
            throw new ServiceException("瀹℃牳涓殑鍚堝悓涓嶅厑璁镐綔搴?);
        }
        if (!CONTRACT_DRAFT.equals(contract.getContractStatus()))
        {
            throw new ServiceException("鍙湁鑽夌鐘舵€佸悎鍚屽彲浠ヤ綔搴?);
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
        String terminateReason = requiredReason(reason, "缁堟鍘熷洜蹇呭～");
        BizContract contract = selectContractById(contractId);
        if (!CONTRACT_PERFORMING.equals(contract.getContractStatus()))
        {
            throw new ServiceException("鍙湁灞ョ害涓殑鍚堝悓鍙互缁堟");
        }
        int rows = contractMapper.updateLifecycleStatus(contractId, null, CONTRACT_TERMINATED, contract.getAuditStatus(), CONTRACT_PERFORMING, SecurityUtils.getUsername());
        assertStateChanged(rows);
        insertStatusLog(contractId, contract.getContractStatus(), CONTRACT_TERMINATED, "terminate", terminateReason);
        return rows;
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
        insertStatusLog(contractId, null, RECEIVE_PENDING, "fee_create", feeContent("鏂板鏀惰垂璁″垝", plan));
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
        plan.put("expectedContractStatus", existed.get("contractStatus"))…2054 tokens truncated…    requireFeeCollectableContractStatus(plan.get("contractStatus"));
        String cleanInvoiceType = StringUtils.isEmpty(invoiceType) ? null : invoiceType.trim();
        if (!StringUtils.isEmpty(cleanInvoiceType)) {
            assertDictValue("law_finance_invoice_type", cleanInvoiceType, "鍙戠エ绫诲瀷涓嶅悎娉?);
        }
        if (!RECEIVE_CONFIRMED.equals(String.valueOf(plan.get("confirm_status")))) {
            throw new ServiceException("鍙湁宸茬‘璁ゆ敹娆剧殑璁″垝鍙互寮€绁?);
        }
        assertDictValue("law_contract_invoice_status", invoiceStatus, "寮€绁ㄧ姸鎬佷笉鍚堟硶");
        if (!INVOICE_DONE.equals(invoiceStatus) && !INVOICE_PARTIAL.equals(invoiceStatus)) {
            throw new ServiceException("寮€绁ㄧ姸鎬佷笉鍚堟硶");
        }
        String currentInvoiceStatus = String.valueOf(plan.get("invoice_status"));
        if (INVOICE_DONE.equals(currentInvoiceStatus)) {
            throw new ServiceException("宸插紑绁ㄧ殑璁″垝涓嶈兘閲嶅寮€绁?);
        }
        if (INVOICE_PARTIAL.equals(currentInvoiceStatus) && !INVOICE_DONE.equals(invoiceStatus)) {
            throw new ServiceException("閮ㄥ垎寮€绁ㄧ殑璁″垝鍙兘琛ラ綈涓哄凡寮€绁?);
        }
        if (INVOICE_NONE.equals(currentInvoiceStatus) && !INVOICE_DONE.equals(invoiceStatus) && !INVOICE_PARTIAL.equals(invoiceStatus)) {
            throw new ServiceException("寮€绁ㄧ姸鎬佷笉鍚堟硶");
        }
        if (!INVOICE_NONE.equals(currentInvoiceStatus) && !INVOICE_PARTIAL.equals(currentInvoiceStatus)) {
            throw new ServiceException("褰撳墠寮€绁ㄧ姸鎬佷笉鍏佽缁х画寮€绁?);
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
        String content = INVOICE_PARTIAL.equals(invoiceStatus) ? "閮ㄥ垎寮€绁? 绗?" + plan.get("period_no") + " 鏈? : (INVOICE_PARTIAL.equals(currentInvoiceStatus) ? "琛ラ綈寮€绁? 绗?" + plan.get("period_no") + " 鏈? : "宸插紑绁? 绗?" + plan.get("period_no") + " 鏈?);
        if (!StringUtils.isEmpty(cleanInvoiceType)) {
            content += "锛屽彂绁ㄧ被鍨?" + cleanInvoiceType;
        }
        insertStatusLog(Long.valueOf(String.valueOf(plan.get("contract_id"))), currentInvoiceStatus, invoiceStatus, "fee_invoice", appendRemark(content, cleanRemark));
        publishForPlan(BusinessEventType.INVOICE_HANDLED, plan,
                eventPayload("planId", planId, "invoiceStatus", invoiceStatus, "invoiceType", cleanInvoiceType));
        return rows;
    }
    @Override public List<Map<String, Object>> selectAttachments(Map<String, Object> params) { return queryService.attachments(params); }
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
        insertStatusLog(contractId, null, null, "attachment_add", "鏂板闄勪欢: " + requiredText(attachment, "fileName", "璇峰～鍐欓檮浠跺悕绉?));
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
        insertStatusLog(contractId, null, null, "attachment_delete", "鍒犻櫎闄勪欢");
        return rows;
    }
    @Override public List<Map<String, Object>> selectStatusLogs(Map<String, Object> params) { return queryService.statusLogs(params); }

    private void validateFeePlan(Map<String, Object> plan)
    {
        Integer periodNo = parseInteger(requiredText(plan, "periodNo", "璇疯緭鍏ユ湡鏁?), "鏈熸暟蹇呴』涓烘暟瀛?);
        if (periodNo <= 0)
        {
            throw new ServiceException("鏈熸暟蹇呴』澶т簬0");
        }
        BigDecimal receivableAmount = parseDecimal(requiredText(plan, "receivableAmount", "璇疯緭鍏ュ簲鏀堕噾棰?), "搴旀敹閲戦蹇呴』涓烘暟瀛?);
        if (receivableAmount.compareTo(BigDecimal.ZERO) <= 0)
        {
            throw new ServiceException("搴旀敹閲戦蹇呴』澶т簬0");
        }
        requiredText(plan, "planReceiveDate", "璇烽€夋嫨璁″垝鏀舵鏃?);
    }

    private void validateAttachment(Map<String, Object> attachment)
    {
        requiredText(attachment, "fileName", "璇峰～鍐欓檮浠跺悕绉?);
        requiredText(attachment, "fileUrl", "璇蜂笂浼犻檮浠?);
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
        return action + ": 绗?" + plan.get("periodNo") + " 鏈燂紝搴旀敹 " + plan.get("receivableAmount");
    }

    private String appendRemark(String content, String remark)
    {
        return StringUtils.isEmpty(remark) ? content : content + "锛涘鐞嗗娉細" + remark;
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
            throw new ServiceException("鍚堝悓蹇呴』鍏宠仈瀹㈡埛");
        }
        BizCustomer customer = customerMapper.selectCustomerById(contract.getCustomerId());
        if (customer == null || "2".equals(customer.getDelFlag()) || !CUSTOMER_NORMAL.equals(customer.getStatus()))
        {
            throw new ServiceException("瀹㈡埛涓嶅瓨鍦ㄣ€佸凡鍋滅敤鎴栧凡鍒犻櫎");
        }
        if (!SecurityUtils.isAdmin() && customerMapper.countCustomerInDataScope(contract.getCustomerId(), SecurityUtils.getUserId(), SecurityUtils.getDeptId(), CUSTOMER_MODULE_PERMISSIONS) == 0)
        {
            throw new ServiceException("鏃犳潈涓鸿瀹㈡埛鍒涘缓鎴栫紪杈戝悎鍚?);
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
            throw new ServiceException("瀵煎叆鍚堝悓蹇呴』濉啓瀹㈡埛鍚嶇О");
        }
        List<BizCustomer> customers = customerMapper.selectCustomersByExactNameInScope(contract.getCustomerName(), SecurityUtils.getUserId(), SecurityUtils.getDeptId(), !SecurityUtils.isAdmin(), CUSTOMER_MODULE_PERMISSIONS);
        if (customers.isEmpty())
        {
            throw new ServiceException("瀹㈡埛涓嶅瓨鍦ㄦ垨鏃犳潈璁块棶锛? + contract.getCustomerName());
        }
        if (customers.size() > 1)
        {
            throw new ServiceException("瀹㈡埛鍚嶇О閲嶅锛岃鍦ㄩ〉闈㈡墜鍔ㄩ€夋嫨瀹㈡埛鍚庢柊寤哄悎鍚岋細" + contract.getCustomerName());
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
            throw new ServiceException("鏂板缓鍚堝悓蹇呴』涓哄緟瀹℃牳鐘舵€?);
        }
        if (!CONTRACT_DRAFT.equals(contract.getContractStatus()))
        {
            throw new ServiceException("鏂板缓鍚堝悓蹇呴』涓鸿崏绋跨姸鎬?);
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
            throw new ServiceException("鍚堝悓涓嶈兘涓虹┖");
        }
        if (StringUtils.isEmpty(contract.getContractName()))
        {
            throw new ServiceException("鍚堝悓鍚嶇О涓嶈兘涓虹┖");
        }
        if (StringUtils.isEmpty(contract.getCaseType()))
        {
            throw new ServiceException("妗堜欢绫诲瀷涓嶈兘涓虹┖");
        }
        assertDictValue("law_contract_case_type", contract.getCaseType(), "妗堜欢绫诲瀷涓嶅悎娉?);
        assertDictValue("law_contract_fee_type", contract.getFeeType(), "鏀惰垂鏂瑰紡涓嶅悎娉?);
        assertDictValue("law_contract_sign_method", contract.getSignMethod(), "绛捐鏂瑰紡涓嶅悎娉?);
        assertDictValue("law_contract_risk_level", contract.getRiskLevel(), "椋庨櫓绛夌骇涓嶅悎娉?);
        if (contract.getSignAmount() == null || contract.getSignAmount().compareTo(BigDecimal.ZERO) <= 0)
        {
            throw new ServiceException("绛剧害閲戦蹇呴』澶т簬0");
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
            throw new ServiceException("瀛楀吀鏈垵濮嬪寲锛? + dictType);
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
        assertRowsChanged(rows, "鍚堝悓鐘舵€佸凡鍙樺寲锛岃鍒锋柊鍚庨噸璇?);
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
        assertDictValue("law_contract_status_action", actionType, "鍚堝悓鐘舵€佸姩浣滀笉鍚堟硶");
        assertRowsChanged(contractMapper.insertStatusLog(contractId, fromStatus, toStatus, actionType, content, SecurityUtils.getUsername()), "Contract status log was not created");
    }

    private void assertEditable(BizContract contract)
    {
        if (AUDIT_REVIEWING.equals(contract.getAuditStatus()))
        {
            throw new ServiceException("瀹℃牳涓殑鍚堝悓涓嶅厑璁哥紪杈?);
        }
        if (AUDIT_PASSED.equals(contract.getAuditStatus()))
        {
            throw new ServiceException("瀹℃牳閫氳繃鐨勫悎鍚屼笉鍏佽鐩存帴缂栬緫");
        }
        if (CONTRACT_ARCHIVED.equals(contract.getContractStatus()) || CONTRACT_VOID.equals(contract.getContractStatus()) || CONTRACT_TERMINATED.equals(contract.getContractStatus()))
        {
            throw new ServiceException("褰掓。銆佷綔搴熸垨缁堟鐨勫悎鍚屼笉鍏佽缂栬緫");
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
            throw new ServiceException("璇烽€夋嫨鍚堝悓");
        }
        return Long.valueOf(String.valueOf(value));
    }

    private Map<String, Object> feePlanInScope(Long planId)
    {
        Map<String, Object> plan = contractMapper.selectFeePlanById(planId);
        if (plan == null)
        {
            throw new ServiceException("鏀惰垂璁″垝涓嶅瓨鍦?);
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
            throw new ServiceException("褰掓。銆佷綔搴熸垨缁堟鐨勫悎鍚屼笉鍏佽缁存姢闄勪欢");
        }
    }

    private void requireFeeEditableContractStatus(Object contractStatus)
    {
        String status = String.valueOf(contractStatus);
        if (CONTRACT_ARCHIVED.equals(status) || CONTRACT_VOID.equals(status) || CONTRACT_TERMINATED.equals(status))
        {
            throw new ServiceException("褰掓。銆佷綔搴熸垨缁堟鐨勫悎鍚屼笉鍏佽缁存姢鏀惰垂璁″垝");
        }
    }

    private void requireFeeCollectableContractStatus(Object contractStatus)
    {
        if (!CONTRACT_PERFORMING.equals(String.valueOf(contractStatus)))
        {
            throw new ServiceException("鍙湁灞ョ害涓殑鍚堝悓鍙互纭鏀舵鎴栧紑绁?);
        }
    }

    private BigDecimal parseAmount(String value, Object defaultValue)
    {
        Object source = StringUtils.isEmpty(value) || "null".equalsIgnoreCase(value) ? defaultValue : value;
        if (source == null || StringUtils.isEmpty(String.valueOf(source)))
        {
            throw new ServiceException("璇疯緭鍏ュ疄鏀堕噾棰?);
        }
        return parseDecimal(String.valueOf(source), "瀹炴敹閲戦蹇呴』涓烘暟瀛?);
    }

    private void assertContractAccess(Long contractId)
    {
        if (contractId == null)
        {
            throw new ServiceException("鍚堝悓涓嶅瓨鍦ㄦ垨宸插垹闄?);
        }
        if (SecurityUtils.isAdmin())
        {
            return;
        }
        if (contractMapper.countContractInDataScope(contractId, SecurityUtils.getUserId(), SecurityUtils.getDeptId(), CONTRACT_MODULE_PERMISSIONS) == 0)
        {
            throw new ServiceException("鏃犳潈璁块棶璇ュ悎鍚?);
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

