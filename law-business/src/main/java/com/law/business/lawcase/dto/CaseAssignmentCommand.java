package com.law.business.lawcase.dto;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CaseAssignmentCommand
{
    private Long caseId;
    private List<Long> caseIds;
    @NotNull(message = "请选择主办律师") private Long mainLawyerId;
    @NotBlank(message = "请选择分配方式") private String assignMethod;
    @NotBlank(message = "请选择优先级") private String priority;
    @NotBlank(message = "请选择分配原因") private String assignReason;
    private BigDecimal estimatedWorkload;
    private String notifyFlag;
    private String assistantLawyerIds;
    private String assistantLawyerNames;
    private String remark;

    public Map<String, Object> toPersistenceMap()
    {
        Map<String, Object> value = new HashMap<>();
        value.put("caseId", caseId); value.put("caseIds", caseIds);
        value.put("mainLawyerId", mainLawyerId); value.put("assignMethod", assignMethod);
        value.put("priority", priority); value.put("assignReason", assignReason);
        value.put("estimatedWorkload", estimatedWorkload); value.put("notifyFlag", notifyFlag);
        value.put("assistantLawyerIds", assistantLawyerIds); value.put("assistantLawyerNames", assistantLawyerNames);
        value.put("remark", remark);
        return value;
    }

    public Long getCaseId(){return caseId;} public void setCaseId(Long v){caseId=v;}
    public List<Long> getCaseIds(){return caseIds;} public void setCaseIds(List<Long> v){caseIds=v;}
    public Long getMainLawyerId(){return mainLawyerId;} public void setMainLawyerId(Long v){mainLawyerId=v;}
    public String getAssignMethod(){return assignMethod;} public void setAssignMethod(String v){assignMethod=v;}
    public String getPriority(){return priority;} public void setPriority(String v){priority=v;}
    public String getAssignReason(){return assignReason;} public void setAssignReason(String v){assignReason=v;}
    public BigDecimal getEstimatedWorkload(){return estimatedWorkload;} public void setEstimatedWorkload(BigDecimal v){estimatedWorkload=v;}
    public String getNotifyFlag(){return notifyFlag;} public void setNotifyFlag(String v){notifyFlag=v;}
    public String getAssistantLawyerIds(){return assistantLawyerIds;} public void setAssistantLawyerIds(String v){assistantLawyerIds=v;}
    public String getAssistantLawyerNames(){return assistantLawyerNames;} public void setAssistantLawyerNames(String v){assistantLawyerNames=v;}
    public String getRemark(){return remark;} public void setRemark(String v){remark=v;}
}
