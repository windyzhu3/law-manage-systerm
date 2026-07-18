package com.law.todo.application.view;

public record TodoHistoricalMigrationRequirementView(
        Long requirementId,String gateCode,String requirementCode,String requirementName,String checkKind,
        String sourceStatus,String sourceRef,String decisionRef,String readinessStatus,String remark)
{
}
