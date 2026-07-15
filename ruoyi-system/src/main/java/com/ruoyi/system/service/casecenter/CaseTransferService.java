package com.ruoyi.system.service.casecenter;

import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.business.event.BusinessEventCommand;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.event.BusinessEventType;
import com.law.business.lawcase.dto.CaseTransferApprovalCommand;
import com.law.business.lawcase.dto.CaseTransferCommand;
import com.law.business.security.BusinessActor;
import com.law.business.security.BusinessActorProvider;
import com.law.business.shared.error.BusinessErrorCode;
import com.law.business.shared.status.CaseStatus;
import com.law.business.shared.status.CaseStatusTransitions;
import com.law.business.shared.status.CaseTransferStatus;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.mapper.BizCaseMapper;
import com.ruoyi.system.service.ISysUserService;

/** Owns lawyer transfer request and approval transactions. */
@Service
public class CaseTransferService
{
    private static final String ENABLED = "Y";

    private final BizCaseMapper mapper;
    private final CaseAccessPolicy access;
    private final ISysUserService users;
    private final CaseWorkflowSupport support;
    private final BusinessEventPublisher events;
    private final BusinessActorProvider actors;

    public CaseTransferService(BizCaseMapper mapper, CaseAccessPolicy access, ISysUserService users,
            CaseWorkflowSupport support, BusinessEventPublisher events, BusinessActorProvider actors)
    {
        this.mapper = mapper; this.access = access; this.users = users;
        this.support = support; this.events = events; this.actors = actors;
    }

    @Transactional
    public int request(CaseTransferCommand command)
    {
        return request(command, actors.current());
    }

    @Transactional
    public int request(CaseTransferCommand command, BusinessActor actor)
    {
        if (command == null || command.getCaseId() == null || command.getToLawyerId() == null)
            throw error(BusinessErrorCode.VALIDATION_FAILED, "请选择案件和拟转入律师");
        if (StringUtils.isEmpty(command.getDetail()))
            throw error(BusinessErrorCode.VALIDATION_FAILED, "转案详情不能为空");

        Map<String,Object> lawCase = access.requireTransferRequestable(command.getCaseId(), actor);
        Long currentLawyerId = longValue(value(lawCase, "main_lawyer_id", "mainLawyerId"));
        if (command.getToLawyerId().equals(currentLawyerId))
            throw error(BusinessErrorCode.VALIDATION_FAILED, "拟转入律师不能与当前主办律师相同");
        SysUser target = requireEligibleLawyer(command.getToLawyerId());
        support.requireDict("law_case_transfer_reason", command.getTransferReason(), "转案原因不合法");
        support.requireDict("law_case_risk_level", command.getRiskLevel(), "风险等级不合法");

        Map<String,Object> row = transferRow(command, lawCase, target, actor);
        changed(mapper.insertTransfer(row), "转案申请创建失败");
        Long transferId = requiredLong(row.get("transferId"), "转案申请创建失败");
        CaseStatusTransitions.requireAllowed(CaseStatus.PROCESSING, CaseStatus.TRANSFERRING);
        changed(mapper.updateCaseStatus(statusUpdate(command.getCaseId(), CaseStatus.PROCESSING,
                CaseStatus.TRANSFERRING, "转案审批中", actor)), "案件状态已变化，请刷新后重试");
        support.statusLog(command.getCaseId(), CaseStatus.PROCESSING.code(), CaseStatus.TRANSFERRING.code(),
                "transfer_request", "发起转案申请：" + command.getTransferReason(), actor);
        support.notice("转案申请待审批", "案件 " + text(lawCase, "case_no", "caseNo") + " 发起转案申请："
                + text(lawCase, "main_lawyer_name", "mainLawyerName") + " -> " + target.getNickName(), actor);
        publishRequested(command.getCaseId(), text(lawCase, "case_no", "caseNo"), transferId,
                command.getToLawyerId());
        return 1;
    }

