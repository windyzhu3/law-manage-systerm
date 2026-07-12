package com.ruoyi.system.service.casecenter;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.business.event.BusinessEventCommand;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.event.BusinessEventType;
import com.law.business.shared.status.CaseStatus;
import com.law.business.security.BusinessActor;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.uuid.IdUtils;
import com.ruoyi.system.mapper.BizCaseMapper;

/** Handles lawyer acceptance or rejection of an assigned case. */
@Service
public class CaseConfirmationService
{
    @Autowired private BizCaseMapper mapper;
    @Autowired private CaseQueryService queryService;
    @Autowired private CaseWorkflowSupport support;
    @Autowired private BusinessEventPublisher publisher;

    @Transactional
    public int handle(Map<String, Object> command)
    {return handle(command,currentActor());}

    @Transactional
    public int handle(Map<String,Object> source,BusinessActor actor)
    {
        Map<String,Object> command=new HashMap<>(source);
        Long confirmId = id(command.get("confirmId"), "请选择确认信息");
        String result = text(command.get("confirmResult"));
        if (!"accepted".equals(result) && !"rejected".equals(result))
            throw error("VALIDATION_FAILED", "确认结果不合法");
        Map<String, Object> entity = mapper.selectConfirmById(confirmId);
        if (entity == null) throw error("DATA_NOT_FOUND", "确认信息不存在");
        Long caseId = id(entity.get("case_id"), "请选择案件");
        if(mapper.selectCaseById(caseId)==null)throw error("DATA_NOT_FOUND","案件不存在");

        command.put("expectedStatus", "pending");
        command.put("handlerId", actor.userId());
        command.put("handlerName", actor.displayName());
        command.put("updateBy", actor.userName());
        int rows = mapper.updateConfirm(command);
        changed(rows, "确认信息状态已变化，请刷新后重试");

        boolean accepted = "accepted".equals(result);
        String target = accepted ? CaseStatus.PROCESSING.code() : CaseStatus.PENDING.code();
        Map<String, Object> update = new HashMap<>();
        update.put("caseId", caseId); update.put("caseStatus", target);
        update.put("currentNode", accepted ? "案件办理中" : "待重新分案");
        update.put("clearAssignment", !accepted); update.put("updateBy", actor.userName());
        changed(mapper.updateCaseConfirmResult(update), "案件确认状态已变化，请刷新后重试");
        support.statusLog(caseId, CaseStatus.CONFIRMING.code(), target, "confirm", "律师接案确认：" + (accepted ? "已接收" : "已拒绝"),actor);
        support.notice("律师接案确认", "案件 " + entity.get("caseNo") + " 接案确认结果：" + (accepted ? "已接收" : "已拒绝"),actor);
        BusinessEventType type = accepted ? BusinessEventType.CASE_ACCEPTED : BusinessEventType.CASE_REJECTED;
        Map<String, Object> payload = new HashMap<>(); payload.put("confirmId", confirmId);
        publisher.publish(new BusinessEventCommand(type, "CASE", caseId, text(entity.get("caseNo")),
                type.name() + ":" + caseId + ":" + IdUtils.fastUUID(), payload));
        return rows;
    }

    private Long id(Object value, String message)
    {
        try { if (value == null) throw new NumberFormatException(); return Long.valueOf(String.valueOf(value)); }
        catch (NumberFormatException e) { throw error("VALIDATION_FAILED", message); }
    }
    private String text(Object value) { return value == null ? null : String.valueOf(value); }
    private void changed(int rows, String message) { if (rows <= 0) throw error("CONCURRENT_MODIFICATION", message); }
    private ServiceException error(String code, String message) { ServiceException e = new ServiceException(message); e.setBusinessCode(code); return e; }
    private BusinessActor currentActor(){try{return new BusinessActor(SecurityUtils.getUserId(),SecurityUtils.getUsername(),SecurityUtils.getLoginUser().getUser().getNickName(),SecurityUtils.getDeptId(),SecurityUtils.isAdmin());}catch(RuntimeException absent){return new BusinessActor(0L,"system","system",null,false);}}
}
