package com.ruoyi.system.service.contract;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.business.event.BusinessEventCommand;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.event.BusinessEventType;
import com.law.business.shared.error.BusinessErrorCode;
import com.law.business.shared.status.ContractAuditStatus;
import com.law.business.shared.status.ContractSignStatus;
import com.law.business.shared.status.ContractStatus;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.uuid.IdUtils;
import com.ruoyi.system.domain.BizContract;
import com.ruoyi.system.mapper.BizContractMapper;
import com.ruoyi.system.service.IBizCaseService;
import com.ruoyi.system.service.ISysDictTypeService;

@Service
public class ContractLifecycleService
{
    private static final String PENDING = ContractAuditStatus.PENDING.code();
    private static final String REVIEWING = ContractAuditStatus.REVIEWING.code();
    private static final String PASSED = ContractAuditStatus.PASSED.code();
    private static final String REJECTED = ContractAuditStatus.REJECTED.code();
    private static final String BACK = ContractAuditStatus.BACK.code();
    private static final String DRAFT = ContractStatus.DRAFT.code();
    private static final String PERFORMING = ContractStatus.PERFORMING.code();
    private static final String ARCHIVED = ContractStatus.ARCHIVED.code();
    private static final String VOID = ContractStatus.VOID.code();
    private static final String TERMINATED = ContractStatus.TERMINATED.code();
    private static final String SIGNED = ContractSignStatus.SIGNED.code();
    private static final String PARTIAL = ContractSignStatus.PARTIAL.code();

    private final BizContractMapper mapper;
    private final ContractQueryService queryService;
    private final IBizCaseService caseService;
    private final ISysDictTypeService dictService;
    private final BusinessEventPublisher eventPublisher;

