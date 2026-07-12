package com.ruoyi.system.service.casecenter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.business.event.BusinessEventCommand;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.event.BusinessEventType;
import com.law.business.shared.status.CaseStatus;
import com.law.business.shared.status.CaseStatusTransitions;
import com.law.business.shared.error.BusinessErrorCode;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.uuid.IdUtils;
import com.ruoyi.system.domain.SysNotice;
import com.ruoyi.system.mapper.BizCaseMapper;
import com.ruoyi.system.service.ISysDictTypeService;
import com.ruoyi.system.service.ISysNoticeService;
import com.ruoyi.system.service.ISysUserService;

/** Owns single and batch case assignment transactions. */
@Service
public class CaseAssignmentService
{
    private static final String ASSIGN_ENABLED = "Y";
    private static final String ASSIGN_DISABLED = "N";

    @Autowired private BizCaseMapper caseMapper;
    @Autowired private CaseQueryService queryService;
    @Autowired private ISysDictTypeService dictTypeService;
    @Autowired private ISysUserService userService;
    @Autowired private ISysNoticeService noticeService;
    @Autowired private BusinessEventPublisher eventPublisher;

    @Transactional
    public int assign(Map<String, Object> assignment)
    {
        return assignSingle(assignment);
    }

    @Transactional
    public int batchAssign(Map<String, Object> assignment)
    {
        List<Long> caseIds = parseIds(assignment.get("caseIds"));
        if (caseIds.isEmpty()) throw error(BusinessErrorCode.VALIDATION_FAILED, "请选择案件");
        int rows = 0;
        for (Long caseId : caseIds)
        {
            Map<String, Object> item = new HashMap<>(assignment);
            item.put("caseId", caseId);
            rows += assignSingle(item);
        }
        return rows;
    }

    private int assignSingle(Map<String, Object> assignment)
    {
        Long caseId = requiredLong(assignment.get("caseId"), "请选择案件");
        Map<String, Object> existed = queryService.caseDetail(caseId);
        String status = text(existed.get("case_status"));
        if (!CaseStatus.PENDING.code().equals(status))
        {
            throw error(BusinessErrorCode.STATE_CONFLICT, "只有待分案案件可以分配，办理中案件请走转案审批");
        }
        Long mainLawyerId = requiredLong(assignment.get("mainLawyerId"), "请选择主办律师");
        SysUser mainLawyer = requireEligibleMainLawyer(mainLawyerId);
        normalizeAssistants(assignment);
        requireDict("law_case_assign_method", assignment.get("assignMethod"), "分配方式不合法");
        requireDict("law_case_priority", assignment.get("priority"), "优先级不合法");
        requireDict("law_case_assign_reason", assignment.get("assignReason"), "分配原因不合法");

        boolean needConfirm = "Y".equals(String.valueOf(assignment.get("notifyFlag")));
        String targetStatus = needConfirm ? CaseStatus.CONFIRMING.code() : CaseStatus.PROCESSING.code();
        CaseStatusTransitions.requireAllowed(CaseStatus.PENDING, CaseStatus.fromCode(targetStatus));
        assignment.put("mainLawyerName", mainLawyer.getNickName());
        assignment.put("caseStatus", targetStatus);
        assignment.put("currentNode", needConfirm ? "待律师确认" : "案件办理中");
        assignment.put("updateBy", SecurityUtils.getUsername());
        int rows = caseMapper.updateCaseAssignment(assignment);
        assertChanged(rows, "案件状态已变化，请刷新后重试");
        assignment.put("createBy", SecurityUtils.getUsername());
        assertChanged(caseMapper.insertAssignment(assignment), "分案记录创建失败");
        insertStatusLog(caseId, status, targetStatus, mainLawyer.getNickName());
        if (needConfirm)
        {
            createConfirm(caseId, mainLawyerId, mainLawyer.getNickName());
            createNotice("案件分配待确认", "案件 " + existed.get("case_no") + " 已分配给 " + mainLawyer.getNickName() + "，请及时确认接收。");
        }
        else
        {
            createNotice("案件已分配", "案件 " + existed.get("case_no") + " 已分配给 " + mainLawyer.getNickName() + "，当前进入办理中。");
        }
        publish(caseId, text(existed.get("case_no")), mainLawyerId, needConfirm);
        return rows;
    }

    private SysUser requireEligibleMainLawyer(Long userId)
    {
        SysUser user = requireActiveUser(userId);
        Map<String, Object> profile = caseMapper.selectLawyerProfileByUserId(userId);
        if (profile == null || profile.get("profileId") == null || StringUtils.isEmpty(String.valueOf(profile.get("profileId"))))
            throw error(BusinessErrorCode.PRECONDITION_FAILED, "请先维护律师档案");
        if (!ASSIGN_ENABLED.equals(safeText(profile.get("assignEnabled"), ASSIGN_DISABLED)))
            throw error(BusinessErrorCode.PRECONDITION_FAILED, "律师已禁用分案");
        if ("assistant".equals(safeText(profile.get("lawyerRole"), "lawyer")))
            throw error(BusinessErrorCode.PRECONDITION_FAILED, "实习律师不能作为主办律师");
        return user;
    }

