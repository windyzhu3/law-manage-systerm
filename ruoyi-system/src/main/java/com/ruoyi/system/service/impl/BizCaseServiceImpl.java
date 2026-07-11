package com.ruoyi.system.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.BizContract;
import com.ruoyi.system.domain.SysNotice;
import com.ruoyi.system.mapper.BizCaseMapper;
import com.ruoyi.system.service.IBizCaseService;
import com.ruoyi.system.service.ISysDictTypeService;
import com.ruoyi.system.service.ISysNoticeService;
import com.ruoyi.system.service.ISysUserService;
import com.law.business.shared.status.CaseStatus;
import com.law.business.shared.status.CaseTransferStatus;
import com.law.business.event.BusinessEventCommand;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.event.BusinessEventType;
import com.ruoyi.common.utils.uuid.IdUtils;
import com.law.business.security.CasePermissions;
import com.ruoyi.system.service.casecenter.CaseQueryService;
import com.ruoyi.system.service.casecenter.CaseCreationService;
import com.ruoyi.system.service.casecenter.LawyerProfileService;

@Service
public class BizCaseServiceImpl implements IBizCaseService
{
    private static final String CASE_PENDING = CaseStatus.PENDING.code();
    private static final String CASE_PROCESSING = CaseStatus.PROCESSING.code();
    private static final String CASE_CONFIRMING = CaseStatus.CONFIRMING.code();
    private static final String CASE_TRANSFERING = CaseStatus.TRANSFERRING.code();

    private static final String TRANSFER_PENDING = CaseTransferStatus.PENDING.code();
    private static final String TRANSFER_PASSED = CaseTransferStatus.PASSED.code();
    private static final String TRANSFER_REJECTED = CaseTransferStatus.REJECTED.code();
    private static final String TRANSFER_SUPPLEMENT = CaseTransferStatus.SUPPLEMENT.code();

    private static final String LAWYER_ROLE_DEFAULT = "lawyer";
    private static final String LAWYER_ROLE_ASSISTANT = "assistant";
    private static final String ASSIGN_ENABLED = "Y";
    private static final String ASSIGN_DISABLED = "N";

    private static final String CASE_MODULE_PERMISSIONS = CasePermissions.DATA_SCOPE;

    @Autowired
    private BizCaseMapper caseMapper;

    @Autowired
    private ISysDictTypeService dictTypeService;

    @Autowired
    private ISysUserService userService;

    @Autowired
    private ISysNoticeService noticeService;

    @Autowired
    private BusinessEventPublisher eventPublisher;

    @Autowired
    private CaseQueryService queryService;

    @Autowired
    private LawyerProfileService lawyerProfileService;

    @Autowired
    private CaseCreationService caseCreationService;

    @Override
    public List<Map<String, Object>> selectCaseList(Map<String, Object> params)
    {
        return queryService.cases(params);
    }

    @Override
    public Map<String, Object> selectCaseById(Long caseId)
    {
        return queryService.caseDetail(caseId);
    }

    @Override
    public Map<String, Object> selectDashboard()
    {
        return queryService.dashboard();
    }

    @Override
    public List<Map<String, Object>> selectLawyerLoads(Map<String, Object> params)
    {
        return queryService.lawyerLoads(params);
    }

    @Override
    public List<Map<String, Object>> selectLawyerSpecialtyStats(Map<String, Object> params)
    {
        return queryService.lawyerSpecialties(params);
    }

    @Override
    public List<Map<String, Object>> selectLawyerProfiles(Map<String, Object> params)
    {
        return queryService.lawyerProfiles(params);
    }

    @Override
    public Map<String, Object> selectLawyerProfileByUserId(Long userId)
    {
        return queryService.lawyerProfile(userId);
    }

    @Override
    public int saveLawyerProfile(Map<String, Object> profile)
    {
        return lawyerProfileService.save(profile);
    }

    @Override
    public int updateLawyerProfileStatus(Map<String, Object> profile)
    {
        return lawyerProfileService.updateStatus(profile);
    }

    @Override
    @Transactional
    public int createCaseFromContract(BizContract contract)
    {
        return caseCreationService.createFromContract(contract);
    }

    @Override
    @Transactional
    public int assignCase(Map<String, Object> assignment)
    {
        return assignSingleCase(assignment);
    }