    @Transactional
    public int approve(CaseTransferApprovalCommand command)
    {
        return approve(command, actors.current());
    }

    @Transactional
    public int approve(CaseTransferApprovalCommand command, BusinessActor actor)
    {
        if (command == null || command.getTransferId() == null || !validAction(command.getAction()))
            throw error(BusinessErrorCode.VALIDATION_FAILED, "审批动作不合法");
        if (StringUtils.isEmpty(command.getOpinion()))
            throw error(BusinessErrorCode.VALIDATION_FAILED, "审批意见必填");

        CaseTransferContext context = access.requireTransferApprovable(command.getTransferId(), actor);
        Map<String,Object> approval = approvalRow(command, actor);
        changed(mapper.updateTransferApproval(approval), "转案状态已变化，请刷新后重试");

        boolean passed = CaseTransferStatus.PASSED.code().equals(command.getAction());
        CaseStatus target = passed ? CaseStatus.CONFIRMING : CaseStatus.PROCESSING;
        CaseStatusTransitions.requireAllowed(CaseStatus.TRANSFERRING, target);
        Map<String,Object> update = statusUpdate(context.caseId(), CaseStatus.TRANSFERRING, target,
                passed ? "待新律师确认" : "案件办理中", actor);
        if (passed)
        {
            update.put("mainLawyerId", context.toLawyerId());
            update.put("mainLawyerName", context.toLawyerName());
        }
        changed(mapper.updateCaseStatus(update), "案件状态已变化，请刷新后重试");
        support.statusLog(context.caseId(), CaseStatus.TRANSFERRING.code(), target.code(), "transfer_approve",
                "转案审批：" + command.getAction() + "，" + command.getOpinion(), actor);
        support.notice("转案审批已处理", "转案单 " + context.transferNo() + " 审批结果："
                + actionLabel(command.getAction()), actor);

        Long confirmId = passed ? createConfirm(context, actor) : null;
        publishApproved(context, command.getAction(), confirmId, passed);
        return 1;
    }

    private Map<String,Object> transferRow(CaseTransferCommand command, Map<String,Object> lawCase,
            SysUser target, BusinessActor actor)
    {
        Map<String,Object> row = new HashMap<>();
        row.put("transferNo", "TR" + System.currentTimeMillis()); row.put("caseId", command.getCaseId());
        row.put("fromLawyerId", value(lawCase, "main_lawyer_id", "mainLawyerId"));
        row.put("fromLawyerName", text(lawCase, "main_lawyer_name", "mainLawyerName"));
        row.put("toLawyerId", command.getToLawyerId()); row.put("toLawyerName", target.getNickName());
        row.put("applicantId", actor.userId()); row.put("applicantName", actor.displayName());
        row.put("transferReason", command.getTransferReason()); row.put("riskLevel", command.getRiskLevel());
        row.put("detail", command.getDetail()); row.put("transferStatus", CaseTransferStatus.PENDING.code());
        row.put("currentNode", "法务经理审批"); row.put("createBy", actor.userName());
        return row;
    }

    private Map<String,Object> approvalRow(CaseTransferApprovalCommand command, BusinessActor actor)
    {
        Map<String,Object> row = new HashMap<>();
        row.put("transferId", command.getTransferId()); row.put("transferStatus", command.getAction());
        row.put("expectedStatus", CaseTransferStatus.PENDING.code()); row.put("currentNode", actionLabel(command.getAction()));
        row.put("opinion", command.getOpinion()); row.put("approverId", actor.userId());
        row.put("approverName", actor.displayName()); row.put("updateBy", actor.userName());
        return row;
    }

    private Map<String,Object> statusUpdate(Long caseId, CaseStatus expected, CaseStatus target,
            String currentNode, BusinessActor actor)
    {
        Map<String,Object> row = new HashMap<>();
        row.put("caseId", caseId); row.put("expectedStatus", expected.code()); row.put("caseStatus", target.code());
        row.put("currentNode", currentNode); row.put("updateBy", actor.userName()); return row;
    }

