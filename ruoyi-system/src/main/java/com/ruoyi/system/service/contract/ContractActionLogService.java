package com.ruoyi.system.service.contract;

import org.springframework.stereotype.Service;
import com.law.business.security.BusinessActor;
import com.law.business.shared.error.BusinessErrorCode;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.mapper.BizContractMapper;

@Service
public class ContractActionLogService
{
    private final BizContractMapper mapper;

    public ContractActionLogService(BizContractMapper mapper)
    {
        this.mapper = mapper;
    }

    public Long record(Long contractId, String fromStatus, String toStatus, String actionType,
            String content, BusinessActor actor)
    {
        if (actor == null) throw error("合同动作日志缺少操作人");
        return record(contractId, fromStatus, toStatus, actionType, content, actor.userName());
    }

    public Long record(Long contractId, String fromStatus, String toStatus, String actionType,
            String content, String operator)
    {
        ContractStatusLogRecord record = new ContractStatusLogRecord();
        record.setContractId(contractId);
        record.setFromStatus(fromStatus);
        record.setToStatus(toStatus);
        record.setActionType(actionType);
        record.setContent(content);
        record.setCreateBy(operator);
        if (mapper.insertStatusLog(record) <= 0 || record.getLogId() == null)
        {
            throw error("合同动作日志创建失败");
        }
        return record.getLogId();
    }

    private ServiceException error(String message)
    {
        return new ServiceException(message, BusinessErrorCode.CONCURRENT_MODIFICATION.name());
    }
}
