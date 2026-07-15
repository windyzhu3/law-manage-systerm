package com.ruoyi.system.service.contract;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.business.contract.dto.ContractCreateCommand;
import com.law.business.contract.dto.ContractUpdateCommand;
import com.law.business.security.BusinessActor;
import com.law.business.security.BusinessActorProvider;
import com.law.business.shared.error.BusinessErrorCode;
import com.law.business.shared.status.ContractAuditStatus;
import com.law.business.shared.status.ContractSignStatus;
import com.law.business.shared.status.ContractStatus;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.BizContract;
import com.ruoyi.system.domain.BizCustomer;
import com.ruoyi.system.mapper.BizContractMapper;
import com.ruoyi.system.service.ISysDictTypeService;
import com.ruoyi.system.service.customer.CustomerAccessPolicy;

@Service
public class ContractCommandService
{
    private static final String ACTIVE = "0";

    private final BizContractMapper mapper;
    private final CustomerAccessPolicy customers;
    private final ContractAccessPolicy access;
    private final ContractNumberService numberService;
    private final ISysDictTypeService dictionaries;
    private final BusinessActorProvider actors;
    private final ContractActionLogService actionLogs;

    public ContractCommandService(BizContractMapper mapper, CustomerAccessPolicy customers,
            ContractAccessPolicy access, ContractNumberService numberService,
            ISysDictTypeService dictionaries, BusinessActorProvider actors,
            ContractActionLogService actionLogs)
    {
        this.mapper = mapper;
        this.customers = customers;
        this.access = access;
        this.numberService = numberService;
        this.dictionaries = dictionaries;
        this.actors = actors;
        this.actionLogs = actionLogs;
    }

    @Transactional
    public int create(ContractCreateCommand command)
    {
        requireCreateCommand(command);
        BusinessActor actor = actors.current();
        BizCustomer customer = customers.requireOperable(command.getCustomerId());
        BizContract contract = map(command);
        contract.setCustomerName(customer.getCustomerName());
        contract.setContractNo(numberService.nextNumber());
        if (contract.getOwnerId() == null)
        {
            contract.setOwnerId(actor.userId());
            contract.setDeptId(actor.deptId());
        }
        contract.setFeeType(defaultValue("law_contract_fee_type", contract.getFeeType(), "once"));
        contract.setSignMethod(defaultValue("law_contract_sign_method", contract.getSignMethod(), "online"));
        contract.setRiskLevel(defaultValue("law_contract_risk_level", contract.getRiskLevel(), "1"));
        validate(contract);
        contract.setSignStatus(ContractSignStatus.UNSIGNED.code());
        contract.setAuditStatus(ContractAuditStatus.PENDING.code());
        contract.setContractStatus(ContractStatus.DRAFT.code());
        contract.setDelFlag(ACTIVE);
        contract.setCreateBy(actor.userName());
        changed(mapper.insertContract(contract), "合同创建失败");
        actionLogs.record(contract.getContractId(), null, ContractStatus.DRAFT.code(),
                "create", "创建合同", actor);
        return 1;
    }

    @Transactional
    public int update(ContractUpdateCommand command)
    {
        if (command == null || command.getContractId() == null)
            throw error(BusinessErrorCode.VALIDATION_FAILED, "请选择合同");
        BizContract current = access.requireOperable(command.getContractId());
        requireEditable(current);
        BizCustomer customer = customers.requireOperable(command.getCustomerId());
        BusinessActor actor = actors.current();
        BizContract contract = map(command);
        contract.setContractId(command.getContractId());
        contract.setCustomerName(customer.getCustomerName());
        validate(contract);
        contract.setUpdateBy(actor.userName());
        int rows = mapper.updateContractConditionally(contract, current.getAuditStatus(),
                current.getContractStatus(), current.getDelFlag());
        changed(rows, "合同状态已变化，请刷新后重试");
        return rows;
    }

    @Transactional
    public int delete(Long[] contractIds)
    {
        if (contractIds == null || contractIds.length == 0)
            throw error(BusinessErrorCode.VALIDATION_FAILED, "请选择合同");
        BusinessActor actor = actors.current();
        for (Long contractId : contractIds)
        {
            BizContract current = access.requireOperable(contractId);
            requireEditable(current);
            changed(mapper.deleteContractConditionally(contractId, current.getAuditStatus(),
                    current.getContractStatus(), actor.userName()), "合同状态已变化，请刷新后重试");
        }
        return contractIds.length;
    }

    public int create(BizContract contract)
    {
        return create(toCreateCommand(contract));
    }

    public int update(BizContract contract)
    {
        ContractUpdateCommand command = toUpdateCommand(contract);
        return update(command);
    }

