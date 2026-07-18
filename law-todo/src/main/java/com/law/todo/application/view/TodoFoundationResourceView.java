package com.law.todo.application.view;

public record TodoFoundationResourceView(
        Long resourceId,
        String gateCode,
        String resourceType,
        String resourceCode,
        String domainCode,
        String deliveryPhase,
        String sourceRef,
        String sourceStatus,
        String decisionRef,
        String expectedValuesJson,
        int minimumActiveItems,
        boolean resourceExists,
        int activeItemCount,
        int expectedItemCount,
        int matchedExpectedItemCount,
        String readinessStatus,
        String remark)
{
}
