package com.law.business.lead.dto;

import java.time.LocalDateTime;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class LeadProgressCompleteCommand
{
    @NotNull private Long leadId;
    @NotNull private Long todoId;
    @NotBlank private String progressType;
    @NotNull private LocalDateTime progressAt;
    private String remark;

    public Long getLeadId() { return leadId; }
    public void setLeadId(Long value) { leadId=value; }
    public Long getTodoId() { return todoId; }
    public void setTodoId(Long value) { todoId=value; }
    public String getProgressType() { return progressType; }
    public void setProgressType(String value) { progressType=value; }
    public LocalDateTime getProgressAt() { return progressAt; }
    public void setProgressAt(LocalDateTime value) { progressAt=value; }
    public String getRemark() { return remark; }
    public void setRemark(String value) { remark=value; }
}
