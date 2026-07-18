package com.law.todo.application.view;

import java.util.List;

public record TodoFileSecurityReadinessView(String gateCode,int total,int ready,int sourceUnresolved,int runtimeMissing,
        boolean gateReady,boolean objectModelReady,boolean tokenControlReady,boolean accessAuditReady,
        boolean cleanupCompensationReady,List<TodoFileSecurityRequirementView> requirements)
{
}
