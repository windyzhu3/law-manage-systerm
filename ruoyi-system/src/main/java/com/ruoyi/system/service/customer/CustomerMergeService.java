package com.ruoyi.system.service.customer;

import static com.ruoyi.system.service.customer.CustomerAccessPolicy.DATA_SCOPE_PERMISSIONS;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.business.customer.dto.CustomerMergeCommand;
import com.law.business.security.BusinessActor;
import com.law.business.security.BusinessActorProvider;
import com.law.business.shared.error.BusinessErrorCode;
import com.ruoyi.common.annotation.DataScope;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.BizCustomer;
import com.ruoyi.system.mapper.BizCustomerMapper;
import com.ruoyi.system.service.contract.ContractActionLogService;

@Service
public class CustomerMergeService
{
    private final BizCustomerMapper mapper;
    private final CustomerAccessPolicy access;
    private final BusinessActorProvider actors;
    private final ContractActionLogService actionLogs;

    public CustomerMergeService(BizCustomerMapper mapper, CustomerAccessPolicy access,
            BusinessActorProvider actors, ContractActionLogService actionLogs)
    {
        this.mapper = mapper;
        this.access = access;
        this.actors = actors;
        this.actionLogs = actionLogs;
    }

    @DataScope(deptAlias = "c", userAlias = "c", userField = "owner_id")
    public List<Map<String, Object>> candidates(BizCustomer query)
    {
        return mapper.selectMergeCandidates(query);
    }

    public List<Map<String, Object>> logs(Map<String, Object> params)
    {
        BusinessActor actor = actors.current();
        params.put("currentUserId", actor.userId());
        params.put("currentDeptId", actor.deptId());
        params.put("dataScope", !actor.administrator());
        params.put("permissions", DATA_SCOPE_PERMISSIONS);
        return mapper.selectMergeLogs(params);
    }

    @Transactional
    public int merge(CustomerMergeCommand command)
    {
        if (command == null || command.getMainCustomerId() == null || command.getMergedCustomerId() == null)
            throw error(BusinessErrorCode.VALIDATION_FAILED, "请选择主客户和待合并客户");
        if (command.getMainCustomerId().equals(command.getMergedCustomerId()))
            throw error(BusinessErrorCode.VALIDATION_FAILED, "主客户和待合并客户不能相同");

        BizCustomer main = access.requireOperable(command.getMainCustomerId());
        BizCustomer merged = access.requireOperable(command.getMergedCustomerId());
        BusinessActor actor = actors.current();
        List<Long> affectedContracts = mapper.selectContractIdsByCustomerId(merged.getCustomerId());
        if (affectedContracts == null) affectedContracts = Collections.emptyList();
        changed(mapper.markMergedConditionally(merged.getCustomerId(), actor.userName(), merged.getStatus()),
                "客户状态已变化，请刷新后重试");

        mapper.moveContacts(merged.getCustomerId(), main.getCustomerId(), actor.userName());
        mapper.moveFollowups(merged.getCustomerId(), main.getCustomerId(), actor.userName());
        mapper.moveTagRelations(merged.getCustomerId(), main.getCustomerId());
        mapper.deleteCustomerTags(merged.getCustomerId());
        int movedContracts = mapper.moveContracts(merged.getCustomerId(), main.getCustomerId(), actor.userName());
        if (movedContracts != affectedContracts.size())
            throw error(BusinessErrorCode.CONCURRENT_MODIFICATION, "客户关联合同已变化，请刷新后重试");

        String content = StringUtils.isEmpty(command.getContent()) ? "手工合并" : command.getContent().trim();
        changed(mapper.insertMergeLog(main.getCustomerId(), merged.getCustomerId(), content, actor.userName()),
                "客户合并日志创建失败");
        for (Long contractId : affectedContracts)
        {
            String detail = limit("客户合并迁移: " + merged.getCustomerName() + " -> "
                    + main.getCustomerName() + "；" + content, 500);
            actionLogs.record(contractId, null, null, "customer_merge", detail, actor);
        }
        return 1;
    }

    private String limit(String value, int maxLength)
    {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private void changed(int rows, String message)
    {
        if (rows <= 0) throw error(BusinessErrorCode.CONCURRENT_MODIFICATION, message);
    }

    private ServiceException error(BusinessErrorCode code, String message)
    {
        return new ServiceException(message, code.name());
    }
}
