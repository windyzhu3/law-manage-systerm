package com.ruoyi.system.service.contract;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.law.business.security.BusinessActor;
import com.law.business.security.BusinessActorProvider;
import com.law.business.security.ContractPermissions;
import com.law.business.shared.error.BusinessErrorCode;
import com.law.business.shared.status.ContractStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.BizContract;
import com.ruoyi.system.mapper.BizContractMapper;

@Service
public class ContractAccessPolicy
{
    private static final String DELETED = "2";

    private final BizContractMapper mapper;
    private final BusinessActorProvider actors;

    public ContractAccessPolicy(BizContractMapper mapper, BusinessActorProvider actors)
    {
        this.mapper = mapper;
        this.actors = actors;
    }

    public BizContract requireReadable(Long contractId)
    {
        BizContract contract = contractId == null ? null : mapper.selectContractById(contractId);
        if (contract == null || DELETED.equals(contract.getDelFlag()))
        {
            throw error(BusinessErrorCode.DATA_NOT_FOUND, "合同不存在或已删除");
        }
        BusinessActor actor = actors.current();
        if (!actor.administrator()
                && mapper.countContractInDataScope(contractId, actor.userId(), actor.deptId(),
                        ContractPermissions.DATA_SCOPE) == 0)
        {
            throw error(BusinessErrorCode.ACCESS_DENIED, "无权访问该合同");
        }
        return contract;
    }

    public BizContract requireOperable(Long contractId)
    {
        BizContract contract = requireReadable(contractId);
        if (isTerminal(contract.getContractStatus()))
        {
            throw error(BusinessErrorCode.STATE_CONFLICT, "当前合同状态不允许操作");
        }
        return contract;
    }

    public ContractFeePlanContext requireFeePlanOperable(Long planId)
    {
        Map<String, Object> plan = planId == null ? null : mapper.selectFeePlanById(planId);
        if (plan == null)
        {
            throw error(BusinessErrorCode.DATA_NOT_FOUND, "收费计划不存在");
        }
        Long contractId = longValue(value(plan, "contract_id", "contractId"));
        requireOperable(contractId);
        return new ContractFeePlanContext(
                planId,
                contractId,
                text(value(plan, "contract_no", "contractNo")),
                text(value(plan, "confirm_status", "confirmStatus")),
                text(value(plan, "invoice_status", "invoiceStatus")),
                text(value(plan, "contractStatus", "contract_status")),
                decimal(value(plan, "receivable_amount", "receivableAmount")),
                decimal(value(plan, "received_amount", "receivedAmount")));
    }

    public Long requireAttachmentOperable(Long attachmentId)
    {
        Long contractId = attachmentId == null ? null : mapper.selectAttachmentContractId(attachmentId);
        if (contractId == null)
        {
            throw error(BusinessErrorCode.DATA_NOT_FOUND, "合同附件不存在");
        }
        requireOperable(contractId);
        return contractId;
    }

    private boolean isTerminal(String status)
    {
        return ContractStatus.ARCHIVED.code().equals(status)
                || ContractStatus.VOID.code().equals(status)
                || ContractStatus.TERMINATED.code().equals(status);
    }

    private Object value(Map<String, Object> map, String primary, String alternate)
    {
        return map.containsKey(primary) ? map.get(primary) : map.get(alternate);
    }

    private Long longValue(Object value)
    {
        if (value == null)
        {
            throw error(BusinessErrorCode.DATA_NOT_FOUND, "收费计划未关联有效合同");
        }
        return value instanceof Number number ? number.longValue() : Long.valueOf(value.toString());
    }

    private BigDecimal decimal(Object value)
    {
        if (value == null) return null;
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }

    private String text(Object value)
    {
        return value == null ? null : value.toString();
    }

    private ServiceException error(BusinessErrorCode code, String message)
    {
        return new ServiceException(message, code.name());
    }
}