    private void normalizeAssistants(Map<String, Object> assignment)
    {
        List<Long> ids = parseIds(assignment.get("assistantLawyerIds"));
        if (ids.isEmpty())
        {
            assignment.put("assistantLawyerIds", null);
            assignment.put("assistantLawyerNames", null);
            return;
        }
        StringBuilder idText = new StringBuilder();
        StringBuilder names = new StringBuilder();
        for (Long id : ids)
        {
            SysUser user = requireActiveUser(id);
            Map<String, Object> profile = caseMapper.selectLawyerProfileByUserId(id);
            if (profile == null || profile.get("profileId") == null || StringUtils.isEmpty(String.valueOf(profile.get("profileId"))))
                throw error(BusinessErrorCode.PRECONDITION_FAILED, "请先维护协办律师档案");
            if (!ASSIGN_ENABLED.equals(safeText(profile.get("assignEnabled"), ASSIGN_DISABLED)))
                throw error(BusinessErrorCode.PRECONDITION_FAILED, "协办律师已禁用分案");
            if (idText.length() > 0) { idText.append(','); names.append(','); }
            idText.append(id); names.append(user.getNickName());
        }
        assignment.put("assistantLawyerIds", idText.toString());
        assignment.put("assistantLawyerNames", names.toString());
    }

    private SysUser requireActiveUser(Long id)
    {
        SysUser user = userService.selectUserById(id);
        if (user == null || "1".equals(user.getStatus())) throw error(BusinessErrorCode.DATA_NOT_FOUND, "律师不存在或已停用");
        return user;
    }

    private void createConfirm(Long caseId, Long userId, String userName)
    {
        Map<String, Object> confirm = new HashMap<>();
        confirm.put("caseId", caseId); confirm.put("confirmType", "accept"); confirm.put("confirmStatus", "pending");
        confirm.put("confirmUserId", userId); confirm.put("confirmUserName", userName);
        confirm.put("content", "请确认接收案件"); confirm.put("createBy", SecurityUtils.getUsername());
        assertChanged(caseMapper.insertConfirm(confirm), "接案确认记录创建失败");
    }

    private void insertStatusLog(Long caseId, String from, String to, String lawyerName)
    {
        requireDict("law_case_status_action", "assign", "案件状态动作不合法");
        Map<String, Object> log = new HashMap<>();
        log.put("caseId", caseId); log.put("fromStatus", from); log.put("toStatus", to);
        log.put("actionType", "assign"); log.put("content", "分配主办律师：" + lawyerName);
        log.put("createBy", SecurityUtils.getUsername());
        assertChanged(caseMapper.insertStatusLog(log), "案件状态记录创建失败");
    }

    private void createNotice(String title, String content)
    {
        SysNotice notice = new SysNotice();
        notice.setNoticeTitle(title); notice.setNoticeType("1"); notice.setNoticeContent(content);
        notice.setStatus("0"); notice.setCreateBy(SecurityUtils.getUsername()); notice.setRemark("案管中心");
        noticeService.insertNotice(notice);
    }

    private void publish(Long caseId, String caseNo, Long lawyerId, boolean needConfirm)
    {
        Map<String, Object> payload = new HashMap<>();
        payload.put("mainLawyerId", lawyerId); payload.put("needConfirm", needConfirm);
        eventPublisher.publish(new BusinessEventCommand(BusinessEventType.CASE_ASSIGNED, "CASE", caseId, caseNo,
                BusinessEventType.CASE_ASSIGNED.name() + ":" + caseId + ":" + IdUtils.fastUUID(), payload));
    }

    private void requireDict(String type, Object value, String message)
    {
        String v = value == null ? null : String.valueOf(value).trim();
        if (StringUtils.isEmpty(v)) throw error(BusinessErrorCode.VALIDATION_FAILED, message);
        List<SysDictData> options = dictTypeService.selectDictDataByType(type);
        if (contains(options, v)) return;
        dictTypeService.resetDictCache();
        options = dictTypeService.selectDictDataByType(type);
        if (options == null || options.isEmpty()) throw error(BusinessErrorCode.PRECONDITION_FAILED, "字典未初始化：" + type);
        if (!contains(options, v)) throw error(BusinessErrorCode.VALIDATION_FAILED, message);
    }

    private boolean contains(List<SysDictData> options, String value)
    {
        if (options == null) return false;
        for (SysDictData item : options) if (value.equals(item.getDictValue())) return true;
        return false;
    }

    private List<Long> parseIds(Object value)
    {
        List<Long> result = new ArrayList<>();
        if (value instanceof List)
        {
            for (Object item : (List<?>) value) result.add(requiredLong(item, "请选择案件"));
        }
        else if (value != null)
        {
            for (String id : String.valueOf(value).split(",")) if (!StringUtils.isEmpty(id)) result.add(requiredLong(id.trim(), "请选择案件"));
        }
        return result;
    }

    private Long requiredLong(Object value, String message)
    {
        if (value == null || StringUtils.isEmpty(String.valueOf(value)) || "null".equalsIgnoreCase(String.valueOf(value)))
            throw error(BusinessErrorCode.VALIDATION_FAILED, message);
        return Long.valueOf(String.valueOf(value));
    }

    private String text(Object value) { return value == null ? null : String.valueOf(value); }
    private String safeText(Object value, String fallback) { return value == null || StringUtils.isEmpty(String.valueOf(value)) ? fallback : String.valueOf(value); }
    private void assertChanged(int rows, String message) { if (rows <= 0) throw error(BusinessErrorCode.CONCURRENT_MODIFICATION, message); }
    private ServiceException error(BusinessErrorCode code, String message) { return new ServiceException(message, code.name()); }
}