    private Long createConfirm(CaseTransferContext context, BusinessActor actor)
    {
        Map<String,Object> confirm = new HashMap<>();
        confirm.put("caseId", context.caseId()); confirm.put("confirmType", "transfer_accept");
        confirm.put("confirmStatus", "pending"); confirm.put("confirmUserId", context.toLawyerId());
        confirm.put("confirmUserName", context.toLawyerName()); confirm.put("content", "请确认接收转入案件");
        confirm.put("createBy", actor.userName());
        changed(mapper.insertConfirm(confirm), "转案接收确认创建失败");
        return requiredLong(confirm.get("confirmId"), "转案接收确认创建失败");
    }

    private SysUser requireEligibleLawyer(Long userId)
    {
        SysUser user = users.selectUserById(userId);
        if (user == null || "1".equals(user.getStatus()))
            throw error(BusinessErrorCode.DATA_NOT_FOUND, "律师不存在或已停用");
        Map<String,Object> profile = mapper.selectLawyerProfileByUserId(userId);
        if (profile == null || value(profile, "profileId", "profile_id") == null)
            throw error(BusinessErrorCode.PRECONDITION_FAILED, "请先维护律师档案");
        if (!ENABLED.equals(text(profile, "assignEnabled", "assign_enabled")))
            throw error(BusinessErrorCode.PRECONDITION_FAILED, "律师已禁用分案");
        if ("assistant".equals(text(profile, "lawyerRole", "lawyer_role")))
            throw error(BusinessErrorCode.PRECONDITION_FAILED, "实习律师不能作为主办律师");
        return user;
    }

    private void publishRequested(Long caseId, String caseNo, Long transferId, Long targetLawyerId)
    {
        Map<String,Object> payload = new HashMap<>();
        payload.put("transferId", transferId); payload.put("toLawyerId", targetLawyerId);
        events.publish(new BusinessEventCommand(BusinessEventType.CASE_TRANSFER_REQUESTED, "CASE", caseId, caseNo,
                "CASE_TRANSFER_REQUESTED:" + caseId + ":" + transferId, payload));
    }

    private void publishApproved(CaseTransferContext context, String action, Long confirmId, boolean passed)
    {
        Map<String,Object> payload = new HashMap<>();
        payload.put("transferId", context.transferId()); if (confirmId != null) payload.put("confirmId", confirmId);
        payload.put("action", action); payload.put("mainLawyerId", context.toLawyerId());
        payload.put("requiresAcceptance", passed);
        events.publish(new BusinessEventCommand(BusinessEventType.CASE_TRANSFER_APPROVED, "CASE", context.caseId(),
                context.caseNo(), "CASE_TRANSFER_APPROVED:" + context.caseId() + ":" + context.transferId(), payload));
    }

    private boolean validAction(String action)
    {
        return CaseTransferStatus.PASSED.code().equals(action)
                || CaseTransferStatus.REJECTED.code().equals(action)
                || CaseTransferStatus.SUPPLEMENT.code().equals(action);
    }

    private String actionLabel(String action)
    {
        if (CaseTransferStatus.PASSED.code().equals(action)) return "已通过";
        if (CaseTransferStatus.SUPPLEMENT.code().equals(action)) return "补充材料";
        return "已驳回";
    }

    private Object value(Map<String,Object> map, String first, String second)
    {
        return map.containsKey(first) ? map.get(first) : map.get(second);
    }
    private String text(Map<String,Object> map, String first, String second)
    {
        Object value = value(map, first, second); return value == null ? null : String.valueOf(value);
    }
    private Long longValue(Object value)
    {
        return value == null ? null : value instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(value));
    }
    private Long requiredLong(Object value, String message)
    {
        if (value == null) throw error(BusinessErrorCode.CONCURRENT_MODIFICATION, message);
        return longValue(value);
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
