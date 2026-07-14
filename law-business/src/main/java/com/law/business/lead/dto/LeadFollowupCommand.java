package com.law.business.lead.dto;

import java.util.Date;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class LeadFollowupCommand
{
    private Long followupId;

    @NotNull(message = "线索不能为空")
    private Long leadId;

    @NotBlank(message = "跟进方式不能为空")
    private String followType;

    @NotBlank(message = "跟进结果不能为空")
    private String followResult;

    @NotBlank(message = "跟进内容不能为空")
    @Size(max = 1000, message = "跟进内容不能超过1000个字符")
    private String content;

    private Date nextFollowTime;
    private String taskStatus;

    public Long getFollowupId() { return followupId; }
    public void setFollowupId(Long followupId) { this.followupId = followupId; }
    public Long getLeadId() { return leadId; }
    public void setLeadId(Long leadId) { this.leadId = leadId; }
    public String getFollowType() { return followType; }
    public void setFollowType(String followType) { this.followType = followType; }
    public String getFollowResult() { return followResult; }
    public void setFollowResult(String followResult) { this.followResult = followResult; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Date getNextFollowTime() { return nextFollowTime; }
    public void setNextFollowTime(Date nextFollowTime) { this.nextFollowTime = nextFollowTime; }
    public String getTaskStatus() { return taskStatus; }
    public void setTaskStatus(String taskStatus) { this.taskStatus = taskStatus; }
}
