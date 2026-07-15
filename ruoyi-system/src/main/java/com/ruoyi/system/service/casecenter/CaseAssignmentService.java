package com.ruoyi.system.service.casecenter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.business.event.BusinessEventCommand;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.event.BusinessEventType;
import com.law.business.lawcase.dto.CaseAssignmentCommand;
import com.law.business.lawcase.dto.CaseBatchAssignmentCommand;
import com.law.business.security.BusinessActor;
import com.law.business.security.BusinessActorProvider;
import com.law.business.shared.error.BusinessErrorCode;
import com.law.business.shared.status.CaseStatus;
import com.law.business.shared.status.CaseStatusTransitions;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.SysNotice;
import com.ruoyi.system.mapper.BizCaseMapper;
import com.ruoyi.system.service.ISysDictTypeService;
import com.ruoyi.system.service.ISysNoticeService;
import com.ruoyi.system.service.ISysUserService;

/** Owns single and batch case assignment transactions. */
@Service
public class CaseAssignmentService
{
    private static final String ENABLED = "Y";

    private final BizCaseMapper mapper;
    private final CaseAccessPolicy access;
    private final ISysDictTypeService dictionaries;
    private final ISysUserService users;
    private final ISysNoticeService notices;
    private final BusinessEventPublisher events;
    private final BusinessActorProvider actors;

    public CaseAssignmentService(BizCaseMapper mapper, CaseAccessPolicy access,
            ISysDictTypeService dictionaries, ISysUserService users, ISysNoticeService notices,
            BusinessEventPublisher events, BusinessActorProvider actors)
    {
        this.mapper = mapper; this.access = access; this.dictionaries = dictionaries;
        this.users = users; this.notices = notices; this.events = events; this.actors = actors;
    }

    @Transactional
    public int assign(CaseAssignmentCommand command)
    {
        return assign(command, actors.current());
    }

    @Transactional
    public int assign(CaseAssignmentCommand command, BusinessActor actor)
    {
        if (command == null || command.getCaseId() == null)
            throw error(BusinessErrorCode.VALIDATION_FAILED, "请选择案件");
        return assignOne(command, actor);
    }

    @Transactional
    public int batchAssign(CaseBatchAssignmentCommand command)
    {
        if (command == null || command.getCaseIds() == null || command.getCaseIds().isEmpty())
            throw error(BusinessErrorCode.VALIDATION_FAILED, "请选择案件");
        BusinessActor actor = actors.current();
        int rows = 0;
        for (Long caseId : command.getCaseIds()) rows += assignOne(single(command, caseId), actor);
        return rows;
    }

    private int assignOne(CaseAssignmentCommand command, BusinessActor actor)
    {
        Long caseId = command.getCaseId();
        Map<String,Object> lawCase = access.requireAssignable(caseId, actor);
        SysUser mainLawyer = requireEligibleLawyer(command.getMainLawyerId(), true);
        AssistantSelection assistants = assistants(command.getAssistantLawyerIds());
        requireDict("law_case_assign_method", command.getAssignMethod(), "分配方式不合法");
        requireDict("law_case_priority", command.getPriority(), "优先级不合法");
        requireDict("law_case_assign_reason", command.getAssignReason(), "分配原因不合法");

        boolean needConfirm = ENABLED.equals(command.getNotifyFlag());
        CaseStatus target = needConfirm ? CaseStatus.CONFIRMING : CaseStatus.PROCESSING;
        CaseStatusTransitions.requireAllowed(CaseStatus.PENDING, target);
        Map<String,Object> row = assignmentRow(command, actor, mainLawyer, assistants, target);
        changed(mapper.updateCaseAssignment(row), "案件状态已变化，请刷新后重试");
        changed(mapper.insertAssignment(row), "分案记录创建失败");
        Long assignmentId = requiredLong(row.get("assignmentId"), "分案记录创建失败");
        Long confirmId = needConfirm ? createConfirm(caseId, mainLawyer, actor) : null;
        insertStatusLog(caseId, target, mainLawyer.getNickName(), actor);
        notice(lawCase, mainLawyer, needConfirm, actor);
        publish(caseId, text(lawCase, "case_no", "caseNo"), assignmentId, confirmId,
                command.getMainLawyerId(), needConfirm);
        return 1;
    }

