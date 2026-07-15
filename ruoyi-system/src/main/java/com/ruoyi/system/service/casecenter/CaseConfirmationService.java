package com.ruoyi.system.service.casecenter;

import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.business.event.BusinessEventCommand;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.event.BusinessEventType;
import com.law.business.lawcase.dto.CaseConfirmCommand;
import com.law.business.security.BusinessActor;
import com.law.business.security.BusinessActorProvider;
import com.law.business.shared.error.BusinessErrorCode;
import com.law.business.shared.status.CaseStatus;
import com.law.business.shared.status.CaseStatusTransitions;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.mapper.BizCaseMapper;

/** Handles acceptance or rejection by the explicitly assigned recipient. */
@Service
public class CaseConfirmationService
{
    private final BizCaseMapper mapper;
    private final CaseAccessPolicy access;
    private final CaseWorkflowSupport support;
    private final BusinessEventPublisher events;
    private final BusinessActorProvider actors;

    public CaseConfirmationService(BizCaseMapper mapper, CaseAccessPolicy access,
            CaseWorkflowSupport support, BusinessEventPublisher events, BusinessActorProvider actors)
    {
        this.mapper = mapper; this.access = access; this.support = support;
        this.events = events; this.actors = actors;
    }

    @Transactional
    public int handle(CaseConfirmCommand command)
    {
        return handle(command, actors.current());
    }

    @Transactional
    public int handle(CaseConfirmCommand command, BusinessActor actor)
    {
        if (command == null || command.getConfirmId() == null || !validResult(command.getConfirmResult()))
            throw error(BusinessErrorCode.VALIDATION_FAILED, "确认结果不合法");

        CaseConfirmContext context = access.requireConfirmable(command.getConfirmId(), actor);
        changed(mapper.updateConfirm(confirmUpdate(command, actor)), "确认信息状态已变化，请刷新后重试");

        boolean accepted = "accepted".equals(command.getConfirmResult());
        CaseStatus target = accepted ? CaseStatus.PROCESSING : CaseStatus.PENDING;
        CaseStatusTransitions.requireAllowed(CaseStatus.CONFIRMING, target);
        changed(mapper.updateCaseConfirmResult(caseUpdate(context.caseId(), target, !accepted, actor)),
                "案件确认状态已变化，请刷新后重试");
        support.statusLog(context.caseId(), CaseStatus.CONFIRMING.code(), target.code(), "confirm",
                "律师接案确认：" + (accepted ? "已接收" : "已拒绝"), actor);
        support.notice("律师接案确认", "案件 " + context.caseNo() + " 接案确认结果："
                + (accepted ? "已接收" : "已拒绝"), actor);
        publish(context, accepted);
        return 1;
    }

    private Map<String,Object> confirmUpdate(CaseConfirmCommand command, BusinessActor actor)
    {
        Map<String,Object> row = new HashMap<>();
        row.put("confirmId", command.getConfirmId()); row.put("confirmResult", command.getConfirmResult());
        row.put("remark", command.getRemark()); row.put("expectedStatus", "pending");
        row.put("handlerId", actor.userId()); row.put("handlerName", actor.displayName());
        row.put("updateBy", actor.userName()); return row;
    }

    private Map<String,Object> caseUpdate(Long caseId, CaseStatus target, boolean clearAssignment, BusinessActor actor)
    {
        Map<String,Object> row = new HashMap<>();
        row.put("caseId", caseId); row.put("expectedStatus", CaseStatus.CONFIRMING.code());
        row.put("caseStatus", target.code()); row.put("currentNode", target == CaseStatus.PROCESSING
                ? "案件办理中" : "待重新分案");
        row.put("clearAssignment", clearAssignment); row.put("updateBy", actor.userName());
        return row;
    }

    private void publish(CaseConfirmContext context, boolean accepted)
    {
        BusinessEventType type = accepted ? BusinessEventType.CASE_ACCEPTED : BusinessEventType.CASE_REJECTED;
        Map<String,Object> payload = new HashMap<>(); payload.put("confirmId", context.confirmId());
        events.publish(new BusinessEventCommand(type, "CASE", context.caseId(), context.caseNo(),
                type.name() + ":" + context.caseId() + ":" + context.confirmId(), payload));
    }

    private boolean validResult(String result)
    {
        return "accepted".equals(result) || "rejected".equals(result);
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
