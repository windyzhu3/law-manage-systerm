package com.ruoyi.system.domain;

import java.util.Date;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * Lead follow-up record.
 */
public class BizLeadFollowup extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long followupId;
    private Long leadId;
    private String leadName;
    private String followType;
    private String followResult;
    private LocalDateTime progressAt;
    private String content;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date nextFollowTime;
    private Long followUserId;
    private Long sourceTodoId;
    private Long schedulePlanId;
    private String idempotencyKey;
    private String followUserName;
    private String taskStatus;
    private Long currentUserId;
    private String listMode;

    public Long getFollowupId() { return followupId; }
    public void setFollowupId(Long followupId) { this.followupId = followupId; }
    public Long getLeadId() { return leadId; }
    public void setLeadId(Long leadId) { this.leadId = leadId; }
    public String getLeadName() { return leadName; }
    public void setLeadName(String leadName) { this.leadName = leadName; }
    public String getFollowType() { return followType; }
    public void setFollowType(String followType) { this.followType = followType; }
    public String getFollowResult() { return followResult; }
    public void setFollowResult(String followResult) { this.followResult = followResult; }
    public LocalDateTime getProgressAt() { return progressAt; }
    public void setProgressAt(LocalDateTime value) { progressAt = value; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Date getNextFollowTime() { return nextFollowTime; }
    public void setNextFollowTime(Date nextFollowTime) { this.nextFollowTime = nextFollowTime; }
    public Long getFollowUserId() { return followUserId; }
    public void setFollowUserId(Long followUserId) { this.followUserId = followUserId; }
    public Long getSourceTodoId() { return sourceTodoId; }
    public void setSourceTodoId(Long value) { sourceTodoId = value; }
    public Long getSchedulePlanId() { return schedulePlanId; }
    public void setSchedulePlanId(Long value) { schedulePlanId = value; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String value) { idempotencyKey = value; }
    public String getFollowUserName() { return followUserName; }
    public void setFollowUserName(String followUserName) { this.followUserName = followUserName; }
    public String getTaskStatus() { return taskStatus; }
    public void setTaskStatus(String taskStatus) { this.taskStatus = taskStatus; }
    public Long getCurrentUserId() { return currentUserId; }
    public void setCurrentUserId(Long currentUserId) { this.currentUserId = currentUserId; }
    public String getListMode() { return listMode; }
    public void setListMode(String listMode) { this.listMode = listMode; }
}

