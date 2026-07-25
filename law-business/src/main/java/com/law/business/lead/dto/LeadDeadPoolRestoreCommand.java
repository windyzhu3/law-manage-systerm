package com.law.business.lead.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Restores one persisted Dead-Pool lead to the public pool. */
public class LeadDeadPoolRestoreCommand
{
    @NotBlank
    @Size(max = 128)
    private String actionId;
    @NotBlank
    @Size(max = 1000)
    private String reason;

    public String getActionId() { return actionId; }
    public void setActionId(String value) { actionId = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }
}
