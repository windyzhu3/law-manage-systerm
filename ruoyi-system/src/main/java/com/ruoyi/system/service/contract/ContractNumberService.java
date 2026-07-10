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
        assertChanged(rows, "缂栧彿瑙勫垯涓嶅瓨鍦ㄦ垨宸插彉鍖栵紝璇峰埛鏂板悗閲嶈瘯");
        Long ruleId = toLong(rule.get("ruleId"), "缂栧彿瑙勫垯涓嶅瓨鍦?);
        if ("0".equals(String.valueOf(rule.get("status"))))
        {
            contractMapper.disableOtherNoRules(ruleId, SecurityUtils.getUsername());
        }
        else if (contractMapper.countOtherEnabledNoRules(ruleId) == 0)
        {
            throw new ServiceException("鑷冲皯闇€瑕佷繚鐣欎竴涓惎鐢ㄧ殑鍚堝悓缂栧彿瑙勫垯");
        }
        return rows;
    }

    @Transactional
    public String nextNumber()
    {
        Map<String, Object> rule = contractMapper.selectActiveNoRule();
        if (rule == null) throw new ServiceException("鏈厤缃惎鐢ㄧ殑鍚堝悓缂栧彿瑙勫垯");
        Long ruleId = toLong(rule.get("ruleId"), "缂栧彿瑙勫垯涓嶅瓨鍦?);
        String prefix = String.valueOf(rule.get("prefix"));
        String datePattern = String.valueOf(rule.get("datePattern"));
        Integer length = toInteger(rule.get("serialLength"), "缂栧彿娴佹按闀垮害蹇呴』涓烘暟瀛?);
        Integer serial = toInteger(rule.get("currentSerial"), "缂栧彿褰撳墠娴佹按蹇呴』涓烘暟瀛?) + 1;
        assertChanged(contractMapper.updateNoRule(ruleId, serial, SecurityUtils.getUsername()), "鍚堝悓缂栧彿瑙勫垯宸插彉鍖栵紝璇峰埛鏂板悗閲嶈瘯");
        return prefix + formatDatePattern(datePattern) + String.format("%0" + length + "d", serial);
    }

    private void validateRule(Map<String, Object> rule)
    {
        if (rule == null) throw new ServiceException("缂栧彿瑙勫垯涓嶅瓨鍦?);
        toLong(rule.get("ruleId"), "缂栧彿瑙勫垯涓嶅瓨鍦?);
        String prefix = requiredText(rule, "prefix", "缂栧彿瑙勫垯鍓嶇紑涓嶈兘涓虹┖");
        String datePattern = requiredText(rule, "datePattern", "缂栧彿瑙勫垯鏃ユ湡鏍煎紡涓嶈兘涓虹┖");
        Integer length = toInteger(rule.get("serialLength"), "缂栧彿娴佹按闀垮害蹇呴』涓烘暟瀛?);
        if (StringUtils.isEmpty(prefix)) throw new ServiceException("缂栧彿瑙勫垯鍓嶇紑涓嶈兘涓虹┖");
        if (length < 3 || length > 12) throw new ServiceException("缂栧彿娴佹按闀垮害蹇呴』鍦?鍒?2涔嬮棿");
        requiredText(rule, "status", "璇烽€夋嫨缂栧彿瑙勫垯鐘舵€?);
        assertDictValue("sys_normal_disable", rule.get("status"), "缂栧彿瑙勫垯鐘舵€佷笉鍚堟硶");
        formatDatePattern(datePattern);
    }

    private String formatDatePattern(String pattern)
    {
        try { return LocalDate.now().format(DateTimeFormatter.ofPattern(pattern)); }
        catch (IllegalArgumentException | DateTimeException e) { throw new ServiceException("缂栧彿瑙勫垯鏃ユ湡鏍煎紡涓嶅悎娉?); }
    }

    private void assertDictValue(String dictType, Object value, String message)
    {
        String expected = value == null ? null : String.valueOf(value).trim();
        List<SysDictData> options = dictTypeService.selectDictDataByType(dictType);
        if (options == null || options.isEmpty()) throw new ServiceException("瀛楀吀鏈垵濮嬪寲锛? + dictType);
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

