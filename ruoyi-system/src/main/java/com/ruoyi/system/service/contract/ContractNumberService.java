package com.ruoyi.system.service.contract;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.business.contract.dto.ContractNumberRuleUpdateCommand;
import com.law.business.security.BusinessActor;
import com.law.business.security.BusinessActorProvider;
import com.law.business.shared.error.BusinessErrorCode;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.mapper.BizContractMapper;
import com.ruoyi.system.service.ISysDictTypeService;

@Service
public class ContractNumberService
{
    private static final String ENABLED = "0";
    private static final String DISABLED = "1";

    private final BizContractMapper mapper;
    private final ISysDictTypeService dictionaries;
    private final BusinessActorProvider actors;

    public ContractNumberService(BizContractMapper mapper, ISysDictTypeService dictionaries,
            BusinessActorProvider actors)
    {
        this.mapper = mapper;
        this.dictionaries = dictionaries;
        this.actors = actors;
    }

    public List<Map<String, Object>> selectRules() { return mapper.selectRules(); }

    @Transactional
    public int updateRule(ContractNumberRuleUpdateCommand command)
    {
        validate(command);
        if (DISABLED.equals(command.getStatus()) && mapper.countOtherEnabledNoRules(command.getRuleId()) == 0)
            throw error(BusinessErrorCode.PRECONDITION_FAILED, "至少需要保留一个启用的合同编号规则");
        BusinessActor actor = actors.current();
        Map<String, Object> row = toMap(command);
        row.put("updateBy", actor.userName());
        int rows = mapper.updateRule(row);
        changed(rows, "编号规则不存在或已变化，请刷新后重试");
        if (ENABLED.equals(command.getStatus()))
            mapper.disableOtherNoRules(command.getRuleId(), actor.userName());
        return rows;
    }

    public int updateRule(Map<String, Object> value) { return updateRule(toCommand(value)); }

    @Transactional
    public String nextNumber()
    {
        Map<String, Object> rule = mapper.selectActiveNoRule();
        if (rule == null) throw error(BusinessErrorCode.PRECONDITION_FAILED, "未配置启用的合同编号规则");
        Long ruleId = longValue(rule.get("ruleId"), "编号规则不存在");
        String prefix = text(rule.get("prefix"));
        String pattern = text(rule.get("datePattern"));
        Integer length = integer(rule.get("serialLength"), "编号流水长度必须为数字");
        Integer serial = integer(rule.get("currentSerial"), "编号当前流水必须为数字") + 1;
        changed(mapper.updateNoRule(ruleId, serial, actors.current().userName()),
                "合同编号规则已变化，请刷新后重试");
        return prefix + format(pattern) + String.format("%0" + length + "d", serial);
    }

    private void validate(ContractNumberRuleUpdateCommand command)
    {
        if (command == null || command.getRuleId() == null)
            throw error(BusinessErrorCode.VALIDATION_FAILED, "请选择编号规则");
        required(command.getRuleName(), "规则名称不能为空");
        required(command.getPrefix(), "编号规则前缀不能为空");
        required(command.getDatePattern(), "编号规则日期格式不能为空");
        if (command.getSerialLength() == null || command.getSerialLength() < 3 || command.getSerialLength() > 12)
            throw error(BusinessErrorCode.VALIDATION_FAILED, "编号流水长度必须在3到12之间");
        dict("sys_normal_disable", command.getStatus(), "编号规则状态不合法");
        format(command.getDatePattern());
    }

    private Map<String, Object> toMap(ContractNumberRuleUpdateCommand command)
    {
        Map<String, Object> row = new HashMap<>();
        row.put("ruleId", command.getRuleId());
        row.put("ruleName", command.getRuleName().trim());
        row.put("prefix", command.getPrefix().trim());
        row.put("datePattern", command.getDatePattern().trim());
        row.put("serialLength", command.getSerialLength());
        row.put("status", command.getStatus());
        row.put("remark", clean(command.getRemark()));
        return row;
    }

    private ContractNumberRuleUpdateCommand toCommand(Map<String, Object> value)
    {
        if (value == null) return null;
        ContractNumberRuleUpdateCommand command = new ContractNumberRuleUpdateCommand();
        command.setRuleId(longValue(value.get("ruleId"), "请选择编号规则"));
        command.setRuleName(text(value.get("ruleName")));
        command.setPrefix(text(value.get("prefix")));
        command.setDatePattern(text(value.get("datePattern")));
        command.setSerialLength(integer(value.get("serialLength"), "编号流水长度必须为数字"));
        command.setStatus(text(value.get("status")));
        command.setRemark(text(value.get("remark")));
        return command;
    }

    private String format(String pattern)
    {
        try { return LocalDate.now().format(DateTimeFormatter.ofPattern(pattern)); }
        catch (RuntimeException exception)
        {
            throw error(BusinessErrorCode.VALIDATION_FAILED, "编号规则日期格式不合法");
        }
    }

    private void dict(String type, String value, String message)
    {
        List<SysDictData> options = dictionaries.selectDictDataByType(type);
        if (options == null || options.isEmpty())
            throw error(BusinessErrorCode.PRECONDITION_FAILED, "字典未初始化：" + type);
        for (SysDictData option : options) if (value != null && value.equals(option.getDictValue())) return;
        throw error(BusinessErrorCode.VALIDATION_FAILED, message);
    }

    private void required(String value, String message)
    {
        if (StringUtils.isEmpty(value)) throw error(BusinessErrorCode.VALIDATION_FAILED, message);
    }
    private Long longValue(Object value, String message)
    {
        try { return Long.valueOf(String.valueOf(value)); }
        catch (RuntimeException exception) { throw error(BusinessErrorCode.VALIDATION_FAILED, message); }
    }
    private Integer integer(Object value, String message)
    {
        try { return Integer.valueOf(String.valueOf(value)); }
        catch (RuntimeException exception) { throw error(BusinessErrorCode.VALIDATION_FAILED, message); }
    }
    private String text(Object value) { return value == null ? null : String.valueOf(value); }
    private String clean(String value) { return StringUtils.isEmpty(value) ? null : value.trim(); }
    private void changed(int rows, String message)
    {
        if (rows <= 0) throw error(BusinessErrorCode.CONCURRENT_MODIFICATION, message);
    }
    private ServiceException error(BusinessErrorCode code, String message)
    {
        return new ServiceException(message, code.name());
    }
}
