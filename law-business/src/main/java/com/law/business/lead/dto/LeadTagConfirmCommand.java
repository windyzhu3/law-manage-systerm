package com.law.business.lead.dto;

public class LeadTagConfirmCommand
{
    private Long leadId;
    private Long tagRelationId;
    private String confirmStatus;

    public Long getLeadId() { return leadId; }
    public void setLeadId(Long leadId) { this.leadId = leadId; }
    public Long getTagRelationId() { return tagRelationId; }
    public void setTagRelationId(Long tagRelationId) { this.tagRelationId = tagRelationId; }
    public String getConfirmStatus() { return confirmStatus; }
    public void setConfirmStatus(String confirmStatus) { this.confirmStatus = confirmStatus; }
}
