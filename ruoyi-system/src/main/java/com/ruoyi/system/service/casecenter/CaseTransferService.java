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
import com.law.business.shared.status.CaseTransferStatus;
import com.law.business.security.BusinessActor;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.uuid.IdUtils;
import com.ruoyi.system.mapper.BizCaseMapper;
import com.ruoyi.system.service.ISysUserService;

/** Transaction boundary for transfer requests and approvals. */
@Service
public class CaseTransferService
{
    @Autowired private BizCaseMapper mapper;
    @Autowired private CaseQueryService queryService;
    @Autowired private ISysUserService userService;
    @Autowired private CaseWorkflowSupport support;
    @Autowired private BusinessEventPublisher publisher;

    @Transactional
    public int request(Map<String, Object> command)
    {
        Long caseId = id(command.get("caseId"), "请选择案件");
        Map<String, Object> existed = queryService.caseDetail(caseId);
        if (!CaseStatus.PROCESSING.code().equals(text(existed.get("case_status")))) fail("STATE_CONFLICT", "只有办理中的案件可以发起转案");
        Long targetId = id(command.get("toLawyerId"), "请选择拟转入律师");
        if (targetId.equals(nullableId(existed.get("main_lawyer_id")))) fail("VALIDATION_FAILED", "拟转入律师不能与当前主办律师相同");
        SysUser target = eligibleLawyer(targetId);
        support.requireDict("law_case_transfer_reason", command.get("transferReason"), "转案原因不合法");
        support.requireDict("law_case_risk_level", command.get("riskLevel"), "风险等级不合法");
        if (StringUtils.isEmpty(text(command.get("detail")))) fail("VALIDATION_FAILED", "转案详情不能为空");
        command.put("transferNo", "TR" + System.currentTimeMillis());
        command.put("fromLawyerId", existed.get("main_lawyer_id")); command.put("fromLawyerName", existed.get("main_lawyer_name"));
        command.put("toLawyerName", target.getNickName()); command.put("transferStatus", CaseTransferStatus.PENDING.code());
        command.put("currentNode", "法务经理审批"); command.put("createBy", SecurityUtils.getUsername());
        int rows = mapper.insertTransfer(command); changed(rows, "转案申请创建失败");
        Map<String, Object> update = statusUpdate(caseId, CaseStatus.PROCESSING.code(), CaseStatus.TRANSFERRING.code(), "转案审批中");
        changed(mapper.updateCaseStatus(update), "案件状态已变化，请刷新后重试");
        support.statusLog(caseId, CaseStatus.PROCESSING.code(), CaseStatus.TRANSFERRING.code(), "transfer_request", "发起转案申请：" + command.get("transferReason"));
        support.notice("转案申请待审批", "案件 " + existed.get("case_no") + " 发起转案申请：" + existed.get("main_lawyer_name") + " -> " + target.getNickName());
        publish(BusinessEventType.CASE_TRANSFER_REQUESTED, caseId, text(existed.get("case_no")), data("transferId", command.get("transferId"), "toLawyerId", targetId));
        return rows;
    }

    @Transactional
    public int approve(Map<String, Object> command)
    {return approve(command,currentActor());}

    @Transactional
    public int approve(Map<String,Object> source,BusinessActor actor)
    {
        Map<String,Object> command=new HashMap<>(source);
        Long transferId = id(command.get("transferId"), "请选择转案申请");
        String action = text(command.get("action")), opinion = text(command.get("opinion"));
        if (!validAction(action)) fail("VALIDATION_FAILED", "审批动作不合法");
        if (StringUtils.isEmpty(opinion)) fail("VALIDATION_FAILED", "审批意见必填");
        Map<String, Object> transfer = mapper.selectTransferById(transferId);
        if (transfer == null) fail("DATA_NOT_FOUND", "转案申请不存在");
        Long caseId = id(transfer.get("case_id"), "请选择案件"); if(mapper.selectCaseById(caseId)==null)fail("DATA_NOT_FOUND","案件不存在");
        if (!CaseTransferStatus.PENDING.code().equals(text(transfer.get("transfer_status")))) fail("STATE_CONFLICT", "只有待审批转案可以处理");
        command.put("transferStatus", action); command.put("expectedStatus", CaseTransferStatus.PENDING.code()); command.put("currentNode", label(action));
        command.put("approverId", actor.userId()); command.put("approverName", actor.displayName()); command.put("updateBy", actor.userName());
        int rows = mapper.updateTransferApproval(command); changed(rows, "转案状态已变化，请刷新后重试");
        boolean passed=CaseTransferStatus.PASSED.code().equals(action);String targetStatus=passed?CaseStatus.CONFIRMING.code():CaseStatus.PROCESSING.code();
        Map<String, Object> update = statusUpdate(caseId, CaseStatus.TRANSFERRING.code(), targetStatus, passed?"待新律师确认":"案件办理中",actor.userName());
        if (passed) { update.put("mainLawyerId", transfer.get("to_lawyer_id")); update.put("mainLawyerName", transfer.get("to_lawyer_name")); }
        changed(mapper.updateCaseStatus(update), "案件状态已变化，请刷新后重试");
        support.statusLog(caseId, CaseStatus.TRANSFERRING.code(), targetStatus, "transfer_approve", "转案审批：" + action + "，" + opinion,actor);
        support.notice("转案审批已处理", "转案单 " + transfer.get("transfer_no") + " 审批结果：" + label(action) + "，案件已回到办理流程。",actor);
        Long confirmId=null;if(passed){Map<String,Object> confirm=new HashMap<>();confirm.put("caseId",caseId);confirm.put("confirmType","transfer_accept");confirm.put("confirmStatus","pending");confirm.put("confirmUserId",transfer.get("to_lawyer_id"));confirm.put("confirmUserName",transfer.get("to_lawyer_name"));confirm.put("content","请确认接收转入案件");confirm.put("createBy",actor.userName());changed(mapper.insertConfirm(confirm),"转案接收确认创建失败");confirmId=Long.valueOf(String.valueOf(confirm.get("confirmId")));}
        publish(BusinessEventType.CASE_TRANSFER_APPROVED, caseId, text(transfer.get("case_no")), data("transferId", transferId,"confirmId",confirmId,"action", action,"mainLawyerId",transfer.get("to_lawyer_id"),"requiresAcceptance",passed));
        return rows;
    }