    @Override
    @Transactional
    public int batchAssignCases(Map<String, Object> assignment)
    {
        List<Long> caseIds = parseIds(assignment.get("caseIds"));
        if (caseIds.isEmpty())
        {
            throw new ServiceException("请选择案件");
        }
        int rows = 0;
        for (Long caseId : caseIds)
        {
            Map<String, Object> item = new HashMap<>(assignment);
            item.put("caseId", caseId);
            rows += assignSingleCase(item);
        }
        return rows;
    }

    private int assignSingleCase(Map<String, Object> assignment)
    {
        Long caseId = toLong(assignment.get("caseId"), "请选择案件");
        Map<String, Object> existed = selectCaseById(caseId);
        String status = text(existed.get("case_status"));
        if (!CASE_PENDING.equals(status))
        {
            throw new ServiceException("只有待分案案件可以分配，办理中案件请走转案审批");
        }
        Long mainLawyerId = toLong(assignment.get("mainLawyerId"), "请选择主办律师");
        SysUser mainLawyer = assertMainLawyerEligible(mainLawyerId);
        normalizeAssistantLawyers(assignment);
        assertDictValue("law_case_assign_method", assignment.get("assignMethod"), "分配方式不合法");
        assertDictValue("law_case_priority", assignment.get("priority"), "优先级不合法");
        assertDictValue("law_case_assign_reason", assignment.get("assignReason"), "分配原因不合法");

        boolean needConfirm = "Y".equals(String.valueOf(assignment.get("notifyFlag")));
        String targetStatus = needConfirm ? CASE_CONFIRMING : CASE_PROCESSING;
        assignment.put("mainLawyerName", mainLawyer.getNickName());
        assignment.put("caseStatus", targetStatus);
        assignment.put("currentNode", needConfirm ? "待律师确认" : "案件办理中");
        assignment.put("updateBy", SecurityUtils.getUsername());
        int rows = caseMapper.updateCaseAssignment(assignment);
        assertRowsChanged(rows, "案件状态已变化，请刷新后重试");
        assignment.put("createBy", SecurityUtils.getUsername());
        assertRowsChanged(caseMapper.insertAssignment(assignment), "分案记录创建失败");
        insertStatusLog(caseId, status, targetStatus, "assign", "分配主办律师：" + mainLawyer.getNickName());
        if (needConfirm)
        {
            createConfirm(caseId, mainLawyerId, mainLawyer.getNickName(), "accept", "请确认接收案件");
            createNotice("案件分配待确认", "案件 " + existed.get("case_no") + " 已分配给 " + mainLawyer.getNickName() + "，请及时确认接收。");
        }
        else
        {
            createNotice("案件已分配", "案件 " + existed.get("case_no") + " 已分配给 " + mainLawyer.getNickName() + "，当前进入办理中。");
        }
        publish(BusinessEventType.CASE_ASSIGNED, caseId, text(existed.get("case_no")),
                eventPayload("mainLawyerId", mainLawyerId, "needConfirm", needConfirm));
        return rows;
    }

    @Override
    public List<Map<String, Object>> selectAssignments(Map<String, Object> params)
    {
        return queryService.assignments(params);
    }

