package com.law.todo.application.view;

public record TodoFileSecurityRequirementView(Long requirementId,String gateCode,String requirementCode,String requirementName,
        String checkKind,String sourceStatus,String sourceRef,String readinessStatus,String remark)
{
}
