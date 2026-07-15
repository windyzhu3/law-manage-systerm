package com.law.business.lawcase.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public class CaseBatchAssignmentCommand
{
    @NotEmpty(message = "请选择案件")
    private List<@NotNull(message = "请选择案件") Long> caseIds;
    @NotNull(message = "请选择主办律师") private Long mainLawyerId;
    @NotBlank(message = "请选择分配方式") private String assignMethod;
    @NotBlank(message = "请选择优先级") private String priority;
    @NotBlank(message = "请选择分配原因") private String assignReason;
    @DecimalMin(value = "0.01", message = "预计工作量必须大于0") private BigDecimal estimatedWorkload;
    private String estimatedCycle;
    private LocalDate planStartDate;
    private String notifyFlag;
    private String assistantLawyerIds;
    private String assistantLawyerNames;
    private String remark;

    public List<Long> getCaseIds(){return caseIds;} public void setCaseIds(List<Long> v){caseIds=v;}
    public Long getMainLawyerId(){return mainLawyerId;} public void setMainLawyerId(Long v){mainLawyerId=v;}
    public String getAssignMethod(){return assignMethod;} public void setAssignMethod(String v){assignMethod=v;}
    public String getPriority(){return priority;} public void setPriority(String v){priority=v;}
    public String getAssignReason(){return assignReason;} public void setAssignReason(String v){assignReason=v;}
    public BigDecimal getEstimatedWorkload(){return estimatedWorkload;} public void setEstimatedWorkload(BigDecimal v){estimatedWorkload=v;}
    public String getEstimatedCycle(){return estimatedCycle;} public void setEstimatedCycle(String v){estimatedCycle=v;}
    public LocalDate getPlanStartDate(){return planStartDate;} public void setPlanStartDate(LocalDate v){planStartDate=v;}
    public String getNotifyFlag(){return notifyFlag;} public void setNotifyFlag(String v){notifyFlag=v;}
    public String getAssistantLawyerIds(){return assistantLawyerIds;} public void setAssistantLawyerIds(String v){assistantLawyerIds=v;}
    public String getAssistantLawyerNames(){return assistantLawyerNames;} public void setAssistantLawyerNames(String v){assistantLawyerNames=v;}
    public String getRemark(){return remark;} public void setRemark(String v){remark=v;}
}
