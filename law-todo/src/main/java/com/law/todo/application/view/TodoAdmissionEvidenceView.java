package com.law.todo.application.view;

import java.time.LocalDateTime;

public record TodoAdmissionEvidenceView(
        Long evidenceId,
        String evidenceCode,
        String gateCode,
        String category,
        String title,
        String description,
        String deliveryPhase,
        String status,
        Long ownerUserId,
        String ownerUserName,
        String ownerNickName,
        Long reviewerUserId,
        String reviewerUserName,
        String reviewerNickName,
        LocalDateTime dueAt,
        String artifactRef,
        String conclusion,
        String reviewedBy,
        LocalDateTime reviewedTime,
        String updateBy,
        LocalDateTime updateTime,
        int version) { }
