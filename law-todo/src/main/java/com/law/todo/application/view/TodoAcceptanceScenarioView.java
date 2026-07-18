package com.law.todo.application.view;

import java.time.LocalDateTime;

public record TodoAcceptanceScenarioView(Long scenarioId, String scenarioCode, String scenarioName,
        String deliveryPhase, String businessPath, String preconditionsJson, String stepsJson,
        String expectedOutcomesJson, String datasetRef, String datasetChecksum, Integer datasetVersion,
        Long ownerUserId, String ownerUserName, String ownerNickName, Long acceptorUserId,
        String acceptorUserName, String acceptorNickName, Long reviewerUserId, String reviewerUserName,
        String reviewerNickName, LocalDateTime dueAt, String status, String conclusion, String reviewedBy,
        LocalDateTime reviewedTime, String createBy, LocalDateTime createTime, String updateBy,
        LocalDateTime updateTime, int version)
{
}
