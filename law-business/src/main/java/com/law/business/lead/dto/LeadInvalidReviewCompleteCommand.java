package com.law.business.lead.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** TD-002 completion input. Todo, review and lead identities come from persisted provenance. */
public class LeadInvalidReviewCompleteCommand
{
    @NotBlank
    @Size(max = 128)
    private String actionId;
    @NotBlank
    @Size(max = 32)
    private String reviewResult;
    @NotBlank
    @Size(max = 1000)
    @JsonAlias("reviewComment")
    private String reviewOpinion;
    private List<Long> fileObjectIds;

    public String getActionId() { return actionId; }
    public void setActionId(String value) { actionId = value; }
    public String getReviewResult() { return reviewResult; }
    public void setReviewResult(String value) { reviewResult = value; }
    public String getReviewOpinion() { return reviewOpinion; }
    public void setReviewOpinion(String value) { reviewOpinion = value; }
    public List<Long> getFileObjectIds() { return fileObjectIds; }
    public void setFileObjectIds(List<Long> value) { fileObjectIds = value; }
}
