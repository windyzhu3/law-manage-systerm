package com.law.business.lead.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Public wire command. Lead identity and confirmation state are server-derived. */
public class LeadTagConfirmationRequest
{
    @NotNull
    @Positive
    private Long tagRelationId;

    public Long getTagRelationId() { return tagRelationId; }
    public void setTagRelationId(Long value) { tagRelationId = value; }
}
