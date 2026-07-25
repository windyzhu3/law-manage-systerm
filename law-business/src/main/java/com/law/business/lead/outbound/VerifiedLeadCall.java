package com.law.business.lead.outbound;

import java.time.LocalDateTime;

public record VerifiedLeadCall(Long leadId,Long todoId,String channel,String externalCallId,
        LocalDateTime startedAt,LocalDateTime endedAt,Integer durationSeconds,String callResult,
        Long recordingFileObjectId,String providerNotes,String providerSummaryHash) { }
