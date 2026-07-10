package com.ruoyi.system.service.contract;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.mapper.BizContractMapper;
import com.ruoyi.system.service.ISysDictTypeService;

/** Owns contract number rule configuration and atomic serial allocation. */
@Service
public class ContractNumberService
{
    private final BizContractMapper contractMapper;
    private final ISysDictTypeService dictTypeService;

    public ContractNumberService(BizContractMapper contractMapper, ISysDictTypeService dictTypeService)
    {
        this.contractMapper = contractMapper;
        this.dictTypeService = dictTypeService;
    }

    public List<Map<String, Object>> selectRules()
    {
        return contractMapper.selectRules();
    }

    @Transactional
    public int updateRule(Map<String, Object> rule)
    {
        validateRule(rule);
        rule.put("updateBy", SecurityUtils.getUsername());
        int rows = contractMapper.updateRule(rule);
        assertChanged(rows, "编号规则不存在或已变化，请刷新后重试");
        Long ruleId = toLong(rule.get("ruleId"), "编号规则不存在");
        if ("0".equals(String.valueOf(rule.get("status"))))
        {
            contractMapper.disableOtherNoRules(ruleId, SecurityUtils.getUsername());
        }
        else if (contractMapper.countOtherEnabledNoRules(ruleId) == 0)
        {
            throw new ServiceException("至少需要保留一个启用的合同编号规则");
        }
        return rows;
    }

    @Transactional
    public String nextNumber()
    {
        Map<String, Object> rule = contractMapper.selectActiveNoRule();
        if (rule == null) throw new ServiceException("未配置启用的合同编号规则");
        Long ruleId = toLong(rule.get("ruleId"), "编号规则不存在");
        String prefix = String.valueOf(rule.get("prefix"));
        String datePattern = String.valueOf(rule.get("datePattern"));
        Integer length = toInteger(rule.get("serialLength"), "编号流水长度必须为数字");
        Integer serial = toInteger(rule.get("currentSerial"), "编号当前流水必须为数字") + 1;
        assertChanged(contractMapper.updateNoRule(ruleId, serial, SecurityUtils.getUsername()), "合同编号规则已变化，请刷新后重试");
        return prefix + formatDatePattern(datePattern) + String.format("%0" + length + "d", serial);
    }

    private void validateRule(Map<String, Object> rule)
    {
        if (rule == null) throw new ServiceException("编号规则不存在");
        toLong(rule.get("ruleId"), "编号规则不存在");
        String prefix = requiredText(rule, "prefix", "编号规则前缀不能为空");
        String datePattern = requiredText(rule, "datePattern", "编号规则日期格式不能为空");
        Integer length = toInteger(rule.get("serialLength"), "编号流水长度必须为数字");
        if (StringUtils.isEmpty(prefix)) throw new ServiceException("编号规则前缀不能为空");
        if (length < 3 || length > 12) throw new ServiceException("编号流水长度必须在3到12之间");
        requiredText(rule, "status", "请选择编号规则状态");
        assertDictValue("sys_normal_disable", rule.get("status"), "编号规则状态不合法");
        formatDatePattern(datePattern);
    }

    private String formatDatePattern(String pattern)
    {
        try { return LocalDate.now().format(DateTimeFormatter.ofPattern(pattern)); }
        catch (IllegalArgumentException | DateTimeException e) { throw new ServiceException("编号规则日期格式不合法"); }
    }

    private void assertDictValue(String dictType, Object value, String message)
    {
        String expected = value == null ? null : String.valueOf(value).trim();
        List<SysDictData> options = dictTypeService.selectDictDataByType(dictType);
        if (options == null || options.isEmpty()) throw new ServiceException("字典未初始化：" + dictType);
        for (SysDictData option : options) if (expected.equals(option.getDictValue())) return;
        throw new ServiceException(message);
    }

    private String requiredText(Map<String, Object> source, String key, String message)
    {
        Object value = source == null ? null : source.get(key);
        if (value == null || StringUtils.isEmpty(String.valueOf(value)) || "null".equalsIgnoreCase(String.valueOf(value)))
            throw new ServiceException(message);
        return String.valueOf(value).trim();
    }

    private Long toLong(Object value, String message)
    {
        try { return Long.valueOf(String.valueOf(value)); }
        catch (RuntimeException e) { throw new ServiceException(message); }
    }

    private Integer toInteger(Object value, String message)
    {
        try { return Integer.valueOf(String.valueOf(value)); }
        catch (RuntimeException e) { throw new ServiceException(message); }
    }

    private void assertChanged(int rows, String message)
    {
        if (rows <= 0) throw new ServiceException(message);
    }
}

