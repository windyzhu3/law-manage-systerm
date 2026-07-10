package com.ruoyi.system.service.contract;

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

/** Maintains reusable contract templates independently from contract lifecycle. */
@Service
public class ContractTemplateService
{
    private final BizContractMapper contractMapper;
    private final ISysDictTypeService dictTypeService;

    public ContractTemplateService(BizContractMapper contractMapper, ISysDictTypeService dictTypeService)
    {
        this.contractMapper = contractMapper;
        this.dictTypeService = dictTypeService;
    }

    public List<Map<String, Object>> select(Map<String, Object> params)
    {
        return contractMapper.selectTemplates(params);
    }

    @Transactional
    public int create(Map<String, Object> template)
    {
        validate(template);
        template.put("createBy", SecurityUtils.getUsername());
        return assertChanged(contractMapper.insertTemplate(template), "鍚堝悓妯℃澘鍒涘缓澶辫触");
    }

    @Transactional
    public int update(Map<String, Object> template)
    {
        requireId(template == null ? null : template.get("templateId"));
        validate(template);
        template.put("updateBy", SecurityUtils.getUsername());
        return assertChanged(contractMapper.updateTemplate(template), "鍚堝悓妯℃澘宸插彉鍖栵紝璇峰埛鏂板悗閲嶈瘯");
    }

    @Transactional
    public int delete(Long templateId)
    {
        requireId(templateId);
        Map<String, Object> template = contractMapper.selectTemplateById(templateId);
        if (template == null) throw new ServiceException("鍚堝悓妯℃澘涓嶅瓨鍦?);
        if ("0".equals(String.valueOf(template.get("status"))))
        {
            throw new ServiceException("鍚敤涓殑鍚堝悓妯℃澘涓嶅厑璁稿垹闄わ紝璇峰厛鍋滅敤");
        }
        return assertChanged(contractMapper.deleteTemplate(templateId), "鍚堝悓妯℃澘宸插彉鍖栵紝璇峰埛鏂板悗閲嶈瘯");
    }

    private void validate(Map<String, Object> template)
    {
        requiredText(template, "templateName", "璇疯緭鍏ユā鏉垮悕绉?);
        requiredText(template, "caseType", "璇烽€夋嫨妗堜欢绫诲瀷");
        requiredText(template, "fileUrl", "璇蜂笂浼犳ā鏉挎枃浠?);
        assertDictValue("law_contract_case_type", template.get("caseType"), "妯℃澘妗堜欢绫诲瀷涓嶅悎娉?);
        if (isEmpty(template.get("status"))) template.put("status", "0");
        assertDictValue("sys_normal_disable", template.get("status"), "妯℃澘鐘舵€佷笉鍚堟硶");
        if (isEmpty(template.get("versionNo"))) template.put("versionNo", "v1");
    }

    private void assertDictValue(String dictType, Object value, String message)
    {
        String expected = value == null ? null : String.valueOf(value).trim();
        if (StringUtils.isEmpty(expected)) return;
        List<SysDictData> options = dictTypeService.selectDictDataByType(dictType);
        if (options == null || options.isEmpty()) throw new ServiceException("瀛楀吀鏈垵濮嬪寲锛? + dictType);
        for (SysDictData option : options) if (expected.equals(option.getDictValue())) return;
        throw new ServiceException(message);
    }

    private String requiredText(Map<String, Object> source, String key, String message)
    {
        Object value = source == null ? null : source.get(key);
        if (isEmpty(value)) throw new ServiceException(message);
        return String.valueOf(value).trim();
    }

    private void requireId(Object value)
    {
        if (value == null || StringUtils.isEmpty(String.valueOf(value))) throw new ServiceException("璇烽€夋嫨鍚堝悓妯℃澘");
        try { Long.valueOf(String.valueOf(value)); }
        catch (NumberFormatException e) { throw new ServiceException("鍚堝悓妯℃澘缂栧彿涓嶅悎娉?); }
    }

    private boolean isEmpty(Object value)
    {
        return value == null || StringUtils.isEmpty(String.valueOf(value)) || "null".equalsIgnoreCase(String.valueOf(value));
    }

    private int assertChanged(int rows, String message)
    {
        if (rows <= 0) throw new ServiceException(message);
        return rows;
    }
}