    @Override
    @Transactional
    public int requestTransfer(Map<String, Object> transfer)
    {
        Long caseId = toLong(transfer.get("caseId"), "请选择案件");
        Map<String, Object> existed = selectCaseById(caseId);
        if (!CASE_PROCESSING.equals(text(existed.get("case_status"))))
        {
            throw new ServiceException("只有办理中的案件可以发起转案");
        }
        Long toLawyerId = toLong(transfer.get("toLawyerId"), "请选择拟转入律师");
        Long currentMainLawyerId = toNullableLong(existed.get("main_lawyer_id"));
        if (currentMainLawyerId != null && currentMainLawyerId.equals(toLawyerId))
        {
            throw new ServiceException("拟转入律师不能与当前主办律师相同");
        }
        SysUser toLawyer = assertMainLawyerEligible(toLawyerId);
        assertDictValue("law_case_transfer_reason", transfer.get("transferReason"), "转案原因不合法");
        assertDictValue("law_case_risk_level", transfer.get("riskLevel"), "风险等级不合法");
        requiredText(transfer.get("detail"), "转案详情不能为空");
        transfer.put("transferNo", "TR" + System.currentTimeMillis());
        transfer.put("fromLawyerId", existed.get("main_lawyer_id"));
        transfer.put("fromLawyerName", existed.get("main_lawyer_name"));
        transfer.put("toLawyerName", toLawyer.getNickName());
        transfer.put("transferStatus", TRANSFER_PENDING);
        transfer.put("currentNode", "法务经理审批");
        transfer.put("createBy", SecurityUtils.getUsername());
        int rows = caseMapper.insertTransfer(transfer);
        assertRowsChanged(rows, "转案申请创建失败");

        Map<String, Object> update = new HashMap<>();
        update.put("caseId", caseId);
        update.put("caseStatus", CASE_TRANSFERING);
        update.put("expectedStatus", CASE_PROCESSING);
        update.put("currentNode", "转案审批中");
        update.put("updateBy", SecurityUtils.getUsername());
        assertRowsChanged(caseMapper.updateCaseStatus(update), "案件状态已变化，请刷新后重试");
        insertStatusLog(caseId, CASE_PROCESSING, CASE_TRANSFERING, "transfer_request", "发起转案申请：" + safeText(transfer.get("transferReason"), "转案申请"));
        createNotice("转案申请待审批", "案件 " + existed.get("case_no") + " 发起转案申请：" + existed.get("main_lawyer_name") + " -> " + toLawyer.getNickName());
        publish(BusinessEventType.CASE_TRANSFER_REQUESTED, caseId, text(existed.get("case_no")),
                eventPayload("transferId", transfer.get("transferId"), "toLawyerId", toLawyerId));
        return rows;
    }

    @Override
    @Transactional
    public int approveTransfer(Map<String, Object> approval)
    {
        Long transferId = toLong(approval.get("transferId"), "请选择转案申请");
        String action = safeText(approval.get("action"), "");
        String opinion = safeText(approval.get("opinion"), "");
        if (!TRANSFER_PASSED.equals(action) && !TRANSFER_REJECTED.equals(action) && !TRANSFER_SUPPLEMENT.equals(action))
        {
            throw new ServiceException("审批动作不合法");
        }
        if (StringUtils.isEmpty(opinion))
        {
            throw new ServiceException("审批意见必填");
        }
        Map<String, Object> transfer = caseMapper.selectTransferById(transferId);
        if (transfer == null)
        {
            throw new ServiceException("转案申请不存在");
        }
        assertCaseAccess(toLong(transfer.get("case_id"), "请选择案件"));
        if (!TRANSFER_PENDING.equals(text(transfer.get("transfer_status"))))
        {
            throw new ServiceException("只有待审批转案可以处理");
        }
        approval.put("transferStatus", action);
        approval.put("expectedStatus", TRANSFER_PENDING);
        approval.put("currentNode", TRANSFER_PASSED.equals(action) ? "已通过" : (TRANSFER_SUPPLEMENT.equals(action) ? "补充材料" : "已驳回"));
        approval.put("approverId", SecurityUtils.getUserId());
        approval.put("approverName", SecurityUtils.getLoginUser().getUser().getNickName());
        approval.put("updateBy", SecurityUtils.getUsername());
        int rows = caseMapper.updateTransferApproval(approval);
        assertRowsChanged(rows, "转案状态已变化，请刷新后重试");

        Map<String, Object> caseUpdate = new HashMap<>();
        caseUpdate.put("caseId", transfer.get("case_id"));
        caseUpdate.put("expectedStatus", CASE_TRANSFERING);
        caseUpdate.put("caseStatus", CASE_PROCESSING);
        caseUpdate.put("currentNode", "案件办理中");
        caseUpdate.put("updateBy", SecurityUtils.getUsername());
        if (TRANSFER_PASSED.equals(action))
        {
            caseUpdate.put("mainLawyerId", transfer.get("to_lawyer_id"));
            caseUpdate.put("mainLawyerName", transfer.get("to_lawyer_name"));
        }
        assertRowsChanged(caseMapper.updateCaseStatus(caseUpdate), "案件状态已变化，请刷新后重试");
        insertStatusLog(toLong(transfer.get("case_id"), "请选择案件"), CASE_TRANSFERING, CASE_PROCESSING, "transfer_approve", "转案审批：" + action + "，" + opinion);
        createNotice("转案审批已处理", "转案单 " + transfer.get("transfer_no") + " 审批结果：" + transferActionLabel(action) + "，案件已回到办理流程。");
        publish(BusinessEventType.CASE_TRANSFER_APPROVED, toLong(transfer.get("case_id"), "请选择案件"),
                text(transfer.get("case_no")), eventPayload("transferId", transferId, "action", action));
        return rows;
    }

