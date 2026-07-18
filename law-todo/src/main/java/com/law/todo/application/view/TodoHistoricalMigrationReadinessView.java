package com.law.todo.application.view;

import java.util.List;

public record TodoHistoricalMigrationReadinessView(
        String gateCode,int total,int ready,int sourceUnresolved,int runtimeMissing,int runtimeInvalid,boolean gateReady,
        int historicalCaseCount,int historicalTodoCount,int orphanTodoVersionCount,boolean caseBusinessLineColumnExists,
        List<TodoHistoricalMigrationRequirementView> requirements)
{
}
