package com.law.business.lead.dto;

public class LeadFirstContactCommand
{
    private Long leadId;
    private Long todoId;
    private String contactResult;
    private String contactName;
    private String city;
    private String legalDemand;
    private String visited;
    private String invalidReasonCode;
    private String salesExplanation;
    private LeadCallRecordCommand callRecord;

    public Long getLeadId() { return leadId; }
    public void setLeadId(Long value) { leadId = value; }
    public Long getTodoId() { return todoId; }
    public void setTodoId(Long value) { todoId = value; }
    public String getContactResult() { return contactResult; }
    public void setContactResult(String value) { contactResult = value; }
    public String getContactName() { return contactName; }
    public void setContactName(String value) { contactName = value; }
    public String getCity() { return city; }
    public void setCity(String value) { city = value; }
    public String getLegalDemand() { return legalDemand; }
    public void setLegalDemand(String value) { legalDemand = value; }
    public String getVisited() { return visited; }
    public void setVisited(String value) { visited = value; }
    public String getInvalidReasonCode() { return invalidReasonCode; }
    public void setInvalidReasonCode(String value) { invalidReasonCode = value; }
    public String getSalesExplanation() { return salesExplanation; }
    public void setSalesExplanation(String value) { salesExplanation = value; }
    public LeadCallRecordCommand getCallRecord() { return callRecord; }
    public void setCallRecord(LeadCallRecordCommand value) { callRecord = value; }
}