    private BizContract map(ContractCreateCommand command)
    {
        BizContract contract = new BizContract();
        contract.setContractName(trim(command.getContractName()));
        contract.setCustomerId(command.getCustomerId());
        contract.setCaseType(trim(command.getCaseType()));
        contract.setLawyerId(command.getLawyerId());
        contract.setLawyerName(trim(command.getLawyerName()));
        contract.setOwnerId(command.getOwnerId());
        contract.setDeptId(command.getDeptId());
        contract.setSignAmount(command.getSignAmount());
        contract.setFeeType(trim(command.getFeeType()));
        contract.setSignDate(date(command.getSignDate()));
        contract.setEffectiveDate(date(command.getEffectiveDate()));
        contract.setExpireDate(date(command.getExpireDate()));
        contract.setSignMethod(trim(command.getSignMethod()));
        contract.setRiskLevel(trim(command.getRiskLevel()));
        contract.setRemark(trim(command.getRemark()));
        return contract;
    }

    private void validate(BizContract contract)
    {
        required(contract.getContractName(), "合同名称不能为空");
        required(contract.getCaseType(), "案件类型不能为空");
        if (contract.getSignAmount() == null || contract.getSignAmount().compareTo(BigDecimal.ZERO) <= 0)
            throw error(BusinessErrorCode.VALIDATION_FAILED, "签约金额必须大于0");
        dict("law_contract_case_type", contract.getCaseType(), "案件类型不合法");
        dict("law_contract_fee_type", contract.getFeeType(), "收费方式不合法");
        dict("law_contract_sign_method", contract.getSignMethod(), "签订方式不合法");
        dict("law_contract_risk_level", contract.getRiskLevel(), "风险等级不合法");
    }

    private void requireCreateCommand(ContractCreateCommand command)
    {
        if (command == null || command.getCustomerId() == null)
            throw error(BusinessErrorCode.VALIDATION_FAILED, "合同和客户不能为空");
    }

    private void requireEditable(BizContract contract)
    {
        if (ContractAuditStatus.REVIEWING.code().equals(contract.getAuditStatus())
                || ContractAuditStatus.PASSED.code().equals(contract.getAuditStatus())
                || !ContractStatus.DRAFT.code().equals(contract.getContractStatus()))
            throw error(BusinessErrorCode.STATE_CONFLICT, "当前合同状态不允许编辑或删除");
    }

    private String defaultValue(String type, String value, String fallback)
    {
        if (!StringUtils.isEmpty(value)) return value;
        List<SysDictData> options = dictionaries.selectDictDataByType(type);
        if (options != null)
        {
            for (SysDictData option : options) if (option.getDefault()) return option.getDictValue();
            for (SysDictData option : options) if (fallback.equals(option.getDictValue())) return fallback;
        }
        return fallback;
    }

    private void dict(String type, String value, String message)
    {
        List<SysDictData> options = dictionaries.selectDictDataByType(type);
        if (options == null || options.isEmpty())
            throw error(BusinessErrorCode.PRECONDITION_FAILED, "字典未初始化：" + type);
        for (SysDictData option : options) if (value != null && value.equals(option.getDictValue())) return;
        throw error(BusinessErrorCode.VALIDATION_FAILED, message);
    }

    private ContractCreateCommand toCreateCommand(BizContract value)
    {
        if (value == null) return null;
        ContractCreateCommand command = new ContractCreateCommand();
        copy(value, command);
        return command;
    }

    private ContractUpdateCommand toUpdateCommand(BizContract value)
    {
        if (value == null) return null;
        ContractUpdateCommand command = new ContractUpdateCommand();
        command.setContractId(value.getContractId());
        copy(value, command);
        return command;
    }

    private void copy(BizContract source, ContractCreateCommand target)
    {
        target.setContractName(source.getContractName());
        target.setCustomerId(source.getCustomerId());
        target.setCaseType(source.getCaseType());
        target.setLawyerId(source.getLawyerId());
        target.setLawyerName(source.getLawyerName());
        target.setOwnerId(source.getOwnerId());
        target.setDeptId(source.getDeptId());
        target.setSignAmount(source.getSignAmount());
        target.setFeeType(source.getFeeType());
        target.setSignDate(localDate(source.getSignDate()));
        target.setEffectiveDate(localDate(source.getEffectiveDate()));
        target.setExpireDate(localDate(source.getExpireDate()));
        target.setSignMethod(source.getSignMethod());
        target.setRiskLevel(source.getRiskLevel());
        target.setRemark(source.getRemark());
    }

    private Date date(LocalDate value) { return value == null ? null : Date.valueOf(value); }
    private LocalDate localDate(java.util.Date value)
    {
        return value == null ? null : new Date(value.getTime()).toLocalDate();
    }
    private String trim(String value) { return StringUtils.isEmpty(value) ? null : value.trim(); }
    private void required(String value, String message)
    {
        if (StringUtils.isEmpty(value)) throw error(BusinessErrorCode.VALIDATION_FAILED, message);
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