    public ContractLifecycleService(BizContractMapper mapper, ContractQueryService queryService,
            IBizCaseService caseService, ISysDictTypeService dictService, BusinessEventPublisher eventPublisher)
    {
        this.mapper = mapper;
        this.queryService = queryService;
        this.caseService = caseService;
        this.dictService = dictService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public int submit(Long contractId)
    {
        BizContract contract = queryService.contract(contractId);
        if (!PENDING.equals(contract.getAuditStatus()) && !REJECTED.equals(contract.getAuditStatus()) && !BACK.equals(contract.getAuditStatus()))
            throw error(BusinessErrorCode.STATE_CONFLICT, "当前审核状态不允许提交");
        if (!DRAFT.equals(contract.getContractStatus())) throw error(BusinessErrorCode.STATE_CONFLICT, "只有草稿状态合同可以提交审批");
        int rows = mapper.updateAuditStatus(contractId, REVIEWING, contract.getContractStatus(),
                contract.getAuditStatus(), DRAFT, SecurityUtils.getUsername());
        assertChanged(rows);
        log(contractId, contract.getAuditStatus(), REVIEWING, "submit", "提交审批");
        publish(BusinessEventType.CONTRACT_SUBMITTED, contract, payload("auditStatus", REVIEWING));
        return rows;
    }

    @Transactional
    public int approve(Long contractId, String action, String opinion)
    {
        if (StringUtils.isEmpty(opinion)) throw error(BusinessErrorCode.VALIDATION_FAILED, "审批意见必填");
        assertDict("law_contract_approval_action", action, "审批动作不合法");
        if (!"pass".equals(action) && !"reject".equals(action) && !"back".equals(action)) throw new ServiceException("审批动作不合法");
        BizContract contract = queryService.contract(contractId);
        if (!REVIEWING.equals(contract.getAuditStatus())) throw error(BusinessErrorCode.STATE_CONFLICT, "只有审核中的合同可以审批");
        String auditStatus = "pass".equals(action) ? PASSED : ("back".equals(action) ? BACK : REJECTED);
        String contractStatus = PASSED.equals(auditStatus) && (SIGNED.equals(contract.getSignStatus()) || PARTIAL.equals(contract.getSignStatus()))
                ? PERFORMING : contract.getContractStatus();
        Map<String, Object> approval = new HashMap<>();
        approval.put("contractId", contractId); approval.put("approvalAction", action); approval.put("approvalOpinion", opinion);
        approval.put("approverId", SecurityUtils.getUserId()); approval.put("approverName", SecurityUtils.getLoginUser().getUser().getNickName());
        approval.put("createBy", SecurityUtils.getUsername());
        int rows = mapper.updateAuditStatus(contractId, auditStatus, contractStatus, REVIEWING,
                contract.getContractStatus(), SecurityUtils.getUsername());
        assertChanged(rows);
        if (mapper.insertApproval(approval) <= 0) throw new ServiceException("审批记录创建失败");
        log(contractId, contract.getAuditStatus(), auditStatus, "approval", opinion);
        publish(BusinessEventType.CONTRACT_APPROVED, contract, payload("action", action, "auditStatus", auditStatus, "opinion", opinion));
        return rows;
    }

    @Transactional
    public int sign(Long contractId, String signStatus)
    {
        BizContract contract = queryService.contract(contractId);
        if (!PASSED.equals(contract.getAuditStatus())) throw error(BusinessErrorCode.PRECONDITION_FAILED, "只有审批通过的合同可以签署");
        if (ARCHIVED.equals(contract.getContractStatus()) || VOID.equals(contract.getContractStatus()) || TERMINATED.equals(contract.getContractStatus()))
            throw new ServiceException("归档、作废或终止的合同不允许签署");
        assertDict("law_contract_sign_status", signStatus, "签署状态不合法");
        if (!SIGNED.equals(signStatus) && !PARTIAL.equals(signStatus)) throw new ServiceException("签署状态不合法");
        if (SIGNED.equals(contract.getSignStatus())) throw error(BusinessErrorCode.DUPLICATE_OPERATION, "合同已签订，不能重复签署");
        String expectedStatus = DRAFT;
        String content = PARTIAL.equals(signStatus) ? "合同部分签署" : "合同签署";
        if (PARTIAL.equals(contract.getSignStatus()))
        {
            if (!PERFORMING.equals(contract.getContractStatus()) || !SIGNED.equals(signStatus)) throw new ServiceException("部分签订的合同只能补齐为已签订");
            expectedStatus = PERFORMING; content = "合同补齐签署";
        }
        else if (!DRAFT.equals(contract.getContractStatus())) throw new ServiceException("只有草稿或部分签订的履约中合同可以签署");
        int rows = mapper.updateLifecycleStatus(contractId, signStatus, PERFORMING, PASSED, expectedStatus, SecurityUtils.getUsername());
        assertChanged(rows);
        log(contractId, contract.getContractStatus(), PERFORMING, "sign", content);
        if (SIGNED.equals(signStatus)) caseService.createCaseFromContract(queryService.contract(contractId));
        publish(BusinessEventType.CONTRACT_SIGNED, contract, payload("signStatus", signStatus));
        return rows;
    }

    @Transactional public int archive(Long id, String reason) { return terminal(id, requiredReason(reason, "归档说明必填"), PERFORMING, ARCHIVED, "archive"); }
    @Transactional public int terminate(Long id, String reason) { return terminal(id, requiredReason(reason, "终止原因必填"), PERFORMING, TERMINATED, "terminate"); }

    @Transactional
    public int voidContract(Long id, String reason)
    {
        String value = requiredReason(reason, "作废原因必填");
        BizContract contract = queryService.contract(id);
        if (REVIEWING.equals(contract.getAuditStatus())) throw new ServiceException("审核中的合同不允许作废");
        if (!DRAFT.equals(contract.getContractStatus())) throw new ServiceException("只有草稿状态合同可以作废");
        int rows = mapper.updateLifecycleStatus(id, null, VOID, contract.getAuditStatus(), DRAFT, SecurityUtils.getUsername());
        assertChanged(rows); log(id, contract.getContractStatus(), VOID, "void", value); return rows;
    }

    private int terminal(Long id, String reason, String expected, String target, String action)
    {
        BizContract contract = queryService.contract(id);
        if (!expected.equals(contract.getContractStatus())) throw new ServiceException("只有履约中的合同可以" + (ARCHIVED.equals(target) ? "归档" : "终止"));
        int rows = mapper.updateLifecycleStatus(id, null, target, contract.getAuditStatus(), expected, SecurityUtils.getUsername());
        assertChanged(rows); log(id, contract.getContractStatus(), target, action, reason); return rows;
    }

    private void log(Long id, String from, String to, String action, String content)
    {
        assertDict("law_contract_status_action", action, "合同状态动作不合法");
        if (mapper.insertStatusLog(id, from, to, action, content, SecurityUtils.getUsername()) <= 0) throw new ServiceException("合同状态记录创建失败");
    }

    private void assertDict(String type, Object value, String message)
    {
        List<SysDictData> options = dictService.selectDictDataByType(type);
        if (options != null) for (SysDictData option : options) if (String.valueOf(value).equals(option.getDictValue())) return;
        throw new ServiceException(options == null || options.isEmpty() ? "字典未初始化：" + type : message);
    }

    private String requiredReason(String value, String message)
    {
        if (StringUtils.isEmpty(value) || "null".equalsIgnoreCase(value.trim())) throw new ServiceException(message);
        return value.trim();
    }

    private void assertChanged(int rows) { if (rows <= 0) throw new ServiceException("合同状态已变化，请刷新后重试"); }
    private ServiceException error(BusinessErrorCode code, String message) { return new ServiceException(message, code.name()); }
    private void publish(BusinessEventType type, BizContract contract, Map<String, Object> value)
    {
        eventPublisher.publish(new BusinessEventCommand(type, "CONTRACT", contract.getContractId(), contract.getContractNo(),
                type.name() + ":" + contract.getContractId() + ":" + IdUtils.fastUUID(), value));
    }
    private Map<String, Object> payload(Object... values)
    {
        Map<String, Object> result = new HashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) result.put(String.valueOf(values[i]), values[i + 1]);
        return result;
    }
}
