package com.law.business.lead.dto;

import java.time.LocalDateTime;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Manual call entry. Channel, lead, actor and provider provenance are server-owned. */
public class LeadManualCallRecordCommand
{
    @NotBlank
    @Size(max = 128)
    private String actionId;
    @NotNull
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    @PositiveOrZero
    private Integer durationSeconds;
    @Size(max = 32)
    private String callResult;
    private Long recordingFileObjectId;
    @Size(max = 1000)
    private String manualNotes;

    public String getActionId() { return actionId; }
    public void setActionId(String value) { actionId = value; }
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
}