    @Override
    public List<Map<String, Object>> selectTransfers(Map<String, Object> params)
    {
        return queryService.transfers(params);
    }

    @Override
    public List<Map<String, Object>> selectConfirms(Map<String, Object> params)
    {
        return queryService.confirms(params);
    }

    @Override
    @Transactional
    public int handleConfirm(Map<String, Object> confirm)
    {
        Long confirmId = toLong(confirm.get("confirmId"), "请选择确认信息");
        String result = safeText(confirm.get("confirmResult"), "");
        if (!"accepted".equals(result) && !"rejected".equals(result))
        {
            throw new ServiceException("确认结果不合法");
        }
        Map<String, Object> confirmEntity = caseMapper.selectConfirmById(confirmId);
        if (confirmEntity == null)
        {
            throw new ServiceException("确认信息不存在");
        }
        Long caseId = toLong(confirmEntity.get("case_id"), "请选择案件");
        assertCaseAccess(caseId);
        confirm.put("expectedStatus", "pending");
        confirm.put("handlerId", SecurityUtils.getUserId());
        confirm.put("handlerName", SecurityUtils.getLoginUser().getUser().getNickName());
        confirm.put("updateBy", SecurityUtils.getUsername());
        int rows = caseMapper.updateConfirm(confirm);
        assertRowsChanged(rows, "确认信息状态已变化，请刷新后重试");

        Map<String, Object> caseUpdate = new HashMap<>();
        caseUpdate.put("caseId", caseId);
        caseUpdate.put("caseStatus", "accepted".equals(result) ? CASE_PROCESSING : CASE_PENDING);
        caseUpdate.put("currentNode", "accepted".equals(result) ? "案件办理中" : "待重新分案");
        caseUpdate.put("clearAssignment", "rejected".equals(result));
        caseUpdate.put("updateBy", SecurityUtils.getUsername());
        assertRowsChanged(caseMapper.updateCaseConfirmResult(caseUpdate), "案件确认状态已变化，请刷新后重试");
        insertStatusLog(caseId, text(confirmEntity.get("caseStatus")), "accepted".equals(result) ? CASE_PROCESSING : CASE_PENDING,
                "confirm", "律师接案确认：" + ("accepted".equals(result) ? "已接收" : "已拒绝"));
        createNotice("律师接案确认", "案件 " + confirmEntity.get("caseNo") + " 接案确认结果：" + ("accepted".equals(result) ? "已接收" : "已拒绝"));
        publish("accepted".equals(result) ? BusinessEventType.CASE_ACCEPTED : BusinessEventType.CASE_REJECTED,
                caseId, text(confirmEntity.get("caseNo")), eventPayload("confirmId", confirmId));
        return rows;
    }

    @Override
    public List<Map<String, Object>> selectStatusLogs(Map<String, Object> params)
    {
        return queryService.statusLogs(params);
    }

    private void createConfirm(Long caseId, Long userId, String userName, String type, String content)
    {
        Map<String, Object> confirm = new HashMap<>();
        confirm.put("caseId", caseId);
        confirm.put("confirmType", type);
        confirm.put("confirmStatus", "pending");
        confirm.put("confirmUserId", userId);
        confirm.put("confirmUserName", userName);
        confirm.put("content", content);
        confirm.put("createBy", SecurityUtils.getUsername());
        caseMapper.insertConfirm(confirm);
    }

    private void insertStatusLog(Long caseId, String fromStatus, String toStatus, String actionType, String content)
    {
        assertDictValue("law_case_status_action", actionType, "案件状态动作不合法");
        Map<String, Object> log = new HashMap<>();
        log.put("caseId", caseId);
        log.put("fromStatus", fromStatus);
        log.put("toStatus", toStatus);
        log.put("actionType", actionType);
        log.put("content", content);
        log.put("createBy", SecurityUtils.getUsername());
        assertRowsChanged(caseMapper.insertStatusLog(log), "案件状态记录创建失败");
    }

