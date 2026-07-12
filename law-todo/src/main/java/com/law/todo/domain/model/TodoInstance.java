package com.law.todo.domain.model;

import java.time.LocalDateTime;

public class TodoInstance
{
    private Long todoId; private String todoNo; private Long templateId; private Long templateVersionId;
    private String title; private String businessType; private Long businessId; private String businessNo;
    private Long ownerId; private Long ownerDeptId; private String status; private String priority; private String slaStatus;
    private LocalDateTime createdAt; private LocalDateTime dueAt; private LocalDateTime completedAt;
    private Long previousTodoId; private Long rootTodoId; private String triggerEventId; private String triggerIdempotencyKey;
    private String nextIdempotencyKey; private String dodSnapshotJson; private Integer version;
    public Long getTodoId(){return todoId;} public void setTodoId(Long v){todoId=v;}
    public String getTodoNo(){return todoNo;} public void setTodoNo(String v){todoNo=v;}
    public Long getTemplateId(){return templateId;} public void setTemplateId(Long v){templateId=v;}
    public Long getTemplateVersionId(){return templateVersionId;} public void setTemplateVersionId(Long v){templateVersionId=v;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getBusinessType(){return businessType;} public void setBusinessType(String v){businessType=v;}
    public Long getBusinessId(){return businessId;} public void setBusinessId(Long v){businessId=v;}
    public String getBusinessNo(){return businessNo;} public void setBusinessNo(String v){businessNo=v;}
    public Long getOwnerId(){return ownerId;} public void setOwnerId(Long v){ownerId=v;}
    public Long getOwnerDeptId(){return ownerDeptId;} public void setOwnerDeptId(Long v){ownerDeptId=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getPriority(){return priority;} public void setPriority(String v){priority=v;}
    public String getSlaStatus(){return slaStatus;} public void setSlaStatus(String v){slaStatus=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
    public LocalDateTime getDueAt(){return dueAt;} public void setDueAt(LocalDateTime v){dueAt=v;}
    public LocalDateTime getCompletedAt(){return completedAt;} public void setCompletedAt(LocalDateTime v){completedAt=v;}
    public Long getPreviousTodoId(){return previousTodoId;} public void setPreviousTodoId(Long v){previousTodoId=v;}
    public Long getRootTodoId(){return rootTodoId;} public void setRootTodoId(Long v){rootTodoId=v;}
    public String getTriggerEventId(){return triggerEventId;} public void setTriggerEventId(String v){triggerEventId=v;}
    public String getTriggerIdempotencyKey(){return triggerIdempotencyKey;} public void setTriggerIdempotencyKey(String v){triggerIdempotencyKey=v;}
    public String getNextIdempotencyKey(){return nextIdempotencyKey;} public void setNextIdempotencyKey(String v){nextIdempotencyKey=v;}
    public String getDodSnapshotJson(){return dodSnapshotJson;} public void setDodSnapshotJson(String v){dodSnapshotJson=v;}
    public Integer getVersion(){return version;} public void setVersion(Integer v){version=v;}
}
