package com.law.business.lead.dto;

import java.time.LocalDateTime;

public class LeadCallRecordCommand
{
    private Long leadId;
    private Long todoId;
    private String callChannel;
    private String externalCallId;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Integer durationSeconds;
    private String callResult;
    private Long recordingFileObjectId;
    private String manualNotes;
    private String providerSummaryHash;
    private String businessOccurrenceKey;

    public Long getLeadId() { return leadId; }
    public void setLeadId(Long value) { leadId = value; }
    public Long getTodoId() { return todoId; }
    public void setTodoId(Long value) { todoId = value; }
    public String getCallChannel() { return callChannel; }
    public void setCallChannel(String value) { callChannel = value; }
    public String getExternalCallId() { return externalCallId; }
    public void setExternalCallId(String value) { externalCallId = value; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime value) { startedAt = value; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime value) { endedAt = value; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer value) { durationSeconds = value; }
    public String getCallResult() { return callResult; }
    public void setCallResult(String value) { callResult = value; }
    public Long getRecordingFileObjectId() { return recordingFileObjectId; }
    public void setRecordingFileObjectId(Long value) { recordingFileObjectId = value; }
    public String getManualNotes() { return manualNotes; }
    public void setManualNotes(String value) { manualNotes = value; }
    public String getProviderSummaryHash() { return providerSummaryHash; }
    public void setProviderSummaryHash(String value) { providerSummaryHash = value; }
    public String getBusinessOccurrenceKey() { return businessOccurrenceKey; }
    public void setBusinessOccurrenceKey(String value) { businessOccurrenceKey = value; }
}