    private void createNotice(String title, String content)
    {
        SysNotice notice = new SysNotice();
        notice.setNoticeTitle(title);
        notice.setNoticeType("1");
        notice.setNoticeContent(content);
        notice.setStatus("0");
        notice.setCreateBy(SecurityUtils.getUsername());
        notice.setRemark("案管中心");
        noticeService.insertNotice(notice);
    }

    private String transferActionLabel(String action)
    {
        if (TRANSFER_PASSED.equals(action))
        {
            return "同意转案";
        }
        if (TRANSFER_REJECTED.equals(action))
        {
            return "驳回";
        }
        if (TRANSFER_SUPPLEMENT.equals(action))
        {
            return "补充材料";
        }
        return action;
    }

    private void assertCaseAccess(Long caseId)
    {
        if (caseId == null)
        {
            throw new ServiceException("案件不存在或已删除");
        }
        if (SecurityUtils.isAdmin())
        {
            return;
        }
        if (caseMapper.countCaseInDataScope(caseId, SecurityUtils.getUserId(), SecurityUtils.getDeptId(), true, CASE_MODULE_PERMISSIONS) == 0)
        {
            throw new ServiceException("无权访问该案件");
        }
    }

    private void applyDataScope(Map<String, Object> params)
    {
        params.put("currentUserId", SecurityUtils.getUserId());
        params.put("currentDeptId", SecurityUtils.getDeptId());
        params.put("dataScope", !SecurityUtils.isAdmin());
        params.put("permissions", CASE_MODULE_PERMISSIONS);
    }

    private String dictDefault(String dictType, String fallback)
    {
        List<SysDictData> options = dictTypeService.selectDictDataByType(dictType);
        if (options != null)
        {
            for (SysDictData item : options)
            {
                if (item.getDefault())
                {
                    return item.getDictValue();
                }
            }
        }
        return fallback;
    }

    private String dictValue(String dictType, String preferredValue)
    {
        List<SysDictData> options = dictTypeService.selectDictDataByType(dictType);
        if (options != null)
        {
            for (SysDictData item : options)
            {
                if (preferredValue != null && preferredValue.equals(item.getDictValue()))
                {
                    return item.getDictValue();
                }
            }
        }
        return dictDefault(dictType, preferredValue);
    }

    private void assertDictValue(String dictType, Object value, String message)
    {
        String valueText = value == null ? null : String.valueOf(value).trim();
        if (StringUtils.isEmpty(valueText))
        {
            throw new ServiceException(message);
        }
        List<SysDictData> options = dictTypeService.selectDictDataByType(dictType);
        if (containsDictValue(options, valueText))
        {
            return;
        }
        dictTypeService.resetDictCache();
        options = dictTypeService.selectDictDataByType(dictType);
        if (options == null || options.isEmpty())
        {
            throw new ServiceException("字典未初始化：" + dictType);
        }
        if (containsDictValue(options, valueText))
        {
            return;
        }
        throw new ServiceException(message);
    }

    private boolean containsDictValue(List<SysDictData> options, String value)
    {
        if (options == null)
        {
            return false;
        }
        for (SysDictData item : options)
        {
            if (value.equals(item.getDictValue()))
            {
                return true;
            }
        }
        return false;
    }

    private List<Long> parseIds(Object value)
    {
        List<Long> result = new ArrayList<>();
        if (value instanceof List)
        {
            for (Object item : (List<?>) value)
            {
                result.add(toLong(item, "请选择案件"));
            }
            return result;
        }
        if (value != null)
        {
            String[] ids = String.valueOf(value).split(",");
            for (String id : ids)
            {
                if (!StringUtils.isEmpty(id))
                {
                    result.add(toLong(id.trim(), "请选择案件"));
                }
            }
        }
        return result;
    }

