package com.ruoyi.system.domain;

public class LeadAssignmentPolicyCandidateView
{
    private Long policyId;
    private Long userId;
    private String userName;
    private String nickName;
    private Integer sortOrder;
    private String availabilityStatus;
    private Long delegateUserId;

    public Long getPolicyId() { return policyId; }
    public void setPolicyId(Long value) { policyId = value; }
    public Long getUserId() { return userId; }
    public void setUserId(Long value) { userId = value; }
    public String getUserName() { return userName; }
    public void setUserName(String value) { userName = value; }
    public String getNickName() { return nickName; }
    public void setNickName(String value) { nickName = value; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer value) { sortOrder = value; }
    public String getAvailabilityStatus() { return availabilityStatus; }
    public void setAvailabilityStatus(String value) { availabilityStatus = value; }
    public Long getDelegateUserId() { return delegateUserId; }
    public void setDelegateUserId(Long value) { delegateUserId = value; }
}
