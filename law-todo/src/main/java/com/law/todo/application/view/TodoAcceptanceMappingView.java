package com.law.todo.application.view;

import java.time.LocalDateTime;

public record TodoAcceptanceMappingView(Long mappingId, String acceptanceRef, String templateCode,
        String dimensionCode, Long scenarioId, String scenarioCode, String scenarioName,
        String scenarioStatus, String plannedTestRef, String evidenceNote, Long ownerUserId,
        String ownerUserName, String ownerNickName, Long reviewerUserId, String reviewerUserName,
        String reviewerNickName, LocalDateTime dueAt, String status, String conclusion, String reviewedBy,
        LocalDateTime reviewedTime, String updateBy, LocalDateTime updateTime, int version)
{
}
