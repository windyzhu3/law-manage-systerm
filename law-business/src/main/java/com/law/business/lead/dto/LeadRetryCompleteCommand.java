package com.law.business.lead.dto;

import java.util.Date;

public class LeadRetryCompleteCommand
{
    private Long leadId;
    private Long todoId;
    private Long occurrenceId;
    private Integer occurrenceNo;
    private Long planId;
    private String windowCode;
    private Integer attemptNo;
    private String result;
    private String nextWindowCode;
    private Date nextRetryTime;
    private String contactName;
    private String city;
    private String legalDemand;
    private String visited;
    private LeadCallRecordCommand callRecord;

    public Long getLeadId() { return leadId; }
    public void setLeadId(Long value) { leadId = value; }
    public Long getTodoId() { return todoId; }
    public void setTodoId(Long value) { todoId = value; }
    public Long getOccurrenceId() { return occurrenceId; }
    public void setOccurrenceId(Long value) { occurrenceId = value; }
    public Integer getOccurrenceNo() { return occurrenceNo; }
    public void setOccurrenceNo(Integer value) { occurrenceNo = value; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long value) { planId = value; }
    public String getWindowCode() { return windowCode; }
    public void setWindowCode(String value) { windowCode = value; }
    public Integer getAttemptNo() { return attemptNo; }
    public void setAttemptNo(Integer value) { attemptNo = value; }
    public String getResult() { return result; }
    public void setResult(String value) { result = value; }
    public String getNextWindowCode() { return nextWindowCode; }
    public void setNextWindowCode(String value) { nextWindowCode = value; }
    public Date getNextRetryTime() { return nextRetryTime; }
    public void setNextRetryTime(Date value) { nextRetryTime = value; }
    public String getContactName() { return contactName; }
    public void setContactName(String value) { contactName = value; }
    public String getCity() { return city; }
    public void setCity(String value) { city = value; }
    public String getLegalDemand() { return legalDemand; }
    public void setLegalDemand(String value) { legalDemand = value; }
    public String getVisited() { return visited; }
    public void setVisited(String value) { visited = value; }
    public LeadCallRecordCommand getCallRecord() { return callRecord; }
    public void setCallRecord(LeadCallRecordCommand value) { callRecord = value; }
}
