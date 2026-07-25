package com.law.business.lead.dto;

public class LeadInvalidReviewCommand
{
    private Long leadId;
    private Long reviewId;
    private Long todoId;
    private String reviewResult;
    private String reviewComment;

    public Long getLeadId() { return leadId; }
    public void setLeadId(Long value) { leadId = value; }
    public Long getReviewId() { return reviewId; }
    public void setReviewId(Long value) { reviewId = value; }
    public Long getTodoId() { return todoId; }
    public void setTodoId(Long value) { todoId = value; }
    public String getReviewResult() { return reviewResult; }
    public void setReviewResult(String value) { reviewResult = value; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String value) { reviewComment = value; }
}
