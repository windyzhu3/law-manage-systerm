package com.law.todo.application.view;

public record TodoAcceptanceRequirementView(Long requirementId, String gateCode, String requirementCode,
        String requirementName, String checkKind, String sourceRef, String readinessStatus, String remark)
{
}
