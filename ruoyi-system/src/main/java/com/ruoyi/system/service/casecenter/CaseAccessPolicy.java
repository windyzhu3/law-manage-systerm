package com.ruoyi.system.service.casecenter;

import java.util.Map;
import org.springframework.stereotype.Service;
import com.law.business.security.BusinessActor;
import com.law.business.security.BusinessActorProvider;
import com.law.business.security.CasePermissions;
import com.law.business.shared.error.BusinessErrorCode;
import com.law.business.shared.status.CaseStatus;
import com.law.business.shared.status.CaseTransferStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.mapper.BizCaseMapper;

@Service
public class CaseAccessPolicy
{
    private final BizCaseMapper mapper;
    private final BusinessActorProvider actors;

    public CaseAccessPolicy(BizCaseMapper mapper, BusinessActorProvider actors)
    {
        this.mapper = mapper;
        this.actors = actors;
    }

    public Map<String,Object> requireReadable(Long caseId)
    {
        return requireReadable(caseId, actors.current());
    }

    public Map<String,Object> requireReadable(Long caseId, BusinessActor actor)
    {
        Map<String,Object> value = caseId == null ? null : mapper.selectCaseById(caseId);
        if (value == null) throw error(BusinessErrorCode.DATA_NOT_FOUND, "案件不存在或已删除");
        if (!actor.administrator() && mapper.countCaseInDataScope(caseId, actor.userId(), actor.deptId(),
                true, CasePermissions.DATA_SCOPE) == 0)
            throw error(BusinessErrorCode.ACCESS_DENIED, "无权访问该案件");
        return value;
    }

    public Map<String,Object> requireAssignable(Long caseId, BusinessActor actor)
    {
        Map<String,Object> value = requireReadable(caseId, actor);
        if (!CaseStatus.PENDING.code().equals(text(value, "case_status", "caseStatus")))
            throw error(BusinessErrorCode.STATE_CONFLICT, "只有待分案案件可以分配，办理中案件请走转案审批");
        return value;
    }

    public Map<String,Object> requireTransferRequestable(Long caseId, BusinessActor actor)
    {
        Map<String,Object> value = requireReadable(caseId, actor);
        if (!CaseStatus.PROCESSING.code().equals(text(value, "case_status", "caseStatus")))
            throw error(BusinessErrorCode.STATE_CONFLICT, "只有办理中的案件可以发起转案");
        if (!actor.administrator())
        {
            Long ownerId = longValue(value(value, "owner_id", "ownerId"));
            Long lawyerId = longValue(value(value, "main_lawyer_id", "mainLawyerId"));
            if (!actor.userId().equals(ownerId) && !actor.userId().equals(lawyerId))
                throw error(BusinessErrorCode.ACCESS_DENIED, "只有案件负责人或当前主办律师可以发起转案");
        }
        return value;
    }

    public CaseTransferContext requireTransferApprovable(Long transferId, BusinessActor actor)
    {
        Map<String,Object> transfer = transferId == null ? null : mapper.selectTransferById(transferId);
        if (transfer == null) throw error(BusinessErrorCode.DATA_NOT_FOUND, "转案申请不存在");
        if (!CaseTransferStatus.PENDING.code().equals(text(transfer, "transfer_status", "transferStatus")))
            throw error(BusinessErrorCode.STATE_CONFLICT, "只有待审批转案可以处理");
        Long caseId = requiredLong(value(transfer, "case_id", "caseId"), "转案申请未关联有效案件");
        Map<String,Object> lawCase = requireReadable(caseId, actor);
        return new CaseTransferContext(transferId, caseId, text(lawCase, "case_no", "caseNo"),
                text(transfer, "transfer_no", "transferNo"),
                text(transfer, "transfer_status", "transferStatus"),
                longValue(value(transfer, "from_lawyer_id", "fromLawyerId")),
                text(transfer, "from_lawyer_name", "fromLawyerName"),
                longValue(value(transfer, "to_lawyer_id", "toLawyerId")),
                text(transfer, "to_lawyer_name", "toLawyerName"));
    }

    public CaseConfirmContext requireConfirmable(Long confirmId, BusinessActor actor)
    {
        Map<String,Object> confirm = confirmId == null ? null : mapper.selectConfirmById(confirmId);
        if (confirm == null) throw error(BusinessErrorCode.DATA_NOT_FOUND, "确认信息不存在");
        if (!"pending".equals(text(confirm, "confirm_status", "confirmStatus")))
            throw error(BusinessErrorCode.STATE_CONFLICT, "确认信息已处理");
        Long caseId = requiredLong(value(confirm, "case_id", "caseId"), "确认信息未关联有效案件");
        Map<String,Object> lawCase = requireReadable(caseId, actor);
        if (!CaseStatus.CONFIRMING.code().equals(text(lawCase, "case_status", "caseStatus")))
            throw error(BusinessErrorCode.STATE_CONFLICT, "案件已不处于待确认状态");
        Long confirmUserId = requiredLong(value(confirm, "confirm_user_id", "confirmUserId"), "确认信息未指定处理人");
        if (!actor.administrator() && !actor.userId().equals(confirmUserId))
            throw error(BusinessErrorCode.ACCESS_DENIED, "只有指定接案人员可以处理确认");
        return new CaseConfirmContext(confirmId, caseId, text(lawCase, "case_no", "caseNo"),
                text(confirm, "confirm_status", "confirmStatus"), confirmUserId,
                text(confirm, "confirm_user_name", "confirmUserName"),
                text(confirm, "confirm_type", "confirmType"));
    }

    private Object value(Map<String,Object> map, String snake, String camel)
    {
        return map.containsKey(snake) ? map.get(snake) : map.get(camel);
    }

    private String text(Map<String,Object> map, String snake, String camel)
    {
        Object value = value(map, snake, camel);
        return value == null ? null : String.valueOf(value);
    }

    private Long longValue(Object value)
    {
        return value == null ? null : value instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(value));
    }

    private Long requiredLong(Object value, String message)
    {
        if (value == null) throw error(BusinessErrorCode.DATA_NOT_FOUND, message);
        try { return longValue(value); }
        catch (RuntimeException exception) { throw error(BusinessErrorCode.DATA_NOT_FOUND, message); }
    }

    private ServiceException error(BusinessErrorCode code, String message)
    {
        return new ServiceException(message, code.name());
    }
}
