package com.ruoyi.system.service.contract;

import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.business.security.ContractPermissions;
import com.law.business.shared.status.ContractStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.BizContract;
import com.ruoyi.system.mapper.BizContractMapper;

@Service
public class ContractAttachmentService
{
    private final BizContractMapper mapper;
    private final ContractActionLogService actionLogs;

    public ContractAttachmentService(BizContractMapper mapper, ContractActionLogService actionLogs)
    {
        this.mapper = mapper;
        this.actionLogs = actionLogs;
    }

    @Transactional
    public int create(Map<String, Object> attachment)
    {
        Long contractId = toLong(attachment == null ? null : attachment.get("contractId"));
        BizContract contract = requireAccessible(contractId);
        requireEditable(contract);
        String fileName = requiredText(attachment, "fileName", "请填写附件名称");
        requiredText(attachment, "fileUrl", "请上传附件");
        attachment.put("createBy", SecurityUtils.getUsername());
        int rows = mapper.insertAttachment(attachment);
        assertChanged(rows, "附件创建失败");
        log(contractId, "attachment_add", "新增附件: " + fileName);
        return rows;
    }

    @Transactional
    public int delete(Long attachmentId)
    {
        if (attachmentId == null) throw new ServiceException("请选择附件");
        Long contractId = mapper.selectAttachmentContractId(attachmentId);
        if (contractId == null) throw new ServiceException("附件不存在");
        BizContract contract = requireAccessible(contractId);
        requireEditable(contract);
        int rows = mapper.deleteAttachment(attachmentId, contractId);
        assertChanged(rows, "附件已变化，请刷新后重试");
        log(contractId, "attachment_delete", "删除附件");
        return rows;
    }

    private BizContract requireAccessible(Long contractId)
    {
        BizContract contract = mapper.selectContractById(contractId);
        if (contract == null || "2".equals(contract.getDelFlag())) throw new ServiceException("合同不存在或已删除");
        if (!SecurityUtils.isAdmin() && mapper.countContractInDataScope(contractId, SecurityUtils.getUserId(),
                SecurityUtils.getDeptId(), ContractPermissions.DATA_SCOPE) == 0) throw new ServiceException("无权访问该合同");
        return contract;
    }

    private void requireEditable(BizContract contract)
    {
        String status = contract.getContractStatus();
        if (ContractStatus.ARCHIVED.code().equals(status) || ContractStatus.VOID.code().equals(status)
                || ContractStatus.TERMINATED.code().equals(status))
            throw new ServiceException("归档、作废或终止的合同不允许维护附件");
    }

    private void log(Long contractId, String action, String content)
    {
        actionLogs.record(contractId, null, null, action, content, SecurityUtils.getUsername());
    }

    private String requiredText(Map<String, Object> source, String key, String message)
    {
        Object value = source == null ? null : source.get(key);
        if (value == null || StringUtils.isEmpty(String.valueOf(value)) || "null".equalsIgnoreCase(String.valueOf(value)))
            throw new ServiceException(message);
        return String.valueOf(value).trim();
    }

    private Long toLong(Object value)
    {
        try { return Long.valueOf(String.valueOf(value)); }
        catch (RuntimeException e) { throw new ServiceException("请选择合同"); }
    }

    private void assertChanged(int rows, String message)
    {
        if (rows <= 0) throw new ServiceException(message);
    }
}
