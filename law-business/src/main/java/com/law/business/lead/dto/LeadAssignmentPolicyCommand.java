package com.law.business.lead.dto;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Versioned assignment and retry policy. Business type and active state are server-owned. */
public class LeadAssignmentPolicyCommand
{
    private Long policyId;
    @NotBlank
    @Size(max = 128)
    private String policyName;
    @NotNull
    @Positive
    private Long salesDeptId;
    @NotBlank
    @Size(max = 40)
    private String sourceCode;
    @NotNull
    @Min(0)
    private Integer expectedVersion;
    @NotNull
    @Positive
    private Long templateVersionId;
    @NotNull
    @Positive
    private Long ruleVersionId;
    @NotBlank
    @Size(max = 64)
    private String timezone;
    @NotEmpty
    private List<@Positive Long> candidateUserIds;
    @NotEmpty
    @Valid
    private List<RetryWindow> windows;

    public Long getPolicyId() { return policyId; }
    public void setPolicyId(Long value) { policyId = value; }
    public String getPolicyName() { return policyName; }
    public void setPolicyName(String value) { policyName = value; }
    public Long getSalesDeptId() { return salesDeptId; }
    public void setSalesDeptId(Long value) { salesDeptId = value; }
    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String value) { sourceCode = value; }
    public Integer getExpectedVersion() { return expectedVersion; }
    public void setExpectedVersion(Integer value) { expectedVersion = value; }
    public Long getTemplateVersionId() { return templateVersionId; }
    public void setTemplateVersionId(Long value) { templateVersionId = value; }
    public Long getRuleVersionId() { return ruleVersionId; }
    public void setRuleVersionId(Long value) { ruleVersionId = value; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String value) { timezone = value; }
    public List<Long> getCandidateUserIds() { return candidateUserIds; }
    public void setCandidateUserIds(List<Long> value) { candidateUserIds = value; }
    public List<RetryWindow> getWindows() { return windows; }
    public void setWindows(List<RetryWindow> value) { windows = value; }

    public static class RetryWindow
    {
        @NotBlank
        @Size(max = 32)
        private String windowCode;
        @NotNull
        @Min(0)
        private Integer windowOrder;
        @NotNull
        @Min(0)
        private Integer dayOffset;
        private String startTime;
        private String endTime;
        @Min(0)
        private Integer startOffsetMinutes;
        @Positive
        private Integer durationMinutes;
        @NotNull
        @Positive
        private Integer maxAttempts;
        @Positive
        private Integer occurrenceNo;

        public String getWindowCode() { return windowCode; }
        public void setWindowCode(String value) { windowCode = value; }
        public Integer getWindowOrder() { return windowOrder; }
        public void setWindowOrder(Integer value) { windowOrder = value; }
        public Integer getDayOffset() { return dayOffset; }
        public void setDayOffset(Integer value) { dayOffset = value; }
        public String getStartTime() { return startTime; }
        public void setStartTime(String value) { startTime = value; }
        public String getEndTime() { return endTime; }
        public void setEndTime(String value) { endTime = value; }
        public Integer getStartOffsetMinutes() { return startOffsetMinutes; }
        public void setStartOffsetMinutes(Integer value) { startOffsetMinutes = value; }
        public Integer getDurationMinutes() { return durationMinutes; }
        public void setDurationMinutes(Integer value) { durationMinutes = value; }
        public Integer getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(Integer value) { maxAttempts = value; }
        public Integer getOccurrenceNo() { return occurrenceNo; }
        public void setOccurrenceNo(Integer value) { occurrenceNo = value; }
    }
}
