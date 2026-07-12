package com.law.business.lead.dto;
import jakarta.validation.constraints.NotNull;
public class LeadAssignCommand {
    @NotNull(message="请选择线索") private Long leadId;
    @NotNull(message="请选择负责人") private Long ownerId;
    private String reason;
    public Long getLeadId(){return leadId;} public void setLeadId(Long value){leadId=value;}
    public Long getOwnerId(){return ownerId;} public void setOwnerId(Long value){ownerId=value;}
    public String getReason(){return reason;} public void setReason(String value){reason=value;}
}