    private CaseAssignmentCommand single(CaseBatchAssignmentCommand source, Long caseId)
    {
        CaseAssignmentCommand value = new CaseAssignmentCommand();
        value.setCaseId(caseId); value.setMainLawyerId(source.getMainLawyerId());
        value.setAssignMethod(source.getAssignMethod()); value.setPriority(source.getPriority());
        value.setAssignReason(source.getAssignReason()); value.setEstimatedWorkload(source.getEstimatedWorkload());
        value.setEstimatedCycle(source.getEstimatedCycle()); value.setPlanStartDate(source.getPlanStartDate());
        value.setNotifyFlag(source.getNotifyFlag()); value.setAssistantLawyerIds(source.getAssistantLawyerIds());
        value.setRemark(source.getRemark());
        return value;
    }

    private Map<String,Object> assignmentRow(CaseAssignmentCommand command, BusinessActor actor,
            SysUser lawyer, AssistantSelection assistants, CaseStatus target)
    {
        Map<String,Object> row = new HashMap<>();
        row.put("caseId", command.getCaseId()); row.put("mainLawyerId", command.getMainLawyerId());
        row.put("mainLawyerName", lawyer.getNickName()); row.put("assistantLawyerIds", assistants.ids());
        row.put("assistantLawyerNames", assistants.names()); row.put("assignMethod", command.getAssignMethod());
        row.put("priority", command.getPriority()); row.put("assignReason", command.getAssignReason());
        row.put("estimatedWorkload", command.getEstimatedWorkload()); row.put("estimatedCycle", command.getEstimatedCycle());
        row.put("planStartDate", command.getPlanStartDate()); row.put("notifyFlag", command.getNotifyFlag());
        row.put("remark", command.getRemark()); row.put("caseStatus", target.code());
        row.put("currentNode", target == CaseStatus.CONFIRMING ? "待律师确认" : "案件办理中");
        row.put("createBy", actor.userName()); row.put("updateBy", actor.userName());
        return row;
    }

    private AssistantSelection assistants(String raw)
    {
        if (StringUtils.isEmpty(raw)) return new AssistantSelection(null, null);
        List<String> ids = new ArrayList<>(); List<String> names = new ArrayList<>();
        for (String item : raw.split(","))
        {
            if (StringUtils.isEmpty(item.trim())) continue;
            Long id;
            try { id = Long.valueOf(item.trim()); }
            catch (NumberFormatException exception) { throw error(BusinessErrorCode.VALIDATION_FAILED, "协办律师不合法"); }
            SysUser lawyer = requireEligibleLawyer(id, false);
            ids.add(String.valueOf(id)); names.add(lawyer.getNickName());
        }
        return new AssistantSelection(ids.isEmpty() ? null : String.join(",", ids),
                names.isEmpty() ? null : String.join(",", names));
    }

    private SysUser requireEligibleLawyer(Long userId, boolean main)
    {
        if (userId == null) throw error(BusinessErrorCode.VALIDATION_FAILED, main ? "请选择主办律师" : "请选择协办律师");
        SysUser user = users.selectUserById(userId);
        if (user == null || "1".equals(user.getStatus()))
            throw error(BusinessErrorCode.DATA_NOT_FOUND, "律师不存在或已停用");
        Map<String,Object> profile = mapper.selectLawyerProfileByUserId(userId);
        if (profile == null || value(profile, "profileId", "profile_id") == null)
            throw error(BusinessErrorCode.PRECONDITION_FAILED, main ? "请先维护律师档案" : "请先维护协办律师档案");
        if (!ENABLED.equals(text(profile, "assignEnabled", "assign_enabled")))
            throw error(BusinessErrorCode.PRECONDITION_FAILED, main ? "律师已禁用分案" : "协办律师已禁用分案");
        if (main && "assistant".equals(text(profile, "lawyerRole", "lawyer_role")))
            throw error(BusinessErrorCode.PRECONDITION_FAILED, "实习律师不能作为主办律师");
        return user;
    }

