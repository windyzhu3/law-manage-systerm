package com.ruoyi.system.service.contract;

import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.business.contract.dto.ContractAttachmentCreateCommand;
import com.law.business.security.BusinessActor;
import com.law.business.security.BusinessActorProvider;
import com.law.business.shared.error.BusinessErrorCode;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.BizContract;
import com.ruoyi.system.mapper.BizContractMapper;

@Service
public class ContractAttachmentService
{
    private final BizContractMapper mapper;
    private final ContractAccessPolicy access;
    private final BusinessActorProvider actors;
    private final ContractActionLogService actionLogs;

    public ContractAttachmentService(BizContractMapper mapper, ContractAccessPolicy access,
            BusinessActorProvider actors, ContractActionLogService actionLogs)
    {
        this.mapper = mapper;
        this.access = access;
        this.actors = actors;
        this.actionLogs = actionLogs;
    }

    @Transactional
    public int create(ContractAttachmentCreateCommand command)
    {
        validate(command);
        access.requireOperable(command.getContractId());
        BusinessActor actor = actors.current();
        Map<String, Object> row = toMap(command);
        row.put("createBy", actor.userName());
        int rows = mapper.insertAttachment(row);
        changed(rows, "附件创建失败");
        actionLogs.record(command.getContractId(), null, null, "attachment_add",
                "新增附件: " + command.getFileName(), actor);
        return rows;
    }

    public int create(Map<String, Object> value) { return create(toCommand(value)); }

    @Transactional
    public int delete(Long attachmentId)
    {
        Long contractId = access.requireAttachmentOperable(attachmentId);
        BizContract contract = access.requireOperable(contractId);
        BusinessActor actor = actors.current();
        int rows = mapper.deleteAttachment(attachmentId, contractId, contract.getContractStatus());
        changed(rows, "附件已变化，请刷新后重试");
        actionLogs.record(contractId, null, null, "attachment_delete", "删除附件", actor);
        return rows;
    }

    private void validate(ContractAttachmentCreateCommand command)
    {
        if (command == null || command.getContractId() == null)
            throw error(BusinessErrorCode.VALIDATION_FAILED, "请选择合同");
        required(command.getFileName(), "请填写附件名称");
        required(command.getFileUrl(), "请上传附件");
        required(command.getFileType(), "请选择文件类型");
        if (command.getFileSize() != null && command.getFileSize() < 0)
            throw error(BusinessErrorCode.VALIDATION_FAILED, "文件大小不能小于0");
    }

    private Map<String, Object> toMap(ContractAttachmentCreateCommand command)
    {
        Map<String, Object> row = new HashMap<>();
        row.put("contractId", command.getContractId());
        row.put("fileName", command.getFileName().trim());
        row.put("fileUrl", command.getFileUrl().trim());
        row.put("fileType", command.getFileType().trim());
        row.put("fileSize", command.getFileSize());
        row.put("remark", clean(command.getRemark()));
        return row;
    }

    private ContractAttachmentCreateCommand toCommand(Map<String, Object> value)
    {
        if (value == null) return null;
        ContractAttachmentCreateCommand command = new ContractAttachmentCreateCommand();
        command.setContractId(longValue(value.get("contractId")));
        command.setFileName(text(value.get("fileName")));
        command.setFileUrl(text(value.get("fileUrl")));
        Object fileType = value.containsKey("fileType") ? value.get("fileType") : value.get("attachmentType");
        command.setFileType(text(fileType));
        command.setFileSize(longValue(value.get("fileSize")));
        command.setRemark(text(value.get("remark")));
        return command;
    }

    private void required(String value, String message)
    {
        if (StringUtils.isEmpty(value)) throw error(BusinessErrorCode.VALIDATION_FAILED, message);
    }
    private Long longValue(Object value)
    {
        if (value == null || StringUtils.isEmpty(String.valueOf(value))) return null;
        try { return Long.valueOf(String.valueOf(value)); }
        catch (NumberFormatException exception) { throw error(BusinessErrorCode.VALIDATION_FAILED, "编号必须为数字"); }
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
