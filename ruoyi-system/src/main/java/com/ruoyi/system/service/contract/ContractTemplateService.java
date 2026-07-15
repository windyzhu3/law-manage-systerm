package com.ruoyi.system.service.contract;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.business.contract.dto.ContractTemplateCreateCommand;
import com.law.business.contract.dto.ContractTemplateUpdateCommand;
import com.law.business.security.BusinessActorProvider;
import com.law.business.shared.error.BusinessErrorCode;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.mapper.BizContractMapper;
import com.ruoyi.system.service.ISysDictTypeService;

@Service
public class ContractTemplateService
{
    private static final String ENABLED = "0";

    private final BizContractMapper mapper;
    private final ISysDictTypeService dictionaries;
    private final BusinessActorProvider actors;

    public ContractTemplateService(BizContractMapper mapper, ISysDictTypeService dictionaries,
            BusinessActorProvider actors)
    {
        this.mapper = mapper;
        this.dictionaries = dictionaries;
        this.actors = actors;
    }

    public List<Map<String, Object>> select(Map<String, Object> params)
    {
        return mapper.selectTemplates(params);
    }

    @Transactional
    public int create(ContractTemplateCreateCommand command)
    {
        validate(command);
        Map<String, Object> row = toMap(command);
        row.put("createBy", actors.current().userName());
        return changed(mapper.insertTemplate(row), "合同模板创建失败");
    }

    @Transactional
    public int update(ContractTemplateUpdateCommand command)
    {
        if (command == null || command.getTemplateId() == null)
            throw error(BusinessErrorCode.VALIDATION_FAILED, "请选择合同模板");
        validate(command);
        Map<String, Object> row = toMap(command);
        row.put("templateId", command.getTemplateId());
        row.put("updateBy", actors.current().userName());
        return changed(mapper.updateTemplate(row), "合同模板已变化，请刷新后重试");
    }

    public int create(Map<String, Object> value) { return create(toCreate(value)); }
    public int update(Map<String, Object> value) { return update(toUpdate(value)); }

    @Transactional
    public int delete(Long templateId)
    {
        if (templateId == null) throw error(BusinessErrorCode.VALIDATION_FAILED, "请选择合同模板");
        Map<String, Object> template = mapper.selectTemplateById(templateId);
        if (template == null) throw error(BusinessErrorCode.DATA_NOT_FOUND, "合同模板不存在");
        if (ENABLED.equals(String.valueOf(template.get("status"))))
            throw error(BusinessErrorCode.STATE_CONFLICT, "启用中的合同模板不允许删除，请先停用");
        return changed(mapper.deleteTemplate(templateId), "合同模板已变化，请刷新后重试");
    }

    private void validate(ContractTemplateCreateCommand command)
    {
        if (command == null) throw error(BusinessErrorCode.VALIDATION_FAILED, "合同模板不能为空");
        required(command.getTemplateName(), "请输入模板名称");
        required(command.getCaseType(), "请选择案件类型");
        required(command.getFileName(), "请输入文件名称");
        required(command.getFileUrl(), "请上传模板文件");
        dict("law_contract_case_type", command.getCaseType(), "模板案件类型不合法");
        String status = StringUtils.isEmpty(command.getStatus()) ? ENABLED : command.getStatus();
        dict("sys_normal_disable", status, "模板状态不合法");
    }

    private Map<String, Object> toMap(ContractTemplateCreateCommand command)
    {
        Map<String, Object> row = new HashMap<>();
        row.put("templateName", command.getTemplateName().trim());
        row.put("caseType", command.getCaseType().trim());
        row.put("fileName", command.getFileName().trim());
        row.put("fileUrl", command.getFileUrl().trim());
        row.put("versionNo", StringUtils.isEmpty(command.getVersionNo()) ? "v1" : command.getVersionNo().trim());
        row.put("status", StringUtils.isEmpty(command.getStatus()) ? ENABLED : command.getStatus());
        row.put("remark", clean(command.getRemark()));
        return row;
    }

    private ContractTemplateCreateCommand toCreate(Map<String, Object> value)
    {
        if (value == null) return null;
        ContractTemplateCreateCommand command = new ContractTemplateCreateCommand();
        copy(value, command);
        return command;
    }

    private ContractTemplateUpdateCommand toUpdate(Map<String, Object> value)
    {
        if (value == null) return null;
        ContractTemplateUpdateCommand command = new ContractTemplateUpdateCommand();
        command.setTemplateId(longValue(value.get("templateId")));
        copy(value, command);
        return command;
    }

    private void copy(Map<String, Object> value, ContractTemplateCreateCommand command)
    {
        command.setTemplateName(text(value.get("templateName")));
        command.setCaseType(text(value.get("caseType")));
        command.setFileName(text(value.get("fileName")));
        command.setFileUrl(text(value.get("fileUrl")));
        command.setVersionNo(text(value.get("versionNo")));
        command.setStatus(text(value.get("status")));
        command.setRemark(text(value.get("remark")));
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
    private Long longValue(Object value)
    {
        try { return Long.valueOf(String.valueOf(value)); }
        catch (RuntimeException exception) { throw error(BusinessErrorCode.VALIDATION_FAILED, "合同模板编号不合法"); }
    }
    private String text(Object value) { return value == null ? null : String.valueOf(value); }
    private String clean(String value) { return StringUtils.isEmpty(value) ? null : value.trim(); }
    private int changed(int rows, String message)
    {
        if (rows <= 0) throw error(BusinessErrorCode.CONCURRENT_MODIFICATION, message);
        return rows;
    }
    private ServiceException error(BusinessErrorCode code, String message)
    {
        return new ServiceException(message, code.name());
    }
}