    private SysUser eligibleLawyer(Long id) { SysUser u=userService.selectUserById(id); if(u==null||"1".equals(u.getStatus())) fail("PRECONDITION_FAILED","律师不存在或已停用"); Map<String,Object> p=mapper.selectLawyerProfileByUserId(id); if(p==null||p.get("profileId")==null) fail("PRECONDITION_FAILED","请先维护律师档案"); if(!"Y".equals(text(p.get("assignEnabled")))) fail("PRECONDITION_FAILED","律师已禁用分案"); if("assistant".equals(text(p.get("lawyerRole")))) fail("PRECONDITION_FAILED","实习律师不能作为主办律师"); return u; }
    private Map<String,Object> statusUpdate(Long id,String expected,String target,String node){Map<String,Object> m=new HashMap<>();m.put("caseId",id);m.put("expectedStatus",expected);m.put("caseStatus",target);m.put("currentNode",node);m.put("updateBy",SecurityUtils.getUsername());return m;}
    private Map<String,Object> statusUpdate(Long id,String expected,String target,String node,String operator){Map<String,Object> m=new HashMap<>();m.put("caseId",id);m.put("expectedStatus",expected);m.put("caseStatus",target);m.put("currentNode",node);m.put("updateBy",operator);return m;}
    private boolean validAction(String a){return CaseTransferStatus.PASSED.code().equals(a)||CaseTransferStatus.REJECTED.code().equals(a)||CaseTransferStatus.SUPPLEMENT.code().equals(a);}
    private String label(String a){if(CaseTransferStatus.PASSED.code().equals(a))return "已通过";if(CaseTransferStatus.SUPPLEMENT.code().equals(a))return "补充材料";return "已驳回";}
    private void publish(BusinessEventType t,Long id,String no,Map<String,Object> d){publisher.publish(new BusinessEventCommand(t,"CASE",id,no,t.name()+":"+id+":"+IdUtils.fastUUID(),d));}
    private Map<String,Object> data(Object...v){Map<String,Object> m=new HashMap<>();for(int i=0;i+1<v.length;i+=2)if(v[i+1]!=null)m.put(String.valueOf(v[i]),v[i+1]);return m;}
    private Long id(Object v,String msg){try{if(v==null)throw new NumberFormatException();return Long.valueOf(String.valueOf(v));}catch(NumberFormatException e){fail("VALIDATION_FAILED",msg);return null;}}
    private Long nullableId(Object v){return v==null||StringUtils.isEmpty(String.valueOf(v))?null:Long.valueOf(String.valueOf(v));}
    private String text(Object v){return v==null?null:String.valueOf(v);}
    private void changed(int rows,String msg){if(rows<=0)fail("CONCURRENT_MODIFICATION",msg);}
    private void fail(String code,String msg){ServiceException e=new ServiceException(msg);e.setBusinessCode(code);throw e;}
    private BusinessActor currentActor(){try{return new BusinessActor(SecurityUtils.getUserId(),SecurityUtils.getUsername(),SecurityUtils.getLoginUser().getUser().getNickName(),SecurityUtils.getDeptId(),SecurityUtils.isAdmin());}catch(RuntimeException absent){return new BusinessActor(0L,"system","system",null,false);}}
}
