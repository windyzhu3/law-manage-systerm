package com.law.business.lead.dto;
import jakarta.validation.constraints.NotNull;
public class LeadPoolCommand {
    @NotNull(message="请选择线索") private Long leadId;
    private String reason;
    public Long getLeadId(){return leadId;} public void setLeadId(Long value){leadId=value;}
    public String getReason(){return reason;} public void setReason(String value){reason=value;}
}
