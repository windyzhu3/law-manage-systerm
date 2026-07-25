package com.law.business.lead.dto;

import java.util.List;
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
    @Size(max = 1000)
    private String reviewComment;
    private List<Long> fileObjectIds;

    public String getActionId() { return actionId; }
    public void setActionId(String value) { actionId = value; }
    public String getReviewResult() { return reviewResult; }
    public void setReviewResult(String value) { reviewResult = value; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String value) { reviewComment = value; }
    public List<Long> getFileObjectIds() { return fileObjectIds; }
    public void setFileObjectIds(List<Long> value) { fileObjectIds = value; }
}
