package com.ruoyi.system.service.contract;

import static com.ruoyi.system.service.customer.CustomerAccessPolicy.DATA_SCOPE_PERMISSIONS;

import java.sql.Date;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.business.contract.dto.ContractCreateCommand;
import com.law.business.contract.dto.ContractUpdateCommand;
import com.law.business.security.BusinessActor;
import com.law.business.security.BusinessActorProvider;
import com.law.business.security.ContractPermissions;
import com.law.business.shared.error.BusinessErrorCode;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.BizContract;
import com.ruoyi.system.domain.BizCustomer;
import com.ruoyi.system.mapper.BizContractMapper;
import com.ruoyi.system.mapper.BizCustomerMapper;

@Service
public class ContractImportService
{
    private final BizContractMapper contracts;
    private final BizCustomerMapper customers;
    private final ContractCommandService commands;
    private final BusinessActorProvider actors;

    public ContractImportService(BizContractMapper contracts, BizCustomerMapper customers,
            ContractCommandService commands, BusinessActorProvider actors)
    {
        this.contracts = contracts;
        this.customers = customers;
        this.commands = commands;
        this.actors = actors;
    }

    @Transactional
    public String importContracts(ContractImportCommand command)
    {
        if (command == null || command.rows() == null || command.rows().isEmpty())
            throw error(BusinessErrorCode.VALIDATION_FAILED, "导入合同数据不能为空");
        BusinessActor actor = actors.current();
        int success = 0;
        for (BizContract row : command.rows())
        {
            if (row == null || StringUtils.isEmpty(row.getContractName())) continue;
            Long customerId = resolveCustomerId(row, actor);
            List<BizContract> duplicates = contracts.selectContractsByExactNameInScope(
                    row.getContractName(), actor.userId(), actor.deptId(), !actor.administrator(),
                    ContractPermissions.DATA_SCOPE);
            if (duplicates == null) duplicates = List.of();
            if (duplicates.size() > 1)
                throw error(BusinessErrorCode.PRECONDITION_FAILED,
                        "合同名称重复，请在页面手动编辑：" + row.getContractName());
            if (!duplicates.isEmpty() && !command.updateSupport())
                throw error(BusinessErrorCode.DUPLICATE_OPERATION,
                        "合同已存在，勾选更新后方可覆盖导入：" + row.getContractName());
            if (duplicates.isEmpty())
            {
                commands.create(toCreate(row, customerId));
            }
            else
            {
                ContractUpdateCommand update = toUpdate(row, customerId);
                update.setContractId(duplicates.get(0).getContractId());
                commands.update(update);
            }
            success++;
        }
        return "导入成功，共 " + success + " 条";
    }

    private Long resolveCustomerId(BizContract row, BusinessActor actor)
    {
        if (row.getCustomerId() != null) return row.getCustomerId();
        if (StringUtils.isEmpty(row.getCustomerName()))
            throw error(BusinessErrorCode.VALIDATION_FAILED, "导入合同必须填写客户名称");
        List<BizCustomer> matches = customers.selectCustomersByExactNameInScope(row.getCustomerName(),
                actor.userId(), actor.deptId(), !actor.administrator(), DATA_SCOPE_PERMISSIONS);
        if (matches == null || matches.isEmpty())
            throw error(BusinessErrorCode.DATA_NOT_FOUND, "客户不存在或无权访问：" + row.getCustomerName());
        if (matches.size() > 1)
            throw error(BusinessErrorCode.PRECONDITION_FAILED,
                    "客户名称重复，请手动选择客户后新建合同：" + row.getCustomerName());
        return matches.get(0).getCustomerId();
    }

    private ContractCreateCommand toCreate(BizContract row, Long customerId)
    {
        ContractCreateCommand command = new ContractCreateCommand();
        copy(row, customerId, command);
        return command;
    }

    private ContractUpdateCommand toUpdate(BizContract row, Long customerId)
    {
        ContractUpdateCommand command = new ContractUpdateCommand();
        copy(row, customerId, command);
        return command;
    }

    private void copy(BizContract row, Long customerId, ContractCreateCommand command)
    {
        command.setContractName(row.getContractName());
        command.setCustomerId(customerId);
        command.setCaseType(row.getCaseType());
        command.setLawyerId(row.getLawyerId());
        command.setLawyerName(row.getLawyerName());
        command.setOwnerId(row.getOwnerId());
        command.setDeptId(row.getDeptId());
        command.setSignAmount(row.getSignAmount());
        command.setFeeType(row.getFeeType());
        command.setSignDate(localDate(row.getSignDate()));
        command.setEffectiveDate(localDate(row.getEffectiveDate()));
        command.setExpireDate(localDate(row.getExpireDate()));
        command.setSignMethod(row.getSignMethod());
        command.setRiskLevel(row.getRiskLevel());
        command.setRemark(row.getRemark());
    }

    private java.time.LocalDate localDate(java.util.Date value)
    {
        return value == null ? null : new Date(value.getTime()).toLocalDate();
    }

    private ServiceException error(BusinessErrorCode code, String message)
    {
        return new ServiceException(message, code.name());
    }
}