    private SysUser assertMainLawyerEligible(Long userId)
    {
        SysUser user = assertActiveLawyerUser(userId);
        Map<String, Object> profile = caseMapper.selectLawyerProfileByUserId(userId);
        if (profile == null || profile.get("profileId") == null || StringUtils.isEmpty(String.valueOf(profile.get("profileId"))))
        {
            throw new ServiceException("请先维护律师档案");
        }
        String assignEnabled = safeText(profile.get("assignEnabled"), ASSIGN_DISABLED);
        String lawyerRole = safeText(profile.get("lawyerRole"), LAWYER_ROLE_DEFAULT);
        if (!ASSIGN_ENABLED.equals(assignEnabled))
        {
            throw new ServiceException("律师已禁用分案");
        }
        if (LAWYER_ROLE_ASSISTANT.equals(lawyerRole))
        {
            throw new ServiceException("实习律师不能作为主办律师");
        }
        return user;
    }

    private void normalizeAssistantLawyers(Map<String, Object> assignment)
    {
        List<Long> assistantIds = parseIds(assignment.get("assistantLawyerIds"));
        if (assistantIds.isEmpty())
        {
            assignment.put("assistantLawyerIds", null);
            assignment.put("assistantLawyerNames", null);
            return;
        }
        StringBuilder ids = new StringBuilder();
        StringBuilder names = new StringBuilder();
        for (Long userId : assistantIds)
        {
            SysUser user = assertActiveLawyerUser(userId);
            Map<String, Object> profile = caseMapper.selectLawyerProfileByUserId(userId);
            if (profile == null || profile.get("profileId") == null || StringUtils.isEmpty(String.valueOf(profile.get("profileId"))))
            {
                throw new ServiceException("请先维护协办律师档案");
            }
            String assignEnabled = safeText(profile.get("assignEnabled"), ASSIGN_DISABLED);
            if (!ASSIGN_ENABLED.equals(assignEnabled))
            {
                throw new ServiceException("协办律师已禁用分案");
            }
            if (ids.length() > 0)
            {
                ids.append(",");
                names.append(",");
            }
            ids.append(userId);
            names.append(user.getNickName());
        }
        assignment.put("assistantLawyerIds", ids.toString());
        assignment.put("assistantLawyerNames", names.toString());
    }

    private SysUser assertActiveLawyerUser(Long userId)
    {
        SysUser user = userService.selectUserById(userId);
        if (user == null || "1".equals(user.getStatus()))
        {
            throw new ServiceException("律师不存在或已停用");
        }
        return user;
    }

    private Number defaultNumber(Object value, Number fallback)
    {
        if (value == null || StringUtils.isEmpty(String.valueOf(value)) || "null".equalsIgnoreCase(String.valueOf(value)))
        {
            return fallback;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private Long toLong(Object value, String message)
    {
        if (value == null || StringUtils.isEmpty(String.valueOf(value)) || "null".equalsIgnoreCase(String.valueOf(value)))
        {
            throw new ServiceException(message);
        }
        return Long.valueOf(String.valueOf(value));
    }

    private Long toNullableLong(Object value)
    {
        if (value == null || StringUtils.isEmpty(String.valueOf(value)) || "null".equalsIgnoreCase(String.valueOf(value)))
        {
            return null;
        }
        return Long.valueOf(String.valueOf(value));
    }

    private String text(Object value)
    {
        return value == null ? null : String.valueOf(value);
    }

    private String safeText(Object value, String fallback)
    {
        return value == null || StringUtils.isEmpty(String.valueOf(value)) || "null".equalsIgnoreCase(String.valueOf(value)) ? fallback : String.valueOf(value);
    }

    private String requiredText(Object value, String message)
    {
        String text = value == null ? null : String.valueOf(value).trim();
        if (StringUtils.isEmpty(text) || "null".equalsIgnoreCase(text))
        {
            throw new ServiceException(message);
        }
        return text;
    }

    private void assertRowsChanged(int rows, String message)
    {
        if (rows <= 0)
        {
            throw new ServiceException(message);
        }
    }

    private void publish(BusinessEventType type, Long caseId, String caseNo, Map<String, Object> payload)
    {
        eventPublisher.publish(new BusinessEventCommand(type, "CASE", caseId, caseNo,
                type.name() + ":" + caseId + ":" + IdUtils.fastUUID(), payload));
    }

    private Map<String, Object> eventPayload(Object... values)
    {
        Map<String, Object> payload = new HashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2)
        {
            if (values[i + 1] != null) payload.put(String.valueOf(values[i]), values[i + 1]);
        }
        return payload;
    }
}