    private Long createConfirm(Long caseId, SysUser lawyer, BusinessActor actor)
    {
        Map<String,Object> confirm = new HashMap<>();
        confirm.put("caseId", caseId); confirm.put("confirmType", "accept"); confirm.put("confirmStatus", "pending");
        confirm.put("confirmUserId", lawyer.getUserId()); confirm.put("confirmUserName", lawyer.getNickName());
        confirm.put("content", "请确认接收案件"); confirm.put("createBy", actor.userName());
        changed(mapper.insertConfirm(confirm), "接案确认记录创建失败");
        return requiredLong(confirm.get("confirmId"), "接案确认记录创建失败");
    }

    private void insertStatusLog(Long caseId, CaseStatus target, String lawyerName, BusinessActor actor)
    {
        requireDict("law_case_status_action", "assign", "案件状态动作不合法");
        Map<String,Object> log = new HashMap<>();
        log.put("caseId", caseId); log.put("fromStatus", CaseStatus.PENDING.code()); log.put("toStatus", target.code());
        log.put("actionType", "assign"); log.put("content", "分配主办律师：" + lawyerName); log.put("createBy", actor.userName());
        changed(mapper.insertStatusLog(log), "案件状态记录创建失败");
    }

    private void notice(Map<String,Object> lawCase, SysUser lawyer, boolean confirm, BusinessActor actor)
    {
        SysNotice value = new SysNotice();
        value.setNoticeTitle(confirm ? "案件分配待确认" : "案件已分配"); value.setNoticeType("1");
        value.setNoticeContent("案件 " + text(lawCase, "case_no", "caseNo") + " 已分配给 " + lawyer.getNickName()
                + (confirm ? "，请及时确认接收。" : "，当前进入办理中。"));
        value.setStatus("0"); value.setCreateBy(actor.userName()); value.setRemark("案管中心");
        notices.insertNotice(value);
    }

    private void publish(Long caseId, String caseNo, Long assignmentId, Long confirmId,
            Long lawyerId, boolean needConfirm)
    {
        Map<String,Object> payload = new HashMap<>();
        payload.put("assignmentId", assignmentId); if (confirmId != null) payload.put("confirmId", confirmId);
        payload.put("mainLawyerId", lawyerId); payload.put("needConfirm", needConfirm);
        events.publish(new BusinessEventCommand(BusinessEventType.CASE_ASSIGNED, "CASE", caseId, caseNo,
                "CASE_ASSIGNED:" + caseId + ":" + assignmentId, payload));
    }

    private void requireDict(String type, Object raw, String message)
    {
        String wanted = raw == null ? null : String.valueOf(raw).trim();
        List<SysDictData> options = dictionaries.selectDictDataByType(type);
        if (options == null || options.isEmpty()) throw error(BusinessErrorCode.PRECONDITION_FAILED, "字典未初始化：" + type);
        for (SysDictData option : options) if (wanted != null && wanted.equals(option.getDictValue())) return;
        throw error(BusinessErrorCode.VALIDATION_FAILED, message);
    }

    private Object value(Map<String,Object> map, String first, String second)
    {
        return map.containsKey(first) ? map.get(first) : map.get(second);
    }
    private String text(Map<String,Object> map, String first, String second)
    {
        Object value = value(map, first, second); return value == null ? null : String.valueOf(value);
    }
    private Long requiredLong(Object value, String message)
    {
        if (value == null) throw error(BusinessErrorCode.CONCURRENT_MODIFICATION, message);
        return value instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(value));
    }
    private void changed(int rows, String message)
    {
        if (rows <= 0) throw error(BusinessErrorCode.CONCURRENT_MODIFICATION, message);
    }
    private ServiceException error(BusinessErrorCode code, String message)
    {
        return new ServiceException(message, code.name());
    }
    private record AssistantSelection(String ids, String names) { }
}
